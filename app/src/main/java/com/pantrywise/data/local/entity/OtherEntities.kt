package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pantrywise.domain.model.ActionType
import com.pantrywise.domain.model.LocationType
import com.pantrywise.domain.model.SourceType
import com.pantrywise.domain.model.Unit
import java.util.UUID

@Entity(tableName = "purchase_transactions")
data class PurchaseTransactionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val store: String? = null,
    val date: Long = System.currentTimeMillis(),
    val itemsJson: String = "[]", // JSON serialized purchase items
    val total: Double,
    val currency: String = "USD",
    val sessionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class PurchaseItem(
    val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val quantity: Double,
    val unit: Unit,
    val unitPrice: Double? = null,
    val totalPrice: Double,
    val category: String
)

@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val preferredBrandsJson: String = "[]",
    val dislikedBrandsJson: String = "[]",
    val allergensJson: String = "[]",
    val dietaryRestrictionsJson: String = "[]",
    val defaultCurrency: String = "USD",
    val defaultLocation: LocationType = LocationType.PANTRY,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "action_events")
data class ActionEventEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType,
    val timestamp: Long = System.currentTimeMillis(),
    val entityType: String? = null,
    val entityId: String? = null,
    val payloadJson: String? = null,
    val source: SourceType? = null
)

@Entity(tableName = "pending_lookups")
data class PendingLookupEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val barcode: String,
    val scannedAt: Long = System.currentTimeMillis(),
    val context: String? = null,
    val retryCount: Int = 0,
    val resolved: Boolean = false
) {
    val canRetry: Boolean
        get() = retryCount < 3 && !resolved
}

/**
 * Minimum stock rules for tracking when products need restocking.
 * Items marked as isStaple=true are household essentials that must always be in stock.
 */
@Entity(tableName = "minimum_stock_rules")
data class MinimumStockRuleEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val productName: String,
    val minimumQuantity: Double = 1.0,
    val reorderQuantity: Double = 1.0,
    val autoAddToList: Boolean = true,
    val lastTriggeredAt: Long? = null,
    val isActive: Boolean = true,
    val isStaple: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun shouldTrigger(currentQuantity: Double): Boolean {
        if (!isActive) return false
        return currentQuantity <= minimumQuantity
    }

    fun displayMinimum(): String {
        return if (minimumQuantity == minimumQuantity.toLong().toDouble()) {
            minimumQuantity.toLong().toString()
        } else {
            String.format("%.1f", minimumQuantity)
        }
    }

    fun displayReorder(): String {
        return if (reorderQuantity == reorderQuantity.toLong().toDouble()) {
            reorderQuantity.toLong().toString()
        } else {
            String.format("%.1f", reorderQuantity)
        }
    }
}

/**
 * Represents a stock alert for an item below minimum threshold
 */
data class StockAlert(
    val rule: MinimumStockRuleEntity,
    val currentQuantity: Double,
    val suggestedQuantity: Double
)
