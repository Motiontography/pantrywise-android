package com.pantrywise.services.ml

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pantrywise.data.local.dao.InventoryDao
import com.pantrywise.data.local.dao.MealPlanDao
import com.pantrywise.data.local.dao.PriceDao
import com.pantrywise.data.local.dao.ProductDao
import com.pantrywise.data.local.dao.ShoppingListDao
import com.pantrywise.data.local.entity.InventoryItemEntity
import com.pantrywise.data.local.entity.RecipeEntity
import com.pantrywise.data.local.entity.RecipeIngredient
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

private val gson = Gson()

private fun RecipeEntity.getIngredients(): List<RecipeIngredient> {
    return try {
        val type = object : TypeToken<List<RecipeIngredient>>() {}.type
        gson.fromJson(ingredientsJson, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

enum class SuggestionType {
    LOW_STOCK,
    FREQUENTLY_BOUGHT,
    EXPIRING_SOON,
    MEAL_PLAN_NEEDED,
    SEASONAL,
    BUDGET_FRIENDLY,
    COMPANION_ITEM,
    RESTOCK_PATTERN,
    PRICE_DROP
}

enum class SuggestionPriority {
    HIGH,
    MEDIUM,
    LOW
}

data class SmartSuggestion(
    val id: String,
    val type: SuggestionType,
    val priority: SuggestionPriority,
    val productId: String?,
    val productName: String,
    val title: String,
    val description: String,
    val actionLabel: String,
    val quantity: Double = 1.0,
    val unit: String = "item",
    val confidenceScore: Float = 0.0f,
    val metadata: Map<String, String> = emptyMap()
)

@Singleton
class SmartSuggestionEngine @Inject constructor(
    private val purchasePatternAnalyzer: PurchasePatternAnalyzer,
    private val inventoryDao: InventoryDao,
    private val shoppingListDao: ShoppingListDao,
    private val mealPlanDao: MealPlanDao,
    private val priceDao: PriceDao,
    private val productDao: ProductDao
) {
    companion object {
        private const val MAX_SUGGESTIONS = 10
        private const val EXPIRING_SOON_DAYS = 7
        private const val LOW_STOCK_THRESHOLD = 2
    }

    // Cache product names for performance
    private val productNameCache = mutableMapOf<String, String>()

    private suspend fun getProductName(productId: String): String {
        return productNameCache.getOrPut(productId) {
            productDao.getProductById(productId)?.name ?: "Unknown Product"
        }
    }

    /**
     * Generates all smart suggestions based on current data
     */
    suspend fun generateSuggestions(): List<SmartSuggestion> {
        val suggestions = mutableListOf<SmartSuggestion>()

        // Get current shopping list items to avoid suggesting duplicates
        val shoppingListItems = shoppingListDao.getAllShoppingListItems().first()
        val shoppingListProductIds = shoppingListItems.map { it.productId }.toSet()

        // Generate each type of suggestion
        suggestions.addAll(generateLowStockSuggestions(shoppingListProductIds))
        suggestions.addAll(generateExpiringSoonSuggestions(shoppingListProductIds))
        suggestions.addAll(generateRestockPatternSuggestions(shoppingListProductIds))
        suggestions.addAll(generateFrequentlyBoughtSuggestions(shoppingListProductIds))
        suggestions.addAll(generateMealPlanSuggestions(shoppingListProductIds))
        suggestions.addAll(generateSeasonalSuggestions(shoppingListProductIds))
        suggestions.addAll(generatePriceDropSuggestions(shoppingListProductIds))

        // Sort by priority and limit
        return suggestions
            .distinctBy { it.productId ?: it.id }
            .sortedWith(
                compareBy<SmartSuggestion> { it.priority.ordinal }
                    .thenByDescending { it.confidenceScore }
            )
            .take(MAX_SUGGESTIONS)
    }

    /**
     * Suggestions for items running low in inventory
     */
    private suspend fun generateLowStockSuggestions(
        excludeProductIds: Set<String?>
    ): List<SmartSuggestion> {
        val inventoryItems = inventoryDao.getAllInventoryItems().first()

        return inventoryItems
            .filter { it.quantityOnHand <= LOW_STOCK_THRESHOLD && it.productId !in excludeProductIds }
            .map { item ->
                val productName = getProductName(item.productId)
                SmartSuggestion(
                    id = "low_stock_${item.id}",
                    type = SuggestionType.LOW_STOCK,
                    priority = if (item.quantityOnHand == 0.0) SuggestionPriority.HIGH else SuggestionPriority.MEDIUM,
                    productId = item.productId,
                    productName = productName,
                    title = "Running low on $productName",
                    description = if (item.quantityOnHand == 0.0) "Out of stock"
                    else "Only ${item.quantityOnHand.toInt()} ${item.unit.displayName} left",
                    actionLabel = "Add to list",
                    quantity = item.quantityOnHand,
                    unit = item.unit.displayName,
                    confidenceScore = 1.0f
                )
            }
    }

    /**
     * Suggestions for items expiring soon
     */
    private suspend fun generateExpiringSoonSuggestions(
        excludeProductIds: Set<String?>
    ): List<SmartSuggestion> {
        val inventoryItems = inventoryDao.getAllInventoryItems().first()
        val today = LocalDate.now()

        return inventoryItems
            .filter { item ->
                item.productId !in excludeProductIds &&
                        item.expirationDate != null &&
                        item.quantityOnHand > 0
            }
            .mapNotNull { item ->
                val expirationDate = Instant.ofEpochMilli(item.expirationDate!!)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                val daysUntilExpiration = ChronoUnit.DAYS.between(today, expirationDate).toInt()

                if (daysUntilExpiration in 0..EXPIRING_SOON_DAYS) {
                    val productName = getProductName(item.productId)
                    SmartSuggestion(
                        id = "expiring_${item.id}",
                        type = SuggestionType.EXPIRING_SOON,
                        priority = if (daysUntilExpiration <= 2) SuggestionPriority.HIGH else SuggestionPriority.MEDIUM,
                        productId = item.productId,
                        productName = productName,
                        title = "$productName expiring soon",
                        description = when (daysUntilExpiration) {
                            0 -> "Expires today!"
                            1 -> "Expires tomorrow"
                            else -> "Expires in $daysUntilExpiration days"
                        },
                        actionLabel = "Use it or replace",
                        quantity = item.quantityOnHand,
                        unit = item.unit.displayName,
                        confidenceScore = 1.0f,
                        metadata = mapOf("daysUntilExpiration" to daysUntilExpiration.toString())
                    )
                } else null
            }
    }

    /**
     * Suggestions based on purchase pattern analysis
     */
    private suspend fun generateRestockPatternSuggestions(
        excludeProductIds: Set<String?>
    ): List<SmartSuggestion> {
        val productsDueForRestock = purchasePatternAnalyzer.getProductsDueForRestock()

        return productsDueForRestock
            .filter { (pattern, _) -> pattern.productId !in excludeProductIds }
            .map { (pattern, daysUntilRestock) ->
                val priority = when {
                    daysUntilRestock < 0 -> SuggestionPriority.HIGH
                    daysUntilRestock <= 3 -> SuggestionPriority.MEDIUM
                    else -> SuggestionPriority.LOW
                }

                SmartSuggestion(
                    id = "restock_${pattern.productId}",
                    type = SuggestionType.RESTOCK_PATTERN,
                    priority = priority,
                    productId = pattern.productId,
                    productName = pattern.productName,
                    title = "Time to restock ${pattern.productName}",
                    description = when {
                        daysUntilRestock < 0 -> "Usually purchased ${-daysUntilRestock} days ago"
                        daysUntilRestock == 0 -> "Usually purchased today"
                        else -> "Usually purchased in $daysUntilRestock days"
                    },
                    actionLabel = "Add to list",
                    quantity = pattern.averageQuantity,
                    confidenceScore = pattern.confidenceScore,
                    metadata = mapOf(
                        "avgInterval" to pattern.averagePurchaseInterval.toString(),
                        "totalPurchases" to pattern.totalPurchases.toString()
                    )
                )
            }
    }

    /**
     * Suggestions for frequently purchased items not in inventory
     */
    private suspend fun generateFrequentlyBoughtSuggestions(
        excludeProductIds: Set<String?>
    ): List<SmartSuggestion> {
        val patterns = purchasePatternAnalyzer.analyzePurchasePatterns()
            .filter { it.isRecurring && it.confidenceScore >= 0.6f }
            .sortedByDescending { it.totalPurchases }
            .take(5)

        val inventoryProductIds = inventoryDao.getAllInventoryItems().first()
            .filter { it.quantityOnHand > 0 }
            .map { it.productId }
            .toSet()

        return patterns
            .filter { pattern ->
                pattern.productId !in excludeProductIds &&
                        pattern.productId !in inventoryProductIds
            }
            .map { pattern ->
                SmartSuggestion(
                    id = "frequent_${pattern.productId}",
                    type = SuggestionType.FREQUENTLY_BOUGHT,
                    priority = SuggestionPriority.LOW,
                    productId = pattern.productId,
                    productName = pattern.productName,
                    title = "Frequently purchased: ${pattern.productName}",
                    description = "Bought ${pattern.totalPurchases} times, not in pantry",
                    actionLabel = "Add to list",
                    quantity = pattern.averageQuantity,
                    confidenceScore = pattern.confidenceScore
                )
            }
    }

    /**
     * Suggestions for meal plan ingredients
     */
    private suspend fun generateMealPlanSuggestions(
        excludeProductIds: Set<String?>
    ): List<SmartSuggestion> {
        val today = LocalDate.now()
        val weekFromNow = today.plusDays(7)

        val todayMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val weekMillis = weekFromNow.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val upcomingMealPlans = mealPlanDao.getEntriesForDateRange(todayMillis, weekMillis).first()
        val inventoryItems = inventoryDao.getAllInventoryItems().first()

        // Build a set of inventory product names (lowercase for comparison)
        val inventoryProductNames = mutableSetOf<String>()
        for (item in inventoryItems) {
            if (item.quantityOnHand > 0) {
                val productName = getProductName(item.productId)
                inventoryProductNames.add(productName.lowercase())
            }
        }

        val suggestions = mutableListOf<SmartSuggestion>()

        for (entry in upcomingMealPlans) {
            val recipe = entry.recipeId?.let { mealPlanDao.getRecipeById(it) }

            recipe?.getIngredients()?.forEach { ingredient ->
                val ingredientLower = ingredient.name.lowercase()

                // Check if ingredient is in inventory
                if (!inventoryProductNames.any { it.contains(ingredientLower) || ingredientLower.contains(it) }) {
                    if (ingredient.name !in excludeProductIds.mapNotNull { it }) {
                        suggestions.add(
                            SmartSuggestion(
                                id = "meal_plan_${entry.id}_${ingredient.name.hashCode()}",
                                type = SuggestionType.MEAL_PLAN_NEEDED,
                                priority = SuggestionPriority.MEDIUM,
                                productId = null,
                                productName = ingredient.name,
                                title = "Needed for: ${recipe.name}",
                                description = "${ingredient.quantity} ${ingredient.unit} ${ingredient.name}",
                                actionLabel = "Add to list",
                                quantity = ingredient.quantity,
                                unit = ingredient.unit,
                                confidenceScore = 0.9f,
                                metadata = mapOf(
                                    "recipeName" to recipe.name,
                                    "mealDate" to entry.date.toString()
                                )
                            )
                        )
                    }
                }
            }
        }

        return suggestions.distinctBy { it.productName.lowercase() }
    }

    /**
     * Suggestions for seasonal items
     */
    private suspend fun generateSeasonalSuggestions(
        excludeProductIds: Set<String?>
    ): List<SmartSuggestion> {
        val seasonalPatterns = purchasePatternAnalyzer.analyzeSeasonalPatterns()
            .filter { it.isCurrentlySeasonal }

        return seasonalPatterns
            .filter { it.productId !in excludeProductIds }
            .map { pattern ->
                SmartSuggestion(
                    id = "seasonal_${pattern.productId}",
                    type = SuggestionType.SEASONAL,
                    priority = SuggestionPriority.LOW,
                    productId = pattern.productId,
                    productName = pattern.productName,
                    title = "${pattern.productName} is in season",
                    description = "You usually buy this around this time",
                    actionLabel = "Add to list",
                    confidenceScore = pattern.seasonalScore
                )
            }
    }

    /**
     * Suggestions for products with price drops
     */
    private suspend fun generatePriceDropSuggestions(
        excludeProductIds: Set<String?>
    ): List<SmartSuggestion> {
        val activeSales = priceDao.getActiveSales().first()

        return activeSales
            .filter { it.productId !in excludeProductIds }
            .map { priceRecord ->
                val productName = getProductName(priceRecord.productId)
                SmartSuggestion(
                    id = "price_drop_${priceRecord.id}",
                    type = SuggestionType.PRICE_DROP,
                    priority = SuggestionPriority.MEDIUM,
                    productId = priceRecord.productId,
                    productName = productName,
                    title = "Price drop alert",
                    description = "Now ${priceRecord.formattedPrice} - On sale!",
                    actionLabel = "Add to list",
                    confidenceScore = 0.8f,
                    metadata = mapOf(
                        "currentPrice" to priceRecord.price.toString(),
                        "storeId" to priceRecord.storeId
                    )
                )
            }
    }

    /**
     * Gets companion product suggestions when adding an item to cart
     */
    suspend fun getCompanionSuggestions(productId: String): List<SmartSuggestion> {
        val companions = purchasePatternAnalyzer.findCompanionProducts(productId)

        val shoppingListProductIds = shoppingListDao.getAllShoppingListItems().first()
            .map { it.productId }
            .toSet()

        return companions
            .filter { it.productId !in shoppingListProductIds }
            .map { companion ->
                SmartSuggestion(
                    id = "companion_${companion.productId}",
                    type = SuggestionType.COMPANION_ITEM,
                    priority = SuggestionPriority.LOW,
                    productId = companion.productId,
                    productName = companion.productName,
                    title = "Frequently bought together",
                    description = "Bought together ${companion.purchasedTogetherCount} times",
                    actionLabel = "Add to list",
                    confidenceScore = companion.coOccurrenceScore
                )
            }
    }

    /**
     * Dismisses a suggestion (stored in preferences)
     */
    suspend fun dismissSuggestion(suggestionId: String) {
        // Would store dismissed suggestions in DataStore/SharedPreferences
        // to avoid showing them again
    }
}
