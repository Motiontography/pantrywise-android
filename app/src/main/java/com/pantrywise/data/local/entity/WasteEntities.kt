package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Reasons why food items were wasted
 */
enum class WasteReason {
    EXPIRED,
    SPOILED,
    MOLDY,
    FREEZER_BURN,
    STALE,
    TASTE_OFF,
    FORGOT_ABOUT_IT,
    TOO_MUCH_COOKED,
    DIDNT_LIKE_IT,
    CHANGED_PLANS,
    DAMAGED,
    OTHER
}

/**
 * Represents a food waste event for tracking and reducing waste
 */
@Entity(
    tableName = "waste_events",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventoryItemId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("productId"),
        Index("inventoryItemId"),
        Index("wastedAt"),
        Index("reason")
    ]
)
data class WasteEventEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val productId: String?,
    val inventoryItemId: String?,
    val productName: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val reason: WasteReason,
    val estimatedCost: Double? = null,
    val currency: String = "USD",
    val daysBeforeExpiration: Int? = null,
    val notes: String? = null,
    val imageUrl: String? = null,
    val wastedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val formattedCost: String?
        get() = estimatedCost?.let { String.format("$%.2f", it) }

    val displayReason: String
        get() = when (reason) {
            WasteReason.EXPIRED -> "Expired"
            WasteReason.SPOILED -> "Spoiled"
            WasteReason.MOLDY -> "Moldy"
            WasteReason.FREEZER_BURN -> "Freezer burn"
            WasteReason.STALE -> "Stale"
            WasteReason.TASTE_OFF -> "Taste was off"
            WasteReason.FORGOT_ABOUT_IT -> "Forgot about it"
            WasteReason.TOO_MUCH_COOKED -> "Cooked too much"
            WasteReason.DIDNT_LIKE_IT -> "Didn't like it"
            WasteReason.CHANGED_PLANS -> "Plans changed"
            WasteReason.DAMAGED -> "Damaged"
            WasteReason.OTHER -> "Other"
        }
}

/**
 * Data class for waste statistics
 */
data class WasteStatistics(
    val totalItems: Int,
    val totalCost: Double,
    val topReason: WasteReason?,
    val topCategory: String?,
    val periodStart: Long,
    val periodEnd: Long
)

/**
 * Data class for waste by reason aggregation
 */
data class WasteByReason(
    val reason: WasteReason,
    val count: Int,
    val totalCost: Double
)

/**
 * Data class for waste by category aggregation
 */
data class WasteByCategory(
    val category: String,
    val count: Int,
    val totalCost: Double
)
