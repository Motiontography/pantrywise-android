package com.pantrywise.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pantrywise.workers.NotificationWorker
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receiver that handles device boot completion.
 * Reschedules all notifications and alarms after device restart.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed, rescheduling notifications")

            // Reschedule expiration and low stock notification checks via WorkManager
            rescheduleExpirationNotifications(context)

            // Reschedule geofence reminders
            rescheduleGeofences(context)
        }
    }

    private fun rescheduleExpirationNotifications(context: Context) {
        Log.d(TAG, "Rescheduling expiration notifications via WorkManager")
        // Schedule the daily notification worker
        NotificationWorker.schedule(context)
    }

    private fun rescheduleGeofences(context: Context) {
        // TODO: Re-register geofences for store reminders
        Log.d(TAG, "Rescheduling geofence reminders")
    }
}
