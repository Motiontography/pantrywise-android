package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.ActionEventEntity
import com.pantrywise.data.local.entity.PendingLookupEntity
import com.pantrywise.data.local.entity.UserPreferencesEntity
import com.pantrywise.domain.model.ActionType
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferencesDao {
    // User Preferences
    @Query("SELECT * FROM user_preferences LIMIT 1")
    suspend fun getUserPreferences(): UserPreferencesEntity?

    @Query("SELECT * FROM user_preferences LIMIT 1")
    fun getUserPreferencesFlow(): Flow<UserPreferencesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserPreferences(preferences: UserPreferencesEntity): Long

    @Update
    suspend fun updateUserPreferences(preferences: UserPreferencesEntity)

    @Query("DELETE FROM user_preferences")
    suspend fun deleteAllUserPreferences()

    // Action Events (for audit/analytics)
    @Query("SELECT * FROM action_events ORDER BY timestamp DESC")
    fun getAllActionEvents(): Flow<List<ActionEventEntity>>

    @Query("SELECT * FROM action_events WHERE id = :id")
    suspend fun getActionEventById(id: String): ActionEventEntity?

    @Query("SELECT * FROM action_events WHERE type = :type ORDER BY timestamp DESC")
    fun getActionEventsByType(type: ActionType): Flow<List<ActionEventEntity>>

    @Query("SELECT * FROM action_events WHERE timestamp >= :startDate AND timestamp <= :endDate ORDER BY timestamp DESC")
    fun getActionEventsByDateRange(startDate: Long, endDate: Long): Flow<List<ActionEventEntity>>

    @Query("SELECT * FROM action_events WHERE entityType = :entityType AND entityId = :entityId ORDER BY timestamp DESC")
    fun getActionEventsForEntity(entityType: String, entityId: String): Flow<List<ActionEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionEvent(event: ActionEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionEvents(events: List<ActionEventEntity>)

    @Delete
    suspend fun deleteActionEvent(event: ActionEventEntity)

    @Query("DELETE FROM action_events WHERE timestamp < :beforeDate")
    suspend fun deleteOldActionEvents(beforeDate: Long)

    @Query("SELECT COUNT(*) FROM action_events")
    suspend fun getActionEventCount(): Int

    // Pending Lookups (for offline barcode handling)
    @Query("SELECT * FROM pending_lookups WHERE resolved = 0 ORDER BY scannedAt DESC")
    fun getUnresolvedPendingLookups(): Flow<List<PendingLookupEntity>>

    @Query("SELECT * FROM pending_lookups WHERE id = :id")
    suspend fun getPendingLookupById(id: String): PendingLookupEntity?

    @Query("SELECT * FROM pending_lookups WHERE barcode = :barcode AND resolved = 0")
    suspend fun getPendingLookupByBarcode(barcode: String): PendingLookupEntity?

    @Query("SELECT * FROM pending_lookups WHERE retryCount < 3 AND resolved = 0 ORDER BY scannedAt ASC")
    suspend fun getRetryablePendingLookups(): List<PendingLookupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingLookup(lookup: PendingLookupEntity): Long

    @Update
    suspend fun updatePendingLookup(lookup: PendingLookupEntity)

    @Query("UPDATE pending_lookups SET resolved = 1 WHERE id = :id")
    suspend fun markPendingLookupResolved(id: String)

    @Query("UPDATE pending_lookups SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: String)

    @Delete
    suspend fun deletePendingLookup(lookup: PendingLookupEntity)

    @Query("DELETE FROM pending_lookups WHERE id = :id")
    suspend fun deletePendingLookupById(id: String)

    @Query("DELETE FROM pending_lookups WHERE resolved = 1")
    suspend fun deleteResolvedPendingLookups()

    @Query("SELECT COUNT(*) FROM pending_lookups WHERE resolved = 0")
    suspend fun getUnresolvedPendingLookupCount(): Int
}
