package com.pantrywise.data.repository

import com.pantrywise.data.local.dao.PriceDao
import com.pantrywise.data.local.entity.PriceAlertEntity
import com.pantrywise.data.local.entity.PriceRecordEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class PriceStatistics(
    val currentPrice: Double?,
    val lowestPrice: Double?,
    val highestPrice: Double?,
    val averagePrice: Double?,
    val priceCount: Int
)

data class StorePriceComparison(
    val storeId: String,
    val storeName: String?,
    val price: Double,
    val recordedAt: Long,
    val isLowest: Boolean
)

@Singleton
class PriceRepository @Inject constructor(
    private val priceDao: PriceDao,
    private val storeRepository: StoreRepository
) {
    fun getPriceHistoryForProduct(productId: String): Flow<List<PriceRecordEntity>> =
        priceDao.getPriceHistoryForProduct(productId)

    fun getPriceHistoryForProductAtStore(productId: String, storeId: String): Flow<List<PriceRecordEntity>> =
        priceDao.getPriceHistoryForProductAtStore(productId, storeId)

    suspend fun getLatestPriceForProduct(productId: String): PriceRecordEntity? =
        priceDao.getLatestPriceForProduct(productId)

    suspend fun getLatestPriceForProductAtStore(productId: String, storeId: String): PriceRecordEntity? =
        priceDao.getLatestPriceForProductAtStore(productId, storeId)

    suspend fun recordPrice(
        productId: String,
        storeId: String,
        price: Double,
        currency: String = "USD",
        unitSize: Double? = null,
        unitType: String? = null,
        isOnSale: Boolean = false,
        saleEndDate: Long? = null,
        notes: String? = null
    ): PriceRecordEntity {
        val pricePerUnit = if (unitSize != null && unitSize > 0) {
            price / unitSize
        } else null

        val record = PriceRecordEntity(
            productId = productId,
            storeId = storeId,
            price = price,
            currency = currency,
            unitSize = unitSize,
            unitType = unitType,
            pricePerUnit = pricePerUnit,
            isOnSale = isOnSale,
            saleEndDate = saleEndDate,
            notes = notes
        )
        priceDao.insertPriceRecord(record)

        // Check for price alerts
        checkPriceAlerts(productId, price)

        return record
    }

    suspend fun getPriceStatistics(productId: String, days: Int = 365): PriceStatistics {
        val since = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days)
        }.timeInMillis

        val current = priceDao.getLatestPriceForProduct(productId)
        val lowest = priceDao.getLowestPriceSince(productId, since)
        val highest = priceDao.getHighestPriceSince(productId, since)
        val average = priceDao.getAveragePriceSince(productId, since)
        val recentPrices = priceDao.getRecentPricesForProduct(productId, 100)

        return PriceStatistics(
            currentPrice = current?.price,
            lowestPrice = lowest,
            highestPrice = highest,
            averagePrice = average,
            priceCount = recentPrices.size
        )
    }

    suspend fun getPriceComparison(productId: String): List<StorePriceComparison> {
        val prices = priceDao.getPriceComparisonForProduct(productId)
        if (prices.isEmpty()) return emptyList()

        val lowestPrice = prices.minOf { it.price }

        return prices.map { record ->
            val store = storeRepository.getStoreById(record.storeId)
            StorePriceComparison(
                storeId = record.storeId,
                storeName = store?.name,
                price = record.price,
                recordedAt = record.recordedAt,
                isLowest = record.price == lowestPrice
            )
        }
    }

    fun getActiveSales(): Flow<List<PriceRecordEntity>> =
        priceDao.getActiveSales()

    // Price alerts
    fun getActivePriceAlerts(): Flow<List<PriceAlertEntity>> =
        priceDao.getActivePriceAlerts()

    suspend fun createPriceAlert(productId: String, targetPrice: Double): PriceAlertEntity {
        val alert = PriceAlertEntity(
            productId = productId,
            targetPrice = targetPrice
        )
        priceDao.insertPriceAlert(alert)
        return alert
    }

    suspend fun deactivatePriceAlert(id: String) {
        priceDao.setAlertActive(id, false)
    }

    suspend fun deletePriceAlert(id: String) {
        val alert = priceDao.getPriceAlertsForProduct(id).firstOrNull { it.id == id }
        if (alert != null) {
            priceDao.deletePriceAlert(alert)
        }
    }

    private suspend fun checkPriceAlerts(productId: String, price: Double) {
        val alerts = priceDao.getPriceAlertsForProduct(productId)
        alerts.filter { it.isActive && price <= it.targetPrice }.forEach { alert ->
            priceDao.triggerAlert(alert.id)
            // TODO: Send notification about price drop
        }
    }

    suspend fun deletePriceHistory(productId: String) {
        priceDao.deletePriceHistoryForProduct(productId)
    }

    suspend fun getPriceRecordCount(): Int = priceDao.getPriceRecordCount()

    suspend fun getTrackedProductCount(): Int = priceDao.getTrackedProductCount()
}
