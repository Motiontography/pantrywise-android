package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.PurchaseTransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing a flattened view of a purchased item
 * for pattern analysis purposes.
 */
data class FlatPurchaseRecord(
    val transactionId: String,
    val productId: String,
    val productName: String,
    val quantity: Double,
    val unitPrice: Double?,
    val purchaseDate: Long,
    val storeId: String?,
    val category: String
)

/**
 * Repository that provides flattened purchase records from JSON-based transactions.
 * This acts as a "DAO" for the ML pattern analysis but actually deserializes JSON.
 */
@Singleton
class PurchaseDao @Inject constructor(
    private val transactionDao: TransactionDao,
    private val productDao: ProductDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Gets all purchases as a flattened list of individual items
     */
    fun getAllPurchases(): Flow<List<FlatPurchaseRecord>> {
        return transactionDao.getAllTransactions().map { transactions ->
            transactions.flatMap { transaction ->
                parsePurchaseItems(transaction)
            }
        }
    }

    /**
     * Gets purchases within a date range
     */
    fun getPurchasesInRange(startDate: Long, endDate: Long): Flow<List<FlatPurchaseRecord>> {
        return transactionDao.getTransactionsByDateRange(startDate, endDate).map { transactions ->
            transactions.flatMap { transaction ->
                parsePurchaseItems(transaction)
            }
        }
    }

    /**
     * Gets purchases for a specific product
     */
    suspend fun getPurchasesForProduct(productId: String): List<FlatPurchaseRecord> {
        return transactionDao.getAllTransactions()
            .map { transactions ->
                transactions.flatMap { transaction ->
                    parsePurchaseItems(transaction)
                }.filter { it.productId == productId }
            }
            .let { flow ->
                var result: List<FlatPurchaseRecord> = emptyList()
                flow.collect { result = it }
                result
            }
    }

    private suspend fun parsePurchaseItems(
        transaction: PurchaseTransactionEntity
    ): List<FlatPurchaseRecord> {
        if (transaction.itemsJson.isBlank() || transaction.itemsJson == "[]") {
            return emptyList()
        }

        return try {
            val items = json.decodeFromString<List<PurchaseItemJson>>(transaction.itemsJson)
            items.mapNotNull { item ->
                // Try to get product name from product table
                val product = productDao.getProductById(item.productId)
                val productName = product?.name ?: item.productId

                FlatPurchaseRecord(
                    transactionId = transaction.id,
                    productId = item.productId,
                    productName = productName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    purchaseDate = transaction.date,
                    storeId = transaction.store,
                    category = item.category
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@kotlinx.serialization.Serializable
private data class PurchaseItemJson(
    val id: String = "",
    val productId: String,
    val quantity: Double,
    val unit: String = "item",
    val unitPrice: Double? = null,
    val totalPrice: Double = 0.0,
    val category: String = "Other"
)
