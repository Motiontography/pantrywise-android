package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.WasteByCategory
import com.pantrywise.data.local.entity.WasteByReason
import com.pantrywise.data.local.entity.WasteEventEntity
import com.pantrywise.data.local.entity.WasteReason
import kotlinx.coroutines.flow.Flow

@Dao
interface WasteDao {
    @Query("SELECT * FROM waste_events ORDER BY wastedAt DESC")
    fun getAllWasteEvents(): Flow<List<WasteEventEntity>>

    @Query("SELECT * FROM waste_events WHERE id = :id")
    suspend fun getWasteEventById(id: String): WasteEventEntity?

    @Query("SELECT * FROM waste_events WHERE wastedAt >= :startDate AND wastedAt <= :endDate ORDER BY wastedAt DESC")
    fun getWasteEventsByDateRange(startDate: Long, endDate: Long): Flow<List<WasteEventEntity>>

    @Query("SELECT * FROM waste_events WHERE productId = :productId ORDER BY wastedAt DESC")
    fun getWasteEventsForProduct(productId: String): Flow<List<WasteEventEntity>>

    @Query("SELECT * FROM waste_events WHERE category = :category ORDER BY wastedAt DESC")
    fun getWasteEventsByCategory(category: String): Flow<List<WasteEventEntity>>

    @Query("SELECT * FROM waste_events WHERE reason = :reason ORDER BY wastedAt DESC")
    fun getWasteEventsByReason(reason: WasteReason): Flow<List<WasteEventEntity>>

    @Query("SELECT * FROM waste_events ORDER BY wastedAt DESC LIMIT :limit")
    suspend fun getRecentWasteEvents(limit: Int = 10): List<WasteEventEntity>

    // Aggregations
    @Query("SELECT SUM(estimatedCost) FROM waste_events WHERE wastedAt >= :startDate AND wastedAt <= :endDate")
    suspend fun getTotalWasteCostInRange(startDate: Long, endDate: Long): Double?

    @Query("SELECT COUNT(*) FROM waste_events WHERE wastedAt >= :startDate AND wastedAt <= :endDate")
    suspend fun getWasteCountInRange(startDate: Long, endDate: Long): Int

    @Query("""
        SELECT reason, COUNT(*) as count, COALESCE(SUM(estimatedCost), 0) as totalCost
        FROM waste_events
        WHERE wastedAt >= :startDate AND wastedAt <= :endDate
        GROUP BY reason
        ORDER BY count DESC
    """)
    suspend fun getWasteByReasonInRange(startDate: Long, endDate: Long): List<WasteByReason>

    @Query("""
        SELECT category, COUNT(*) as count, COALESCE(SUM(estimatedCost), 0) as totalCost
        FROM waste_events
        WHERE wastedAt >= :startDate AND wastedAt <= :endDate
        GROUP BY category
        ORDER BY count DESC
    """)
    suspend fun getWasteByCategoryInRange(startDate: Long, endDate: Long): List<WasteByCategory>

    @Query("""
        SELECT productName, COUNT(*) as wasteCount
        FROM waste_events
        WHERE wastedAt >= :startDate AND wastedAt <= :endDate
        GROUP BY productName
        ORDER BY wasteCount DESC
        LIMIT :limit
    """)
    suspend fun getTopWastedProducts(startDate: Long, endDate: Long, limit: Int = 10): List<TopWastedProduct>

    @Query("""
        SELECT reason
        FROM waste_events
        WHERE wastedAt >= :startDate AND wastedAt <= :endDate
        GROUP BY reason
        ORDER BY COUNT(*) DESC
        LIMIT 1
    """)
    suspend fun getMostCommonWasteReason(startDate: Long, endDate: Long): WasteReason?

    // CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWasteEvent(wasteEvent: WasteEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWasteEvents(wasteEvents: List<WasteEventEntity>)

    @Update
    suspend fun updateWasteEvent(wasteEvent: WasteEventEntity)

    @Delete
    suspend fun deleteWasteEvent(wasteEvent: WasteEventEntity)

    @Query("DELETE FROM waste_events WHERE id = :id")
    suspend fun deleteWasteEventById(id: String)

    @Query("DELETE FROM waste_events WHERE productId = :productId")
    suspend fun deleteWasteEventsForProduct(productId: String)

    // Stats
    @Query("SELECT COUNT(*) FROM waste_events")
    suspend fun getTotalWasteEventCount(): Int

    @Query("SELECT COALESCE(SUM(estimatedCost), 0) FROM waste_events")
    suspend fun getTotalWasteCost(): Double

    // Monthly trend data
    @Query("""
        SELECT strftime('%Y-%m', wastedAt / 1000, 'unixepoch') as month,
               COUNT(*) as count,
               COALESCE(SUM(estimatedCost), 0) as totalCost
        FROM waste_events
        WHERE wastedAt >= :startDate
        GROUP BY month
        ORDER BY month ASC
    """)
    suspend fun getMonthlyWasteTrend(startDate: Long): List<MonthlyWasteTrend>
}

data class TopWastedProduct(
    val productName: String,
    val wasteCount: Int
)

data class MonthlyWasteTrend(
    val month: String,
    val count: Int,
    val totalCost: Double
)
