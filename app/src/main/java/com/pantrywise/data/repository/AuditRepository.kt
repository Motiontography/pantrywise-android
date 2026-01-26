package com.pantrywise.data.repository

import com.pantrywise.data.local.dao.AuditDao
import com.pantrywise.data.local.dao.InventoryDao
import com.pantrywise.data.local.dao.ProductDao
import com.pantrywise.data.local.entity.AuditAction
import com.pantrywise.data.local.entity.AuditItemEntity
import com.pantrywise.data.local.entity.AuditSessionEntity
import com.pantrywise.data.local.entity.AuditStatus
import com.pantrywise.data.local.entity.AuditSummary
import com.pantrywise.domain.model.LocationType
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditRepository @Inject constructor(
    private val auditDao: AuditDao,
    private val inventoryDao: InventoryDao,
    private val productDao: ProductDao
) {
    fun getAllAuditSessions(): Flow<List<AuditSessionEntity>> = auditDao.getAllAuditSessions()

    fun getAuditSessionsByStatus(status: AuditStatus): Flow<List<AuditSessionEntity>> =
        auditDao.getAuditSessionsByStatus(status)

    suspend fun getAuditSessionById(id: String): AuditSessionEntity? =
        auditDao.getAuditSessionById(id)

    suspend fun getActiveAuditSession(): AuditSessionEntity? =
        auditDao.getActiveAuditSession()

    suspend fun getRecentAuditSessions(limit: Int = 10): List<AuditSessionEntity> =
        auditDao.getRecentAuditSessions(limit)

    suspend fun startNewAudit(name: String? = null, location: LocationType? = null): AuditSessionEntity {
        // Cancel any existing active audit
        val activeAudit = auditDao.getActiveAuditSession()
        if (activeAudit != null) {
            auditDao.updateAuditSessionStatus(
                activeAudit.id,
                AuditStatus.CANCELLED,
                System.currentTimeMillis()
            )
        }

        // Generate default name if not provided
        val auditName = name ?: generateAuditName()

        // Get inventory items to audit
        val inventoryItems = inventoryDao.getAllItemsSnapshot()
        val filteredItems = if (location != null) {
            inventoryItems.filter { it.location == location }
        } else {
            inventoryItems
        }

        // Create the audit session
        val session = AuditSessionEntity(
            name = auditName,
            totalItems = filteredItems.size
        )
        auditDao.insertAuditSession(session)

        // Create audit items for each inventory item
        val auditItems = filteredItems.mapNotNull { item ->
            val product = productDao.getProductById(item.productId)
            if (product != null) {
                AuditItemEntity(
                    sessionId = session.id,
                    inventoryItemId = item.id,
                    productName = product.displayName,
                    category = product.category,
                    location = item.location.name,
                    expectedQuantity = item.quantityOnHand,
                    unit = item.unit.name
                )
            } else {
                null
            }
        }
        auditDao.insertAuditItems(auditItems)

        return session
    }

    private fun generateAuditName(): String {
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return "Audit - ${formatter.format(Date())}"
    }

    fun getAuditItemsForSession(sessionId: String): Flow<List<AuditItemEntity>> =
        auditDao.getAuditItemsForSession(sessionId)

    fun getPendingAuditItems(sessionId: String): Flow<List<AuditItemEntity>> =
        auditDao.getPendingAuditItems(sessionId)

    fun getCompletedAuditItems(sessionId: String): Flow<List<AuditItemEntity>> =
        auditDao.getCompletedAuditItems(sessionId)

    suspend fun confirmItem(itemId: String, notes: String? = null) {
        val item = auditDao.getAuditItemById(itemId) ?: return
        auditDao.recordAuditResult(
            id = itemId,
            actualQuantity = item.expectedQuantity,
            action = AuditAction.CONFIRMED,
            notes = notes
        )
        auditDao.incrementAuditProgress(item.sessionId, wasAdjusted = false, wasRemoved = false)
    }

    suspend fun adjustItem(itemId: String, actualQuantity: Double, notes: String? = null) {
        val item = auditDao.getAuditItemById(itemId) ?: return
        auditDao.recordAuditResult(
            id = itemId,
            actualQuantity = actualQuantity,
            action = AuditAction.ADJUSTED,
            notes = notes
        )
        auditDao.incrementAuditProgress(item.sessionId, wasAdjusted = true, wasRemoved = false)

        // Update the actual inventory
        item.inventoryItemId?.let { inventoryItemId ->
            val inventoryItem = inventoryDao.getItemById(inventoryItemId)
            if (inventoryItem != null) {
                inventoryDao.updateItem(
                    inventoryItem.copy(
                        quantityOnHand = actualQuantity,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun removeItem(itemId: String, notes: String? = null) {
        val item = auditDao.getAuditItemById(itemId) ?: return
        auditDao.recordAuditResult(
            id = itemId,
            actualQuantity = 0.0,
            action = AuditAction.REMOVED,
            notes = notes
        )
        auditDao.incrementAuditProgress(item.sessionId, wasAdjusted = false, wasRemoved = true)

        // Remove from actual inventory
        item.inventoryItemId?.let { inventoryItemId ->
            inventoryDao.deleteItemById(inventoryItemId)
        }
    }

    suspend fun skipItem(itemId: String, notes: String? = null) {
        val item = auditDao.getAuditItemById(itemId) ?: return
        auditDao.recordAuditResult(
            id = itemId,
            actualQuantity = item.expectedQuantity,
            action = AuditAction.SKIPPED,
            notes = notes
        )
        auditDao.incrementAuditProgress(item.sessionId, wasAdjusted = false, wasRemoved = false)
    }

    suspend fun completeAudit(sessionId: String) {
        auditDao.updateAuditSessionStatus(
            id = sessionId,
            status = AuditStatus.COMPLETED,
            completedAt = System.currentTimeMillis()
        )
    }

    suspend fun cancelAudit(sessionId: String) {
        auditDao.updateAuditSessionStatus(
            id = sessionId,
            status = AuditStatus.CANCELLED,
            completedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteAudit(sessionId: String) {
        auditDao.deleteAuditItemsForSession(sessionId)
        auditDao.deleteAuditSessionById(sessionId)
    }

    suspend fun getAuditSummary(sessionId: String): AuditSummary? {
        val data = auditDao.getAuditSummary(sessionId) ?: return null
        return AuditSummary(
            sessionId = data.sessionId,
            totalItems = data.totalItems,
            confirmedItems = data.confirmedItems,
            adjustedItems = data.adjustedItems,
            removedItems = data.removedItems,
            skippedItems = data.skippedItems,
            totalDiscrepancy = data.totalDiscrepancy
        )
    }

    suspend fun getCompletedAuditCount(): Int = auditDao.getCompletedAuditCount()

    suspend fun getAverageDiscrepancyRate(): Double? = auditDao.getAverageDiscrepancyRate()
}
