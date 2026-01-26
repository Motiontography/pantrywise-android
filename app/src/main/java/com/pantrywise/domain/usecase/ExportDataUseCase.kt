package com.pantrywise.domain.usecase

import android.content.Context
import com.google.gson.GsonBuilder
import com.pantrywise.data.local.dao.*
import com.pantrywise.data.local.entity.*
import com.pantrywise.domain.model.ActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class ExportFormat {
    JSON,
    CSV
}

data class ExportResult(
    val success: Boolean,
    val filePath: String?,
    val errorMessage: String?
)

class ExportDataUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val productDao: ProductDao,
    private val inventoryDao: InventoryDao,
    private val shoppingListDao: ShoppingListDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val preferencesDao: PreferencesDao
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    /**
     * Exports all data to a file in the specified format.
     */
    suspend operator fun invoke(format: ExportFormat): ExportResult = withContext(Dispatchers.IO) {
        try {
            val timestamp = dateFormat.format(Date())
            val fileName = "pantrywise_export_$timestamp"

            val file = when (format) {
                ExportFormat.JSON -> exportToJson(fileName)
                ExportFormat.CSV -> exportToCsv(fileName)
            }

            // Log export event
            preferencesDao.insertActionEvent(
                ActionEventEntity(
                    type = ActionType.EXPORT_COMPLETED,
                    payloadJson = "{\"format\": \"${format.name}\", \"path\": \"${file.absolutePath}\"}"
                )
            )

            ExportResult(
                success = true,
                filePath = file.absolutePath,
                errorMessage = null
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                filePath = null,
                errorMessage = e.message ?: "Export failed"
            )
        }
    }

    private suspend fun exportToJson(fileName: String): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val file = File(exportDir, "$fileName.json")

        val exportData = ExportData(
            exportedAt = System.currentTimeMillis(),
            version = "1.0.0",
            products = productDao.getAllProducts().first(),
            inventoryItems = inventoryDao.getAllInventoryItems().first(),
            shoppingLists = shoppingListDao.getAllShoppingLists().first(),
            shoppingListItems = getAllShoppingListItems(),
            transactions = transactionDao.getAllTransactions().first(),
            categories = categoryDao.getAllCategories().first(),
            preferences = preferencesDao.getUserPreferences()
        )

        file.writeText(gson.toJson(exportData))
        return file
    }

    private suspend fun getAllShoppingListItems(): List<ShoppingListItemEntity> {
        val lists = shoppingListDao.getAllShoppingLists().first()
        return lists.flatMap { list ->
            shoppingListDao.getItemsByListId(list.id).first()
        }
    }

    private suspend fun exportToCsv(fileName: String): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val zipFile = File(exportDir, "$fileName.csv")

        val csvContent = StringBuilder()

        // Products CSV
        csvContent.appendLine("=== PRODUCTS ===")
        csvContent.appendLine("id,barcode,name,brand,category,default_unit,typical_price,currency,source,user_confirmed,created_at")
        productDao.getAllProducts().first().forEach { product ->
            csvContent.appendLine(
                "${product.id},${product.barcode ?: ""},\"${escapeCsv(product.name)}\",\"${escapeCsv(product.brand ?: "")}\",\"${escapeCsv(product.category)}\",${product.defaultUnit},${product.typicalPrice ?: ""},${product.currency},${product.source ?: ""},${product.userConfirmed},${product.createdAt}"
            )
        }

        csvContent.appendLine()
        csvContent.appendLine("=== INVENTORY ===")
        csvContent.appendLine("id,product_id,location,quantity_on_hand,unit,stock_status,reorder_threshold,expiration_date,created_at")
        inventoryDao.getAllInventoryItems().first().forEach { item ->
            csvContent.appendLine(
                "${item.id},${item.productId},${item.location},${item.quantityOnHand},${item.unit},${item.stockStatus},${item.reorderThreshold},${item.expirationDate ?: ""},${item.createdAt}"
            )
        }

        csvContent.appendLine()
        csvContent.appendLine("=== TRANSACTIONS ===")
        csvContent.appendLine("id,store,date,total,currency,created_at")
        transactionDao.getAllTransactions().first().forEach { transaction ->
            csvContent.appendLine(
                "${transaction.id},\"${escapeCsv(transaction.store ?: "")}\",${transaction.date},${transaction.total},${transaction.currency},${transaction.createdAt}"
            )
        }

        zipFile.writeText(csvContent.toString())
        return zipFile
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"")
    }

    /**
     * Gets the size of data to be exported.
     */
    suspend fun getExportStats(): ExportStats {
        return ExportStats(
            productCount = productDao.getProductCount(),
            inventoryItemCount = inventoryDao.getInventoryItemCount(),
            transactionCount = transactionDao.getTransactionCount()
        )
    }
}

data class ExportData(
    val exportedAt: Long,
    val version: String,
    val products: List<ProductEntity>,
    val inventoryItems: List<InventoryItemEntity>,
    val shoppingLists: List<ShoppingListEntity>,
    val shoppingListItems: List<ShoppingListItemEntity>,
    val transactions: List<PurchaseTransactionEntity>,
    val categories: List<CategoryEntity>,
    val preferences: UserPreferencesEntity?
)

data class ExportStats(
    val productCount: Int,
    val inventoryItemCount: Int,
    val transactionCount: Int
)
