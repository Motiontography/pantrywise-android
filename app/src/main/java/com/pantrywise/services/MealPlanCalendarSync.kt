package com.pantrywise.services

import com.pantrywise.data.local.dao.MealPlanDao
import com.pantrywise.data.local.entity.MealPlanEntryEntity
import com.pantrywise.data.local.entity.MealType
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a synced meal plan event
 */
data class MealPlanCalendarEvent(
    val entryId: String,
    val mealType: String,
    val recipeName: String?,
    val notes: String?,
    val plannedDate: Long,
    val servings: Int
)

@Singleton
class MealPlanCalendarSync @Inject constructor(
    private val calendarManager: CalendarManager,
    private val mealPlanDao: MealPlanDao
) {
    companion object {
        const val SYNC_ID_PREFIX = "pantrywise_meal_"

        // Default reminder: 2 hours before meal
        const val DEFAULT_REMINDER_MINUTES = 120

        // Meal time defaults (24-hour format)
        val MEAL_TIMES = mapOf(
            MealType.BREAKFAST to 8,    // 8 AM
            MealType.LUNCH to 12,       // 12 PM
            MealType.DINNER to 18,      // 6 PM
            MealType.SNACK to 15        // 3 PM
        )

        // Meal duration in minutes
        const val MEAL_DURATION_MINUTES = 60
    }

    /**
     * Sync all meal plan entries to calendar
     */
    suspend fun syncMealPlan(
        calendarId: Long,
        mealPlanId: String,
        reminderMinutes: Int = DEFAULT_REMINDER_MINUTES
    ): CalendarResult<Int> {
        val entries = mealPlanDao.getEntriesForMealPlan(mealPlanId).first()

        var syncedCount = 0
        var errorMessage: String? = null

        for (entry in entries) {
            val result = syncMealPlanEntry(calendarId, entry, reminderMinutes)
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

    /**
     * Sync a single meal plan entry
     */
    suspend fun syncMealPlanEntry(
        calendarId: Long,
        entry: MealPlanEntryEntity,
        reminderMinutes: Int = DEFAULT_REMINDER_MINUTES
    ): CalendarResult<Long> {
        // Get recipe name if available
        val recipeName = entry.recipeId?.let { recipeId ->
            mealPlanDao.getRecipeById(recipeId)?.name
        }

        val syncId = "$SYNC_ID_PREFIX${entry.id}"

        // Check if event already exists
        val existingEvent = calendarManager.findEventBySyncId(calendarId, syncId)

        // Determine meal time
        val mealHour = MEAL_TIMES[entry.mealType] ?: 12

        // Create event at planned date with meal time
        val calendar = Calendar.getInstance().apply {
            timeInMillis = entry.date
            set(Calendar.HOUR_OF_DAY, mealHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val title = buildMealTitle(entry.mealType, recipeName, entry.notes)
        val description = buildMealDescription(entry, recipeName)

        val event = CalendarEvent(
            title = title,
            description = description,
            startTime = calendar.timeInMillis,
            endTime = calendar.timeInMillis + (MEAL_DURATION_MINUTES * 60 * 1000),
            isAllDay = false,
            reminderMinutes = listOf(reminderMinutes),
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

    private fun buildMealTitle(
        mealType: MealType,
        recipeName: String?,
        notes: String?
    ): String {
        val mealLabel = mealType.name.lowercase().replaceFirstChar { it.uppercase() }

        return when {
            recipeName != null -> "$mealLabel: $recipeName"
            notes != null -> "$mealLabel: $notes"
            else -> mealLabel
        }
    }

    private fun buildMealDescription(
        entry: MealPlanEntryEntity,
        recipeName: String?
    ): String {
        return buildString {
            val mealLabel = entry.mealType.name.lowercase().replaceFirstChar { it.uppercase() }
            append("Meal: $mealLabel\n")

            if (recipeName != null) {
                append("Recipe: $recipeName\n")
            }

            append("Servings: ${entry.servings}\n")

            if (!entry.notes.isNullOrBlank()) {
                append("\nNotes: ${entry.notes}\n")
            }

            append("\n📱 Added from PantryWise")
        }
    }

    /**
     * Remove meal plan event from calendar
     */
    suspend fun removeMealPlanEvent(
        calendarId: Long,
        entryId: String
    ): CalendarResult<Boolean> {
        val syncId = "$SYNC_ID_PREFIX$entryId"

        return when (val findResult = calendarManager.findEventBySyncId(calendarId, syncId)) {
            is CalendarResult.Success -> {
                val eventId = findResult.data
                if (eventId != null) {
                    calendarManager.deleteEvent(eventId)
                } else {
                    CalendarResult.Success(true) // Event doesn't exist
                }
            }
            is CalendarResult.Error -> CalendarResult.Error(findResult.message)
        }
    }

    /**
     * Get upcoming meals that should be synced
     */
    suspend fun getUpcomingMeals(
        mealPlanId: String,
        daysAhead: Int = 14
    ): List<MealPlanCalendarEvent> {
        val now = System.currentTimeMillis()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val futureLimit = startOfToday + (daysAhead.toLong() * 24 * 60 * 60 * 1000)

        val entries = mealPlanDao.getEntriesForMealPlan(mealPlanId).first()

        return entries
            .filter { it.date in startOfToday..futureLimit }
            .map { entry ->
                val recipeName = entry.recipeId?.let { recipeId ->
                    mealPlanDao.getRecipeById(recipeId)?.name
                }

                MealPlanCalendarEvent(
                    entryId = entry.id,
                    mealType = entry.mealType.name.lowercase().replaceFirstChar { it.uppercase() },
                    recipeName = recipeName,
                    notes = entry.notes,
                    plannedDate = entry.date,
                    servings = entry.servings
                )
            }
            .sortedBy { it.plannedDate }
    }

    /**
     * Sync meals for a date range
     */
    suspend fun syncMealsForDateRange(
        calendarId: Long,
        mealPlanId: String,
        startDate: Long,
        endDate: Long,
        reminderMinutes: Int = DEFAULT_REMINDER_MINUTES
    ): CalendarResult<Int> {
        val entries = mealPlanDao.getEntriesForMealPlan(mealPlanId).first()
        val rangeEntries = entries.filter { it.date in startDate..endDate }

        var syncedCount = 0
        var errorMessage: String? = null

        for (entry in rangeEntries) {
            val result = syncMealPlanEntry(calendarId, entry, reminderMinutes)
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

    /**
     * Remove all meal plan events from calendar
     */
    suspend fun removeAllMealPlanEvents(calendarId: Long): CalendarResult<Int> {
        // This will delete all PantryWise events - might need to filter by prefix
        return calendarManager.deleteAllPantryWiseEvents(calendarId)
    }
}
