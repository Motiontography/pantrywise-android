package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.CalendarEventEntity
import com.pantrywise.data.local.entity.CalendarEventType
import com.pantrywise.data.local.entity.CalendarSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    // Calendar events
    @Query("SELECT * FROM calendar_events ORDER BY startTime ASC")
    fun getAllCalendarEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun getCalendarEventById(id: String): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE calendarEventId = :calendarEventId")
    suspend fun getCalendarEventByDeviceId(calendarEventId: Long): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun getCalendarEventForEntity(entityType: String, entityId: String): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE eventType = :eventType ORDER BY startTime ASC")
    fun getCalendarEventsByType(eventType: CalendarEventType): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE startTime >= :startDate AND startTime <= :endDate ORDER BY startTime ASC")
    fun getCalendarEventsInRange(startDate: Long, endDate: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE calendarId = :calendarId ORDER BY startTime ASC")
    fun getCalendarEventsForCalendar(calendarId: Long): Flow<List<CalendarEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarEvent(event: CalendarEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarEvents(events: List<CalendarEventEntity>)

    @Update
    suspend fun updateCalendarEvent(event: CalendarEventEntity)

    @Query("UPDATE calendar_events SET lastSyncedAt = :lastSyncedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLastSynced(id: String, lastSyncedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteCalendarEvent(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteCalendarEventById(id: String)

    @Query("DELETE FROM calendar_events WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteCalendarEventForEntity(entityType: String, entityId: String)

    @Query("DELETE FROM calendar_events WHERE calendarId = :calendarId")
    suspend fun deleteCalendarEventsForCalendar(calendarId: Long)

    @Query("DELETE FROM calendar_events WHERE eventType = :eventType")
    suspend fun deleteCalendarEventsByType(eventType: CalendarEventType)

    // Calendar settings
    @Query("SELECT * FROM calendar_settings LIMIT 1")
    suspend fun getCalendarSettings(): CalendarSettingsEntity?

    @Query("SELECT * FROM calendar_settings LIMIT 1")
    fun observeCalendarSettings(): Flow<CalendarSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarSettings(settings: CalendarSettingsEntity): Long

    @Update
    suspend fun updateCalendarSettings(settings: CalendarSettingsEntity)

    @Query("UPDATE calendar_settings SET isEnabled = :isEnabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCalendarSyncEnabled(id: String, isEnabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE calendar_settings SET calendarId = :calendarId, calendarName = :calendarName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSelectedCalendar(id: String, calendarId: Long, calendarName: String, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE calendar_settings
        SET syncExpirations = :syncExpirations,
            syncMealPlans = :syncMealPlans,
            syncShoppingTrips = :syncShoppingTrips,
            syncRestockReminders = :syncRestockReminders,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateSyncPreferences(
        id: String,
        syncExpirations: Boolean,
        syncMealPlans: Boolean,
        syncShoppingTrips: Boolean,
        syncRestockReminders: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    // Stats
    @Query("SELECT COUNT(*) FROM calendar_events")
    suspend fun getCalendarEventCount(): Int

    @Query("SELECT COUNT(*) FROM calendar_events WHERE eventType = :eventType")
    suspend fun getCalendarEventCountByType(eventType: CalendarEventType): Int

    // Sync helpers
    @Query("SELECT calendarEventId FROM calendar_events")
    suspend fun getAllDeviceEventIds(): List<Long>

    @Query("SELECT * FROM calendar_events WHERE lastSyncedAt < :beforeDate")
    suspend fun getEventsNeedingSync(beforeDate: Long): List<CalendarEventEntity>
}
