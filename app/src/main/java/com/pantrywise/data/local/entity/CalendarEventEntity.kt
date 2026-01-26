package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Type of calendar event synced with device calendar
 */
enum class CalendarEventType {
    EXPIRATION_REMINDER,
    MEAL_PLAN,
    SHOPPING_TRIP,
    RESTOCK_REMINDER
}

/**
 * Tracks calendar events synced from PantryWise to device calendar
 * Used to manage updates and deletions
 */
@Entity(
    tableName = "calendar_events",
    indices = [
        Index("calendarEventId"),
        Index("entityType"),
        Index("entityId"),
        Index("eventType")
    ]
)
data class CalendarEventEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val calendarEventId: Long,  // Device calendar event ID
    val calendarId: Long,       // Device calendar ID
    val eventType: CalendarEventType,
    val entityType: String,     // InventoryItem, MealPlanEntry, etc.
    val entityId: String,       // ID of the related entity
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val isAllDay: Boolean = true,
    val reminderMinutes: Int? = null,
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Calendar sync settings and preferences
 */
@Entity(tableName = "calendar_settings")
data class CalendarSettingsEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val isEnabled: Boolean = false,
    val calendarId: Long? = null,
    val calendarName: String? = null,
    val syncExpirations: Boolean = true,
    val syncMealPlans: Boolean = true,
    val syncShoppingTrips: Boolean = false,
    val syncRestockReminders: Boolean = true,
    val expirationReminderDays: Int = 3,  // Days before expiration to create event
    val mealPlanReminderMinutes: Int = 60,  // Minutes before meal to remind
    val defaultReminderMinutes: Int = 30,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Represents a device calendar available for syncing
 */
data class DeviceCalendar(
    val id: Long,
    val accountName: String,
    val displayName: String,
    val color: Int,
    val isPrimary: Boolean = false
)

/**
 * Calendar sync job status
 */
data class CalendarSyncStatus(
    val isEnabled: Boolean,
    val lastSyncAt: Long?,
    val pendingSyncCount: Int,
    val errorMessage: String?
)
