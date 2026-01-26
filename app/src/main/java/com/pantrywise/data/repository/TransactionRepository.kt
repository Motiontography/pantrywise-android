package com.pantrywise.data.repository

import com.pantrywise.data.local.dao.TransactionDao
import com.pantrywise.data.local.entity.PurchaseTransactionEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class SpendingSummary(
    val totalSpent: Double,
    val transactionCount: Int,
    val averageTransaction: Double,
    val periodStart: Long,
    val periodEnd: Long
)

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {
    fun getAllTransactions(): Flow<List<PurchaseTransactionEntity>> = transactionDao.getAllTransactions()

    suspend fun getTransactionById(id: String): PurchaseTransactionEntity? = transactionDao.getTransactionById(id)

    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<PurchaseTransactionEntity>> =
        transactionDao.getTransactionsByDateRange(startDate, endDate)

    fun getTransactionsByStore(store: String): Flow<List<PurchaseTransactionEntity>> =
        transactionDao.getTransactionsByStore(store)

    fun getAllStores(): Flow<List<String>> = transactionDao.getAllStores()

    suspend fun insertTransaction(transaction: PurchaseTransactionEntity): Long =
        transactionDao.insertTransaction(transaction)

    suspend fun updateTransaction(transaction: PurchaseTransactionEntity) =
        transactionDao.updateTransaction(transaction)

    suspend fun deleteTransaction(id: String) = transactionDao.deleteTransactionById(id)

    // Spending summaries
    suspend fun getDailySpendingSummary(date: Long = System.currentTimeMillis()): SpendingSummary {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis - 1

        return getSpendingSummary(startOfDay, endOfDay)
    }

    suspend fun getWeeklySpendingSummary(date: Long = System.currentTimeMillis()): SpendingSummary {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = date
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfWeek = calendar.timeInMillis

        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        val endOfWeek = calendar.timeInMillis - 1

        return getSpendingSummary(startOfWeek, endOfWeek)
    }

    suspend fun getMonthlySpendingSummary(date: Long = System.currentTimeMillis()): SpendingSummary {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = date
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val endOfMonth = calendar.timeInMillis - 1

        return getSpendingSummary(startOfMonth, endOfMonth)
    }

    private suspend fun getSpendingSummary(startDate: Long, endDate: Long): SpendingSummary {
        val totalSpent = transactionDao.getTotalSpentInRange(startDate, endDate) ?: 0.0
        val transactionCount = transactionDao.getTransactionCountInRange(startDate, endDate)
        val averageTransaction = if (transactionCount > 0) totalSpent / transactionCount else 0.0

        return SpendingSummary(
            totalSpent = totalSpent,
            transactionCount = transactionCount,
            averageTransaction = averageTransaction,
            periodStart = startDate,
            periodEnd = endDate
        )
    }

    suspend fun getSpendingByStore(store: String, startDate: Long, endDate: Long): Double {
        return transactionDao.getTotalSpentByStoreInRange(store, startDate, endDate) ?: 0.0
    }
}
