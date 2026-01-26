package com.pantrywise.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.dao.AuditDao
import com.pantrywise.data.local.dao.AuditSummaryData
import com.pantrywise.data.local.dao.InventoryDao
import com.pantrywise.data.local.dao.ProductDao
import com.pantrywise.data.local.entity.AuditAction
import com.pantrywise.data.local.entity.AuditItemEntity
import com.pantrywise.data.local.entity.AuditSessionEntity
import com.pantrywise.data.local.entity.AuditStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AuditUiState(
    val sessions: List<AuditSessionEntity> = emptyList(),
    val activeSession: AuditSessionEntity? = null,
    val currentItems: List<AuditItemEntity> = emptyList(),
    val pendingItems: List<AuditItemEntity> = emptyList(),
    val completedItems: List<AuditItemEntity> = emptyList(),
    val summary: AuditSummaryData? = null,
    val isLoading: Boolean = false,
    val showStartDialog: Boolean = false,
    val showCompleteDialog: Boolean = false,
    val selectedItem: AuditItemEntity? = null,
    val error: String? = null
)

@HiltViewModel
class AuditViewModel @Inject constructor(
    private val auditDao: AuditDao,
    private val inventoryDao: InventoryDao,
    private val productDao: ProductDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuditUiState())
    val uiState: StateFlow<AuditUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
        checkForActiveSession()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            auditDao.getAllAuditSessions().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
            }
        }
    }

    private fun checkForActiveSession() {
        viewModelScope.launch {
            val activeSession = auditDao.getActiveAuditSession()
            if (activeSession != null) {
                _uiState.update { it.copy(activeSession = activeSession) }
                loadSessionItems(activeSession.id)
            }
        }
    }

    fun showStartDialog() {
        _uiState.update { it.copy(showStartDialog = true) }
    }

    fun hideStartDialog() {
        _uiState.update { it.copy(showStartDialog = false) }
    }

    fun startNewAudit(name: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showStartDialog = false) }

            try {
                // Get all inventory items
                val inventoryItems = inventoryDao.getAllInventoryItems().first()

                // Create audit session
                val session = AuditSessionEntity(
                    id = UUID.randomUUID().toString(),
                    name = name ?: "Audit ${java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}",
                    totalItems = inventoryItems.size,
                    status = AuditStatus.IN_PROGRESS
                )

                auditDao.insertAuditSession(session)

                // Create audit items for each inventory item
                val auditItems = inventoryItems.map { item ->
                    val product = productDao.getProductById(item.productId)
                    AuditItemEntity(
                        sessionId = session.id,
                        inventoryItemId = item.id,
                        productName = product?.name ?: "Unknown Product",
                        category = product?.category ?: "Uncategorized",
                        location = item.location.displayName,
                        expectedQuantity = item.quantityOnHand,
                        unit = item.unit.displayName
                    )
                }

                auditDao.insertAuditItems(auditItems)

                _uiState.update {
                    it.copy(
                        activeSession = session,
                        isLoading = false
                    )
                }

                loadSessionItems(session.id)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to start audit: ${e.message}"
                    )
                }
            }
        }
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            val session = auditDao.getAuditSessionById(sessionId)
            _uiState.update { it.copy(activeSession = session) }
            if (session != null) {
                loadSessionItems(sessionId)
            }
        }
    }

    private fun loadSessionItems(sessionId: String) {
        viewModelScope.launch {
            // Load pending items
            launch {
                auditDao.getPendingAuditItems(sessionId).collect { items ->
                    _uiState.update { it.copy(pendingItems = items) }
                }
            }

            // Load completed items
            launch {
                auditDao.getCompletedAuditItems(sessionId).collect { items ->
                    _uiState.update { it.copy(completedItems = items) }
                }
            }

            // Load summary
            val summary = auditDao.getAuditSummary(sessionId)
            _uiState.update { it.copy(summary = summary) }
        }
    }

    fun selectItem(item: AuditItemEntity) {
        _uiState.update { it.copy(selectedItem = item) }
    }

    fun clearSelectedItem() {
        _uiState.update { it.copy(selectedItem = null) }
    }

    fun confirmItem(item: AuditItemEntity) {
        recordAuditResult(item, item.expectedQuantity, AuditAction.CONFIRMED, null)
    }

    fun adjustItem(item: AuditItemEntity, actualQuantity: Double, notes: String?) {
        recordAuditResult(item, actualQuantity, AuditAction.ADJUSTED, notes)
    }

    fun removeItem(item: AuditItemEntity, notes: String?) {
        recordAuditResult(item, 0.0, AuditAction.REMOVED, notes)
    }

    fun skipItem(item: AuditItemEntity) {
        recordAuditResult(item, item.expectedQuantity, AuditAction.SKIPPED, null)
    }

    private fun recordAuditResult(
        item: AuditItemEntity,
        actualQuantity: Double,
        action: AuditAction,
        notes: String?
    ) {
        viewModelScope.launch {
            try {
                auditDao.recordAuditResult(
                    id = item.id,
                    actualQuantity = actualQuantity,
                    action = action,
                    notes = notes
                )

                val wasAdjusted = action == AuditAction.ADJUSTED
                val wasRemoved = action == AuditAction.REMOVED

                _uiState.value.activeSession?.let { session ->
                    auditDao.incrementAuditProgress(session.id, wasAdjusted, wasRemoved)

                    // Update inventory if adjusted or removed
                    if (wasAdjusted && item.inventoryItemId != null) {
                        inventoryDao.updateQuantity(item.inventoryItemId, actualQuantity)
                    } else if (wasRemoved && item.inventoryItemId != null) {
                        inventoryDao.deleteById(item.inventoryItemId)
                    }

                    // Refresh session
                    val updatedSession = auditDao.getAuditSessionById(session.id)
                    _uiState.update { it.copy(activeSession = updatedSession, selectedItem = null) }

                    // Refresh summary
                    val summary = auditDao.getAuditSummary(session.id)
                    _uiState.update { it.copy(summary = summary) }
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to record result: ${e.message}") }
            }
        }
    }

    fun showCompleteDialog() {
        _uiState.update { it.copy(showCompleteDialog = true) }
    }

    fun hideCompleteDialog() {
        _uiState.update { it.copy(showCompleteDialog = false) }
    }

    fun completeAudit() {
        viewModelScope.launch {
            _uiState.value.activeSession?.let { session ->
                auditDao.updateAuditSessionStatus(
                    id = session.id,
                    status = AuditStatus.COMPLETED,
                    completedAt = System.currentTimeMillis()
                )

                _uiState.update {
                    it.copy(
                        activeSession = null,
                        showCompleteDialog = false,
                        pendingItems = emptyList(),
                        completedItems = emptyList(),
                        summary = null
                    )
                }
            }
        }
    }

    fun cancelAudit() {
        viewModelScope.launch {
            _uiState.value.activeSession?.let { session ->
                auditDao.updateAuditSessionStatus(
                    id = session.id,
                    status = AuditStatus.CANCELLED
                )

                _uiState.update {
                    it.copy(
                        activeSession = null,
                        pendingItems = emptyList(),
                        completedItems = emptyList(),
                        summary = null
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun deleteSession(session: AuditSessionEntity) {
        viewModelScope.launch {
            auditDao.deleteAuditSession(session)
        }
    }
}
