package com.pantrywise.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.repository.CategoryRepository
import com.pantrywise.data.repository.InventoryRepository
import com.pantrywise.data.repository.PreferencesRepository
import com.pantrywise.data.repository.ProductRepository
import com.pantrywise.data.repository.TransactionRepository
import com.pantrywise.domain.model.LocationType
import com.pantrywise.domain.usecase.ExportDataUseCase
import com.pantrywise.domain.usecase.ExportFormat
import com.pantrywise.domain.usecase.ExportResult
import com.pantrywise.services.PantryNotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val defaultLocation: LocationType = LocationType.PANTRY,
    val defaultCurrency: String = "USD",
    val categoryCount: Int = 0,
    val productCount: Int = 0,
    val inventoryCount: Int = 0,
    val exportResult: ExportResult? = null,
    val isExporting: Boolean = false,
    val error: String? = null,
    val notificationsEnabled: Boolean = true,
    val hasNotificationPermission: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val exportDataUseCase: ExportDataUseCase,
    private val notificationManager: PantryNotificationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadStats()
        checkNotificationStatus()
    }

    fun checkNotificationStatus() {
        _uiState.update {
            it.copy(
                notificationsEnabled = notificationManager.areNotificationsEnabled(),
                hasNotificationPermission = notificationManager.hasNotificationPermission()
            )
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val preferences = preferencesRepository.getUserPreferences()
            _uiState.update {
                it.copy(
                    defaultLocation = preferences.defaultLocation,
                    defaultCurrency = preferences.defaultCurrency
                )
            }
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val categoryCount = categoryRepository.getCategoryCount()
            val productCount = productRepository.getProductCount()
            val inventoryCount = inventoryRepository.getInventoryItemCount()

            _uiState.update {
                it.copy(
                    categoryCount = categoryCount,
                    productCount = productCount,
                    inventoryCount = inventoryCount
                )
            }
        }
    }

    fun setDefaultLocation(location: LocationType) {
        viewModelScope.launch {
            preferencesRepository.updateDefaultLocation(location)
            _uiState.update { it.copy(defaultLocation = location) }
        }
    }

    fun setDefaultCurrency(currency: String) {
        viewModelScope.launch {
            preferencesRepository.updateDefaultCurrency(currency)
            _uiState.update { it.copy(defaultCurrency = currency) }
        }
    }

    fun exportData(format: ExportFormat) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }

            val result = exportDataUseCase(format)

            _uiState.update {
                it.copy(
                    isExporting = false,
                    exportResult = result
                )
            }
        }
    }

    fun clearExportResult() {
        _uiState.update { it.copy(exportResult = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
