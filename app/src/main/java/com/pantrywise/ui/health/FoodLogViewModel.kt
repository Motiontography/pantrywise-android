package com.pantrywise.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.dao.NutritionDao
import com.pantrywise.data.local.entity.NutritionLogEntry
import com.pantrywise.services.HealthConnectManager
import com.pantrywise.services.HealthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

sealed class FoodLogEvent {
    data object None : FoodLogEvent()
    data object EntryAdded : FoodLogEvent()
    data object EntryDeleted : FoodLogEvent()
    data class Error(val message: String) : FoodLogEvent()
}

data class FoodLogUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val entries: List<NutritionLogEntry> = emptyList(),
    val isLoading: Boolean = false,
    val event: FoodLogEvent = FoodLogEvent.None
)

@HiltViewModel
class FoodLogViewModel @Inject constructor(
    private val nutritionDao: NutritionDao,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodLogUiState())
    val uiState: StateFlow<FoodLogUiState> = _uiState.asStateFlow()

    init {
        loadEntries()
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        loadEntries()
    }

    private fun loadEntries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val date = _uiState.value.selectedDate
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            try {
                val entries = nutritionDao.getNutritionLogEntriesForDay(startOfDay, endOfDay)
                _uiState.update {
                    it.copy(
                        entries = entries,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        event = FoodLogEvent.Error(e.message ?: "Failed to load entries")
                    )
                }
            }
        }
    }

    fun addEntry(
        name: String,
        calories: Double?,
        protein: Double?,
        carbs: Double?,
        fat: Double?,
        servings: Double,
        mealType: String
    ) {
        viewModelScope.launch {
            try {
                val entry = NutritionLogEntry(
                    productId = null,
                    productName = name,
                    servings = servings,
                    calories = calories,
                    protein = protein,
                    carbohydrates = carbs,
                    fat = fat,
                    fiber = null,
                    sugar = null,
                    sodium = null,
                    mealType = mealType,
                    loggedAt = System.currentTimeMillis()
                )

                // Save to local database
                nutritionDao.insertNutritionLogEntry(entry)

                // Sync to Health Connect
                val healthResult = healthConnectManager.syncNutritionLogEntry(
                    productName = name,
                    servings = servings,
                    calories = calories,
                    protein = protein,
                    carbohydrates = carbs,
                    fat = fat,
                    fiber = null,
                    sugar = null,
                    sodium = null,
                    mealType = mealType,
                    loggedAt = entry.loggedAt
                )

                when (healthResult) {
                    is HealthResult.Success -> {
                        _uiState.update { it.copy(event = FoodLogEvent.EntryAdded) }
                    }
                    is HealthResult.Error -> {
                        // Entry was saved locally but failed to sync to Health Connect
                        _uiState.update { it.copy(event = FoodLogEvent.EntryAdded) }
                    }
                }

                loadEntries()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(event = FoodLogEvent.Error(e.message ?: "Failed to add entry"))
                }
            }
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            try {
                nutritionDao.deleteNutritionLogEntryById(entryId)
                _uiState.update { it.copy(event = FoodLogEvent.EntryDeleted) }
                loadEntries()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(event = FoodLogEvent.Error(e.message ?: "Failed to delete entry"))
                }
            }
        }
    }

    fun clearEvent() {
        _uiState.update { it.copy(event = FoodLogEvent.None) }
    }
}
