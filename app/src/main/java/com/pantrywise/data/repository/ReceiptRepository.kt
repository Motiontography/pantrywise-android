package com.pantrywise.data.repository

import com.pantrywise.data.local.dao.ReceiptDao
import com.pantrywise.data.local.entity.ReceiptEntity
import com.pantrywise.data.local.entity.ReceiptImageEntity
import com.pantrywise.data.local.entity.ReceiptStatus
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepository @Inject constructor(
    private val receiptDao: ReceiptDao
) {
    // Receipts
    fun getAllReceipts(): Flow<List<ReceiptEntity>> = receiptDao.getAllReceipts()

    fun getReceiptsByStatus(status: ReceiptStatus): Flow<List<ReceiptEntity>> =
        receiptDao.getReceiptsByStatus(status)

    fun getReceiptsForStore(storeId: String): Flow<List<ReceiptEntity>> =
        receiptDao.getReceiptsForStore(storeId)

    fun getReceiptsByDateRange(startDate: Long, endDate: Long): Flow<List<ReceiptEntity>> =
        receiptDao.getReceiptsByDateRange(startDate, endDate)

    fun getPendingReceipts(): Flow<List<ReceiptEntity>> = receiptDao.getPendingReceipts()

    suspend fun getReceiptById(id: String): ReceiptEntity? = receiptDao.getReceiptById(id)

    fun observeReceiptById(id: String): Flow<ReceiptEntity?> = receiptDao.observeReceiptById(id)

    suspend fun getReceiptForTransaction(transactionId: String): ReceiptEntity? =
        receiptDao.getReceiptForTransaction(transactionId)

    suspend fun getRecentReceipts(limit: Int = 10): List<ReceiptEntity> =
        receiptDao.getRecentReceipts(limit)

    suspend fun insertReceipt(receipt: ReceiptEntity): Long = receiptDao.insertReceipt(receipt)

    suspend fun updateReceipt(receipt: ReceiptEntity) = receiptDao.updateReceipt(receipt)

    suspend fun updateReceiptStatus(id: String, status: ReceiptStatus, error: String? = null) =
        receiptDao.updateReceiptStatus(id, status, error)

    suspend fun setReceiptVerified(id: String, isVerified: Boolean) =
        receiptDao.setReceiptVerified(id, isVerified)

    suspend fun linkReceiptToTransaction(receiptId: String, transactionId: String) =
        receiptDao.linkReceiptToTransaction(receiptId, transactionId)

    suspend fun linkReceiptToStore(receiptId: String, storeId: String) =
        receiptDao.linkReceiptToStore(receiptId, storeId)

    suspend fun deleteReceipt(receipt: ReceiptEntity) = receiptDao.deleteReceipt(receipt)

    suspend fun deleteReceiptById(id: String) = receiptDao.deleteReceiptById(id)

    // Receipt images
    fun getImagesForReceipt(receiptId: String): Flow<List<ReceiptImageEntity>> =
        receiptDao.getImagesForReceipt(receiptId)

    suspend fun insertReceiptImage(image: ReceiptImageEntity): Long =
        receiptDao.insertReceiptImage(image)

    suspend fun insertReceiptImages(images: List<ReceiptImageEntity>) =
        receiptDao.insertReceiptImages(images)

    suspend fun deleteImagesForReceipt(receiptId: String) =
        receiptDao.deleteImagesForReceipt(receiptId)

    // Statistics
    suspend fun getReceiptCount(): Int = receiptDao.getReceiptCount()

    suspend fun getProcessedReceiptCount(): Int = receiptDao.getProcessedReceiptCount()

    suspend fun getTotalReceiptAmount(): Double = receiptDao.getTotalReceiptAmount()

    suspend fun getTotalReceiptAmountInRange(startDate: Long, endDate: Long): Double =
        receiptDao.getTotalReceiptAmountInRange(startDate, endDate)

    suspend fun getThisMonthReceiptTotal(): Double {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val endOfMonth = calendar.timeInMillis - 1

        return receiptDao.getTotalReceiptAmountInRange(startOfMonth, endOfMonth)
    }

    suspend fun getThisWeekReceiptTotal(): Double {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis

        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        val endOfWeek = calendar.timeInMillis - 1

        return receiptDao.getTotalReceiptAmountInRange(startOfWeek, endOfWeek)
    }
}
