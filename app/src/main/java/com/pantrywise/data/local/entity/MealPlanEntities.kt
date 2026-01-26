package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Meal types for meal planning
 */
enum class MealType(val displayName: String, val icon: String) {
    BREAKFAST("Breakfast", "free_breakfast"),
    LUNCH("Lunch", "lunch_dining"),
    DINNER("Dinner", "dinner_dining"),
    SNACK("Snack", "cookie")
}

/**
 * Recipe entity for storing recipes
 */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val instructions: String? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val servings: Int = 4,
    val ingredientsJson: String = "[]", // JSON array of RecipeIngredient
    val imageUrl: String? = null,
    val sourceUrl: String? = null,
    val isAiGenerated: Boolean = false,
    val aiQuery: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val totalTimeMinutes: Int?
        get() {
            val prep = prepTimeMinutes ?: 0
            val cook = cookTimeMinutes ?: 0
            return if (prep > 0 || cook > 0) prep + cook else null
        }
}

/**
 * Ingredient for a recipe (stored as JSON in RecipeEntity)
 */
data class RecipeIngredient(
    val name: String,
    val quantity: Double,
    val unit: String,
    val productId: String? = null,
    val notes: String? = null
) {
    val displayString: String
        get() = buildString {
            if (quantity > 0) {
                append(quantity.toString().removeSuffix(".0"))
                append(" ")
            }
            if (unit.isNotEmpty() && unit != "item") {
                append(unit)
                append(" ")
            }
            append(name)
            notes?.let { append(" ($it)") }
        }
}

/**
 * Meal plan entity for storing a week's worth of meal plans
 */
@Entity(
    tableName = "meal_plans",
    indices = [Index("weekStartDate")]
)
data class MealPlanEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val weekStartDate: Long, // Monday of the week (normalized to midnight)
    val name: String? = null, // Optional name for the plan
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Meal plan entry - a single meal on a specific day
 */
@Entity(
    tableName = "meal_plan_entries",
    foreignKeys = [
        ForeignKey(
            entity = MealPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealPlanId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("mealPlanId"), Index("recipeId"), Index("date")]
)
data class MealPlanEntryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val mealPlanId: String,
    val date: Long, // The specific day for this meal
    val mealType: MealType,
    val recipeId: String? = null, // Link to a recipe, or null for custom meal
    val customMealName: String? = null, // For quick entry without full recipe
    val servings: Int = 2,
    val notes: String? = null,
    val calendarEventId: String? = null, // For calendar sync
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Recipe with entries - used for displaying recipes with their usage
 */
data class RecipeWithUsage(
    val recipe: RecipeEntity,
    val usageCount: Int
)
