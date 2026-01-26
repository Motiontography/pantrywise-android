package com.pantrywise.services

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a calendar on the device
 */
data class DeviceCalendar(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val ownerAccount: String,
    val isPrimary: Boolean,
    val color: Int
)

/**
 * Represents a calendar event
 */
data class CalendarEvent(
    val id: Long? = null,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startTime: Long,
    val endTime: Long,
    val isAllDay: Boolean = false,
    val reminderMinutes: List<Int> = listOf(60), // Default 1 hour reminder
    val calendarId: Long? = null,
    val syncId: String? = null // For tracking PantryWise events
)

/**
 * Result of calendar operations
 */
sealed class CalendarResult<T> {
    data class Success<T>(val data: T) : CalendarResult<T>()
    data class Error<T>(val message: String) : CalendarResult<T>()
}

@Singleton
class CalendarManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val PANTRYWISE_CALENDAR_NAME = "PantryWise"
        const val PANTRYWISE_ACCOUNT_NAME = "pantrywise@local"
        const val PANTRYWISE_ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL
        const val PANTRYWISE_CALENDAR_COLOR = 0xFF4CAF50.toInt() // Green

        // Event types for tracking
        const val EVENT_TYPE_EXPIRATION = "expiration"
        const val EVENT_TYPE_MEAL_PLAN = "meal_plan"

        // Reminder intervals in minutes
        val REMINDER_OPTIONS = listOf(
            0 to "At time of event",
            15 to "15 minutes before",
            30 to "30 minutes before",
            60 to "1 hour before",
            120 to "2 hours before",
            1440 to "1 day before",
            2880 to "2 days before",
            4320 to "3 days before",
            10080 to "1 week before"
        )
    }

    private val contentResolver: ContentResolver
        get() = context.contentResolver

    /**
     * Get all calendars on the device
     */
    suspend fun getCalendars(): CalendarResult<List<DeviceCalendar>> = withContext(Dispatchers.IO) {
        try {
            val calendars = mutableListOf<DeviceCalendar>()

            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.CALENDAR_COLOR
            )

            val cursor: Cursor? = contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    calendars.add(
                        DeviceCalendar(
                            id = it.getLong(0),
                            name = it.getString(1) ?: "Unknown",
                            accountName = it.getString(2) ?: "",
                            accountType = it.getString(3) ?: "",
                            ownerAccount = it.getString(4) ?: "",
                            isPrimary = it.getInt(5) == 1,
                            color = it.getInt(6)
                        )
                    )
                }
            }

            CalendarResult.Success(calendars)
        } catch (e: SecurityException) {
            CalendarResult.Error("Calendar permission denied")
        } catch (e: Exception) {
            CalendarResult.Error(e.message ?: "Failed to get calendars")
        }
    }

    /**
     * Get or create the PantryWise calendar
     */
    suspend fun getOrCreatePantryWiseCalendar(): CalendarResult<Long> = withContext(Dispatchers.IO) {
        try {
            // Check if PantryWise calendar already exists
            val existingId = findPantryWiseCalendar()
            if (existingId != null) {
                return@withContext CalendarResult.Success(existingId)
            }

            // Create new PantryWise calendar
            val values = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, PANTRYWISE_ACCOUNT_NAME)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, PANTRYWISE_ACCOUNT_TYPE)
                put(CalendarContract.Calendars.NAME, PANTRYWISE_CALENDAR_NAME)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, PANTRYWISE_CALENDAR_NAME)
                put(CalendarContract.Calendars.CALENDAR_COLOR, PANTRYWISE_CALENDAR_COLOR)
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, PANTRYWISE_ACCOUNT_NAME)
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            }

            val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, PANTRYWISE_ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, PANTRYWISE_ACCOUNT_TYPE)
                .build()

            val result = contentResolver.insert(uri, values)

            if (result != null) {
                val calendarId = ContentUris.parseId(result)
                CalendarResult.Success(calendarId)
            } else {
                CalendarResult.Error("Failed to create PantryWise calendar")
            }
        } catch (e: SecurityException) {
            CalendarResult.Error("Calendar permission denied")
        } catch (e: Exception) {
            CalendarResult.Error(e.message ?: "Failed to create calendar")
        }
    }

    private fun findPantryWiseCalendar(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
                "${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(PANTRYWISE_ACCOUNT_NAME, PANTRYWISE_ACCOUNT_TYPE)

        val cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        return cursor?.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    /**
     * Add an event to a calendar
     */
    suspend fun addEvent(
        calendarId: Long,
        event: CalendarEvent
    ): CalendarResult<Long> = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
                put(CalendarContract.Events.DTSTART, event.startTime)
                put(CalendarContract.Events.DTEND, event.endTime)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
                event.syncId?.let { put(CalendarContract.Events.SYNC_DATA1, it) }
            }

            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

            if (uri != null) {
                val eventId = ContentUris.parseId(uri)

                // Add reminders
                for (minutes in event.reminderMinutes) {
                    addReminder(eventId, minutes)
                }

                CalendarResult.Success(eventId)
            } else {
                CalendarResult.Error("Failed to create event")
            }
        } catch (e: SecurityException) {
            CalendarResult.Error("Calendar permission denied")
        } catch (e: Exception) {
            CalendarResult.Error(e.message ?: "Failed to add event")
        }
    }

    private fun addReminder(eventId: Long, minutes: Int) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutes)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
    }

    /**
     * Update an existing event
     */
    suspend fun updateEvent(
        eventId: Long,
        event: CalendarEvent
    ): CalendarResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
                put(CalendarContract.Events.DTSTART, event.startTime)
                put(CalendarContract.Events.DTEND, event.endTime)
                put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
            }

            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = contentResolver.update(uri, values, null, null)

            if (rows > 0) {
                // Update reminders - delete existing and add new
                deleteReminders(eventId)
                for (minutes in event.reminderMinutes) {
                    addReminder(eventId, minutes)
                }
                CalendarResult.Success(true)
            } else {
                CalendarResult.Error("Event not found")
            }
        } catch (e: SecurityException) {
            CalendarResult.Error("Calendar permission denied")
        } catch (e: Exception) {
            CalendarResult.Error(e.message ?: "Failed to update event")
        }
    }

    private fun deleteReminders(eventId: Long) {
        contentResolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString())
        )
    }

    /**
     * Delete an event
     */
    suspend fun deleteEvent(eventId: Long): CalendarResult<Boolean> = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = contentResolver.delete(uri, null, null)

            if (rows > 0) {
                CalendarResult.Success(true)
            } else {
                CalendarResult.Error("Event not found")
            }
        } catch (e: SecurityException) {
            CalendarResult.Error("Calendar permission denied")
        } catch (e: Exception) {
            CalendarResult.Error(e.message ?: "Failed to delete event")
        }
    }

    /**
     * Find events by sync ID (for updating/deleting PantryWise events)
     */
    suspend fun findEventBySyncId(
        calendarId: Long,
        syncId: String
    ): CalendarResult<Long?> = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(CalendarContract.Events._ID)
            val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                    "${CalendarContract.Events.SYNC_DATA1} = ?"
            val selectionArgs = arrayOf(calendarId.toString(), syncId)

            val cursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            val eventId = cursor?.use {
                if (it.moveToFirst()) it.getLong(0) else null
            }

            CalendarResult.Success(eventId)
        } catch (e: SecurityException) {
            CalendarResult.Error("Calendar permission denied")
        } catch (e: Exception) {
            CalendarResult.Error(e.message ?: "Failed to find event")
        }
    }

    /**
     * Delete all PantryWise events from a calendar
     */
    suspend fun deleteAllPantryWiseEvents(calendarId: Long): CalendarResult<Int> = withContext(Dispatchers.IO) {
        try {
            val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                    "${CalendarContract.Events.SYNC_DATA1} IS NOT NULL"
            val selectionArgs = arrayOf(calendarId.toString())

            val rows = contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                selection,
                selectionArgs
            )

            CalendarResult.Success(rows)
        } catch (e: SecurityException) {
            CalendarResult.Error("Calendar permission denied")
        } catch (e: Exception) {
            CalendarResult.Error(e.message ?: "Failed to delete events")
        }
    }

    /**
     * Get upcoming events from a calendar
     */
    suspend fun getUpcomingEvents(
        calendarId: Long,
        fromTime: Long = System.currentTimeMillis(),
        limit: Int = 50
    ): CalendarResult<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        try {
            val events = mutableListOf<CalendarEvent>()

            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.SYNC_DATA1
            )

            val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                    "${CalendarContract.Events.DTSTART} >= ?"
            val selectionArgs = arrayOf(calendarId.toString(), fromTime.toString())

            val cursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC LIMIT $limit"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    events.add(
                        CalendarEvent(
                            id = it.getLong(0),
                            title = it.getString(1) ?: "",
                            description = it.getString(2),
                            location = it.getString(3),
                            startTime = it.getLong(4),
                            endTime = it.getLong(5),
                            isAllDay = it.getInt(6) == 1,
                            syncId = it.getString(7)
                        )
                    )
                }
            }

            CalendarResult.Success(events)
        } catch (e: SecurityException) {
            CalendarResult.Error("Calendar permission denied")
        } catch (e: Exception) {
            CalendarResult.Error(e.message ?: "Failed to get events")
        }
    }
}
