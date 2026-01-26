package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Comprehensive nutrition data for a product
 * Based on FDA nutrition label requirements and extended nutrients
 */
@Entity(
    tableName = "nutrition_data",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId", unique = true)]
)
data class NutritionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val productId: String,

    // Serving Information
    val servingSize: Double? = null,
    val servingSizeUnit: String? = null,
    val servingsPerContainer: Double? = null,

    // Core Macronutrients (per serving)
    val calories: Double? = null,
    val totalFat: Double? = null,
    val saturatedFat: Double? = null,
    val transFat: Double? = null,
    val polyunsaturatedFat: Double? = null,
    val monounsaturatedFat: Double? = null,
    val cholesterol: Double? = null,
    val sodium: Double? = null,
    val totalCarbohydrates: Double? = null,
    val dietaryFiber: Double? = null,
    val totalSugars: Double? = null,
    val addedSugars: Double? = null,
    val sugarAlcohols: Double? = null,
    val protein: Double? = null,

    // Vitamins (% Daily Value or absolute)
    val vitaminA: Double? = null,
    val vitaminC: Double? = null,
    val vitaminD: Double? = null,
    val vitaminE: Double? = null,
    val vitaminK: Double? = null,
    val vitaminB1: Double? = null,  // Thiamin
    val vitaminB2: Double? = null,  // Riboflavin
    val vitaminB3: Double? = null,  // Niacin
    val vitaminB6: Double? = null,
    val vitaminB12: Double? = null,
    val folate: Double? = null,
    val biotin: Double? = null,
    val pantothenicAcid: Double? = null,

    // Minerals
    val calcium: Double? = null,
    val iron: Double? = null,
    val potassium: Double? = null,
    val magnesium: Double? = null,
    val zinc: Double? = null,
    val phosphorus: Double? = null,
    val copper: Double? = null,
    val manganese: Double? = null,
    val selenium: Double? = null,
    val chromium: Double? = null,
    val molybdenum: Double? = null,
    val iodine: Double? = null,

    // Extended Nutrients
    val caffeine: Double? = null,
    val water: Double? = null,
    val alcohol: Double? = null,
    val omega3: Double? = null,
    val omega6: Double? = null,

    // Metadata
    val sourceApi: String? = null,
    val confidence: Float = 1.0f,
    val isUserEdited: Boolean = false,
    val labelFormat: String? = null,  // US, EU, Canadian, etc.
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Display helpers for nutrition label
    val caloriesDisplay: String
        get() = calories?.toInt()?.toString() ?: "—"

    val totalFatDisplay: String
        get() = totalFat?.let { "${it}g" } ?: "—"

    val carbsDisplay: String
        get() = totalCarbohydrates?.let { "${it}g" } ?: "—"

    val proteinDisplay: String
        get() = protein?.let { "${it}g" } ?: "—"

    val sodiumDisplay: String
        get() = sodium?.let { "${it.toInt()}mg" } ?: "—"

    val fiberDisplay: String
        get() = dietaryFiber?.let { "${it}g" } ?: "—"

    val sugarDisplay: String
        get() = totalSugars?.let { "${it}g" } ?: "—"

    // Calculate % Daily Value (based on FDA 2,000 calorie diet)
    fun fatDailyValue(): Int? = totalFat?.let { (it / 78 * 100).toInt() }
    fun saturatedFatDailyValue(): Int? = saturatedFat?.let { (it / 20 * 100).toInt() }
    fun cholesterolDailyValue(): Int? = cholesterol?.let { (it / 300 * 100).toInt() }
    fun sodiumDailyValue(): Int? = sodium?.let { (it / 2300 * 100).toInt() }
    fun carbsDailyValue(): Int? = totalCarbohydrates?.let { (it / 275 * 100).toInt() }
    fun fiberDailyValue(): Int? = dietaryFiber?.let { (it / 28 * 100).toInt() }
    fun addedSugarsDailyValue(): Int? = addedSugars?.let { (it / 50 * 100).toInt() }
    fun proteinDailyValue(): Int? = protein?.let { (it / 50 * 100).toInt() }
    fun vitaminDDailyValue(): Int? = vitaminD?.let { (it / 20 * 100).toInt() }
    fun calciumDailyValue(): Int? = calcium?.let { (it / 1300 * 100).toInt() }
    fun ironDailyValue(): Int? = iron?.let { (it / 18 * 100).toInt() }
    fun potassiumDailyValue(): Int? = potassium?.let { (it / 4700 * 100).toInt() }
}

/**
 * Daily nutrition log entry for health tracking
 */
@Entity(
    tableName = "nutrition_log_entries",
    indices = [Index("loggedAt")]
)
data class NutritionLogEntry(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val productId: String?,
    val productName: String,
    val servings: Double = 1.0,
    val calories: Double?,
    val protein: Double?,
    val carbohydrates: Double?,
    val fat: Double?,
    val fiber: Double?,
    val sugar: Double?,
    val sodium: Double?,
    val mealType: String?,  // breakfast, lunch, dinner, snack
    val notes: String? = null,
    val loggedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Daily nutrition goals for health tracking
 */
@Entity(tableName = "nutrition_goals")
data class NutritionGoalsEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val caloriesGoal: Double = 2000.0,
    val proteinGoal: Double = 50.0,
    val carbsGoal: Double = 275.0,
    val fatGoal: Double = 78.0,
    val fiberGoal: Double = 28.0,
    val sugarLimit: Double = 50.0,
    val sodiumLimit: Double = 2300.0,
    val waterGoal: Double = 2000.0,  // ml
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
