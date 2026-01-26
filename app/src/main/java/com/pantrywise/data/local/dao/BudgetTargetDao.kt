package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.BudgetTargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetTargetDao {

    @Query("SELECT * FROM budget_targets ORDER BY isActive DESC, updatedAt DESC")
    fun getAllBudgets(): Flow<List<BudgetTargetEntity>>

    @Query("SELECT * FROM budget_targets WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getActiveBudgets(): Flow<List<BudgetTargetEntity>>

    @Query("SELECT * FROM budget_targets WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveBudget(): BudgetTargetEntity?

    @Query("SELECT * FROM budget_targets WHERE id = :id")
    suspend fun getBudgetById(id: String): BudgetTargetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetTargetEntity): Long

    @Update
    suspend fun updateBudget(budget: BudgetTargetEntity)

    @Delete
    suspend fun deleteBudget(budget: BudgetTargetEntity)

    @Query("DELETE FROM budget_targets WHERE id = :id")
    suspend fun deleteBudgetById(id: String)

    @Query("UPDATE budget_targets SET isActive = :isActive WHERE id = :id")
    suspend fun updateActiveStatus(id: String, isActive: Boolean)

    @Query("SELECT COUNT(*) FROM budget_targets WHERE isActive = 1")
    suspend fun getActiveBudgetCount(): Int
}
