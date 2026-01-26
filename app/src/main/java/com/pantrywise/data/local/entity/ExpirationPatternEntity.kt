package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents learned expiration patterns for products
 * Used by ML to predict expiration dates for items without explicit dates
 */
@Entity(
    tableName = "expiration_patterns",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("productId"),
        Index("category")
    ]
)
data class ExpirationPatternEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val productId: String?,
    val productName: String?,
    val category: String,
    val typicalDaysToExpiration: Int,
    val minDays: Int? = null,
    val maxDays: Int? = null,
    val storageLocation: String? = null,  // pantry, fridge, freezer
    val openedShelfLife: Int? = null,  // days after opening
    val confidence: Float = 1.0f,
    val sampleSize: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val displayRange: String
        get() = if (minDays != null && maxDays != null) {
            "$minDays-$maxDays days"
        } else {
            "$typicalDaysToExpiration days"
        }

    val displayOpenedShelfLife: String?
        get() = openedShelfLife?.let { "$it days after opening" }
}

/**
 * Default expiration patterns for common product categories
 */
object DefaultExpirationPatterns {
    val patterns = mapOf(
        // Fresh produce
        "Produce" to CategoryPattern(
            category = "Produce",
            subPatterns = mapOf(
                "leafy_greens" to SubPattern(5, 3, 7, "fridge"),
                "berries" to SubPattern(5, 3, 7, "fridge"),
                "citrus" to SubPattern(14, 10, 21, "fridge"),
                "apples" to SubPattern(28, 21, 42, "fridge"),
                "bananas" to SubPattern(5, 3, 7, "pantry"),
                "tomatoes" to SubPattern(7, 5, 10, "fridge"),
                "potatoes" to SubPattern(21, 14, 35, "pantry"),
                "onions" to SubPattern(30, 21, 60, "pantry"),
                "carrots" to SubPattern(21, 14, 28, "fridge"),
                "default" to SubPattern(7, 5, 14, "fridge")
            )
        ),
        // Dairy
        "Dairy & Eggs" to CategoryPattern(
            category = "Dairy & Eggs",
            subPatterns = mapOf(
                "milk" to SubPattern(7, 5, 10, "fridge"),
                "eggs" to SubPattern(28, 21, 35, "fridge"),
                "cheese_hard" to SubPattern(42, 30, 60, "fridge"),
                "cheese_soft" to SubPattern(14, 7, 21, "fridge"),
                "yogurt" to SubPattern(14, 10, 21, "fridge"),
                "butter" to SubPattern(30, 21, 60, "fridge"),
                "cream" to SubPattern(10, 7, 14, "fridge"),
                "default" to SubPattern(14, 7, 21, "fridge")
            )
        ),
        // Meat & Seafood
        "Meat & Seafood" to CategoryPattern(
            category = "Meat & Seafood",
            subPatterns = mapOf(
                "fresh_fish" to SubPattern(2, 1, 3, "fridge"),
                "fresh_poultry" to SubPattern(2, 1, 3, "fridge"),
                "fresh_beef" to SubPattern(4, 3, 5, "fridge"),
                "fresh_pork" to SubPattern(4, 3, 5, "fridge"),
                "ground_meat" to SubPattern(2, 1, 3, "fridge"),
                "deli_meat" to SubPattern(5, 3, 7, "fridge"),
                "frozen_meat" to SubPattern(180, 120, 365, "freezer"),
                "frozen_fish" to SubPattern(90, 60, 180, "freezer"),
                "default" to SubPattern(3, 2, 5, "fridge")
            )
        ),
        // Frozen
        "Frozen" to CategoryPattern(
            category = "Frozen",
            subPatterns = mapOf(
                "vegetables" to SubPattern(240, 180, 365, "freezer"),
                "fruit" to SubPattern(240, 180, 365, "freezer"),
                "ice_cream" to SubPattern(60, 30, 90, "freezer"),
                "prepared_meals" to SubPattern(90, 60, 180, "freezer"),
                "default" to SubPattern(180, 90, 365, "freezer")
            )
        ),
        // Pantry Staples
        "Pantry Staples" to CategoryPattern(
            category = "Pantry Staples",
            subPatterns = mapOf(
                "rice" to SubPattern(730, 365, 1095, "pantry"),
                "pasta" to SubPattern(730, 365, 1095, "pantry"),
                "flour" to SubPattern(365, 180, 730, "pantry"),
                "sugar" to SubPattern(730, 365, 1460, "pantry"),
                "oil" to SubPattern(365, 180, 730, "pantry"),
                "canned_goods" to SubPattern(730, 365, 1825, "pantry"),
                "spices" to SubPattern(730, 365, 1095, "pantry"),
                "default" to SubPattern(365, 180, 730, "pantry")
            )
        ),
        // Bakery
        "Bakery" to CategoryPattern(
            category = "Bakery",
            subPatterns = mapOf(
                "bread" to SubPattern(5, 3, 7, "pantry"),
                "rolls" to SubPattern(5, 3, 7, "pantry"),
                "pastries" to SubPattern(3, 2, 5, "pantry"),
                "tortillas" to SubPattern(14, 7, 21, "pantry"),
                "default" to SubPattern(5, 3, 10, "pantry")
            )
        ),
        // Beverages
        "Beverages" to CategoryPattern(
            category = "Beverages",
            subPatterns = mapOf(
                "juice" to SubPattern(10, 7, 14, "fridge"),
                "soda" to SubPattern(180, 90, 365, "pantry"),
                "coffee" to SubPattern(365, 180, 730, "pantry"),
                "tea" to SubPattern(730, 365, 1095, "pantry"),
                "default" to SubPattern(180, 90, 365, "pantry")
            )
        ),
        // Condiments
        "Condiments & Sauces" to CategoryPattern(
            category = "Condiments & Sauces",
            subPatterns = mapOf(
                "ketchup" to SubPattern(180, 90, 365, "fridge"),
                "mustard" to SubPattern(365, 180, 730, "fridge"),
                "mayonnaise" to SubPattern(60, 30, 90, "fridge"),
                "salad_dressing" to SubPattern(60, 30, 90, "fridge"),
                "soy_sauce" to SubPattern(730, 365, 1095, "pantry"),
                "hot_sauce" to SubPattern(365, 180, 730, "fridge"),
                "default" to SubPattern(180, 90, 365, "fridge")
            )
        ),
        // Snacks
        "Snacks" to CategoryPattern(
            category = "Snacks",
            subPatterns = mapOf(
                "chips" to SubPattern(60, 30, 90, "pantry"),
                "crackers" to SubPattern(120, 60, 180, "pantry"),
                "cookies" to SubPattern(60, 30, 120, "pantry"),
                "nuts" to SubPattern(180, 90, 365, "pantry"),
                "chocolate" to SubPattern(365, 180, 730, "pantry"),
                "default" to SubPattern(90, 30, 180, "pantry")
            )
        )
    )

    data class CategoryPattern(
        val category: String,
        val subPatterns: Map<String, SubPattern>
    )

    data class SubPattern(
        val typicalDays: Int,
        val minDays: Int,
        val maxDays: Int,
        val storageLocation: String
    )

    fun getPatternForCategory(category: String): SubPattern {
        return patterns[category]?.subPatterns?.get("default")
            ?: SubPattern(30, 14, 90, "pantry")
    }

    fun getPatternForProduct(category: String, productType: String): SubPattern {
        return patterns[category]?.subPatterns?.get(productType)
            ?: getPatternForCategory(category)
    }
}
