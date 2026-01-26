package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pantrywise.domain.model.LocationType
import com.pantrywise.domain.model.StockStatus
import com.pantrywise.domain.model.Unit
import java.util.Calendar
import java.util.UUID

@Entity(
    tableName = "inventory_items",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId")]
)
data class InventoryItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val location: LocationType = LocationType.PANTRY,
    val quantityOnHand: Double,
    val unit: Unit,
    val stockStatus: StockStatus = StockStatus.UNKNOWN,
    val reorderThreshold: Double = 1.0,
    val expirationDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun calculateStockStatus(): StockStatus {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        expirationDate?.let { expDate ->
            if (expDate < today) {
                return StockStatus.EXPIRED
            }
            val threeDaysFromNow = today + (3 * 24 * 60 * 60 * 1000)
            if (expDate <= threeDaysFromNow) {
                return StockStatus.EXPIRING_SOON
            }
        }

        return when {
            quantityOnHand == 0.0 -> StockStatus.OUT_OF_STOCK
            quantityOnHand <= reorderThreshold -> StockStatus.LOW
            else -> StockStatus.IN_STOCK
        }
    }

    val daysUntilExpiration: Int?
        get() {
            expirationDate ?: return null
            val today = System.currentTimeMillis()
            val diff = expirationDate - today
            return (diff / (24 * 60 * 60 * 1000)).toInt()
        }

    val expirationStatusText: String?
        get() {
            val days = daysUntilExpiration ?: return null
            return when {
                days < 0 -> "Expired ${-days} days ago"
                days == 0 -> "Expires today"
                days == 1 -> "Expires tomorrow"
                else -> "Expires in $days days"
            }
        }
}
