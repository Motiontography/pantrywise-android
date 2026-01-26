package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Status of an audit session
 */
enum class AuditStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

/**
 * Action taken on an item during audit
 */
enum class AuditAction {
    CONFIRMED,
    ADJUSTED,
    REMOVED,
    SKIPPED
}

/**
 * Represents an inventory audit session for reconciling actual vs recorded stock
 */
@Entity(tableName = "audit_sessions")
data class AuditSessionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String? = null,
    val status: AuditStatus = AuditStatus.IN_PROGRESS,
    val totalItems: Int = 0,
    val auditedItems: Int = 0,
    val adjustedItems: Int = 0,
    val removedItems: Int = 0,
    val notes: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (totalItems > 0) auditedItems.toFloat() / totalItems else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()

    val isComplete: Boolean
        get() = status == AuditStatus.COMPLETED

    val displayStatus: String
        get() = when (status) {
            AuditStatus.IN_PROGRESS -> "In Progress"
            AuditStatus.COMPLETED -> "Completed"
            AuditStatus.CANCELLED -> "Cancelled"
        }
}

/**
 * Represents an individual item's audit result within a session
 */
@Entity(
    tableName = "audit_items",
    foreignKeys = [
        ForeignKey(
            entity = AuditSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["inventoryItemId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("sessionId"),
        Index("inventoryItemId")
    ]
)
data class AuditItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val inventoryItemId: String?,
    val productName: String,
    val category: String,
    val location: String,
    val expectedQuantity: Double,
    val actualQuantity: Double? = null,
    val unit: String,
    val action: AuditAction? = null,
    val notes: String? = null,
    val auditedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val quantityDifference: Double?
        get() = actualQuantity?.let { it - expectedQuantity }

    val hasDiscrepancy: Boolean
        get() = quantityDifference?.let { it != 0.0 } ?: false

    val displayAction: String?
        get() = when (action) {
            AuditAction.CONFIRMED -> "Confirmed"
            AuditAction.ADJUSTED -> "Adjusted"
            AuditAction.REMOVED -> "Removed"
            AuditAction.SKIPPED -> "Skipped"
            null -> null
        }
}

/**
 * Summary of audit results
 */
data class AuditSummary(
    val sessionId: String,
    val totalItems: Int,
    val confirmedItems: Int,
    val adjustedItems: Int,
    val removedItems: Int,
    val skippedItems: Int,
    val totalDiscrepancy: Double
)
