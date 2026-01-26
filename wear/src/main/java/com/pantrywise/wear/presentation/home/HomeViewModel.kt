package com.pantrywise.wear.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.wear.data.WearDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class HomeUiState(
    val uncheckedCount: Int = 0,
    val expiringCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastSyncTime: Long? = null
) {
    val lastSyncText: String
        get() = lastSyncTime?.let {
            val format = SimpleDateFormat("h:mm a", Locale.getDefault())
            "Last synced: ${format.format(Date(it))}"
        } ?: "Tap to sync"
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WearDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeSyncData()
        observeSyncStatus()
    }

    private fun observeSyncData() {
        viewModelScope.launch {
            repository.syncData.collect { data ->
                _uiState.update {
                    it.copy(
                        uncheckedCount = data.uncheckedShoppingCount,
                        expiringCount = data.expiringItemCount,
                        lastSyncTime = data.lastSyncDate
                    )
                }
            }
        }
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            repository.isSyncing.collect { syncing ->
                _uiState.update { it.copy(isSyncing = syncing) }
            }
        }
    }

    fun requestSync() {
        repository.requestSync()
    }
}
