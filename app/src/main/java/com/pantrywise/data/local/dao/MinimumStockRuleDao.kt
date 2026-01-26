package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.MinimumStockRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MinimumStockRuleDao {

    @Query("SELECT * FROM minimum_stock_rules WHERE isActive = 1 ORDER BY productName ASC")
    fun getAllActiveRules(): Flow<List<MinimumStockRuleEntity>>

    @Query("SELECT * FROM minimum_stock_rules ORDER BY productName ASC")
    fun getAllRules(): Flow<List<MinimumStockRuleEntity>>

    @Query("SELECT * FROM minimum_stock_rules WHERE isStaple = 1 AND isActive = 1 ORDER BY productName ASC")
    fun getStapleRules(): Flow<List<MinimumStockRuleEntity>>

    @Query("SELECT * FROM minimum_stock_rules WHERE id = :id")
    suspend fun getRuleById(id: String): MinimumStockRuleEntity?

    @Query("SELECT * FROM minimum_stock_rules WHERE productId = :productId AND isActive = 1")
    suspend fun getRuleByProductId(productId: String): MinimumStockRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: MinimumStockRuleEntity)

    @Update
    suspend fun updateRule(rule: MinimumStockRuleEntity)

    @Delete
    suspend fun deleteRule(rule: MinimumStockRuleEntity)

    @Query("DELETE FROM minimum_stock_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)

    @Query("UPDATE minimum_stock_rules SET lastTriggeredAt = :timestamp, updatedAt = :timestamp WHERE id = :id")
    suspend fun markTriggered(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE minimum_stock_rules SET isStaple = :isStaple, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateStapleStatus(id: String, isStaple: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE minimum_stock_rules SET isActive = :isActive, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateActiveStatus(id: String, isActive: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM minimum_stock_rules WHERE isStaple = 1 AND isActive = 1")
    fun getStapleCount(): Flow<Int>
}
