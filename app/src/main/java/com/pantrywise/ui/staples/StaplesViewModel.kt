package com.pantrywise.ui.staples

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.entity.MinimumStockRuleEntity
import com.pantrywise.data.local.entity.StockAlert
import com.pantrywise.data.repository.MinimumStockRepository
import com.pantrywise.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StaplesState(
    val isLoading: Boolean = true,
    val stapleCount: Int = 0,
    val needRestockCount: Int = 0,
    val wellStockedCount: Int = 0,
    val stapleAlerts: List<StockAlert> = emptyList(),
    val wellStockedStaples: List<MinimumStockRuleEntity> = emptyList(),
    val error: String? = null,
    val showAddStapleSheet: Boolean = false
)

@HiltViewModel
class StaplesViewModel @Inject constructor(
    private val minimumStockRepository: MinimumStockRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StaplesState())
    val state: StateFlow<StaplesState> = _state.asStateFlow()

    // Products flow for the add staple sheet
    val products = productRepository.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadStaples()
    }

    fun loadStaples() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // Get staple count
                minimumStockRepository.getStapleCount().collect { count ->
                    _state.update { it.copy(stapleCount = count) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.localizedMessage) }
            }

            try {
                // Get alerts and well-stocked
                val alerts = minimumStockRepository.checkStapleStockLevels()
                val wellStocked = minimumStockRepository.getWellStockedStaples()

                _state.update {
                    it.copy(
                        isLoading = false,
                        stapleAlerts = alerts,
                        wellStockedStaples = wellStocked,
                        needRestockCount = alerts.size,
                        wellStockedCount = wellStocked.size
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage
                    )
                }
            }
        }
    }

    fun showAddStapleSheet() {
        _state.update { it.copy(showAddStapleSheet = true) }
    }

    fun hideAddStapleSheet() {
        _state.update { it.copy(showAddStapleSheet = false) }
    }

    fun addStaple(
        productId: String,
        productName: String,
        minimumQuantity: Double,
        reorderQuantity: Double
    ) {
        viewModelScope.launch {
            try {
                minimumStockRepository.createRule(
                    productId = productId,
                    productName = productName,
                    minimumQuantity = minimumQuantity,
                    reorderQuantity = reorderQuantity,
                    autoAddToList = true,
                    isStaple = true
                )
                hideAddStapleSheet()
                loadStaples()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.localizedMessage) }
            }
        }
    }

    fun deleteStaple(rule: MinimumStockRuleEntity) {
        viewModelScope.launch {
            try {
                minimumStockRepository.deleteRule(rule)
                loadStaples()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.localizedMessage) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
