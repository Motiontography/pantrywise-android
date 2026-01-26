package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.AuditAction
import com.pantrywise.data.local.entity.AuditItemEntity
import com.pantrywise.data.local.entity.AuditSessionEntity
import com.pantrywise.data.local.entity.AuditStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    // Audit sessions
    @Query("SELECT * FROM audit_sessions ORDER BY startedAt DESC")
    fun getAllAuditSessions(): Flow<List<AuditSessionEntity>>

    @Query("SELECT * FROM audit_sessions WHERE id = :id")
    suspend fun getAuditSessionById(id: String): AuditSessionEntity?

    @Query("SELECT * FROM audit_sessions WHERE status = :status ORDER BY startedAt DESC")
    fun getAuditSessionsByStatus(status: AuditStatus): Flow<List<AuditSessionEntity>>

    @Query("SELECT * FROM audit_sessions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveAuditSession(): AuditSessionEntity?

    @Query("SELECT * FROM audit_sessions ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getRecentAuditSessions(limit: Int = 10): List<AuditSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditSession(session: AuditSessionEntity): Long

    @Update
    suspend fun updateAuditSession(session: AuditSessionEntity)

    @Query("""
        UPDATE audit_sessions
        SET status = :status,
            completedAt = :completedAt,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateAuditSessionStatus(
        id: String,
        status: AuditStatus,
        completedAt: Long? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE audit_sessions
        SET auditedItems = auditedItems + 1,
            adjustedItems = CASE WHEN :wasAdjusted THEN adjustedItems + 1 ELSE adjustedItems END,
            removedItems = CASE WHEN :wasRemoved THEN removedItems + 1 ELSE removedItems END,
            updatedAt = :updatedAt
        WHERE id = :sessionId
    """)
    suspend fun incrementAuditProgress(
        sessionId: String,
        wasAdjusted: Boolean,
        wasRemoved: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deleteAuditSession(session: AuditSessionEntity)

    @Query("DELETE FROM audit_sessions WHERE id = :id")
    suspend fun deleteAuditSessionById(id: String)

    // Audit items
    @Query("SELECT * FROM audit_items WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getAuditItemsForSession(sessionId: String): Flow<List<AuditItemEntity>>

    @Query("SELECT * FROM audit_items WHERE sessionId = :sessionId AND action IS NULL ORDER BY createdAt ASC")
    fun getPendingAuditItems(sessionId: String): Flow<List<AuditItemEntity>>

    @Query("SELECT * FROM audit_items WHERE sessionId = :sessionId AND action IS NOT NULL ORDER BY auditedAt DESC")
    fun getCompletedAuditItems(sessionId: String): Flow<List<AuditItemEntity>>

    @Query("SELECT * FROM audit_items WHERE id = :id")
    suspend fun getAuditItemById(id: String): AuditItemEntity?

    @Query("SELECT COUNT(*) FROM audit_items WHERE sessionId = :sessionId")
    suspend fun getAuditItemCountForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM audit_items WHERE sessionId = :sessionId AND action IS NOT NULL")
    suspend fun getCompletedAuditItemCount(sessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditItem(item: AuditItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditItems(items: List<AuditItemEntity>)

    @Update
    suspend fun updateAuditItem(item: AuditItemEntity)

    @Query("""
        UPDATE audit_items
        SET actualQuantity = :actualQuantity,
            action = :action,
            notes = :notes,
            auditedAt = :auditedAt,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun recordAuditResult(
        id: String,
        actualQuantity: Double,
        action: AuditAction,
        notes: String?,
        auditedAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deleteAuditItem(item: AuditItemEntity)

    @Query("DELETE FROM audit_items WHERE sessionId = :sessionId")
    suspend fun deleteAuditItemsForSession(sessionId: String)

    // Aggregations
    @Query("""
        SELECT
            sessionId,
            COUNT(*) as totalItems,
            SUM(CASE WHEN action = 'CONFIRMED' THEN 1 ELSE 0 END) as confirmedItems,
            SUM(CASE WHEN action = 'ADJUSTED' THEN 1 ELSE 0 END) as adjustedItems,
            SUM(CASE WHEN action = 'REMOVED' THEN 1 ELSE 0 END) as removedItems,
            SUM(CASE WHEN action = 'SKIPPED' THEN 1 ELSE 0 END) as skippedItems,
            COALESCE(SUM(actualQuantity - expectedQuantity), 0) as totalDiscrepancy
        FROM audit_items
        WHERE sessionId = :sessionId
        GROUP BY sessionId
    """)
    suspend fun getAuditSummary(sessionId: String): AuditSummaryData?

    // Stats
    @Query("SELECT COUNT(*) FROM audit_sessions WHERE status = 'COMPLETED'")
    suspend fun getCompletedAuditCount(): Int

    @Query("""
        SELECT AVG(adjustedItems * 100.0 / totalItems)
        FROM audit_sessions
        WHERE status = 'COMPLETED' AND totalItems > 0
    """)
    suspend fun getAverageDiscrepancyRate(): Double?
}

data class AuditSummaryData(
    val sessionId: String,
    val totalItems: Int,
    val confirmedItems: Int,
    val adjustedItems: Int,
    val removedItems: Int,
    val skippedItems: Int,
    val totalDiscrepancy: Double
)
