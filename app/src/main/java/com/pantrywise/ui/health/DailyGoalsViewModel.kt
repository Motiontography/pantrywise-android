package com.pantrywise.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.dao.NutritionDao
import com.pantrywise.data.local.entity.NutritionGoalsEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DailyGoalsEvent {
    data object None : DailyGoalsEvent()
    data object Saved : DailyGoalsEvent()
    data class Error(val message: String) : DailyGoalsEvent()
}

data class DailyGoalsUiState(
    val caloriesGoal: Double = 2000.0,
    val proteinGoal: Double = 50.0,
    val carbsGoal: Double = 275.0,
    val fatGoal: Double = 78.0,
    val fiberGoal: Double = 28.0,
    val sugarLimit: Double = 50.0,
    val sodiumLimit: Double = 2300.0,
    val waterGoal: Double = 2000.0,
    val isSaving: Boolean = false,
    val event: DailyGoalsEvent = DailyGoalsEvent.None
)

@HiltViewModel
class DailyGoalsViewModel @Inject constructor(
    private val nutritionDao: NutritionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyGoalsUiState())
    val uiState: StateFlow<DailyGoalsUiState> = _uiState.asStateFlow()

    private var currentGoalsId: String? = null

    init {
        loadGoals()
    }

    private fun loadGoals() {
        viewModelScope.launch {
            val goals = nutritionDao.getActiveNutritionGoals()
            if (goals != null) {
                currentGoalsId = goals.id
                _uiState.update {
                    it.copy(
                        caloriesGoal = goals.caloriesGoal,
                        proteinGoal = goals.proteinGoal,
                        carbsGoal = goals.carbsGoal,
                        fatGoal = goals.fatGoal,
                        fiberGoal = goals.fiberGoal,
                        sugarLimit = goals.sugarLimit,
                        sodiumLimit = goals.sodiumLimit,
                        waterGoal = goals.waterGoal
                    )
                }
            }
        }
    }

    fun updateCaloriesGoal(value: Double) {
        _uiState.update { it.copy(caloriesGoal = value) }
    }

    fun updateProteinGoal(value: Double) {
        _uiState.update { it.copy(proteinGoal = value) }
    }

    fun updateCarbsGoal(value: Double) {
        _uiState.update { it.copy(carbsGoal = value) }
    }

    fun updateFatGoal(value: Double) {
        _uiState.update { it.copy(fatGoal = value) }
    }

    fun updateFiberGoal(value: Double) {
        _uiState.update { it.copy(fiberGoal = value) }
    }

    fun updateSugarLimit(value: Double) {
        _uiState.update { it.copy(sugarLimit = value) }
    }

    fun updateSodiumLimit(value: Double) {
        _uiState.update { it.copy(sodiumLimit = value) }
    }

    fun updateWaterGoal(value: Double) {
        _uiState.update { it.copy(waterGoal = value) }
    }

    fun applyPreset(preset: NutritionPreset) {
        when (preset) {
            NutritionPreset.WEIGHT_LOSS -> {
                _uiState.update {
                    it.copy(
                        caloriesGoal = 1500.0,
                        proteinGoal = 75.0,
                        carbsGoal = 150.0,
                        fatGoal = 50.0,
                        fiberGoal = 30.0,
                        sugarLimit = 25.0,
                        sodiumLimit = 2000.0,
                        waterGoal = 2500.0
                    )
                }
            }
            NutritionPreset.MAINTENANCE -> {
                _uiState.update {
                    it.copy(
                        caloriesGoal = 2000.0,
                        proteinGoal = 50.0,
                        carbsGoal = 275.0,
                        fatGoal = 78.0,
                        fiberGoal = 28.0,
                        sugarLimit = 50.0,
                        sodiumLimit = 2300.0,
                        waterGoal = 2000.0
                    )
                }
            }
            NutritionPreset.MUSCLE_GAIN -> {
                _uiState.update {
                    it.copy(
                        caloriesGoal = 2500.0,
                        proteinGoal = 150.0,
                        carbsGoal = 300.0,
                        fatGoal = 80.0,
                        fiberGoal = 35.0,
                        sugarLimit = 60.0,
                        sodiumLimit = 2500.0,
                        waterGoal = 3000.0
                    )
                }
            }
        }
    }

    fun saveGoals() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                val state = _uiState.value

                // Create new goals
                val goals = NutritionGoalsEntity(
                    caloriesGoal = state.caloriesGoal,
                    proteinGoal = state.proteinGoal,
                    carbsGoal = state.carbsGoal,
                    fatGoal = state.fatGoal,
                    fiberGoal = state.fiberGoal,
                    sugarLimit = state.sugarLimit,
                    sodiumLimit = state.sodiumLimit,
                    waterGoal = state.waterGoal,
                    isActive = true
                )

                nutritionDao.insertNutritionGoals(goals)

                // Deactivate other goals
                nutritionDao.deactivateOtherGoals(goals.id)
                currentGoalsId = goals.id

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        event = DailyGoalsEvent.Saved
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        event = DailyGoalsEvent.Error(e.message ?: "Failed to save goals")
                    )
                }
            }
        }
    }

    fun clearEvent() {
        _uiState.update { it.copy(event = DailyGoalsEvent.None) }
    }
}
