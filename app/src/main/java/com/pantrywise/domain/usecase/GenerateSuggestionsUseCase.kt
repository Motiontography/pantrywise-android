package com.pantrywise.domain.usecase

import com.pantrywise.data.local.dao.InventoryDao
import com.pantrywise.data.local.dao.ProductDao
import com.pantrywise.data.local.dao.ShoppingListDao
import com.pantrywise.data.local.entity.InventoryItemEntity
import com.pantrywise.data.local.entity.ProductEntity
import com.pantrywise.domain.model.SourceType
import com.pantrywise.domain.model.Unit
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject

enum class SuggestionType {
    LOW_STOCK,
    OUT_OF_STOCK,
    EXPIRING_SOON,
    FREQUENT_PURCHASE
}

data class ShoppingSuggestion(
    val product: ProductEntity,
    val inventoryItem: InventoryItemEntity?,
    val type: SuggestionType,
    val reason: String,
    val suggestedQuantity: Double,
    val unit: Unit,
    val priority: Int
)

class GenerateSuggestionsUseCase @Inject constructor(
    private val inventoryDao: InventoryDao,
    private val productDao: ProductDao,
    private val shoppingListDao: ShoppingListDao
) {
    /**
     * Generates shopping suggestions based on:
     * 1. LOW STOCK - items below reorder threshold
     * 2. OUT OF STOCK - items with zero quantity
     * 3. EXPIRING SOON - items expiring within 7 days
     *
     * Excludes items already on the active shopping list.
     */
    suspend operator fun invoke(activeListId: String? = null): List<ShoppingSuggestion> {
        val suggestions = mutableListOf<ShoppingSuggestion>()

        // Get items already on shopping list to exclude
        val existingListProductIds = if (activeListId != null) {
            shoppingListDao.getItemsByListId(activeListId).first()
                .map { it.productId }
                .toSet()
        } else {
            emptySet()
        }

        // 1. OUT OF STOCK suggestions (highest priority)
        val outOfStockItems = inventoryDao.getOutOfStockItems().first()
        for (item in outOfStockItems) {
            if (item.productId in existingListProductIds) continue

            val product = productDao.getProductById(item.productId) ?: continue

            suggestions.add(
                ShoppingSuggestion(
                    product = product,
                    inventoryItem = item,
                    type = SuggestionType.OUT_OF_STOCK,
                    reason = "Out of stock",
                    suggestedQuantity = item.reorderThreshold.coerceAtLeast(1.0),
                    unit = item.unit,
                    priority = 10
                )
            )
        }

        // 2. LOW STOCK suggestions
        val lowStockItems = inventoryDao.getLowStockItems().first()
        for (item in lowStockItems) {
            if (item.productId in existingListProductIds) continue
            // Skip if already added as out of stock
            if (suggestions.any { it.product.id == item.productId }) continue

            val product = productDao.getProductById(item.productId) ?: continue

            val suggestedQty = (item.reorderThreshold - item.quantityOnHand).coerceAtLeast(1.0)

            suggestions.add(
                ShoppingSuggestion(
                    product = product,
                    inventoryItem = item,
                    type = SuggestionType.LOW_STOCK,
                    reason = "Low stock (${item.quantityOnHand.toInt()} ${item.unit.displayName} remaining)",
                    suggestedQuantity = suggestedQty,
                    unit = item.unit,
                    priority = 8
                )
            )
        }

        // 3. EXPIRING SOON suggestions
        val sevenDaysFromNow = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
        val expiringItems = inventoryDao.getExpiringItems(sevenDaysFromNow).first()

        for (item in expiringItems) {
            // Only suggest replacement if not already in suggestions
            if (suggestions.any { it.product.id == item.productId }) continue
            if (item.productId in existingListProductIds) continue

            val product = productDao.getProductById(item.productId) ?: continue

            val daysUntilExpiration = item.daysUntilExpiration ?: 0
            val reason = when {
                daysUntilExpiration <= 0 -> "Expired - consider replacing"
                daysUntilExpiration == 1 -> "Expires tomorrow - consider replacing"
                else -> "Expires in $daysUntilExpiration days"
            }

            suggestions.add(
                ShoppingSuggestion(
                    product = product,
                    inventoryItem = item,
                    type = SuggestionType.EXPIRING_SOON,
                    reason = reason,
                    suggestedQuantity = 1.0,
                    unit = item.unit,
                    priority = if (daysUntilExpiration <= 1) 9 else 5
                )
            )
        }

        // Sort by priority (highest first)
        return suggestions.sortedByDescending { it.priority }
    }

    /**
     * Creates a source type for suggestions to track origin.
     */
    fun getSuggestionSourceType(): SourceType = SourceType.SUGGESTION

    /**
     * Gets suggestions count for badge display.
     */
    suspend fun getSuggestionsCount(activeListId: String? = null): Int {
        return invoke(activeListId).size
    }

    /**
     * Gets only urgent suggestions (out of stock or expiring within 3 days).
     */
    suspend fun getUrgentSuggestions(activeListId: String? = null): List<ShoppingSuggestion> {
        return invoke(activeListId).filter { suggestion ->
            suggestion.type == SuggestionType.OUT_OF_STOCK ||
                    (suggestion.type == SuggestionType.EXPIRING_SOON &&
                            (suggestion.inventoryItem?.daysUntilExpiration ?: 0) <= 3)
        }
    }
}
