package com.pantrywise.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearDataRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : DataClient.OnDataChangedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val dataClient: DataClient = Wearable.getDataClient(context)
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val nodeClient: NodeClient = Wearable.getNodeClient(context)

    private val _syncData = MutableStateFlow(WearSyncData())
    val syncData: StateFlow<WearSyncData> = _syncData.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    init {
        dataClient.addListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path ?: return@forEach
                when (path) {
                    WearMessageType.SYNC_RESPONSE.path -> {
                        handleSyncResponse(event)
                    }
                }
            }
        }
    }

    private fun handleSyncResponse(event: DataEvent) {
        try {
            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
            val jsonData = dataMap.getString("data") ?: return

            val data = json.decodeFromString<WearSyncData>(jsonData)
            _syncData.value = data
            _lastSyncTime.value = data.lastSyncDate
            _isSyncing.value = false

            Log.d(TAG, "Sync received: ${data.shoppingItems.size} shopping, ${data.expiringItems.size} expiring")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sync response", e)
            _isSyncing.value = false
        }
    }

    fun requestSync() {
        scope.launch {
            try {
                _isSyncing.value = true
                val nodes = nodeClient.connectedNodes.await()

                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected nodes found")
                    _isSyncing.value = false
                    return@launch
                }

                // Send sync request to the phone
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        WearMessageType.SYNC_REQUEST.path,
                        byteArrayOf()
                    ).await()
                    Log.d(TAG, "Sync request sent to ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting sync", e)
                _isSyncing.value = false
            }
        }
    }

    fun toggleItemChecked(itemId: String, isChecked: Boolean) {
        // Update local state optimistically
        _syncData.update { data ->
            data.copy(
                shoppingItems = data.shoppingItems.map { item ->
                    if (item.id == itemId) item.copy(isChecked = isChecked) else item
                }
            )
        }

        // Send update to phone
        scope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                val payload = json.encodeToString(
                    mapOf("id" to itemId, "checked" to isChecked.toString())
                ).toByteArray()

                val messagePath = if (isChecked) {
                    WearMessageType.ITEM_CHECKED.path
                } else {
                    WearMessageType.ITEM_UNCHECKED.path
                }

                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, messagePath, payload).await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending item check update", e)
                // Revert on error
                _syncData.update { data ->
                    data.copy(
                        shoppingItems = data.shoppingItems.map { item ->
                            if (item.id == itemId) item.copy(isChecked = !isChecked) else item
                        }
                    )
                }
            }
        }
    }

    fun addItem(name: String, quantity: Double = 1.0, unit: String = "each") {
        scope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                val payload = json.encodeToString(
                    mapOf(
                        "name" to name,
                        "quantity" to quantity.toString(),
                        "unit" to unit
                    )
                ).toByteArray()

                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        WearMessageType.ITEM_ADDED.path,
                        payload
                    ).await()
                }

                // Request fresh sync after adding
                requestSync()
            } catch (e: Exception) {
                Log.e(TAG, "Error adding item", e)
            }
        }
    }

    fun cleanup() {
        dataClient.removeListener(this)
    }

    companion object {
        private const val TAG = "WearDataRepository"
    }
}
