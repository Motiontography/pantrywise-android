package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pantrywise.domain.model.CartMatchType
import com.pantrywise.domain.model.SessionStatus
import com.pantrywise.domain.model.SourceType
import com.pantrywise.domain.model.Unit
import java.util.UUID

@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Shopping List",
    val isActive: Boolean = true,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val completedStore: String? = null,
    val completedTotal: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "shopping_list_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId"), Index("productId")]
)
data class ShoppingListItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val productId: String,
    val quantityNeeded: Double,
    val unit: Unit,
    val priority: Int = 5,
    val reason: String? = null,
    val suggestedBy: SourceType? = null,
    val isChecked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shopping_sessions")
data class ShoppingSessionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val store: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val cartItemsJson: String = "[]", // JSON serialized cart items
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class CartItem(
    val id: String = UUID.randomUUID().toString(),
    val productId: String,
    val quantity: Double,
    val unit: Unit,
    val unitPrice: Double? = null,
    val matchType: CartMatchType
) {
    val totalPrice: Double?
        get() = unitPrice?.let { it * quantity }
}
