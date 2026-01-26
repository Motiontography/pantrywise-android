package com.pantrywise.wear.presentation.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.wear.data.WearDataRepository
import com.pantrywise.wear.data.WearQuickAddItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickAddUiState(
    val presets: List<WearQuickAddItem> = WearQuickAddItem.defaultPresets,
    val showAddedMessage: Boolean = false,
    val isAdding: Boolean = false
)

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val repository: WearDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickAddUiState())
    val uiState: StateFlow<QuickAddUiState> = _uiState.asStateFlow()

    init {
        observePresets()
    }

    private fun observePresets() {
        viewModelScope.launch {
            repository.syncData.collect { data ->
                if (data.quickAddPresets.isNotEmpty()) {
                    _uiState.update { it.copy(presets = data.quickAddPresets) }
                }
            }
        }
    }

    fun addItem(item: WearQuickAddItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAdding = true) }

            repository.addItem(
                name = item.name,
                quantity = item.defaultQuantity,
                unit = item.defaultUnit
            )

            // Show success message briefly
            _uiState.update { it.copy(isAdding = false, showAddedMessage = true) }
            delay(2000)
            _uiState.update { it.copy(showAddedMessage = false) }
        }
    }

    fun addCustomItem(name: String, quantity: Double = 1.0, unit: String = "each") {
        viewModelScope.launch {
            _uiState.update { it.copy(isAdding = true) }

            repository.addItem(name = name, quantity = quantity, unit = unit)

            _uiState.update { it.copy(isAdding = false, showAddedMessage = true) }
            delay(2000)
            _uiState.update { it.copy(showAddedMessage = false) }
        }
    }
}
