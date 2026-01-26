package com.pantrywise.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.entity.*
import com.pantrywise.data.repository.ProductRepository
import com.pantrywise.data.repository.ShoppingRepository
import com.pantrywise.domain.model.SourceType
import com.pantrywise.domain.model.Unit
import com.pantrywise.domain.usecase.GenerateSuggestionsUseCase
import com.pantrywise.domain.usecase.ShoppingSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingListItemWithProduct(
    val listItem: ShoppingListItemEntity,
    val product: ProductEntity
)

data class ShoppingListUiState(
    val isLoading: Boolean = true,
    val shoppingLists: List<ShoppingListEntity> = emptyList(),
    val activeList: ShoppingListEntity? = null,
    val items: List<ShoppingListItemWithProduct> = emptyList(),
    val suggestions: List<ShoppingSuggestion> = emptyList(),
    val hasActiveSession: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val shoppingRepository: ShoppingRepository,
    private val productRepository: ProductRepository,
    private val generateSuggestionsUseCase: GenerateSuggestionsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    init {
        loadShoppingLists()
        checkActiveSession()
    }

    private fun loadShoppingLists() {
        viewModelScope.launch {
            shoppingRepository.getAllShoppingLists()
                .collect { lists ->
                    val activeList = lists.firstOrNull { it.isActive }
                    _uiState.update { it.copy(shoppingLists = lists, activeList = activeList) }

                    activeList?.let { list ->
                        loadItemsForList(list.id)
                        loadSuggestions(list.id)
                    }
                }
        }
    }

    private fun loadItemsForList(listId: String) {
        viewModelScope.launch {
            shoppingRepository.getItemsByListId(listId)
                .combine(productRepository.getAllProducts()) { items, products ->
                    val productMap = products.associateBy { it.id }
                    items.mapNotNull { item ->
                        productMap[item.productId]?.let { product ->
                            ShoppingListItemWithProduct(item, product)
                        }
                    }
                }
                .collect { items ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = items.sortedByDescending { item -> item.listItem.priority }
                        )
                    }
                }
        }
    }

    private fun loadSuggestions(listId: String) {
        viewModelScope.launch {
            val suggestions = generateSuggestionsUseCase(listId)
            _uiState.update { it.copy(suggestions = suggestions) }
        }
    }

    private fun checkActiveSession() {
        viewModelScope.launch {
            val activeSession = shoppingRepository.getActiveShoppingSession()
            _uiState.update { it.copy(hasActiveSession = activeSession != null) }
        }
    }

    fun createNewList(name: String) {
        viewModelScope.launch {
            shoppingRepository.createShoppingList(name)
        }
    }

    fun addItemToList(productId: String, quantity: Double, unit: Unit) {
        viewModelScope.launch {
            val activeList = _uiState.value.activeList ?: return@launch
            shoppingRepository.addItemToList(
                listId = activeList.id,
                productId = productId,
                quantity = quantity,
                unit = unit
            )
        }
    }

    fun addSuggestionToList(suggestion: ShoppingSuggestion) {
        viewModelScope.launch {
            val activeList = _uiState.value.activeList ?: return@launch
            shoppingRepository.addItemToList(
                listId = activeList.id,
                productId = suggestion.product.id,
                quantity = suggestion.suggestedQuantity,
                unit = suggestion.unit,
                reason = suggestion.reason,
                suggestedBy = SourceType.SUGGESTION
            )
            // Refresh suggestions after adding
            loadSuggestions(activeList.id)
        }
    }

    fun removeItem(itemId: String) {
        viewModelScope.launch {
            shoppingRepository.removeItemFromList(itemId)
        }
    }

    fun toggleItemChecked(itemId: String, isChecked: Boolean) {
        viewModelScope.launch {
            shoppingRepository.toggleItemChecked(itemId, isChecked)
        }
    }

    suspend fun startShoppingSession(store: String? = null): String {
        val session = shoppingRepository.startShoppingSession(store)
        _uiState.update { it.copy(hasActiveSession = true) }
        return session.id
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
