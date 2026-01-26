package com.pantrywise.ui.nfc

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.nfc.NfcError
import com.pantrywise.nfc.NfcManager
import com.pantrywise.nfc.NfcResult
import com.pantrywise.nfc.NfcScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NfcUiState(
    val isNfcAvailable: Boolean = true,
    val isNfcEnabled: Boolean = true,
    val isScanning: Boolean = false,
    val isWriting: Boolean = false,
    val lastScanResult: NfcScanResult? = null,
    val error: NfcError? = null,
    val writeSuccess: Boolean = false
)

@HiltViewModel
class NfcViewModel @Inject constructor(
    private val nfcManager: NfcManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(NfcUiState())
    val uiState: StateFlow<NfcUiState> = _uiState.asStateFlow()

    init {
        // Update state with NFC availability
        _uiState.update {
            it.copy(
                isNfcAvailable = nfcManager.isNfcAvailable,
                isNfcEnabled = nfcManager.isNfcEnabled
            )
        }

        // Observe NFC manager state
        viewModelScope.launch {
            nfcManager.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }

        viewModelScope.launch {
            nfcManager.isWriting.collect { writing ->
                _uiState.update { it.copy(isWriting = writing) }
            }
        }

        viewModelScope.launch {
            nfcManager.lastError.collect { error ->
                _uiState.update { it.copy(error = error) }
            }
        }
    }

    /**
     * Refresh NFC state (e.g., when returning to the screen)
     */
    fun refreshNfcState() {
        _uiState.update {
            it.copy(
                isNfcAvailable = nfcManager.isNfcAvailable,
                isNfcEnabled = nfcManager.isNfcEnabled
            )
        }
    }

    /**
     * Start scanning for NFC tags
     */
    fun startScanning() {
        _uiState.update {
            it.copy(
                error = null,
                lastScanResult = null,
                writeSuccess = false
            )
        }
        nfcManager.startScanning()
    }

    /**
     * Stop scanning
     */
    fun stopScanning() {
        nfcManager.stopScanning()
    }

    /**
     * Start write mode for a product
     */
    fun startWriteMode(productId: String, productName: String?, barcode: String?) {
        _uiState.update {
            it.copy(
                error = null,
                writeSuccess = false
            )
        }
        nfcManager.startWriteMode(productId, productName, barcode)
    }

    /**
     * Stop write mode
     */
    fun stopWriteMode() {
        nfcManager.stopWriteMode()
    }

    /**
     * Handle an NFC intent from the activity
     */
    fun handleNfcIntent(intent: Intent) {
        viewModelScope.launch {
            when (val result = nfcManager.handleNfcIntent(intent)) {
                is NfcResult.Success -> {
                    _uiState.update {
                        it.copy(
                            lastScanResult = result.data,
                            error = null,
                            writeSuccess = nfcManager.isWriting.value.not() && it.isWriting,
                            isWriting = false
                        )
                    }
                }
                is NfcResult.Error -> {
                    _uiState.update {
                        it.copy(error = result.error)
                    }
                }
            }
        }
    }

    /**
     * Clear the error
     */
    fun clearError() {
        nfcManager.clearError()
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clear the last scan result
     */
    fun clearLastScanResult() {
        nfcManager.clearLastScanResult()
        _uiState.update { it.copy(lastScanResult = null) }
    }

    /**
     * Reset write success flag
     */
    fun resetWriteSuccess() {
        _uiState.update { it.copy(writeSuccess = false) }
    }
}
