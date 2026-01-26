package com.pantrywise.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.dao.ProductDao
import com.pantrywise.data.local.dao.ShoppingListDao
import com.pantrywise.data.local.entity.CartItem
import com.pantrywise.data.local.entity.ShoppingListItemEntity
import com.pantrywise.data.repository.ShoppingRepository
import com.pantrywise.domain.usecase.CompletionSummary
import com.pantrywise.domain.usecase.ReconcileCartUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReconciliationUiState(
    val isLoading: Boolean = true,
    val plannedItems: List<CartItem> = emptyList(),
    val missingItems: List<ShoppingListItemEntity> = emptyList(),
    val extraItems: List<CartItem> = emptyList(),
    val alreadyStockedItems: List<CartItem> = emptyList(),
    val productNames: Map<String, String> = emptyMap(),
    val summary: CompletionSummary? = null,
    val error: String? = null
)

@HiltViewModel
class ReconciliationViewModel @Inject constructor(
    private val shoppingRepository: ShoppingRepository,
    private val shoppingListDao: ShoppingListDao,
    private val productDao: ProductDao,
    private val reconcileCartUseCase: ReconcileCartUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReconciliationUiState())
    val uiState: StateFlow<ReconciliationUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null
    private var currentListId: String? = null

    fun loadReconciliation(sessionId: String) {
        currentSessionId = sessionId

        viewModelScope.launch {
            // Get active shopping list
            val activeList = shoppingListDao.getActiveShoppingList()
            currentListId = activeList?.id

            // Get reconciliation data
            val reconciliation = reconcileCartUseCase.getReconciliation(sessionId, currentListId)
            val summary = reconcileCartUseCase.getCompletionSummary(sessionId, currentListId)

            // Collect all product IDs
            val allProductIds = mutableSetOf<String>()
            allProductIds.addAll(reconciliation.plannedItems.map { it.productId })
            allProductIds.addAll(reconciliation.missingItems.map { it.productId })
            allProductIds.addAll(reconciliation.extraItems.map { it.productId })
            allProductIds.addAll(reconciliation.alreadyStockedItems.map { it.productId })

            // Load product names
            val productNames = allProductIds.associateWith { productId ->
                productDao.getProductById(productId)?.name ?: "Unknown Product"
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    plannedItems = reconciliation.plannedItems,
                    missingItems = reconciliation.missingItems,
                    extraItems = reconciliation.extraItems,
                    alreadyStockedItems = reconciliation.alreadyStockedItems,
                    productNames = productNames,
                    summary = summary
                )
            }
        }
    }

    suspend fun completeSession() {
        val sessionId = currentSessionId ?: return

        val result = reconcileCartUseCase.completeSession(sessionId, currentListId)

        if (result.isFailure) {
            _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
        }
    }

    suspend fun abandonSession() {
        val sessionId = currentSessionId ?: return
        reconcileCartUseCase.abandonSession(sessionId)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
