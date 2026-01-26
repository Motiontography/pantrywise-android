package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.PriceAlertEntity
import com.pantrywise.data.local.entity.PriceRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceDao {
    // Price records
    @Query("SELECT * FROM price_records WHERE productId = :productId ORDER BY recordedAt DESC")
    fun getPriceHistoryForProduct(productId: String): Flow<List<PriceRecordEntity>>

    @Query("SELECT * FROM price_records WHERE productId = :productId ORDER BY recordedAt DESC LIMIT :limit")
    suspend fun getRecentPricesForProduct(productId: String, limit: Int = 10): List<PriceRecordEntity>

    @Query("SELECT * FROM price_records WHERE productId = :productId AND storeId = :storeId ORDER BY recordedAt DESC")
    fun getPriceHistoryForProductAtStore(productId: String, storeId: String): Flow<List<PriceRecordEntity>>

    @Query("SELECT * FROM price_records WHERE storeId = :storeId ORDER BY recordedAt DESC")
    fun getPricesForStore(storeId: String): Flow<List<PriceRecordEntity>>

    @Query("""
        SELECT * FROM price_records
        WHERE productId = :productId
        ORDER BY recordedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestPriceForProduct(productId: String): PriceRecordEntity?

    @Query("""
        SELECT * FROM price_records
        WHERE productId = :productId AND storeId = :storeId
        ORDER BY recordedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestPriceForProductAtStore(productId: String, storeId: String): PriceRecordEntity?

    @Query("""
        SELECT MIN(price) FROM price_records
        WHERE productId = :productId
        AND recordedAt >= :since
    """)
    suspend fun getLowestPriceSince(productId: String, since: Long): Double?

    @Query("""
        SELECT MAX(price) FROM price_records
        WHERE productId = :productId
        AND recordedAt >= :since
    """)
    suspend fun getHighestPriceSince(productId: String, since: Long): Double?

    @Query("""
        SELECT AVG(price) FROM price_records
        WHERE productId = :productId
        AND recordedAt >= :since
    """)
    suspend fun getAveragePriceSince(productId: String, since: Long): Double?

    @Query("""
        SELECT pr.* FROM price_records pr
        INNER JOIN (
            SELECT productId, storeId, MAX(recordedAt) as maxDate
            FROM price_records
            WHERE productId = :productId
            GROUP BY productId, storeId
        ) latest ON pr.productId = latest.productId
            AND pr.storeId = latest.storeId
            AND pr.recordedAt = latest.maxDate
        ORDER BY pr.price ASC
    """)
    suspend fun getPriceComparisonForProduct(productId: String): List<PriceRecordEntity>

    @Query("SELECT * FROM price_records WHERE isOnSale = 1 AND (saleEndDate IS NULL OR saleEndDate >= :currentTime)")
    fun getActiveSales(currentTime: Long = System.currentTimeMillis()): Flow<List<PriceRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceRecord(priceRecord: PriceRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceRecords(priceRecords: List<PriceRecordEntity>)

    @Update
    suspend fun updatePriceRecord(priceRecord: PriceRecordEntity)

    @Delete
    suspend fun deletePriceRecord(priceRecord: PriceRecordEntity)

    @Query("DELETE FROM price_records WHERE id = :id")
    suspend fun deletePriceRecordById(id: String)

    @Query("DELETE FROM price_records WHERE productId = :productId")
    suspend fun deletePriceHistoryForProduct(productId: String)

    // Price alerts
    @Query("SELECT * FROM price_alerts WHERE isActive = 1")
    fun getActivePriceAlerts(): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE productId = :productId")
    suspend fun getPriceAlertsForProduct(productId: String): List<PriceAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceAlert(alert: PriceAlertEntity): Long

    @Update
    suspend fun updatePriceAlert(alert: PriceAlertEntity)

    @Query("UPDATE price_alerts SET isActive = :isActive, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setAlertActive(id: String, isActive: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE price_alerts SET triggeredAt = :triggeredAt, isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun triggerAlert(id: String, triggeredAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deletePriceAlert(alert: PriceAlertEntity)

    @Query("DELETE FROM price_alerts WHERE productId = :productId")
    suspend fun deleteAlertsForProduct(productId: String)

    // Stats
    @Query("SELECT COUNT(*) FROM price_records")
    suspend fun getPriceRecordCount(): Int

    @Query("SELECT COUNT(DISTINCT productId) FROM price_records")
    suspend fun getTrackedProductCount(): Int

    @Query("SELECT DISTINCT productId FROM price_records")
    suspend fun getProductsWithPriceRecords(): List<String>

    @Query("SELECT COUNT(*) FROM price_alerts WHERE isActive = 1")
    suspend fun getActiveAlertsCount(): Int

    @Query("SELECT * FROM price_alerts WHERE productId = :productId AND isActive = 1 LIMIT 1")
    suspend fun getAlertForProduct(productId: String): PriceAlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: PriceAlertEntity): Long

    @Query("DELETE FROM price_alerts WHERE id = :alertId")
    suspend fun deleteAlert(alertId: String)
}
