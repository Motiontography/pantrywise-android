package com.pantrywise.ui.health

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.dao.NutritionDao
import com.pantrywise.services.DailyNutritionSummary
import com.pantrywise.services.HealthConnectManager
import com.pantrywise.services.HealthConnectStatus
import com.pantrywise.services.HealthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed class NutritionDashboardEvent {
    data object None : NutritionDashboardEvent()
    data class Error(val message: String) : NutritionDashboardEvent()
    data class WaterLogged(val amount: Double) : NutritionDashboardEvent()
}

data class NutritionDashboardUiState(
    val healthConnectStatus: HealthConnectStatus = HealthConnectStatus.NotSupported,
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val todaySummary: DailyNutritionSummary? = null,
    val weeklySummaries: List<DailyNutritionSummary> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val goals: NutritionGoals = NutritionGoals(),
    val event: NutritionDashboardEvent = NutritionDashboardEvent.None
)

@HiltViewModel
class NutritionDashboardViewModel @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val nutritionDao: NutritionDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(NutritionDashboardUiState())
    val uiState: StateFlow<NutritionDashboardUiState> = _uiState.asStateFlow()

    init {
        checkHealthConnectStatus()
        loadGoals()
    }

    private fun checkHealthConnectStatus() {
        val status = healthConnectManager.checkAvailability()
        _uiState.update { it.copy(healthConnectStatus = status) }

        if (status == HealthConnectStatus.Available) {
            checkPermissions()
        }
    }

    private fun checkPermissions() {
        viewModelScope.launch {
            val hasPermission = healthConnectManager.hasAllPermissions()
            _uiState.update { it.copy(hasPermission = hasPermission) }

            if (hasPermission) {
                loadData()
            }
        }
    }

    private fun loadGoals() {
        viewModelScope.launch {
            // Load goals from database
            val goalsEntity = nutritionDao.getActiveNutritionGoals()
            if (goalsEntity != null) {
                _uiState.update {
                    it.copy(
                        goals = NutritionGoals(
                            caloriesGoal = goalsEntity.caloriesGoal,
                            proteinGoal = goalsEntity.proteinGoal,
                            carbsGoal = goalsEntity.carbsGoal,
                            fatGoal = goalsEntity.fatGoal,
                            fiberGoal = goalsEntity.fiberGoal,
                            sugarLimit = goalsEntity.sugarLimit,
                            sodiumLimit = goalsEntity.sodiumLimit,
                            waterGoal = goalsEntity.waterGoal
                        )
                    )
                }
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Load today's summary
            when (val result = healthConnectManager.getDailyNutritionSummary(LocalDate.now())) {
                is HealthResult.Success -> {
                    _uiState.update { it.copy(todaySummary = result.data) }
                }
                is HealthResult.Error -> {
                    _uiState.update {
                        it.copy(event = NutritionDashboardEvent.Error(result.message))
                    }
                }
            }

            // Load weekly summaries
            when (val result = healthConnectManager.getWeeklyNutritionSummaries()) {
                is HealthResult.Success -> {
                    _uiState.update { it.copy(weeklySummaries = result.data) }
                }
                is HealthResult.Error -> {
                    // Silently fail for weekly data
                }
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }

        viewModelScope.launch {
            when (val result = healthConnectManager.getDailyNutritionSummary(date)) {
                is HealthResult.Success -> {
                    _uiState.update { it.copy(todaySummary = result.data) }
                }
                is HealthResult.Error -> {
                    _uiState.update {
                        it.copy(event = NutritionDashboardEvent.Error(result.message))
                    }
                }
            }
        }
    }

    fun logWater(amountMl: Double) {
        viewModelScope.launch {
            when (val result = healthConnectManager.writeHydrationRecord(amountMl)) {
                is HealthResult.Success -> {
                    // Refresh today's summary
                    loadData()
                    _uiState.update {
                        it.copy(event = NutritionDashboardEvent.WaterLogged(amountMl))
                    }
                }
                is HealthResult.Error -> {
                    _uiState.update {
                        it.copy(event = NutritionDashboardEvent.Error(result.message))
                    }
                }
            }
        }
    }

    fun getPermissionContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    fun getInstallIntent(): Intent {
        return healthConnectManager.getInstallIntent()
    }

    fun clearEvent() {
        _uiState.update { it.copy(event = NutritionDashboardEvent.None) }
    }
}
