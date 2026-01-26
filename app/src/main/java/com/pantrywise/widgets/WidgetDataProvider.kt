package com.pantrywise.widgets

import android.content.Context
import com.pantrywise.data.local.PantryDatabase
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Data provider for widgets to access app data
 */
class WidgetDataProvider(private val context: Context) {

    private val database by lazy { PantryDatabase.getInstance(context) }

    // Shopping list data for widget
    suspend fun getShoppingItems(): List<WidgetShoppingItem> {
        return try {
            val items = database.shoppingListDao().getActiveShoppingListItems().first()
            items.map { item ->
                val product = database.productDao().getProductById(item.productId)
                WidgetShoppingItem(
                    id = item.id,
                    name = product?.name ?: "Unknown",
                    quantity = item.quantityNeeded.toInt(),
                    category = product?.category ?: "Other",
                    isChecked = item.isChecked
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Expiring items data for widget
    suspend fun getExpiringItems(): List<WidgetExpiringItem> {
        return try {
            val now = System.currentTimeMillis()
            val sevenDaysFromNow = now + TimeUnit.DAYS.toMillis(7)

            val items = database.inventoryDao().getExpiringItemsBetween(now, sevenDaysFromNow).first()
            items.map { item ->
                val product = database.productDao().getProductById(item.productId)
                val daysUntilExpiration = item.expirationDate?.let {
                    TimeUnit.MILLISECONDS.toDays(it - now).toInt()
                } ?: 999

                WidgetExpiringItem(
                    id = item.id,
                    name = product?.name ?: "Unknown",
                    quantity = item.quantityOnHand.toInt(),
                    location = item.location.displayName,
                    daysUntilExpiration = daysUntilExpiration
                )
            }.sortedBy { it.daysUntilExpiration }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Summary data
    suspend fun getShoppingListSummary(): ShoppingListSummary {
        val items = getShoppingItems()
        return ShoppingListSummary(
            totalCount = items.size,
            uncheckedCount = items.count { !it.isChecked },
            checkedCount = items.count { it.isChecked }
        )
    }

    suspend fun getExpiringSummary(): ExpiringSummary {
        val items = getExpiringItems()
        return ExpiringSummary(
            totalCount = items.size,
            urgentCount = items.count { it.daysUntilExpiration <= 2 }
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: WidgetDataProvider? = null

        fun getInstance(context: Context): WidgetDataProvider {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WidgetDataProvider(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}

// Data classes for widgets
data class WidgetShoppingItem(
    val id: String,
    val name: String,
    val quantity: Int,
    val category: String,
    val isChecked: Boolean
)

data class WidgetExpiringItem(
    val id: String,
    val name: String,
    val quantity: Int,
    val location: String,
    val daysUntilExpiration: Int
)

data class ShoppingListSummary(
    val totalCount: Int,
    val uncheckedCount: Int,
    val checkedCount: Int
) {
    val progress: Float
        get() = if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f
}

data class ExpiringSummary(
    val totalCount: Int,
    val urgentCount: Int
)
