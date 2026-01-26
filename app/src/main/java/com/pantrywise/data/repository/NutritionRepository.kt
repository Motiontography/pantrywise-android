package com.pantrywise.data.repository

import com.pantrywise.data.local.dao.DailyNutritionTotals
import com.pantrywise.data.local.dao.NutritionDao
import com.pantrywise.data.local.entity.NutritionEntity
import com.pantrywise.data.local.entity.NutritionGoalsEntity
import com.pantrywise.data.local.entity.NutritionLogEntry
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class NutritionProgress(
    val totals: DailyNutritionTotals,
    val goals: NutritionGoalsEntity?,
    val caloriesProgress: Float,
    val proteinProgress: Float,
    val carbsProgress: Float,
    val fatProgress: Float,
    val fiberProgress: Float,
    val sugarProgress: Float,
    val sodiumProgress: Float
)

@Singleton
class NutritionRepository @Inject constructor(
    private val nutritionDao: NutritionDao
) {
    // Product nutrition
    suspend fun getNutritionForProduct(productId: String): NutritionEntity? =
        nutritionDao.getNutritionForProduct(productId)

    fun observeNutritionForProduct(productId: String): Flow<NutritionEntity?> =
        nutritionDao.observeNutritionForProduct(productId)

    suspend fun getNutritionForProducts(productIds: List<String>): List<NutritionEntity> =
        nutritionDao.getNutritionForProducts(productIds)

    suspend fun saveNutrition(nutrition: NutritionEntity) {
        nutritionDao.insertNutrition(nutrition)
    }

    suspend fun updateNutrition(nutrition: NutritionEntity) {
        nutritionDao.updateNutrition(nutrition.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNutritionForProduct(productId: String) {
        nutritionDao.deleteNutritionForProduct(productId)
    }

    suspend fun markAsUserEdited(productId: String) {
        nutritionDao.markAsUserEdited(productId)
    }

    // Nutrition logging
    fun getAllNutritionLogEntries(): Flow<List<NutritionLogEntry>> =
        nutritionDao.getAllNutritionLogEntries()

    fun getNutritionLogEntriesForDateRange(startDate: Long, endDate: Long): Flow<List<NutritionLogEntry>> =
        nutritionDao.getNutritionLogEntriesForDateRange(startDate, endDate)

    suspend fun logFood(
        productId: String?,
        productName: String,
        servings: Double = 1.0,
        calories: Double?,
        protein: Double?,
        carbohydrates: Double?,
        fat: Double?,
        fiber: Double?,
        sugar: Double?,
        sodium: Double?,
        mealType: String?,
        notes: String? = null
    ): NutritionLogEntry {
        val entry = NutritionLogEntry(
            productId = productId,
            productName = productName,
            servings = servings,
            calories = calories,
            protein = protein,
            carbohydrates = carbohydrates,
            fat = fat,
            fiber = fiber,
            sugar = sugar,
            sodium = sodium,
            mealType = mealType,
            notes = notes
        )
        nutritionDao.insertNutritionLogEntry(entry)
        return entry
    }

    suspend fun logFoodFromProduct(productId: String, servings: Double, mealType: String?): NutritionLogEntry? {
        val nutrition = nutritionDao.getNutritionForProduct(productId) ?: return null

        return logFood(
            productId = productId,
            productName = "Product", // TODO: Get actual product name
            servings = servings,
            calories = nutrition.calories,
            protein = nutrition.protein,
            carbohydrates = nutrition.totalCarbohydrates,
            fat = nutrition.totalFat,
            fiber = nutrition.dietaryFiber,
            sugar = nutrition.totalSugars,
            sodium = nutrition.sodium,
            mealType = mealType
        )
    }

    suspend fun deleteNutritionLogEntry(id: String) {
        nutritionDao.deleteNutritionLogEntryById(id)
    }

    // Daily tracking
    suspend fun getDailyNutritionTotals(date: Long = System.currentTimeMillis()): DailyNutritionTotals {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis - 1

        return nutritionDao.getDailyNutritionTotals(startOfDay, endOfDay)
    }

    suspend fun getDailyNutritionProgress(date: Long = System.currentTimeMillis()): NutritionProgress {
        val totals = getDailyNutritionTotals(date)
        val goals = nutritionDao.getActiveNutritionGoals()

        return NutritionProgress(
            totals = totals,
            goals = goals,
            caloriesProgress = calculateProgress(totals.totalCalories, goals?.caloriesGoal),
            proteinProgress = calculateProgress(totals.totalProtein, goals?.proteinGoal),
            carbsProgress = calculateProgress(totals.totalCarbs, goals?.carbsGoal),
            fatProgress = calculateProgress(totals.totalFat, goals?.fatGoal),
            fiberProgress = calculateProgress(totals.totalFiber, goals?.fiberGoal),
            sugarProgress = calculateProgress(totals.totalSugar, goals?.sugarLimit),
            sodiumProgress = calculateProgress(totals.totalSodium, goals?.sodiumLimit)
        )
    }

    private fun calculateProgress(current: Double, goal: Double?): Float {
        if (goal == null || goal == 0.0) return 0f
        return (current / goal).toFloat().coerceIn(0f, 2f)
    }

    // Goals
    suspend fun getActiveNutritionGoals(): NutritionGoalsEntity? =
        nutritionDao.getActiveNutritionGoals()

    fun observeActiveNutritionGoals(): Flow<NutritionGoalsEntity?> =
        nutritionDao.observeActiveNutritionGoals()

    suspend fun saveNutritionGoals(goals: NutritionGoalsEntity) {
        nutritionDao.deactivateOtherGoals(goals.id)
        nutritionDao.insertNutritionGoals(goals.copy(isActive = true))
    }

    suspend fun updateNutritionGoals(goals: NutritionGoalsEntity) {
        nutritionDao.updateNutritionGoals(goals.copy(updatedAt = System.currentTimeMillis()))
    }

    // Stats
    suspend fun getNutritionDataCount(): Int = nutritionDao.getNutritionDataCount()

    suspend fun getNutritionLogEntryCount(): Int = nutritionDao.getNutritionLogEntryCount()

    suspend fun getDaysWithLogs(): Int = nutritionDao.getDaysWithLogs()
}
