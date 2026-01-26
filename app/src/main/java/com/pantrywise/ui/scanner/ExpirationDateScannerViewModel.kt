package com.pantrywise.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.services.ExpirationDateParser
import com.pantrywise.services.ParsedExpirationDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

sealed class ExpirationScannerEvent {
    data object None : ExpirationScannerEvent()
    data class DateConfirmed(val date: Date) : ExpirationScannerEvent()
    data class Error(val message: String) : ExpirationScannerEvent()
}

data class ExpirationScannerUiState(
    val isScanning: Boolean = true,
    val detectedDates: List<ParsedExpirationDate> = emptyList(),
    val selectedDate: ParsedExpirationDate? = null,
    val currentOcrText: String = "",
    val showDatePicker: Boolean = false,
    val manualDate: Date? = null,
    val event: ExpirationScannerEvent = ExpirationScannerEvent.None,
    val isFlashlightOn: Boolean = false,
    val scanRegionHint: String = "Point camera at expiration date"
)

@HiltViewModel
class ExpirationDateScannerViewModel @Inject constructor(
    private val expirationDateParser: ExpirationDateParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpirationScannerUiState())
    val uiState: StateFlow<ExpirationScannerUiState> = _uiState.asStateFlow()

    private var debounceJob: Job? = null
    private val recentDetections = mutableListOf<ParsedExpirationDate>()
    private val dateConfidenceMap = mutableMapOf<Long, Float>()

    /**
     * Process OCR text from camera frame
     */
    fun processOcrText(text: String) {
        if (text.isBlank()) return

        // Debounce rapid updates
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(100)  // 100ms debounce

            _uiState.update { it.copy(currentOcrText = text) }

            // Parse dates from text
            val detectedDates = expirationDateParser.findAllPotentialDates(text)

            if (detectedDates.isNotEmpty()) {
                // Update confidence for detected dates
                for (detected in detectedDates) {
                    val key = detected.date.time / (24 * 60 * 60 * 1000)  // Round to day
                    val currentConfidence = dateConfidenceMap.getOrDefault(key, 0f)
                    // Increase confidence each time we see the same date
                    dateConfidenceMap[key] = minOf(1f, currentConfidence + 0.15f)
                }

                // Build list with accumulated confidence
                val datesWithAccumulatedConfidence = detectedDates.map { date ->
                    val key = date.date.time / (24 * 60 * 60 * 1000)
                    date.copy(confidence = dateConfidenceMap.getOrDefault(key, date.confidence))
                }.sortedByDescending { it.confidence }

                // Add to recent detections
                recentDetections.addAll(datesWithAccumulatedConfidence)
                if (recentDetections.size > 20) {
                    recentDetections.removeAt(0)
                }

                // Update UI state
                val bestCandidate = datesWithAccumulatedConfidence.firstOrNull()

                _uiState.update { state ->
                    state.copy(
                        detectedDates = datesWithAccumulatedConfidence.take(5),
                        selectedDate = bestCandidate?.takeIf { it.confidence >= 0.6f },
                        scanRegionHint = if (bestCandidate != null && bestCandidate.confidence >= 0.5f) {
                            "Date detected: ${expirationDateParser.formatDate(bestCandidate.date)}"
                        } else {
                            "Looking for date..."
                        }
                    )
                }
            }
        }
    }

    /**
     * Select a specific detected date
     */
    fun selectDate(date: ParsedExpirationDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    /**
     * Confirm the selected date
     */
    fun confirmSelectedDate() {
        val date = _uiState.value.selectedDate?.date ?: _uiState.value.manualDate ?: return

        _uiState.update {
            it.copy(
                isScanning = false,
                event = ExpirationScannerEvent.DateConfirmed(date)
            )
        }
    }

    /**
     * Open manual date picker
     */
    fun openDatePicker() {
        _uiState.update { it.copy(showDatePicker = true) }
    }

    /**
     * Set manual date
     */
    fun setManualDate(date: Date) {
        _uiState.update {
            it.copy(
                showDatePicker = false,
                manualDate = date,
                selectedDate = ParsedExpirationDate(
                    date = date,
                    originalText = "Manual entry",
                    confidence = 1f,
                    formatUsed = "manual"
                )
            )
        }
    }

    /**
     * Dismiss date picker
     */
    fun dismissDatePicker() {
        _uiState.update { it.copy(showDatePicker = false) }
    }

    /**
     * Toggle flashlight
     */
    fun toggleFlashlight() {
        _uiState.update { it.copy(isFlashlightOn = !it.isFlashlightOn) }
    }

    /**
     * Reset scanner to try again
     */
    fun resetScanner() {
        debounceJob?.cancel()
        recentDetections.clear()
        dateConfidenceMap.clear()

        _uiState.update {
            ExpirationScannerUiState(
                isScanning = true,
                scanRegionHint = "Point camera at expiration date"
            )
        }
    }

    /**
     * Get formatted date string
     */
    fun formatDate(date: Date): String {
        return expirationDateParser.formatDate(date)
    }

    /**
     * Get expiration status text
     */
    fun getExpirationStatus(date: Date): String {
        return expirationDateParser.getExpirationStatusText(date)
    }

    /**
     * Get days until expiration
     */
    fun getDaysUntil(date: Date): Int {
        return expirationDateParser.getDaysUntilExpiration(date)
    }

    /**
     * Clear event
     */
    fun clearEvent() {
        _uiState.update { it.copy(event = ExpirationScannerEvent.None) }
    }
}
