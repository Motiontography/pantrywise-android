package com.pantrywise.data.repository

import com.pantrywise.data.local.dao.InventoryDao
import com.pantrywise.data.local.dao.PreferencesDao
import com.pantrywise.data.local.entity.ActionEventEntity
import com.pantrywise.data.local.entity.InventoryItemEntity
import com.pantrywise.domain.model.ActionType
import com.pantrywise.domain.model.LocationType
import com.pantrywise.domain.model.SourceType
import com.pantrywise.domain.model.StockStatus
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val preferencesDao: PreferencesDao
) {
    fun getAllInventoryItems(): Flow<List<InventoryItemEntity>> = inventoryDao.getAllInventoryItems()

    suspend fun getInventoryItemById(id: String): InventoryItemEntity? = inventoryDao.getInventoryItemById(id)

    fun getInventoryItemsByProductId(productId: String): Flow<List<InventoryItemEntity>> =
        inventoryDao.getInventoryItemsByProductId(productId)

    suspend fun getInventoryItemByProductAndLocation(productId: String, location: LocationType): InventoryItemEntity? =
        inventoryDao.getInventoryItemByProductAndLocation(productId, location)

    fun getInventoryItemsByLocation(location: LocationType): Flow<List<InventoryItemEntity>> =
        inventoryDao.getInventoryItemsByLocation(location)

    fun getInventoryItemsByStatus(status: StockStatus): Flow<List<InventoryItemEntity>> =
        inventoryDao.getInventoryItemsByStatus(status)

    fun getLowStockItems(): Flow<List<InventoryItemEntity>> = inventoryDao.getLowStockItems()

    fun getOutOfStockItems(): Flow<List<InventoryItemEntity>> = inventoryDao.getOutOfStockItems()

    fun getExpiringItems(daysAhead: Int = 7): Flow<List<InventoryItemEntity>> {
        val thresholdDate = System.currentTimeMillis() + (daysAhead * 24 * 60 * 60 * 1000L)
        return inventoryDao.getExpiringItems(thresholdDate)
    }

    fun getExpiredItems(): Flow<List<InventoryItemEntity>> {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return inventoryDao.getExpiredItems(today)
    }

    suspend fun addInventoryItem(item: InventoryItemEntity, source: SourceType? = null): Long {
        // Calculate stock status before inserting
        val itemWithStatus = item.copy(stockStatus = item.calculateStockStatus())
        val id = inventoryDao.insert(itemWithStatus)

        // Log action event
        preferencesDao.insertActionEvent(
            ActionEventEntity(
                type = ActionType.INVENTORY_ADDED,
                entityType = "InventoryItem",
                entityId = item.id,
                source = source
            )
        )

        return id
    }

    suspend fun updateInventoryItem(item: InventoryItemEntity) {
        val itemWithStatus = item.copy(
            stockStatus = item.calculateStockStatus(),
            updatedAt = System.currentTimeMillis()
        )
        inventoryDao.update(itemWithStatus)
    }

    suspend fun deleteInventoryItem(item: InventoryItemEntity) {
        inventoryDao.delete(item)

        // Log action event
        preferencesDao.insertActionEvent(
            ActionEventEntity(
                type = ActionType.INVENTORY_REMOVED,
                entityType = "InventoryItem",
                entityId = item.id
            )
        )
    }

    suspend fun deleteInventoryItemById(id: String) = inventoryDao.deleteById(id)

    /**
     * Adjusts the quantity of an inventory item.
     * Positive values add, negative values subtract.
     */
    suspend fun adjustQuantity(id: String, adjustment: Double): Boolean {
        val item = inventoryDao.getInventoryItemById(id) ?: return false
        val newQuantity = (item.quantityOnHand + adjustment).coerceAtLeast(0.0)

        val updatedItem = item.copy(
            quantityOnHand = newQuantity,
            updatedAt = System.currentTimeMillis()
        )
        val itemWithStatus = updatedItem.copy(stockStatus = updatedItem.calculateStockStatus())

        inventoryDao.update(itemWithStatus)
        return true
    }

    /**
     * Moves an inventory item to a different location.
     */
    suspend fun moveItem(id: String, newLocation: LocationType): Boolean {
        val item = inventoryDao.getInventoryItemById(id) ?: return false

        // Check if there's already an item at the new location for the same product
        val existingItem = inventoryDao.getInventoryItemByProductAndLocation(item.productId, newLocation)

        if (existingItem != null) {
            // Merge quantities
            val mergedQuantity = existingItem.quantityOnHand + item.quantityOnHand
            val updatedExisting = existingItem.copy(
                quantityOnHand = mergedQuantity,
                updatedAt = System.currentTimeMillis()
            )
            inventoryDao.update(updatedExisting.copy(stockStatus = updatedExisting.calculateStockStatus()))
            inventoryDao.delete(item)
        } else {
            // Simply update location
            inventoryDao.updateLocation(id, newLocation)
        }

        // Log action event
        preferencesDao.insertActionEvent(
            ActionEventEntity(
                type = ActionType.INVENTORY_MOVED,
                entityType = "InventoryItem",
                entityId = id,
                payloadJson = "{\"newLocation\": \"${newLocation.name}\"}"
            )
        )

        return true
    }

    /**
     * Updates stock status for all items that need recalculation.
     */
    suspend fun refreshAllStockStatuses() {
        // This would typically be done in a background job
        // For now, individual updates handle their own status calculation
    }

    suspend fun getInventoryItemCount(): Int = inventoryDao.getInventoryItemCount()

    suspend fun getInventoryItemCountByLocation(location: LocationType): Int =
        inventoryDao.getInventoryItemCountByLocation(location)
}
