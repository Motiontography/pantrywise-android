package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a store where products can be purchased
 */
@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val phone: String? = null,
    val website: String? = null,
    val notes: String? = null,
    val isFavorite: Boolean = false,
    val lastVisited: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Represents a price record for a product at a specific store and time
 */
@Entity(
    tableName = "price_records",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("productId"),
        Index("storeId"),
        Index("recordedAt")
    ]
)
data class PriceRecordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val storeId: String,
    val price: Double,
    val currency: String = "USD",
    val unitSize: Double? = null,
    val unitType: String? = null,
    val pricePerUnit: Double? = null,
    val isOnSale: Boolean = false,
    val saleEndDate: Long? = null,
    val notes: String? = null,
    val recordedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val formattedPrice: String
        get() = String.format("$%.2f", price)

    val formattedPricePerUnit: String?
        get() = pricePerUnit?.let { String.format("$%.2f/%s", it, unitType ?: "unit") }
}

/**
 * Maps product categories/products to store aisles for shopping optimization
 */
@Entity(
    tableName = "store_aisle_maps",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("storeId")]
)
data class StoreAisleMapEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val storeId: String,
    val categoryName: String,
    val aisle: String,
    val section: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Price alert for tracking price drops
 */
@Entity(
    tableName = "price_alerts",
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
data class PriceAlertEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val targetPrice: Double,
    val isActive: Boolean = true,
    val triggeredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
