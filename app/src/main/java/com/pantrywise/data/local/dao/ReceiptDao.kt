package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.ReceiptEntity
import com.pantrywise.data.local.entity.ReceiptImageEntity
import com.pantrywise.data.local.entity.ReceiptStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    // Receipts
    @Query("SELECT * FROM receipts ORDER BY scannedAt DESC")
    fun getAllReceipts(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getReceiptById(id: String): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE id = :id")
    fun observeReceiptById(id: String): Flow<ReceiptEntity?>

    @Query("SELECT * FROM receipts WHERE status = :status ORDER BY scannedAt DESC")
    fun getReceiptsByStatus(status: ReceiptStatus): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE storeId = :storeId ORDER BY scannedAt DESC")
    fun getReceiptsForStore(storeId: String): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE scannedAt >= :startDate AND scannedAt <= :endDate ORDER BY scannedAt DESC")
    fun getReceiptsByDateRange(startDate: Long, endDate: Long): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts WHERE transactionId = :transactionId")
    suspend fun getReceiptForTransaction(transactionId: String): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE status = 'PENDING' OR status = 'NEEDS_REVIEW' ORDER BY scannedAt ASC")
    fun getPendingReceipts(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipts ORDER BY scannedAt DESC LIMIT :limit")
    suspend fun getRecentReceipts(limit: Int = 10): List<ReceiptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Update
    suspend fun updateReceipt(receipt: ReceiptEntity)

    @Query("""
        UPDATE receipts
        SET status = :status,
            processingError = :error,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateReceiptStatus(
        id: String,
        status: ReceiptStatus,
        error: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE receipts
        SET storeName = :storeName,
            storeAddress = :storeAddress,
            subtotal = :subtotal,
            tax = :tax,
            total = :total,
            receiptDate = :receiptDate,
            receiptNumber = :receiptNumber,
            itemsJson = :itemsJson,
            confidence = :confidence,
            status = :status,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateReceiptData(
        id: String,
        storeName: String?,
        storeAddress: String?,
        subtotal: Double?,
        tax: Double?,
        total: Double?,
        receiptDate: Long?,
        receiptNumber: String?,
        itemsJson: String,
        confidence: Float,
        status: ReceiptStatus,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE receipts SET isVerified = :isVerified, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setReceiptVerified(id: String, isVerified: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE receipts SET transactionId = :transactionId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun linkReceiptToTransaction(id: String, transactionId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE receipts SET storeId = :storeId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun linkReceiptToStore(id: String, storeId: String, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteReceipt(receipt: ReceiptEntity)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteReceiptById(id: String)

    // Receipt images
    @Query("SELECT * FROM receipt_images WHERE receiptId = :receiptId ORDER BY pageNumber ASC")
    fun getImagesForReceipt(receiptId: String): Flow<List<ReceiptImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceiptImage(image: ReceiptImageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceiptImages(images: List<ReceiptImageEntity>)

    @Delete
    suspend fun deleteReceiptImage(image: ReceiptImageEntity)

    @Query("DELETE FROM receipt_images WHERE receiptId = :receiptId")
    suspend fun deleteImagesForReceipt(receiptId: String)

    // Stats
    @Query("SELECT COUNT(*) FROM receipts")
    suspend fun getReceiptCount(): Int

    @Query("SELECT COUNT(*) FROM receipts WHERE status = 'COMPLETED'")
    suspend fun getProcessedReceiptCount(): Int

    @Query("SELECT COALESCE(SUM(total), 0) FROM receipts WHERE status = 'COMPLETED'")
    suspend fun getTotalReceiptAmount(): Double

    @Query("""
        SELECT COALESCE(SUM(total), 0) FROM receipts
        WHERE status = 'COMPLETED'
        AND scannedAt >= :startDate AND scannedAt <= :endDate
    """)
    suspend fun getTotalReceiptAmountInRange(startDate: Long, endDate: Long): Double
}
