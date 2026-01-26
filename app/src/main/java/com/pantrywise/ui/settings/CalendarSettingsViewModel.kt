package com.pantrywise.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.dao.PreferencesDao
import com.pantrywise.services.CalendarManager
import com.pantrywise.services.CalendarResult
import com.pantrywise.services.DeviceCalendar
import com.pantrywise.services.ExpirationCalendarSync
import com.pantrywise.services.MealPlanCalendarSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CalendarSettingsEvent {
    data object None : CalendarSettingsEvent()
    data class SyncComplete(val count: Int) : CalendarSettingsEvent()
    data class Error(val message: String) : CalendarSettingsEvent()
}

data class CalendarSettingsUiState(
    val syncEnabled: Boolean = false,
    val availableCalendars: List<DeviceCalendar> = emptyList(),
    val selectedCalendarId: Long? = null,
    val usePantryWiseCalendar: Boolean = true,
    val syncExpirations: Boolean = true,
    val syncMealPlans: Boolean = true,
    val expirationReminderDays: Int = 3,
    val mealPlanReminderMinutes: Int = 120,
    val isLoadingCalendars: Boolean = false,
    val isSyncing: Boolean = false,
    val event: CalendarSettingsEvent = CalendarSettingsEvent.None
)

@HiltViewModel
class CalendarSettingsViewModel @Inject constructor(
    private val calendarManager: CalendarManager,
    private val expirationCalendarSync: ExpirationCalendarSync,
    private val mealPlanCalendarSync: MealPlanCalendarSync,
    private val preferencesDao: PreferencesDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarSettingsUiState())
    val uiState: StateFlow<CalendarSettingsUiState> = _uiState.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            // Load saved preferences from database
            // For now, use defaults - in production would load from PreferencesDao
            _uiState.update { it.copy(
                syncEnabled = false,
                usePantryWiseCalendar = true,
                syncExpirations = true,
                syncMealPlans = true,
                expirationReminderDays = 3,
                mealPlanReminderMinutes = 120
            )}
        }
    }

    fun loadCalendars() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCalendars = true) }

            when (val result = calendarManager.getCalendars()) {
                is CalendarResult.Success -> {
                    _uiState.update {
                        it.copy(
                            availableCalendars = result.data,
                            isLoadingCalendars = false
                        )
                    }
                }
                is CalendarResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCalendars = false,
                            event = CalendarSettingsEvent.Error(result.message)
                        )
                    }
                }
            }
        }
    }

    fun toggleSyncEnabled() {
        _uiState.update { it.copy(syncEnabled = !it.syncEnabled) }
        savePreferences()
    }

    fun selectCalendar(calendarId: Long) {
        _uiState.update {
            it.copy(
                selectedCalendarId = calendarId,
                usePantryWiseCalendar = false
            )
        }
        savePreferences()
    }

    fun usePantryWiseCalendar() {
        _uiState.update {
            it.copy(
                usePantryWiseCalendar = true,
                selectedCalendarId = null
            )
        }
        savePreferences()
    }

    fun toggleExpirationSync() {
        _uiState.update { it.copy(syncExpirations = !it.syncExpirations) }
        savePreferences()
    }

    fun toggleMealPlanSync() {
        _uiState.update { it.copy(syncMealPlans = !it.syncMealPlans) }
        savePreferences()
    }

    fun setExpirationReminderDays(days: Int) {
        _uiState.update { it.copy(expirationReminderDays = days) }
        savePreferences()
    }

    fun setMealPlanReminderMinutes(minutes: Int) {
        _uiState.update { it.copy(mealPlanReminderMinutes = minutes) }
        savePreferences()
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }

            val calendarId = getOrCreateCalendarId()
            if (calendarId == null) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        event = CalendarSettingsEvent.Error("Could not access calendar")
                    )
                }
                return@launch
            }

            var totalSynced = 0

            // Sync expirations
            if (_uiState.value.syncExpirations) {
                when (val result = expirationCalendarSync.syncUpcomingExpirations(
                    calendarId = calendarId,
                    daysAhead = 30,
                    reminderDaysBeforeExpiration = _uiState.value.expirationReminderDays
                )) {
                    is CalendarResult.Success -> totalSynced += result.data
                    is CalendarResult.Error -> {
                        _uiState.update {
                            it.copy(event = CalendarSettingsEvent.Error(result.message))
                        }
                    }
                }
            }

            // Note: Meal plan sync would need the active meal plan ID
            // This would be fetched from the repository in a real implementation

            _uiState.update {
                it.copy(
                    isSyncing = false,
                    event = CalendarSettingsEvent.SyncComplete(totalSynced)
                )
            }
        }
    }

    fun clearAllEvents() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }

            val calendarId = getOrCreateCalendarId()
            if (calendarId == null) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        event = CalendarSettingsEvent.Error("Could not access calendar")
                    )
                }
                return@launch
            }

            when (val result = calendarManager.deleteAllPantryWiseEvents(calendarId)) {
                is CalendarResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            event = CalendarSettingsEvent.SyncComplete(result.data)
                        )
                    }
                }
                is CalendarResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            event = CalendarSettingsEvent.Error(result.message)
                        )
                    }
                }
            }
        }
    }

    private suspend fun getOrCreateCalendarId(): Long? {
        return if (_uiState.value.usePantryWiseCalendar) {
            when (val result = calendarManager.getOrCreatePantryWiseCalendar()) {
                is CalendarResult.Success -> result.data
                is CalendarResult.Error -> null
            }
        } else {
            _uiState.value.selectedCalendarId
        }
    }

    fun clearEvent() {
        _uiState.update { it.copy(event = CalendarSettingsEvent.None) }
    }

    private fun savePreferences() {
        viewModelScope.launch {
            // Save preferences to database
            // In production, would use PreferencesDao to persist
        }
    }
}
