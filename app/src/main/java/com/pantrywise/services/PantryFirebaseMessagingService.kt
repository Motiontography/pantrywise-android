package com.pantrywise.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Firebase Cloud Messaging service for handling push notifications.
 * Handles:
 * - Household invitation notifications
 * - Shared list update notifications
 * - Member activity notifications
 * - Expiration alerts
 * - Low stock alerts
 */
@AndroidEntryPoint
class PantryFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationManager: PantryNotificationManager

    companion object {
        private const val TAG = "FCMService"
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "New FCM token: $token")
        // TODO: Send token to server for household notifications
        sendRegistrationToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Check if message contains a notification payload (foreground)
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "Message Notification Body: ${notification.body}")
            notificationManager.showNotification(
                NotificationContent(
                    id = System.currentTimeMillis().toInt(),
                    title = notification.title ?: "My Pantry Buddy",
                    body = notification.body ?: "",
                    type = PantryNotificationType.GENERAL
                )
            )
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"] ?: return

        when (type) {
            "household_invite" -> handleHouseholdInvite(data)
            "list_shared" -> handleListShared(data)
            "item_added" -> handleItemAdded(data)
            "member_joined" -> handleMemberJoined(data)
            "expiration_alert" -> handleExpirationAlert(data)
            "low_stock_alert" -> handleLowStockAlert(data)
            "store_reminder" -> handleStoreReminder(data)
            else -> Log.w(TAG, "Unknown message type: $type")
        }
    }

    private fun handleHouseholdInvite(data: Map<String, String>) {
        val householdName = data["householdName"] ?: "a household"
        val inviterName = data["inviterName"] ?: "Someone"
        notificationManager.showHouseholdUpdate(
            memberName = inviterName,
            action = "invited you to join",
            details = householdName
        )
    }

    private fun handleListShared(data: Map<String, String>) {
        val listName = data["listName"] ?: "Shopping list"
        val sharerName = data["sharerName"] ?: "Someone"
        notificationManager.showListShared(sharerName, listName)
    }

    private fun handleItemAdded(data: Map<String, String>) {
        val itemName = data["itemName"] ?: "An item"
        val memberName = data["memberName"] ?: "Someone"
        notificationManager.showHouseholdUpdate(
            memberName = memberName,
            action = "added an item",
            details = itemName
        )
    }

    private fun handleMemberJoined(data: Map<String, String>) {
        val memberName = data["memberName"] ?: "Someone"
        val householdName = data["householdName"] ?: "your household"
        notificationManager.showHouseholdUpdate(
            memberName = memberName,
            action = "joined",
            details = householdName
        )
    }

    private fun handleExpirationAlert(data: Map<String, String>) {
        val itemName = data["itemName"] ?: "An item"
        val daysUntilExpiry = data["daysUntilExpiry"]?.toIntOrNull() ?: 0
        val itemId = data["itemId"]
        notificationManager.showExpirationAlert(itemName, daysUntilExpiry, itemId)
    }

    private fun handleLowStockAlert(data: Map<String, String>) {
        val itemName = data["itemName"] ?: "An item"
        val currentQuantity = data["currentQuantity"]?.toIntOrNull() ?: 0
        val minimumQuantity = data["minimumQuantity"]?.toIntOrNull() ?: 1
        val itemId = data["itemId"]
        notificationManager.showLowStockAlert(itemName, currentQuantity, minimumQuantity, itemId)
    }

    private fun handleStoreReminder(data: Map<String, String>) {
        val storeName = data["storeName"] ?: "A store"
        val listItemCount = data["listItemCount"]?.toIntOrNull() ?: 0
        notificationManager.showStoreReminder(storeName, listItemCount)
    }

    private fun sendRegistrationToServer(token: String) {
        // TODO: Implement sending token to Firebase Firestore for household notifications
        Log.d(TAG, "Sending FCM token to server")
    }
}
