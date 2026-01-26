package com.pantrywise.wear.data

import kotlinx.serialization.Serializable

/**
 * Watch Shopping Item - lightweight model for Watch connectivity
 */
@Serializable
data class WearShoppingItem(
    val id: String,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "each",
    val isChecked: Boolean = false,
    val aisle: String? = null,
    val priority: Int = 5,
    val estimatedPrice: Double? = null
) {
    val displayQuantity: String
        get() = if (quantity == quantity.toLong().toDouble()) {
            "${quantity.toInt()} $unit"
        } else {
            String.format("%.1f %s", quantity, unit)
        }

    val formattedPrice: String?
        get() = estimatedPrice?.let { String.format("$%.2f", it) }
}

/**
 * Watch Expiring Item - lightweight model for Watch connectivity
 */
@Serializable
data class WearExpiringItem(
    val id: String,
    val name: String,
    val expirationDate: Long, // Timestamp
    val quantity: Double = 1.0,
    val unit: String = "each",
    val location: String = "Pantry",
    val daysUntilExpiration: Int = 0
) {
    val isExpired: Boolean
        get() = daysUntilExpiration < 0

    val isExpiringSoon: Boolean
        get() = daysUntilExpiration in 0..3

    val urgencyLevel: UrgencyLevel
        get() = when {
            isExpired -> UrgencyLevel.EXPIRED
            daysUntilExpiration <= 1 -> UrgencyLevel.URGENT
            daysUntilExpiration <= 3 -> UrgencyLevel.WARNING
            else -> UrgencyLevel.NORMAL
        }

    val countdownText: String
        get() = when {
            daysUntilExpiration < 0 -> "Expired ${-daysUntilExpiration}d ago"
            daysUntilExpiration == 0 -> "Expires today"
            daysUntilExpiration == 1 -> "Expires tomorrow"
            else -> "$daysUntilExpiration days left"
        }

    val shortCountdown: String
        get() = when {
            daysUntilExpiration < 0 -> "-${-daysUntilExpiration}d"
            daysUntilExpiration == 0 -> "Today"
            else -> "${daysUntilExpiration}d"
        }
}

enum class UrgencyLevel {
    EXPIRED,
    URGENT,
    WARNING,
    NORMAL
}

/**
 * Quick Add Item - preset for quick adding
 */
@Serializable
data class WearQuickAddItem(
    val id: String,
    val name: String,
    val category: String = "Grocery",
    val defaultQuantity: Double = 1.0,
    val defaultUnit: String = "each",
    val icon: String = "cart"
) {
    companion object {
        val defaultPresets = listOf(
            WearQuickAddItem(id = "1", name = "Milk", category = "Dairy", icon = "drop"),
            WearQuickAddItem(id = "2", name = "Bread", category = "Bakery", icon = "rectangle"),
            WearQuickAddItem(id = "3", name = "Eggs", category = "Dairy", defaultQuantity = 12.0, icon = "oval"),
            WearQuickAddItem(id = "4", name = "Bananas", category = "Produce", icon = "leaf"),
            WearQuickAddItem(id = "5", name = "Apples", category = "Produce", icon = "apple"),
            WearQuickAddItem(id = "6", name = "Chicken", category = "Meat", icon = "fork_knife"),
            WearQuickAddItem(id = "7", name = "Rice", category = "Pantry", icon = "square"),
            WearQuickAddItem(id = "8", name = "Pasta", category = "Pantry", icon = "square"),
            WearQuickAddItem(id = "9", name = "Butter", category = "Dairy", icon = "rectangle"),
            WearQuickAddItem(id = "10", name = "Cheese", category = "Dairy", icon = "square"),
            WearQuickAddItem(id = "11", name = "Onions", category = "Produce", icon = "circle"),
            WearQuickAddItem(id = "12", name = "Tomatoes", category = "Produce", icon = "circle")
        )
    }
}

/**
 * Sync data container
 */
@Serializable
data class WearSyncData(
    val shoppingItems: List<WearShoppingItem> = emptyList(),
    val expiringItems: List<WearExpiringItem> = emptyList(),
    val quickAddPresets: List<WearQuickAddItem> = WearQuickAddItem.defaultPresets,
    val lastSyncDate: Long = System.currentTimeMillis(),
    val shoppingListName: String? = null
) {
    val uncheckedShoppingCount: Int
        get() = shoppingItems.count { !it.isChecked }

    val expiringItemCount: Int
        get() = expiringItems.count { it.daysUntilExpiration <= 3 }
}

/**
 * Message types for phone-watch communication
 */
enum class WearMessageType(val path: String) {
    SYNC_REQUEST("/pantrywise/sync_request"),
    SYNC_RESPONSE("/pantrywise/sync_response"),
    ITEM_CHECKED("/pantrywise/item_checked"),
    ITEM_UNCHECKED("/pantrywise/item_unchecked"),
    ITEM_ADDED("/pantrywise/item_added"),
    REFRESH_REQUIRED("/pantrywise/refresh_required")
}
