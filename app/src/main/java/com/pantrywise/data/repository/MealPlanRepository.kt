package com.pantrywise.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pantrywise.data.local.dao.MealPlanDao
import com.pantrywise.data.local.dao.MealPlanEntryWithRecipe
import com.pantrywise.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealPlanRepository @Inject constructor(
    private val mealPlanDao: MealPlanDao
) {
    private val gson = Gson()

    // ============ Recipes ============

    fun getAllRecipes(): Flow<List<RecipeEntity>> =
        mealPlanDao.getAllRecipes()

    fun searchRecipes(query: String): Flow<List<RecipeEntity>> =
        mealPlanDao.searchRecipes(query)

    fun getFavoriteRecipes(): Flow<List<RecipeEntity>> =
        mealPlanDao.getFavoriteRecipes()

    fun getAiGeneratedRecipes(): Flow<List<RecipeEntity>> =
        mealPlanDao.getAiGeneratedRecipes()

    suspend fun getRecipeById(id: String): RecipeEntity? =
        mealPlanDao.getRecipeById(id)

    suspend fun createRecipe(
        name: String,
        description: String? = null,
        instructions: String? = null,
        prepTimeMinutes: Int? = null,
        cookTimeMinutes: Int? = null,
        servings: Int = 4,
        ingredients: List<RecipeIngredient> = emptyList(),
        imageUrl: String? = null,
        sourceUrl: String? = null,
        isAiGenerated: Boolean = false,
        aiQuery: String? = null
    ): RecipeEntity {
        val recipe = RecipeEntity(
            name = name,
            description = description,
            instructions = instructions,
            prepTimeMinutes = prepTimeMinutes,
            cookTimeMinutes = cookTimeMinutes,
            servings = servings,
            ingredientsJson = gson.toJson(ingredients),
            imageUrl = imageUrl,
            sourceUrl = sourceUrl,
            isAiGenerated = isAiGenerated,
            aiQuery = aiQuery
        )
        mealPlanDao.insertRecipe(recipe)
        return recipe
    }

    suspend fun updateRecipe(recipe: RecipeEntity) {
        mealPlanDao.updateRecipe(recipe.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteRecipe(recipe: RecipeEntity) {
        mealPlanDao.deleteRecipe(recipe)
    }

    suspend fun deleteRecipeById(id: String) {
        mealPlanDao.deleteRecipeById(id)
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) {
        mealPlanDao.updateFavoriteStatus(id, isFavorite)
    }

    suspend fun getRecipeCount(): Int = mealPlanDao.getRecipeCount()

    // Helper to parse ingredients from JSON
    fun parseIngredients(recipe: RecipeEntity): List<RecipeIngredient> {
        return try {
            val type = object : TypeToken<List<RecipeIngredient>>() {}.type
            gson.fromJson(recipe.ingredientsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Helper to update recipe ingredients
    suspend fun updateRecipeIngredients(recipeId: String, ingredients: List<RecipeIngredient>) {
        val recipe = getRecipeById(recipeId) ?: return
        val updatedRecipe = recipe.copy(
            ingredientsJson = gson.toJson(ingredients),
            updatedAt = System.currentTimeMillis()
        )
        mealPlanDao.updateRecipe(updatedRecipe)
    }

    // ============ Meal Plans ============

    fun getAllMealPlans(): Flow<List<MealPlanEntity>> =
        mealPlanDao.getAllMealPlans()

    suspend fun getMealPlanForWeek(weekStartDate: Long): MealPlanEntity? =
        mealPlanDao.getMealPlanForWeek(weekStartDate)

    suspend fun getOrCreateMealPlanForWeek(weekStartDate: Long, name: String? = null): MealPlanEntity {
        val existing = getMealPlanForWeek(weekStartDate)
        if (existing != null) return existing

        val mealPlan = MealPlanEntity(
            weekStartDate = weekStartDate,
            name = name
        )
        mealPlanDao.insertMealPlan(mealPlan)
        return mealPlan
    }

    suspend fun updateMealPlan(mealPlan: MealPlanEntity) {
        mealPlanDao.updateMealPlan(mealPlan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteMealPlan(mealPlan: MealPlanEntity) {
        mealPlanDao.deleteMealPlan(mealPlan)
    }

    // ============ Meal Plan Entries ============

    fun getEntriesForMealPlan(mealPlanId: String): Flow<List<MealPlanEntryEntity>> =
        mealPlanDao.getEntriesForMealPlan(mealPlanId)

    fun getEntriesForDate(date: Long): Flow<List<MealPlanEntryEntity>> =
        mealPlanDao.getEntriesForDate(date)

    fun getEntriesForDateRange(startDate: Long, endDate: Long): Flow<List<MealPlanEntryEntity>> =
        mealPlanDao.getEntriesForDateRange(startDate, endDate)

    fun getEntriesWithRecipeForDateRange(startDate: Long, endDate: Long): Flow<List<MealPlanEntryWithRecipe>> =
        mealPlanDao.getEntriesWithRecipeForDateRange(startDate, endDate)

    suspend fun getEntryById(id: String): MealPlanEntryEntity? =
        mealPlanDao.getEntryById(id)

    suspend fun createEntry(
        mealPlanId: String,
        date: Long,
        mealType: MealType,
        recipeId: String? = null,
        customMealName: String? = null,
        servings: Int = 2,
        notes: String? = null
    ): MealPlanEntryEntity {
        val entry = MealPlanEntryEntity(
            mealPlanId = mealPlanId,
            date = date,
            mealType = mealType,
            recipeId = recipeId,
            customMealName = customMealName,
            servings = servings,
            notes = notes
        )
        mealPlanDao.insertEntry(entry)
        return entry
    }

    suspend fun updateEntry(entry: MealPlanEntryEntity) {
        mealPlanDao.updateEntry(entry.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteEntry(entry: MealPlanEntryEntity) {
        mealPlanDao.deleteEntry(entry)
    }

    suspend fun deleteEntryById(id: String) {
        mealPlanDao.deleteEntryById(id)
    }

    suspend fun deleteEntriesForMealPlan(mealPlanId: String) {
        mealPlanDao.deleteEntriesForMealPlan(mealPlanId)
    }

    suspend fun getEntryCountForDate(date: Long): Int =
        mealPlanDao.getEntryCountForDate(date)

    // ============ Shopping List Generation ============

    suspend fun getRecipeIdsForDateRange(startDate: Long, endDate: Long): List<String> =
        mealPlanDao.getRecipeIdsForDateRange(startDate, endDate)

    suspend fun getIngredientsForDateRange(startDate: Long, endDate: Long): List<RecipeIngredient> {
        val recipeIds = getRecipeIdsForDateRange(startDate, endDate)
        val allIngredients = mutableListOf<RecipeIngredient>()

        for (recipeId in recipeIds) {
            val recipe = getRecipeById(recipeId)
            if (recipe != null) {
                allIngredients.addAll(parseIngredients(recipe))
            }
        }

        return allIngredients
    }

    // Aggregate ingredients by name (combine quantities for same ingredient)
    suspend fun getAggregatedIngredientsForDateRange(startDate: Long, endDate: Long): List<RecipeIngredient> {
        val ingredients = getIngredientsForDateRange(startDate, endDate)
        val aggregated = mutableMapOf<String, RecipeIngredient>()

        for (ingredient in ingredients) {
            val key = "${ingredient.name.lowercase()}_${ingredient.unit.lowercase()}"
            val existing = aggregated[key]
            if (existing != null) {
                aggregated[key] = existing.copy(quantity = existing.quantity + ingredient.quantity)
            } else {
                aggregated[key] = ingredient
            }
        }

        return aggregated.values.toList()
    }

    // ============ Date Utilities ============

    companion object {
        /**
         * Get the start of the week (Monday) for a given timestamp
         */
        fun getWeekStartDate(timestamp: Long): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = timestamp
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return calendar.timeInMillis
        }

        /**
         * Get the end of the week (Sunday 23:59:59) for a given timestamp
         */
        fun getWeekEndDate(timestamp: Long): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = getWeekStartDate(timestamp)
                add(Calendar.DAY_OF_WEEK, 6)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            return calendar.timeInMillis
        }

        /**
         * Get the start of a day (midnight)
         */
        fun getDayStartDate(timestamp: Long): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return calendar.timeInMillis
        }
    }
}
