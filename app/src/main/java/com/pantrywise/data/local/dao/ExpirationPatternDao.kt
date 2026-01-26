package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.ExpirationPatternEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpirationPatternDao {
    @Query("SELECT * FROM expiration_patterns ORDER BY category ASC, productName ASC")
    fun getAllExpirationPatterns(): Flow<List<ExpirationPatternEntity>>

    @Query("SELECT * FROM expiration_patterns WHERE id = :id")
    suspend fun getExpirationPatternById(id: String): ExpirationPatternEntity?

    @Query("SELECT * FROM expiration_patterns WHERE productId = :productId")
    suspend fun getExpirationPatternForProduct(productId: String): ExpirationPatternEntity?

    @Query("SELECT * FROM expiration_patterns WHERE category = :category AND productId IS NULL ORDER BY sampleSize DESC LIMIT 1")
    suspend fun getDefaultPatternForCategory(category: String): ExpirationPatternEntity?

    @Query("SELECT * FROM expiration_patterns WHERE category = :category ORDER BY sampleSize DESC")
    fun getExpirationPatternsForCategory(category: String): Flow<List<ExpirationPatternEntity>>

    @Query("""
        SELECT * FROM expiration_patterns
        WHERE productName LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%'
        ORDER BY sampleSize DESC
    """)
    fun searchExpirationPatterns(query: String): Flow<List<ExpirationPatternEntity>>

    @Query("SELECT * FROM expiration_patterns WHERE storageLocation = :location ORDER BY category ASC")
    fun getPatternsByStorageLocation(location: String): Flow<List<ExpirationPatternEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpirationPattern(pattern: ExpirationPatternEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpirationPatterns(patterns: List<ExpirationPatternEntity>)

    @Update
    suspend fun updateExpirationPattern(pattern: ExpirationPatternEntity)

    @Query("""
        UPDATE expiration_patterns
        SET typicalDaysToExpiration = :typicalDays,
            minDays = :minDays,
            maxDays = :maxDays,
            sampleSize = sampleSize + 1,
            confidence = :confidence,
            lastUpdated = :lastUpdated
        WHERE id = :id
    """)
    suspend fun updatePatternWithNewData(
        id: String,
        typicalDays: Int,
        minDays: Int?,
        maxDays: Int?,
        confidence: Float,
        lastUpdated: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deleteExpirationPattern(pattern: ExpirationPatternEntity)

    @Query("DELETE FROM expiration_patterns WHERE productId = :productId")
    suspend fun deletePatternForProduct(productId: String)

    // Stats
    @Query("SELECT COUNT(*) FROM expiration_patterns")
    suspend fun getPatternCount(): Int

    @Query("SELECT COUNT(DISTINCT category) FROM expiration_patterns")
    suspend fun getTrackedCategoryCount(): Int

    @Query("SELECT AVG(sampleSize) FROM expiration_patterns")
    suspend fun getAverageSampleSize(): Double?

    // For ML learning
    @Query("""
        SELECT * FROM expiration_patterns
        WHERE sampleSize >= :minSamples
        AND confidence >= :minConfidence
        ORDER BY lastUpdated DESC
    """)
    suspend fun getHighConfidencePatterns(
        minSamples: Int = 3,
        minConfidence: Float = 0.7f
    ): List<ExpirationPatternEntity>
}
