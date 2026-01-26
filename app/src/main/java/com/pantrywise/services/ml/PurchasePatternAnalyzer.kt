package com.pantrywise.services.ml

import com.pantrywise.data.local.dao.FlatPurchaseRecord
import com.pantrywise.data.local.dao.InventoryDao
import com.pantrywise.data.local.dao.PurchaseDao
import com.pantrywise.data.local.dao.ShoppingListDao
import com.pantrywise.data.local.entity.InventoryItemEntity
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class PurchasePattern(
    val productId: String,
    val productName: String,
    val averagePurchaseInterval: Long, // in days
    val lastPurchaseDate: LocalDate?,
    val totalPurchases: Int,
    val averageQuantity: Double,
    val preferredDayOfWeek: DayOfWeek?,
    val isRecurring: Boolean,
    val confidenceScore: Float // 0.0 to 1.0
)

data class CompanionProduct(
    val productId: String,
    val productName: String,
    val coOccurrenceScore: Float, // 0.0 to 1.0 - how often bought together
    val purchasedTogetherCount: Int
)

data class SeasonalPattern(
    val productId: String,
    val productName: String,
    val peakMonths: List<Int>, // 1-12
    val isCurrentlySeasonal: Boolean,
    val seasonalScore: Float
)

@Singleton
class PurchasePatternAnalyzer @Inject constructor(
    private val purchaseDao: PurchaseDao,
    private val inventoryDao: InventoryDao,
    private val shoppingListDao: ShoppingListDao
) {
    companion object {
        private const val MIN_PURCHASES_FOR_PATTERN = 3
        private const val RECURRING_THRESHOLD_DAYS = 45 // If avg interval < 45 days, consider recurring
        private const val CONFIDENCE_THRESHOLD = 0.6f
        private const val CO_OCCURRENCE_MIN_COUNT = 2
    }

    /**
     * Analyzes purchase history to identify buying patterns for each product
     */
    suspend fun analyzePurchasePatterns(): List<PurchasePattern> {
        val allPurchases = purchaseDao.getAllPurchases().first()

        if (allPurchases.isEmpty()) return emptyList()

        // Group by product
        val purchasesByProduct = allPurchases.groupBy { it.productId }

        return purchasesByProduct.mapNotNull { (productId, purchases) ->
            if (purchases.size < MIN_PURCHASES_FOR_PATTERN) return@mapNotNull null

            analyzeProductPattern(productId, purchases)
        }
    }

    private fun analyzeProductPattern(
        productId: String,
        purchases: List<FlatPurchaseRecord>
    ): PurchasePattern? {
        val sortedPurchases = purchases.sortedByDescending { it.purchaseDate }
        val productName = sortedPurchases.first().productName

        // Calculate intervals between purchases
        val intervals = mutableListOf<Long>()
        for (i in 0 until sortedPurchases.size - 1) {
            val current = Instant.ofEpochMilli(sortedPurchases[i].purchaseDate)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val previous = Instant.ofEpochMilli(sortedPurchases[i + 1].purchaseDate)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            intervals.add(ChronoUnit.DAYS.between(previous, current))
        }

        if (intervals.isEmpty()) return null

        val avgInterval = intervals.average().toLong()
        val intervalVariance = calculateVariance(intervals)

        // Higher confidence if intervals are consistent
        val consistencyScore = 1.0f / (1.0f + (intervalVariance / 100.0f).toFloat())

        // Confidence based on number of data points and consistency
        val datapointScore = minOf(purchases.size / 10.0f, 1.0f)
        val confidenceScore = (consistencyScore * 0.7f + datapointScore * 0.3f)

        // Find preferred day of week
        val dayOfWeekCounts = sortedPurchases.map { purchase ->
            Instant.ofEpochMilli(purchase.purchaseDate)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .dayOfWeek
        }.groupingBy { it }.eachCount()

        val preferredDay = dayOfWeekCounts.maxByOrNull { it.value }?.key

        val lastPurchaseDate = Instant.ofEpochMilli(sortedPurchases.first().purchaseDate)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        return PurchasePattern(
            productId = productId,
            productName = productName,
            averagePurchaseInterval = avgInterval,
            lastPurchaseDate = lastPurchaseDate,
            totalPurchases = purchases.size,
            averageQuantity = purchases.map { it.quantity }.average(),
            preferredDayOfWeek = preferredDay,
            isRecurring = avgInterval <= RECURRING_THRESHOLD_DAYS,
            confidenceScore = confidenceScore
        )
    }

    /**
     * Finds products that are frequently bought together
     */
    suspend fun findCompanionProducts(productId: String): List<CompanionProduct> {
        val allPurchases = purchaseDao.getAllPurchases().first()

        // Group purchases by shopping session (purchases within 1 hour)
        val sessions = groupIntoSessions(allPurchases)

        // Find sessions containing the target product
        val sessionsWithProduct = sessions.filter { session ->
            session.any { it.productId == productId }
        }

        if (sessionsWithProduct.isEmpty()) return emptyList()

        // Count co-occurrences
        val coOccurrences = mutableMapOf<String, MutableList<FlatPurchaseRecord>>()

        sessionsWithProduct.forEach { session ->
            session.filter { it.productId != productId }.forEach { purchase ->
                coOccurrences.getOrPut(purchase.productId) { mutableListOf() }.add(purchase)
            }
        }

        return coOccurrences
            .filter { it.value.size >= CO_OCCURRENCE_MIN_COUNT }
            .map { (companionId, purchases) ->
                CompanionProduct(
                    productId = companionId,
                    productName = purchases.first().productName,
                    coOccurrenceScore = purchases.size.toFloat() / sessionsWithProduct.size,
                    purchasedTogetherCount = purchases.size
                )
            }
            .sortedByDescending { it.coOccurrenceScore }
            .take(5)
    }

    /**
     * Identifies seasonal buying patterns
     */
    suspend fun analyzeSeasonalPatterns(): List<SeasonalPattern> {
        val allPurchases = purchaseDao.getAllPurchases().first()
        val currentMonth = LocalDate.now().monthValue

        val purchasesByProduct = allPurchases.groupBy { it.productId }

        return purchasesByProduct.mapNotNull { (productId, purchases) ->
            if (purchases.size < MIN_PURCHASES_FOR_PATTERN * 2) return@mapNotNull null

            val monthCounts = purchases.map { purchase ->
                Instant.ofEpochMilli(purchase.purchaseDate)
                    .atZone(ZoneId.systemDefault())
                    .monthValue
            }.groupingBy { it }.eachCount()

            // Find months with significantly higher purchases
            val avgMonthlyCount = monthCounts.values.average()
            val peakMonths = monthCounts
                .filter { it.value > avgMonthlyCount * 1.5 }
                .keys
                .toList()

            if (peakMonths.isEmpty()) return@mapNotNull null

            val isCurrentlySeasonal = peakMonths.contains(currentMonth)
            val seasonalVariance = calculateVariance(monthCounts.values.map { it.toLong() })
            val seasonalScore = minOf((seasonalVariance / 10.0).toFloat(), 1.0f)

            SeasonalPattern(
                productId = productId,
                productName = purchases.first().productName,
                peakMonths = peakMonths.sorted(),
                isCurrentlySeasonal = isCurrentlySeasonal,
                seasonalScore = seasonalScore
            )
        }.filter { it.seasonalScore > 0.3f }
    }

    /**
     * Predicts when a product is likely to run out based on usage patterns
     */
    suspend fun predictRestockDate(productId: String): LocalDate? {
        val patterns = analyzePurchasePatterns()
        val pattern = patterns.find { it.productId == productId } ?: return null

        if (!pattern.isRecurring || pattern.confidenceScore < CONFIDENCE_THRESHOLD) {
            return null
        }

        val lastPurchase = pattern.lastPurchaseDate ?: return null
        return lastPurchase.plusDays(pattern.averagePurchaseInterval)
    }

    /**
     * Gets products that are due for restock
     */
    suspend fun getProductsDueForRestock(): List<Pair<PurchasePattern, Int>> {
        val patterns = analyzePurchasePatterns()
            .filter { it.isRecurring && it.confidenceScore >= CONFIDENCE_THRESHOLD }

        val today = LocalDate.now()

        return patterns.mapNotNull { pattern ->
            val lastPurchase = pattern.lastPurchaseDate ?: return@mapNotNull null
            val expectedNextPurchase = lastPurchase.plusDays(pattern.averagePurchaseInterval)
            val daysUntilRestock = ChronoUnit.DAYS.between(today, expectedNextPurchase).toInt()

            // Only return if due within next 7 days or overdue
            if (daysUntilRestock <= 7) {
                Pair(pattern, daysUntilRestock)
            } else null
        }.sortedBy { it.second }
    }

    private fun groupIntoSessions(
        purchases: List<FlatPurchaseRecord>
    ): List<List<FlatPurchaseRecord>> {
        if (purchases.isEmpty()) return emptyList()

        val sorted = purchases.sortedBy { it.purchaseDate }
        val sessions = mutableListOf<MutableList<FlatPurchaseRecord>>()
        var currentSession = mutableListOf<FlatPurchaseRecord>()

        sorted.forEach { purchase ->
            if (currentSession.isEmpty()) {
                currentSession.add(purchase)
            } else {
                val lastPurchaseTime = currentSession.last().purchaseDate
                val timeDiff = purchase.purchaseDate - lastPurchaseTime

                // If more than 1 hour apart, start new session
                if (timeDiff > 3600000) {
                    sessions.add(currentSession)
                    currentSession = mutableListOf(purchase)
                } else {
                    currentSession.add(purchase)
                }
            }
        }

        if (currentSession.isNotEmpty()) {
            sessions.add(currentSession)
        }

        return sessions
    }

    private fun calculateVariance(values: List<Long>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }
}
