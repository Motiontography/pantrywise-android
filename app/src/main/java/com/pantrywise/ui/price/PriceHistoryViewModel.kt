package com.pantrywise.ui.price

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.dao.PriceDao
import com.pantrywise.data.local.dao.StoreDao
import com.pantrywise.data.local.entity.PriceAlertEntity
import com.pantrywise.data.local.entity.PriceRecordEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorePrice(
    val storeId: String,
    val storeName: String,
    val price: Double,
    val recordedAt: Long
)

data class PriceHistoryUiState(
    val priceRecords: List<PriceRecordEntity> = emptyList(),
    val storeComparison: List<StorePrice> = emptyList(),
    val currentPrice: Double? = null,
    val lowestPrice: Double? = null,
    val highestPrice: Double? = null,
    val averagePrice: Double? = null,
    val priceChange: Double? = null,
    val hasAlert: Boolean = false,
    val isLoading: Boolean = false
)

@HiltViewModel
class PriceHistoryViewModel @Inject constructor(
    private val priceDao: PriceDao,
    private val storeDao: StoreDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PriceHistoryUiState())
    val uiState: StateFlow<PriceHistoryUiState> = _uiState.asStateFlow()

    private var currentProductId: String? = null

    fun loadPriceHistory(productId: String) {
        currentProductId = productId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Get all price records for this product
                val records = priceDao.getPriceHistoryForProduct(productId).first()

                // Calculate statistics
                val prices = records.map { it.price }
                val currentPrice = records.firstOrNull()?.price
                val lowestPrice = prices.minOrNull()
                val highestPrice = prices.maxOrNull()
                val averagePrice = if (prices.isNotEmpty()) prices.average() else null

                // Calculate price change (compare most recent to previous)
                val priceChange = if (records.size >= 2) {
                    val recent = records[0].price
                    val previous = records[1].price
                    ((recent - previous) / previous) * 100
                } else null

                // Get store comparison (latest price per store)
                val storeComparison = getStoreComparison(records)

                // Check if alert exists
                val hasAlert = priceDao.getAlertForProduct(productId) != null

                _uiState.update {
                    it.copy(
                        priceRecords = records,
                        storeComparison = storeComparison,
                        currentPrice = currentPrice,
                        lowestPrice = lowestPrice,
                        highestPrice = highestPrice,
                        averagePrice = averagePrice,
                        priceChange = priceChange,
                        hasAlert = hasAlert,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun getStoreComparison(records: List<PriceRecordEntity>): List<StorePrice> {
        // Group by store and get latest price for each
        val latestByStore = records.groupBy { it.storeId }
            .mapValues { (_, storeRecords) -> storeRecords.maxByOrNull { it.recordedAt } }
            .values
            .filterNotNull()

        return latestByStore.mapNotNull { record ->
            val store = storeDao.getStoreById(record.storeId)
            store?.let {
                StorePrice(
                    storeId = record.storeId,
                    storeName = it.name,
                    price = record.price,
                    recordedAt = record.recordedAt
                )
            }
        }.sortedBy { it.price }
    }

    fun toggleAlert(productId: String) {
        viewModelScope.launch {
            val existingAlert = priceDao.getAlertForProduct(productId)

            if (existingAlert != null) {
                // Delete existing alert
                priceDao.deleteAlert(existingAlert.id)
                _uiState.update { it.copy(hasAlert = false) }
            } else {
                // Create new alert at current price
                val currentPrice = _uiState.value.currentPrice ?: return@launch
                val alert = PriceAlertEntity(
                    productId = productId,
                    targetPrice = currentPrice * 0.9 // Alert when price drops 10%
                )
                priceDao.insertAlert(alert)
                _uiState.update { it.copy(hasAlert = true) }
            }
        }
    }
}
