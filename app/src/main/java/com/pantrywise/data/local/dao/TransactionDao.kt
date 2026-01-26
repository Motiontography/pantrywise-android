package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.PurchaseTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    // Purchase Transactions
    @Query("SELECT * FROM purchase_transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<PurchaseTransactionEntity>>

    @Query("SELECT * FROM purchase_transactions WHERE id = :id")
    suspend fun getTransactionById(id: String): PurchaseTransactionEntity?

    @Query("SELECT * FROM purchase_transactions WHERE sessionId = :sessionId")
    suspend fun getTransactionBySessionId(sessionId: String): PurchaseTransactionEntity?

    @Query("SELECT * FROM purchase_transactions WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<PurchaseTransactionEntity>>

    @Query("SELECT * FROM purchase_transactions WHERE store = :store ORDER BY date DESC")
    fun getTransactionsByStore(store: String): Flow<List<PurchaseTransactionEntity>>

    @Query("SELECT SUM(total) FROM purchase_transactions WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalSpentInRange(startDate: Long, endDate: Long): Double?

    @Query("SELECT SUM(total) FROM purchase_transactions WHERE store = :store AND date >= :startDate AND date <= :endDate")
    suspend fun getTotalSpentByStoreInRange(store: String, startDate: Long, endDate: Long): Double?

    @Query("SELECT DISTINCT store FROM purchase_transactions WHERE store IS NOT NULL ORDER BY store ASC")
    fun getAllStores(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: PurchaseTransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: PurchaseTransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: PurchaseTransactionEntity)

    @Query("DELETE FROM purchase_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("SELECT COUNT(*) FROM purchase_transactions")
    suspend fun getTransactionCount(): Int

    @Query("SELECT COUNT(*) FROM purchase_transactions WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTransactionCountInRange(startDate: Long, endDate: Long): Int

    // For budget tracking - alias for getTotalSpentInRange
    @Query("SELECT SUM(total) FROM purchase_transactions WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalSpentInDateRange(startDate: Long, endDate: Long): Double?

    // Daily spending for budget progress charts
    @Query("""
        SELECT date as date, SUM(total) as amount
        FROM purchase_transactions
        WHERE date >= :startDate AND date <= :endDate
        GROUP BY strftime('%Y-%m-%d', date / 1000, 'unixepoch')
        ORDER BY date ASC
    """)
    suspend fun getDailySpendingInDateRange(startDate: Long, endDate: Long): List<DailySpendingData>
}

data class DailySpendingData(
    val date: Long,
    val amount: Double
)
