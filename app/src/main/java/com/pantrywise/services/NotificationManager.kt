package com.pantrywise.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pantrywise.MainActivity
import com.pantrywise.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notification types for the app
 */
enum class PantryNotificationType(
    val channelId: String,
    val channelName: String,
    val channelDescription: String,
    val importance: Int = NotificationManager.IMPORTANCE_DEFAULT
) {
    EXPIRING(
        "pantry_expiring",
        "Expiration Alerts",
        "Alerts when items are about to expire",
        NotificationManager.IMPORTANCE_HIGH
    ),
    LOW_STOCK(
        "pantry_low_stock",
        "Low Stock Alerts",
        "Alerts when essentials are running low",
        NotificationManager.IMPORTANCE_DEFAULT
    ),
    HOUSEHOLD(
        "pantry_household",
        "Household Updates",
        "Updates from your household members",
        NotificationManager.IMPORTANCE_DEFAULT
    ),
    SHOPPING(
        "pantry_shopping",
        "Shopping Reminders",
        "Reminders when near your favorite stores",
        NotificationManager.IMPORTANCE_DEFAULT
    ),
    STORE_REMINDER(
        "pantry_store_reminder",
        "Store Proximity Alerts",
        "Alerts when near your favorite stores",
        NotificationManager.IMPORTANCE_HIGH
    ),
    GENERAL(
        "pantry_general",
        "General Notifications",
        "General notifications from My Pantry Buddy",
        NotificationManager.IMPORTANCE_DEFAULT
    )
}

/**
 * Data class for notification content
 */
data class NotificationContent(
    val id: Int,
    val title: String,
    val body: String,
    val type: PantryNotificationType,
    val deepLink: String? = null,
    val groupKey: String? = null,
    val autoCancel: Boolean = true,
    val ongoing: Boolean = false,
    val silent: Boolean = false
)

/**
 * Manages all notifications for the app
 */
@Singleton
class PantryNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannels()
    }

    /**
     * Check if notifications are enabled
     */
    fun areNotificationsEnabled(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }

    /**
     * Check if a specific channel is enabled
     */
    fun isChannelEnabled(type: PantryNotificationType): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(type.channelId)
            return channel?.importance != NotificationManager.IMPORTANCE_NONE
        }
        return true
    }

    /**
     * Check if POST_NOTIFICATIONS permission is granted (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Create all notification channels
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PantryNotificationType.entries.forEach { type ->
                val channel = NotificationChannel(
                    type.channelId,
                    type.channelName,
                    type.importance
                ).apply {
                    description = type.channelDescription
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Show a notification
     */
    fun showNotification(content: NotificationContent) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            content.deepLink?.let { putExtra("deepLink", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            content.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, content.type.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(content.autoCancel)
            .setOngoing(content.ongoing)
            .setSilent(content.silent)
            .apply {
                content.groupKey?.let { setGroup(it) }
            }
            .build()

        notificationManager.notify(content.id, notification)
    }

    /**
     * Show expiration alert notification
     */
    fun showExpirationAlert(itemName: String, daysUntilExpiry: Int, itemId: String? = null) {
        val body = when {
            daysUntilExpiry < 0 -> "$itemName has expired!"
            daysUntilExpiry == 0 -> "$itemName expires today!"
            daysUntilExpiry == 1 -> "$itemName expires tomorrow!"
            else -> "$itemName expires in $daysUntilExpiry days"
        }

        showNotification(
            NotificationContent(
                id = itemId?.hashCode() ?: itemName.hashCode(),
                title = "Expiration Alert",
                body = body,
                type = PantryNotificationType.EXPIRING,
                deepLink = "pantry/item/$itemId",
                groupKey = "expiring_items"
            )
        )
    }

    /**
     * Show low stock alert notification
     */
    fun showLowStockAlert(itemName: String, currentQuantity: Int, minimumQuantity: Int, itemId: String? = null) {
        val body = "$itemName is running low ($currentQuantity of $minimumQuantity minimum)"

        showNotification(
            NotificationContent(
                id = (itemId ?: itemName).hashCode() + 10000,
                title = "Low Stock Alert",
                body = body,
                type = PantryNotificationType.LOW_STOCK,
                deepLink = "shopping",
                groupKey = "low_stock_items"
            )
        )
    }

    /**
     * Show household update notification
     */
    fun showHouseholdUpdate(memberName: String, action: String, details: String? = null) {
        val body = buildString {
            append("$memberName $action")
            details?.let { append(": $it") }
        }

        showNotification(
            NotificationContent(
                id = System.currentTimeMillis().toInt(),
                title = "Household Update",
                body = body,
                type = PantryNotificationType.HOUSEHOLD,
                groupKey = "household_updates"
            )
        )
    }

    /**
     * Show store proximity notification
     */
    fun showStoreReminder(storeName: String, listItemCount: Int) {
        val body = if (listItemCount > 0) {
            "You're near $storeName and have $listItemCount items on your list"
        } else {
            "You're near $storeName"
        }

        showNotification(
            NotificationContent(
                id = storeName.hashCode() + 20000,
                title = "Store Nearby",
                body = body,
                type = PantryNotificationType.STORE_REMINDER,
                deepLink = "shopping"
            )
        )
    }

    /**
     * Show shopping list shared notification
     */
    fun showListShared(sharerName: String, listName: String) {
        showNotification(
            NotificationContent(
                id = listName.hashCode() + 30000,
                title = "List Shared",
                body = "$sharerName shared \"$listName\" with you",
                type = PantryNotificationType.SHOPPING,
                deepLink = "shopping"
            )
        )
    }

    /**
     * Show summary notification for multiple expiring items
     */
    fun showExpiringSummary(count: Int, itemNames: List<String>) {
        if (count == 0) return

        val body = if (count == 1) {
            "${itemNames.first()} is expiring soon"
        } else {
            "$count items are expiring soon: ${itemNames.take(3).joinToString(", ")}${if (count > 3) "..." else ""}"
        }

        showNotification(
            NotificationContent(
                id = SUMMARY_NOTIFICATION_ID_EXPIRING,
                title = "Items Expiring Soon",
                body = body,
                type = PantryNotificationType.EXPIRING,
                groupKey = "expiring_items"
            )
        )
    }

    /**
     * Show summary notification for multiple low stock items
     */
    fun showLowStockSummary(count: Int, itemNames: List<String>) {
        if (count == 0) return

        val body = if (count == 1) {
            "${itemNames.first()} is running low"
        } else {
            "$count essentials are running low: ${itemNames.take(3).joinToString(", ")}${if (count > 3) "..." else ""}"
        }

        showNotification(
            NotificationContent(
                id = SUMMARY_NOTIFICATION_ID_LOW_STOCK,
                title = "Low Stock Alert",
                body = body,
                type = PantryNotificationType.LOW_STOCK,
                groupKey = "low_stock_items"
            )
        )
    }

    /**
     * Cancel a specific notification
     */
    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }

    /**
     * Cancel all notifications of a specific type
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    companion object {
        private const val SUMMARY_NOTIFICATION_ID_EXPIRING = 99990
        private const val SUMMARY_NOTIFICATION_ID_LOW_STOCK = 99991
    }
}
