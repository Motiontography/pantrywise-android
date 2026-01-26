package com.pantrywise.ui.price

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.dao.PriceDao
import com.pantrywise.data.local.dao.ProductDao
import com.pantrywise.data.local.dao.StoreDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PriceBookProduct(
    val productId: String,
    val productName: String,
    val category: String,
    val currentPrice: Double,
    val lowestPrice: Double,
    val highestPrice: Double,
    val lowestPriceStore: String,
    val storeCount: Int,
    val potentialSavings: Double,
    val priceChange: Double?,
    val lastUpdated: Long
)

data class PriceBookUiState(
    val products: List<PriceBookProduct> = emptyList(),
    val searchQuery: String = "",
    val sortOption: PriceBookSortOption = PriceBookSortOption.NAME,
    val selectedCategories: Set<String> = emptySet(),
    val selectedStores: Set<String> = emptySet(),
    val availableCategories: List<String> = emptyList(),
    val availableStores: List<String> = emptyList(),
    val totalProducts: Int = 0,
    val totalPotentialSavings: Double = 0.0,
    val activeAlertsCount: Int = 0,
    val activeFiltersCount: Int = 0,
    val isLoading: Boolean = false
)

@HiltViewModel
class PriceBookViewModel @Inject constructor(
    private val priceDao: PriceDao,
    private val productDao: ProductDao,
    private val storeDao: StoreDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PriceBookUiState())
    val uiState: StateFlow<PriceBookUiState> = _uiState.asStateFlow()

    private var allProducts: List<PriceBookProduct> = emptyList()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Get all products with price records
                val productsWithPrices = priceDao.getProductsWithPriceRecords()

                // Build price book products
                val priceBookProducts = productsWithPrices.mapNotNull { productId ->
                    buildPriceBookProduct(productId)
                }

                allProducts = priceBookProducts

                // Get available categories and stores
                val categories = priceBookProducts.map { it.category }.distinct().sorted()
                val stores = storeDao.getAllStores().first().map { it.name }.sorted()

                // Calculate totals
                val totalSavings = priceBookProducts.sumOf { it.potentialSavings }
                val alertsCount = priceDao.getActiveAlertsCount()

                _uiState.update {
                    it.copy(
                        products = applySortAndFilter(priceBookProducts),
                        availableCategories = categories,
                        availableStores = stores,
                        totalProducts = priceBookProducts.size,
                        totalPotentialSavings = totalSavings,
                        activeAlertsCount = alertsCount,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun buildPriceBookProduct(productId: String): PriceBookProduct? {
        val product = productDao.getProductById(productId) ?: return null
        val priceRecords = priceDao.getPriceHistoryForProduct(productId).first()

        if (priceRecords.isEmpty()) return null

        val prices = priceRecords.map { it.price }
        val currentPrice = priceRecords.first().price
        val lowestPrice = prices.minOrNull() ?: currentPrice
        val highestPrice = prices.maxOrNull() ?: currentPrice

        // Find lowest price store
        val lowestPriceRecord = priceRecords.minByOrNull { it.price }
        val lowestPriceStore = lowestPriceRecord?.let {
            storeDao.getStoreById(it.storeId)?.name
        } ?: "Unknown"

        // Get unique stores
        val storeCount = priceRecords.map { it.storeId }.distinct().size

        // Calculate potential savings (current vs lowest)
        val potentialSavings = currentPrice - lowestPrice

        // Calculate price change
        val priceChange = if (priceRecords.size >= 2) {
            val recent = priceRecords[0].price
            val previous = priceRecords[1].price
            ((recent - previous) / previous) * 100
        } else null

        return PriceBookProduct(
            productId = productId,
            productName = product.name,
            category = product.category ?: "Other",
            currentPrice = currentPrice,
            lowestPrice = lowestPrice,
            highestPrice = highestPrice,
            lowestPriceStore = lowestPriceStore,
            storeCount = storeCount,
            potentialSavings = potentialSavings,
            priceChange = priceChange,
            lastUpdated = priceRecords.first().recordedAt
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                products = applySortAndFilter(allProducts, query, it.sortOption, it.selectedCategories, it.selectedStores)
            )
        }
    }

    fun setSortOption(option: PriceBookSortOption) {
        _uiState.update {
            it.copy(
                sortOption = option,
                products = applySortAndFilter(allProducts, it.searchQuery, option, it.selectedCategories, it.selectedStores)
            )
        }
    }

    fun setFilters(categories: Set<String>, stores: Set<String>) {
        val filtersCount = categories.size + stores.size
        _uiState.update {
            it.copy(
                selectedCategories = categories,
                selectedStores = stores,
                activeFiltersCount = filtersCount,
                products = applySortAndFilter(allProducts, it.searchQuery, it.sortOption, categories, stores)
            )
        }
    }

    private fun applySortAndFilter(
        products: List<PriceBookProduct>,
        searchQuery: String = _uiState.value.searchQuery,
        sortOption: PriceBookSortOption = _uiState.value.sortOption,
        selectedCategories: Set<String> = _uiState.value.selectedCategories,
        selectedStores: Set<String> = _uiState.value.selectedStores
    ): List<PriceBookProduct> {
        var filtered = products

        // Apply search filter
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.productName.contains(searchQuery, ignoreCase = true) ||
                        it.category.contains(searchQuery, ignoreCase = true)
            }
        }

        // Apply category filter
        if (selectedCategories.isNotEmpty()) {
            filtered = filtered.filter { it.category in selectedCategories }
        }

        // Apply store filter (products that have prices at selected stores)
        // Note: This would need additional logic to filter by stores,
        // for now we skip this filter if empty

        // Apply sort
        filtered = when (sortOption) {
            PriceBookSortOption.NAME -> filtered.sortedBy { it.productName.lowercase() }
            PriceBookSortOption.PRICE_LOW_HIGH -> filtered.sortedBy { it.currentPrice }
            PriceBookSortOption.PRICE_HIGH_LOW -> filtered.sortedByDescending { it.currentPrice }
            PriceBookSortOption.RECENTLY_UPDATED -> filtered.sortedByDescending { it.lastUpdated }
            PriceBookSortOption.BIGGEST_SAVINGS -> filtered.sortedByDescending { it.potentialSavings }
        }

        return filtered
    }
}
