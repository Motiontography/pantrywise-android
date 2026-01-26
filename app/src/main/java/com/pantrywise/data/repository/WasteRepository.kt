package com.pantrywise.data.repository

import com.pantrywise.data.local.dao.MonthlyWasteTrend
import com.pantrywise.data.local.dao.TopWastedProduct
import com.pantrywise.data.local.dao.WasteDao
import com.pantrywise.data.local.entity.WasteByCategory
import com.pantrywise.data.local.entity.WasteByReason
import com.pantrywise.data.local.entity.WasteEventEntity
import com.pantrywise.data.local.entity.WasteReason
import com.pantrywise.data.local.entity.WasteStatistics
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WasteRepository @Inject constructor(
    private val wasteDao: WasteDao
) {
    fun getAllWasteEvents(): Flow<List<WasteEventEntity>> = wasteDao.getAllWasteEvents()

    fun getWasteEventsByDateRange(startDate: Long, endDate: Long): Flow<List<WasteEventEntity>> =
        wasteDao.getWasteEventsByDateRange(startDate, endDate)

    fun getWasteEventsForProduct(productId: String): Flow<List<WasteEventEntity>> =
        wasteDao.getWasteEventsForProduct(productId)

    fun getWasteEventsByCategory(category: String): Flow<List<WasteEventEntity>> =
        wasteDao.getWasteEventsByCategory(category)

    fun getWasteEventsByReason(reason: WasteReason): Flow<List<WasteEventEntity>> =
        wasteDao.getWasteEventsByReason(reason)

    suspend fun getRecentWasteEvents(limit: Int = 10): List<WasteEventEntity> =
        wasteDao.getRecentWasteEvents(limit)

    suspend fun logWasteEvent(
        productId: String?,
        inventoryItemId: String?,
        productName: String,
        category: String,
        quantity: Double,
        unit: String,
        reason: WasteReason,
        estimatedCost: Double? = null,
        daysBeforeExpiration: Int? = null,
        notes: String? = null,
        imageUrl: String? = null
    ): WasteEventEntity {
        val event = WasteEventEntity(
            productId = productId,
            inventoryItemId = inventoryItemId,
            productName = productName,
            category = category,
            quantity = quantity,
            unit = unit,
            reason = reason,
            estimatedCost = estimatedCost,
            daysBeforeExpiration = daysBeforeExpiration,
            notes = notes,
            imageUrl = imageUrl
        )
        wasteDao.insertWasteEvent(event)
        return event
    }

    suspend fun deleteWasteEvent(id: String) {
        wasteDao.deleteWasteEventById(id)
    }

    // Statistics
    suspend fun getWasteStatistics(startDate: Long, endDate: Long): WasteStatistics {
        val totalItems = wasteDao.getWasteCountInRange(startDate, endDate)
        val totalCost = wasteDao.getTotalWasteCostInRange(startDate, endDate) ?: 0.0
        val topReason = wasteDao.getMostCommonWasteReason(startDate, endDate)
        val byCategory = wasteDao.getWasteByCategoryInRange(startDate, endDate)
        val topCategory = byCategory.maxByOrNull { it.count }?.category

        return WasteStatistics(
            totalItems = totalItems,
            totalCost = totalCost,
            topReason = topReason,
            topCategory = topCategory,
            periodStart = startDate,
            periodEnd = endDate
        )
    }

    suspend fun getWeeklyWasteStatistics(): WasteStatistics {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.timeInMillis

        return getWasteStatistics(startDate, endDate)
    }

    suspend fun getMonthlyWasteStatistics(): WasteStatistics {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.timeInMillis

        return getWasteStatistics(startDate, endDate)
    }

    suspend fun getWasteByReason(startDate: Long, endDate: Long): List<WasteByReason> =
        wasteDao.getWasteByReasonInRange(startDate, endDate)

    suspend fun getWasteByCategory(startDate: Long, endDate: Long): List<WasteByCategory> =
        wasteDao.getWasteByCategoryInRange(startDate, endDate)

    suspend fun getTopWastedProducts(startDate: Long, endDate: Long, limit: Int = 10): List<TopWastedProduct> =
        wasteDao.getTopWastedProducts(startDate, endDate, limit)

    suspend fun getMonthlyWasteTrend(months: Int = 12): List<MonthlyWasteTrend> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -months)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        return wasteDao.getMonthlyWasteTrend(calendar.timeInMillis)
    }

    suspend fun getTotalWasteEventCount(): Int = wasteDao.getTotalWasteEventCount()

    suspend fun getTotalWasteCost(): Double = wasteDao.getTotalWasteCost()
}
