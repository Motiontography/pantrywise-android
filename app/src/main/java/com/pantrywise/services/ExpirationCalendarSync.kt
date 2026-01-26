package com.pantrywise.services

import com.pantrywise.data.local.dao.InventoryDao
import com.pantrywise.data.local.dao.PreferencesDao
import com.pantrywise.data.local.dao.ProductDao
import com.pantrywise.data.local.entity.InventoryItemEntity
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a synced expiration event
 */
data class ExpirationCalendarEvent(
    val inventoryItemId: String,
    val productName: String,
    val expirationDate: Long,
    val location: String,
    val quantity: Double,
    val unit: String
)

@Singleton
class ExpirationCalendarSync @Inject constructor(
    private val calendarManager: CalendarManager,
    private val inventoryDao: InventoryDao,
    private val productDao: ProductDao,
    private val preferencesDao: PreferencesDao
) {
    companion object {
        const val SYNC_ID_PREFIX = "pantrywise_expiration_"

        // Default reminder: 3 days before expiration
        const val DEFAULT_REMINDER_DAYS = 3
    }

    /**
     * Sync all expiring items to calendar
     */
    suspend fun syncExpirations(
        calendarId: Long,
        reminderDaysBeforeExpiration: Int = DEFAULT_REMINDER_DAYS
    ): CalendarResult<Int> {
        // Get items with expiration dates
        val inventoryItems = inventoryDao.getAllInventoryItems().first()
        val expiringItems = inventoryItems.filter { it.expirationDate != null }

        var syncedCount = 0
        var errorMessage: String? = null

        for (item in expiringItems) {
            val result = syncExpirationEvent(calendarId, item, reminderDaysBeforeExpiration)
            when (result) {
                is CalendarResult.Success -> syncedCount++
                is CalendarResult.Error -> {
                    errorMessage = result.message
                }
            }
        }

        return if (errorMessage != null && syncedCount == 0) {
            CalendarResult.Error(errorMessage)
        } else {
            CalendarResult.Success(syncedCount)
        }
    }

    /**
     * Sync a single expiration event
     */
    suspend fun syncExpirationEvent(
        calendarId: Long,
        inventoryItem: InventoryItemEntity,
        reminderDaysBeforeExpiration: Int = DEFAULT_REMINDER_DAYS
    ): CalendarResult<Long> {
        val expirationDate = inventoryItem.expirationDate ?: return CalendarResult.Error("No expiration date")

        // Get product name
        val product = productDao.getProductById(inventoryItem.productId)
        val productName = product?.name ?: "Unknown Product"

        val syncId = "$SYNC_ID_PREFIX${inventoryItem.id}"

        // Check if event already exists
        val existingEvent = calendarManager.findEventBySyncId(calendarId, syncId)

        // Create event at start of expiration day
        val calendar = Calendar.getInstance().apply {
            timeInMillis = expirationDate
            set(Calendar.HOUR_OF_DAY, 9) // 9 AM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val event = CalendarEvent(
            title = "$productName expires",
            description = buildString {
                append("$productName (${inventoryItem.quantityOnHand} ${inventoryItem.unit.displayName}) ")
                append("in ${inventoryItem.location.displayName} expires today.\n\n")
                append("Use it or freeze it before it goes bad!")
            },
            location = inventoryItem.location.displayName,
            startTime = calendar.timeInMillis,
            endTime = calendar.timeInMillis + (30 * 60 * 1000), // 30 minute event
            isAllDay = false,
            reminderMinutes = listOf(reminderDaysBeforeExpiration * 24 * 60), // Days before in minutes
            syncId = syncId
        )

        return when (val existing = existingEvent) {
            is CalendarResult.Success -> {
                if (existing.data != null) {
                    // Update existing event
                    val updateResult = calendarManager.updateEvent(existing.data, event)
                    when (updateResult) {
                        is CalendarResult.Success -> CalendarResult.Success(existing.data)
                        is CalendarResult.Error -> CalendarResult.Error(updateResult.message)
                    }
                } else {
                    // Create new event
                    calendarManager.addEvent(calendarId, event)
                }
            }
            is CalendarResult.Error -> {
                // Try to create anyway
                calendarManager.addEvent(calendarId, event)
            }
        }
    }

    /**
     * Remove expiration event from calendar
     */
    suspend fun removeExpirationEvent(
        calendarId: Long,
        inventoryItemId: String
    ): CalendarResult<Boolean> {
        val syncId = "$SYNC_ID_PREFIX$inventoryItemId"

        return when (val findResult = calendarManager.findEventBySyncId(calendarId, syncId)) {
            is CalendarResult.Success -> {
                val eventId = findResult.data
                if (eventId != null) {
                    calendarManager.deleteEvent(eventId)
                } else {
                    CalendarResult.Success(true) // Event doesn't exist, nothing to delete
                }
            }
            is CalendarResult.Error -> CalendarResult.Error(findResult.message)
        }
    }

    /**
     * Get upcoming expirations that should be synced
     */
    suspend fun getUpcomingExpirations(
        daysAhead: Int = 30
    ): List<ExpirationCalendarEvent> {
        val now = System.currentTimeMillis()
        val futureLimit = now + (daysAhead.toLong() * 24 * 60 * 60 * 1000)

        val inventoryItems = inventoryDao.getAllInventoryItems().first()

        return inventoryItems
            .filter { item ->
                val expDate = item.expirationDate
                expDate != null && expDate in now..futureLimit
            }
            .mapNotNull { item ->
                val product = productDao.getProductById(item.productId)
                if (product != null && item.expirationDate != null) {
                    ExpirationCalendarEvent(
                        inventoryItemId = item.id,
                        productName = product.name,
                        expirationDate = item.expirationDate,
                        location = item.location.displayName,
                        quantity = item.quantityOnHand,
                        unit = item.unit.displayName
                    )
                } else null
            }
            .sortedBy { it.expirationDate }
    }

    /**
     * Remove all expiration events from calendar
     */
    suspend fun removeAllExpirationEvents(calendarId: Long): CalendarResult<Int> {
        return calendarManager.deleteAllPantryWiseEvents(calendarId)
    }

    /**
     * Sync expirations for items expiring within specified days
     */
    suspend fun syncUpcomingExpirations(
        calendarId: Long,
        daysAhead: Int = 30,
        reminderDaysBeforeExpiration: Int = DEFAULT_REMINDER_DAYS
    ): CalendarResult<Int> {
        val now = System.currentTimeMillis()
        val futureLimit = now + (daysAhead.toLong() * 24 * 60 * 60 * 1000)

        val inventoryItems = inventoryDao.getAllInventoryItems().first()
        val expiringItems = inventoryItems.filter { item ->
            val expDate = item.expirationDate
            expDate != null && expDate in now..futureLimit
        }

        var syncedCount = 0
        var errorMessage: String? = null

        for (item in expiringItems) {
            val result = syncExpirationEvent(calendarId, item, reminderDaysBeforeExpiration)
            when (result) {
                is CalendarResult.Success -> syncedCount++
                is CalendarResult.Error -> {
                    if (errorMessage == null) errorMessage = result.message
                }
            }
        }

        return if (errorMessage != null && syncedCount == 0) {
            CalendarResult.Error(errorMessage)
        } else {
            CalendarResult.Success(syncedCount)
        }
    }
}
