package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.MealPlanEntity
import com.pantrywise.data.local.entity.MealPlanEntryEntity
import com.pantrywise.data.local.entity.MealType
import com.pantrywise.data.local.entity.RecipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPlanDao {

    // ============ Recipes ============

    @Query("SELECT * FROM recipes ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipeById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchRecipes(query: String): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE isAiGenerated = 1 ORDER BY createdAt DESC")
    fun getAiGeneratedRecipes(): Flow<List<RecipeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Delete
    suspend fun deleteRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipeById(id: String)

    @Query("UPDATE recipes SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun getRecipeCount(): Int

    // ============ Meal Plans ============

    @Query("SELECT * FROM meal_plans WHERE weekStartDate = :weekStartDate LIMIT 1")
    suspend fun getMealPlanForWeek(weekStartDate: Long): MealPlanEntity?

    @Query("SELECT * FROM meal_plans ORDER BY weekStartDate DESC")
    fun getAllMealPlans(): Flow<List<MealPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlan(mealPlan: MealPlanEntity): Long

    @Update
    suspend fun updateMealPlan(mealPlan: MealPlanEntity)

    @Delete
    suspend fun deleteMealPlan(mealPlan: MealPlanEntity)

    // ============ Meal Plan Entries ============

    @Query("SELECT * FROM meal_plan_entries WHERE mealPlanId = :mealPlanId ORDER BY date ASC, mealType ASC")
    fun getEntriesForMealPlan(mealPlanId: String): Flow<List<MealPlanEntryEntity>>

    @Query("SELECT * FROM meal_plan_entries WHERE date = :date ORDER BY mealType ASC")
    fun getEntriesForDate(date: Long): Flow<List<MealPlanEntryEntity>>

    @Query("SELECT * FROM meal_plan_entries WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, mealType ASC")
    fun getEntriesForDateRange(startDate: Long, endDate: Long): Flow<List<MealPlanEntryEntity>>

    @Query("SELECT * FROM meal_plan_entries WHERE id = :id")
    suspend fun getEntryById(id: String): MealPlanEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MealPlanEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: MealPlanEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: MealPlanEntryEntity)

    @Query("DELETE FROM meal_plan_entries WHERE id = :id")
    suspend fun deleteEntryById(id: String)

    @Query("DELETE FROM meal_plan_entries WHERE mealPlanId = :mealPlanId")
    suspend fun deleteEntriesForMealPlan(mealPlanId: String)

    @Query("SELECT COUNT(*) FROM meal_plan_entries WHERE date = :date")
    suspend fun getEntryCountForDate(date: Long): Int

    @Query("SELECT COUNT(*) FROM meal_plan_entries WHERE date = :date AND mealType = :mealType")
    suspend fun getEntryCountForDateAndType(date: Long, mealType: MealType): Int

    // Get entries with recipe details
    @Transaction
    @Query("""
        SELECT e.*, r.name as recipeName
        FROM meal_plan_entries e
        LEFT JOIN recipes r ON e.recipeId = r.id
        WHERE e.date BETWEEN :startDate AND :endDate
        ORDER BY e.date ASC, e.mealType ASC
    """)
    fun getEntriesWithRecipeForDateRange(startDate: Long, endDate: Long): Flow<List<MealPlanEntryWithRecipe>>

    // Get all recipe IDs used in a date range (for generating shopping list)
    @Query("""
        SELECT DISTINCT recipeId
        FROM meal_plan_entries
        WHERE date BETWEEN :startDate AND :endDate
        AND recipeId IS NOT NULL
    """)
    suspend fun getRecipeIdsForDateRange(startDate: Long, endDate: Long): List<String>
}

/**
 * Entry with recipe name for display
 */
data class MealPlanEntryWithRecipe(
    val id: String,
    val mealPlanId: String,
    val date: Long,
    val mealType: MealType,
    val recipeId: String?,
    val customMealName: String?,
    val servings: Int,
    val notes: String?,
    val calendarEventId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val recipeName: String?
) {
    val displayName: String
        get() = recipeName ?: customMealName ?: "Unnamed meal"
}
