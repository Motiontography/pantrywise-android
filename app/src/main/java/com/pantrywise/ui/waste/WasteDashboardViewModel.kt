package com.pantrywise.ui.waste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.dao.MonthlyWasteTrend
import com.pantrywise.data.local.dao.TopWastedProduct
import com.pantrywise.data.local.dao.WasteDao
import com.pantrywise.data.local.entity.WasteByCategory
import com.pantrywise.data.local.entity.WasteByReason
import com.pantrywise.data.local.entity.WasteEventEntity
import com.pantrywise.data.local.entity.WasteReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

sealed class WasteDashboardEvent {
    data object None : WasteDashboardEvent()
    data object WasteLogged : WasteDashboardEvent()
    data class Error(val message: String) : WasteDashboardEvent()
}

data class WasteDashboardUiState(
    val selectedPeriod: WastePeriod = WastePeriod.MONTH,
    val totalItems: Int = 0,
    val totalCost: Double = 0.0,
    val wasteByReason: List<WasteByReason> = emptyList(),
    val wasteByCategory: List<WasteByCategory> = emptyList(),
    val topWastedProducts: List<TopWastedProduct> = emptyList(),
    val monthlyTrend: List<MonthlyWasteTrend> = emptyList(),
    val isLoading: Boolean = false,
    val event: WasteDashboardEvent = WasteDashboardEvent.None
)

@HiltViewModel
class WasteDashboardViewModel @Inject constructor(
    private val wasteDao: WasteDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(WasteDashboardUiState())
    val uiState: StateFlow<WasteDashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun selectPeriod(period: WastePeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val (startDate, endDate) = getDateRange(_uiState.value.selectedPeriod)

            try {
                // Load total items
                val totalItems = wasteDao.getWasteCountInRange(startDate, endDate)

                // Load total cost
                val totalCost = wasteDao.getTotalWasteCostInRange(startDate, endDate) ?: 0.0

                // Load waste by reason
                val wasteByReason = wasteDao.getWasteByReasonInRange(startDate, endDate)

                // Load waste by category
                val wasteByCategory = wasteDao.getWasteByCategoryInRange(startDate, endDate)

                // Load top wasted products
                val topWastedProducts = wasteDao.getTopWastedProducts(startDate, endDate, 10)

                // Load monthly trend (last 6 months)
                val trendStartDate = LocalDate.now().minusMonths(6)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val monthlyTrend = wasteDao.getMonthlyWasteTrend(trendStartDate)

                _uiState.update {
                    it.copy(
                        totalItems = totalItems,
                        totalCost = totalCost,
                        wasteByReason = wasteByReason,
                        wasteByCategory = wasteByCategory,
                        topWastedProducts = topWastedProducts,
                        monthlyTrend = monthlyTrend,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        event = WasteDashboardEvent.Error(e.message ?: "Failed to load data")
                    )
                }
            }
        }
    }

    private fun getDateRange(period: WastePeriod): Pair<Long, Long> {
        val now = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val endDate = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val startDate = when (period) {
            WastePeriod.WEEK -> now.minusWeeks(1)
            WastePeriod.MONTH -> now.minusMonths(1)
            WastePeriod.THREE_MONTHS -> now.minusMonths(3)
            WastePeriod.YEAR -> now.minusYears(1)
        }.atStartOfDay(zone).toInstant().toEpochMilli()

        return Pair(startDate, endDate)
    }

    fun logWaste(
        productName: String,
        category: String,
        quantity: Double,
        unit: String,
        reason: WasteReason,
        estimatedCost: Double?,
        notes: String?
    ) {
        viewModelScope.launch {
            try {
                val wasteEvent = WasteEventEntity(
                    productId = null,
                    inventoryItemId = null,
                    productName = productName,
                    category = category,
                    quantity = quantity,
                    unit = unit,
                    reason = reason,
                    estimatedCost = estimatedCost,
                    notes = notes
                )

                wasteDao.insertWasteEvent(wasteEvent)

                _uiState.update { it.copy(event = WasteDashboardEvent.WasteLogged) }
                loadData()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(event = WasteDashboardEvent.Error(e.message ?: "Failed to log waste"))
                }
            }
        }
    }

    fun clearEvent() {
        _uiState.update { it.copy(event = WasteDashboardEvent.None) }
    }
}
