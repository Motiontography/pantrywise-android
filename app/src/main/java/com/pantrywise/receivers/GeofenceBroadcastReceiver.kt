package com.pantrywise.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receiver that handles geofence transitions for store reminders.
 * Triggered when user enters/exits geofence areas around favorite stores.
 */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = "Geofence error: ${geofencingEvent.errorCode}"
            Log.e(TAG, errorMessage)
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d(TAG, "User entered geofence")
                handleEnterGeofence(context, geofencingEvent)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d(TAG, "User exited geofence")
                handleExitGeofence(context, geofencingEvent)
            }
            else -> {
                Log.w(TAG, "Unknown geofence transition: $geofenceTransition")
            }
        }
    }

    private fun handleEnterGeofence(context: Context, event: GeofencingEvent) {
        val triggeringGeofences = event.triggeringGeofences ?: return

        for (geofence in triggeringGeofences) {
            val storeId = geofence.requestId
            Log.d(TAG, "User near store: $storeId")

            // TODO: Show notification with shopping list items for this store
            // NotificationService.showStoreReminderNotification(context, storeId)
        }
    }

    private fun handleExitGeofence(context: Context, event: GeofencingEvent) {
        val triggeringGeofences = event.triggeringGeofences ?: return

        for (geofence in triggeringGeofences) {
            val storeId = geofence.requestId
            Log.d(TAG, "User left store area: $storeId")
        }
    }
}
