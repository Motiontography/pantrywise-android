package com.pantrywise.wear.data

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Service that listens for data changes and messages from the phone app.
 * This service runs in the background and keeps data in sync.
 */
class DataLayerListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: ${dataEvents.count} events")
        super.onDataChanged(dataEvents)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived: ${messageEvent.path}")

        when (messageEvent.path) {
            WearMessageType.REFRESH_REQUIRED.path -> {
                // Phone is requesting us to refresh data
                Log.d(TAG, "Refresh required from phone")
            }
        }

        super.onMessageReceived(messageEvent)
    }

    companion object {
        private const val TAG = "DataLayerListener"
    }
}
