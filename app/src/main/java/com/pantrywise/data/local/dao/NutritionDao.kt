package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.NutritionEntity
import com.pantrywise.data.local.entity.NutritionGoalsEntity
import com.pantrywise.data.local.entity.NutritionLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionDao {
    // Nutrition data for products
    @Query("SELECT * FROM nutrition_data WHERE productId = :productId")
    suspend fun getNutritionForProduct(productId: String): NutritionEntity?

    @Query("SELECT * FROM nutrition_data WHERE productId = :productId")
    fun observeNutritionForProduct(productId: String): Flow<NutritionEntity?>

    @Query("SELECT * FROM nutrition_data WHERE productId IN (:productIds)")
    suspend fun getNutritionForProducts(productIds: List<String>): List<NutritionEntity>

    @Query("SELECT * FROM nutrition_data WHERE sourceApi = :sourceApi")
    suspend fun getNutritionBySource(sourceApi: String): List<NutritionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNutrition(nutrition: NutritionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNutritionList(nutritionList: List<NutritionEntity>)

    @Update
    suspend fun updateNutrition(nutrition: NutritionEntity)

    @Query("DELETE FROM nutrition_data WHERE productId = :productId")
    suspend fun deleteNutritionForProduct(productId: String)

    @Query("UPDATE nutrition_data SET isUserEdited = 1, updatedAt = :updatedAt WHERE productId = :productId")
    suspend fun markAsUserEdited(productId: String, updatedAt: Long = System.currentTimeMillis())

    // Nutrition log entries
    @Query("SELECT * FROM nutrition_log_entries ORDER BY loggedAt DESC")
    fun getAllNutritionLogEntries(): Flow<List<NutritionLogEntry>>

    @Query("SELECT * FROM nutrition_log_entries WHERE loggedAt >= :startDate AND loggedAt <= :endDate ORDER BY loggedAt ASC")
    fun getNutritionLogEntriesForDateRange(startDate: Long, endDate: Long): Flow<List<NutritionLogEntry>>

    @Query("SELECT * FROM nutrition_log_entries WHERE loggedAt >= :startOfDay AND loggedAt <= :endOfDay ORDER BY loggedAt ASC")
    suspend fun getNutritionLogEntriesForDay(startOfDay: Long, endOfDay: Long): List<NutritionLogEntry>

    @Query("SELECT * FROM nutrition_log_entries WHERE mealType = :mealType AND loggedAt >= :startOfDay AND loggedAt <= :endOfDay")
    suspend fun getNutritionLogEntriesForMeal(mealType: String, startOfDay: Long, endOfDay: Long): List<NutritionLogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNutritionLogEntry(entry: NutritionLogEntry): Long

    @Update
    suspend fun updateNutritionLogEntry(entry: NutritionLogEntry)

    @Delete
    suspend fun deleteNutritionLogEntry(entry: NutritionLogEntry)

    @Query("DELETE FROM nutrition_log_entries WHERE id = :id")
    suspend fun deleteNutritionLogEntryById(id: String)

    // Daily totals
    @Query("""
        SELECT
            COALESCE(SUM(calories * servings), 0) as totalCalories,
            COALESCE(SUM(protein * servings), 0) as totalProtein,
            COALESCE(SUM(carbohydrates * servings), 0) as totalCarbs,
            COALESCE(SUM(fat * servings), 0) as totalFat,
            COALESCE(SUM(fiber * servings), 0) as totalFiber,
            COALESCE(SUM(sugar * servings), 0) as totalSugar,
            COALESCE(SUM(sodium * servings), 0) as totalSodium
        FROM nutrition_log_entries
        WHERE loggedAt >= :startOfDay AND loggedAt <= :endOfDay
    """)
    suspend fun getDailyNutritionTotals(startOfDay: Long, endOfDay: Long): DailyNutritionTotals

    // Nutrition goals
    @Query("SELECT * FROM nutrition_goals WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveNutritionGoals(): NutritionGoalsEntity?

    @Query("SELECT * FROM nutrition_goals WHERE isActive = 1 LIMIT 1")
    fun observeActiveNutritionGoals(): Flow<NutritionGoalsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNutritionGoals(goals: NutritionGoalsEntity): Long

    @Update
    suspend fun updateNutritionGoals(goals: NutritionGoalsEntity)

    @Query("UPDATE nutrition_goals SET isActive = 0, updatedAt = :updatedAt WHERE id != :exceptId")
    suspend fun deactivateOtherGoals(exceptId: String, updatedAt: Long = System.currentTimeMillis())

    // Stats
    @Query("SELECT COUNT(*) FROM nutrition_data")
    suspend fun getNutritionDataCount(): Int

    @Query("SELECT COUNT(*) FROM nutrition_log_entries")
    suspend fun getNutritionLogEntryCount(): Int

    @Query("SELECT COUNT(DISTINCT strftime('%Y-%m-%d', loggedAt / 1000, 'unixepoch')) FROM nutrition_log_entries")
    suspend fun getDaysWithLogs(): Int
}

data class DailyNutritionTotals(
    val totalCalories: Double,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
    val totalFiber: Double,
    val totalSugar: Double,
    val totalSodium: Double
)
