package com.pantrywise.services

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.pantrywise.data.local.PantryDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Service that handles communication with the Wear OS companion app.
 * Responds to sync requests and item updates from the watch.
 */
@AndroidEntryPoint
class WearableSyncService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val database by lazy { PantryDatabase.getInstance(applicationContext) }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")

        when (messageEvent.path) {
            PATH_SYNC_REQUEST -> handleSyncRequest()
            PATH_ITEM_CHECKED -> handleItemChecked(messageEvent.data, true)
            PATH_ITEM_UNCHECKED -> handleItemChecked(messageEvent.data, false)
            PATH_ITEM_ADDED -> handleItemAdded(messageEvent.data)
        }
    }

    private fun handleSyncRequest() {
        scope.launch {
            try {
                val syncData = buildSyncData()
                sendSyncDataToWatch(syncData)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling sync request", e)
            }
        }
    }

    private suspend fun buildSyncData(): WearSyncData {
        val now = System.currentTimeMillis()
        val sevenDaysFromNow = now + TimeUnit.DAYS.toMillis(7)

        // Get shopping items
        val shoppingItems = try {
            database.shoppingListDao().getActiveShoppingListItems().first().map { item ->
                val product = database.productDao().getProductById(item.productId)
                WearShoppingItem(
                    id = item.id,
                    name = product?.name ?: "Unknown",
                    quantity = item.quantityNeeded,
                    unit = item.unit.displayName,
                    isChecked = item.isChecked,
                    priority = item.priority
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting shopping items", e)
            emptyList()
        }

        // Get expiring items
        val expiringItems = try {
            database.inventoryDao().getExpiringItemsBetween(now, sevenDaysFromNow).first().map { item ->
                val product = database.productDao().getProductById(item.productId)
                val daysUntilExpiration = item.expirationDate?.let {
                    TimeUnit.MILLISECONDS.toDays(it - now).toInt()
                } ?: 999

                WearExpiringItem(
                    id = item.id,
                    name = product?.name ?: "Unknown",
                    expirationDate = item.expirationDate ?: 0,
                    quantity = item.quantityOnHand,
                    unit = item.unit.displayName,
                    location = item.location.displayName,
                    daysUntilExpiration = daysUntilExpiration
                )
            }.sortedBy { it.daysUntilExpiration }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting expiring items", e)
            emptyList()
        }

        return WearSyncData(
            shoppingItems = shoppingItems,
            expiringItems = expiringItems,
            lastSyncDate = System.currentTimeMillis()
        )
    }

    private suspend fun sendSyncDataToWatch(data: WearSyncData) {
        try {
            val dataClient = Wearable.getDataClient(applicationContext)
            val request = PutDataMapRequest.create(PATH_SYNC_RESPONSE).apply {
                dataMap.putString("data", json.encodeToString(data))
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
            Log.d(TAG, "Sync data sent to watch")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending sync data to watch", e)
        }
    }

    private fun handleItemChecked(data: ByteArray, isChecked: Boolean) {
        scope.launch {
            try {
                val payload = json.decodeFromString<Map<String, String>>(String(data))
                val itemId = payload["id"] ?: return@launch

                database.shoppingListDao().updateItemCheckedStatus(itemId, isChecked)
                Log.d(TAG, "Item $itemId ${if (isChecked) "checked" else "unchecked"}")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling item check update", e)
            }
        }
    }

    private fun handleItemAdded(data: ByteArray) {
        scope.launch {
            try {
                val payload = json.decodeFromString<Map<String, String>>(String(data))
                val name = payload["name"] ?: return@launch
                val quantity = payload["quantity"]?.toDoubleOrNull() ?: 1.0
                val unit = payload["unit"] ?: "each"

                // Add item to shopping list
                // This would need to be implemented based on your repository pattern
                Log.d(TAG, "Item added from watch: $name ($quantity $unit)")

                // Request a refresh to sync the new item back
                handleSyncRequest()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling item add", e)
            }
        }
    }

    companion object {
        private const val TAG = "WearableSyncService"

        const val PATH_SYNC_REQUEST = "/pantrywise/sync_request"
        const val PATH_SYNC_RESPONSE = "/pantrywise/sync_response"
        const val PATH_ITEM_CHECKED = "/pantrywise/item_checked"
        const val PATH_ITEM_UNCHECKED = "/pantrywise/item_unchecked"
        const val PATH_ITEM_ADDED = "/pantrywise/item_added"
    }
}

// Data classes for Wear sync (matching the wear module)
@Serializable
data class WearShoppingItem(
    val id: String,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "each",
    val isChecked: Boolean = false,
    val aisle: String? = null,
    val priority: Int = 5,
    val estimatedPrice: Double? = null
)

@Serializable
data class WearExpiringItem(
    val id: String,
    val name: String,
    val expirationDate: Long,
    val quantity: Double = 1.0,
    val unit: String = "each",
    val location: String = "Pantry",
    val daysUntilExpiration: Int = 0
)

@Serializable
data class WearQuickAddItem(
    val id: String,
    val name: String,
    val category: String = "Grocery",
    val defaultQuantity: Double = 1.0,
    val defaultUnit: String = "each",
    val icon: String = "cart"
)

@Serializable
data class WearSyncData(
    val shoppingItems: List<WearShoppingItem> = emptyList(),
    val expiringItems: List<WearExpiringItem> = emptyList(),
    val quickAddPresets: List<WearQuickAddItem> = emptyList(),
    val lastSyncDate: Long = System.currentTimeMillis(),
    val shoppingListName: String? = null
)
