package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Status of receipt processing
 */
enum class ReceiptStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    NEEDS_REVIEW
}

/**
 * Represents a scanned receipt with extracted data
 */
@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PurchaseTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("storeId"),
        Index("transactionId"),
        Index("scannedAt")
    ]
)
data class ReceiptEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val imageUri: String,
    val thumbnailUri: String? = null,
    val storeId: String? = null,
    val transactionId: String? = null,
    val storeName: String? = null,
    val storeAddress: String? = null,
    val subtotal: Double? = null,
    val tax: Double? = null,
    val total: Double? = null,
    val currency: String = "USD",
    val paymentMethod: String? = null,
    val receiptDate: Long? = null,
    val receiptNumber: String? = null,
    val itemsJson: String = "[]",  // JSON array of ReceiptLineItem
    val rawText: String? = null,
    val status: ReceiptStatus = ReceiptStatus.PENDING,
    val processingError: String? = null,
    val confidence: Float = 0f,
    val isVerified: Boolean = false,
    val scannedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val formattedTotal: String?
        get() = total?.let { String.format("$%.2f", it) }

    val formattedSubtotal: String?
        get() = subtotal?.let { String.format("$%.2f", it) }

    val formattedTax: String?
        get() = tax?.let { String.format("$%.2f", it) }

    val displayStatus: String
        get() = when (status) {
            ReceiptStatus.PENDING -> "Pending"
            ReceiptStatus.PROCESSING -> "Processing"
            ReceiptStatus.COMPLETED -> "Completed"
            ReceiptStatus.FAILED -> "Failed"
            ReceiptStatus.NEEDS_REVIEW -> "Needs Review"
        }
}

/**
 * Represents a line item extracted from a receipt
 */
data class ReceiptLineItem(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val quantity: Double = 1.0,
    val unitPrice: Double? = null,
    val totalPrice: Double,
    val productId: String? = null,
    val barcode: String? = null,
    val category: String? = null,
    val isDiscount: Boolean = false,
    val isTax: Boolean = false,
    val confidence: Float = 1.0f
) {
    val formattedPrice: String
        get() = String.format("$%.2f", totalPrice)

    val formattedUnitPrice: String?
        get() = unitPrice?.let { String.format("$%.2f", it) }
}

/**
 * Receipt image stored locally
 */
@Entity(
    tableName = "receipt_images",
    foreignKeys = [
        ForeignKey(
            entity = ReceiptEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("receiptId")]
)
data class ReceiptImageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val receiptId: String,
    val imageUri: String,
    val pageNumber: Int = 1,
    val width: Int? = null,
    val height: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
