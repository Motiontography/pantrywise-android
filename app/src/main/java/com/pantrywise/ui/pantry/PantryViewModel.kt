package com.pantrywise.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.entity.InventoryItemEntity
import com.pantrywise.data.local.entity.ProductEntity
import com.pantrywise.data.repository.InventoryRepository
import com.pantrywise.data.repository.ProductRepository
import com.pantrywise.domain.model.LocationType
import com.pantrywise.domain.model.StockStatus
import com.pantrywise.domain.model.Unit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InventoryItemWithProduct(
    val inventoryItem: InventoryItemEntity,
    val product: ProductEntity
)

data class PantryUiState(
    val isLoading: Boolean = true,
    val items: List<InventoryItemWithProduct> = emptyList(),
    val filteredItems: List<InventoryItemWithProduct> = emptyList(),
    val selectedLocation: LocationType? = null,
    val searchQuery: String = "",
    val expiringCount: Int = 0,
    val lowStockCount: Int = 0,
    val error: String? = null
)

@HiltViewModel
class PantryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PantryUiState())
    val uiState: StateFlow<PantryUiState> = _uiState.asStateFlow()

    init {
        loadInventory()
    }

    private fun loadInventory() {
        viewModelScope.launch {
            inventoryRepository.getAllInventoryItems()
                .combine(productRepository.getAllProducts()) { inventory, products ->
                    val productMap = products.associateBy { it.id }
                    inventory.mapNotNull { item ->
                        productMap[item.productId]?.let { product ->
                            InventoryItemWithProduct(item, product)
                        }
                    }
                }
                .collect { items ->
                    val expiringCount = items.count {
                        it.inventoryItem.stockStatus == StockStatus.EXPIRING_SOON ||
                                it.inventoryItem.stockStatus == StockStatus.EXPIRED
                    }
                    val lowStockCount = items.count {
                        it.inventoryItem.stockStatus == StockStatus.LOW ||
                                it.inventoryItem.stockStatus == StockStatus.OUT_OF_STOCK
                    }

                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            items = items,
                            filteredItems = filterItems(items, state.selectedLocation, state.searchQuery),
                            expiringCount = expiringCount,
                            lowStockCount = lowStockCount
                        )
                    }
                }
        }
    }

    fun setLocationFilter(location: LocationType?) {
        _uiState.update { state ->
            state.copy(
                selectedLocation = location,
                filteredItems = filterItems(state.items, location, state.searchQuery)
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredItems = filterItems(state.items, state.selectedLocation, query)
            )
        }
    }

    private fun filterItems(
        items: List<InventoryItemWithProduct>,
        location: LocationType?,
        query: String
    ): List<InventoryItemWithProduct> {
        return items.filter { item ->
            val matchesLocation = location == null || item.inventoryItem.location == location
            val matchesQuery = query.isBlank() ||
                    item.product.name.contains(query, ignoreCase = true) ||
                    item.product.brand?.contains(query, ignoreCase = true) == true ||
                    item.product.category.contains(query, ignoreCase = true)
            matchesLocation && matchesQuery
        }.sortedWith(
            compareBy<InventoryItemWithProduct> { item ->
                when (item.inventoryItem.stockStatus) {
                    StockStatus.EXPIRED -> 0
                    StockStatus.EXPIRING_SOON -> 1
                    StockStatus.OUT_OF_STOCK -> 2
                    StockStatus.LOW -> 3
                    else -> 4
                }
            }.thenBy { it.product.name }
        )
    }

    fun addInventoryItem(
        productId: String,
        quantity: Double,
        unit: Unit,
        location: LocationType,
        expirationDate: Long? = null
    ) {
        viewModelScope.launch {
            val item = InventoryItemEntity(
                productId = productId,
                quantityOnHand = quantity,
                unit = unit,
                location = location,
                expirationDate = expirationDate
            )
            inventoryRepository.addInventoryItem(item)
        }
    }

    fun updateQuantity(itemId: String, newQuantity: Double) {
        viewModelScope.launch {
            val item = inventoryRepository.getInventoryItemById(itemId) ?: return@launch
            val updatedItem = item.copy(quantityOnHand = newQuantity)
            inventoryRepository.updateInventoryItem(updatedItem)
        }
    }

    fun adjustQuantity(itemId: String, adjustment: Double) {
        viewModelScope.launch {
            inventoryRepository.adjustQuantity(itemId, adjustment)
        }
    }

    fun moveItem(itemId: String, newLocation: LocationType) {
        viewModelScope.launch {
            inventoryRepository.moveItem(itemId, newLocation)
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            inventoryRepository.deleteInventoryItemById(itemId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
