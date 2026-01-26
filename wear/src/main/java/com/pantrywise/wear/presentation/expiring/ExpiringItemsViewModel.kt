package com.pantrywise.wear.presentation.expiring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.wear.data.WearDataRepository
import com.pantrywise.wear.data.WearExpiringItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExpiringItemsUiState(
    val items: List<WearExpiringItem> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class ExpiringItemsViewModel @Inject constructor(
    private val repository: WearDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpiringItemsUiState())
    val uiState: StateFlow<ExpiringItemsUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            repository.syncData.collect { data ->
                _uiState.update {
                    it.copy(
                        items = data.expiringItems.sortedBy { item -> item.daysUntilExpiration }
                    )
                }
            }
        }
    }
}
