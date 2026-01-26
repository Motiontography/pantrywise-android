package com.pantrywise.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.repository.ShoppingRepository
import com.pantrywise.services.VoiceInputService
import com.pantrywise.services.VoiceParsedItem
import com.pantrywise.services.VoiceRecognitionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class VoiceInputEvent {
    data object None : VoiceInputEvent()
    data class ItemsAdded(val count: Int) : VoiceInputEvent()
    data class Error(val message: String) : VoiceInputEvent()
}

enum class VoiceInputState {
    IDLE,
    READY,
    LISTENING,
    PROCESSING,
    RESULTS,
    ERROR
}

data class VoiceInputUiState(
    val state: VoiceInputState = VoiceInputState.IDLE,
    val partialText: String = "",
    val recognizedText: String = "",
    val parsedItems: List<VoiceParsedItem> = emptyList(),
    val selectedItems: Set<Int> = emptySet(), // Indices of selected items
    val errorMessage: String? = null,
    val event: VoiceInputEvent = VoiceInputEvent.None,
    val listId: String? = null
)

@HiltViewModel
class VoiceInputViewModel @Inject constructor(
    private val voiceInputService: VoiceInputService,
    private val shoppingRepository: ShoppingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceInputUiState())
    val uiState: StateFlow<VoiceInputUiState> = _uiState.asStateFlow()

    private var recognitionJob: Job? = null

    val isVoiceAvailable: Boolean
        get() = voiceInputService.isAvailable

    fun setListId(listId: String) {
        _uiState.update { it.copy(listId = listId) }
    }

    /**
     * Start voice recognition
     */
    fun startListening() {
        recognitionJob?.cancel()

        _uiState.update {
            it.copy(
                state = VoiceInputState.IDLE,
                partialText = "",
                recognizedText = "",
                parsedItems = emptyList(),
                selectedItems = emptySet(),
                errorMessage = null
            )
        }

        recognitionJob = viewModelScope.launch {
            voiceInputService.startListening().collect { result ->
                when (result) {
                    is VoiceRecognitionResult.Ready -> {
                        _uiState.update { it.copy(state = VoiceInputState.READY) }
                    }
                    is VoiceRecognitionResult.Listening -> {
                        _uiState.update { it.copy(state = VoiceInputState.LISTENING) }
                    }
                    is VoiceRecognitionResult.Processing -> {
                        _uiState.update { it.copy(state = VoiceInputState.PROCESSING) }
                    }
                    is VoiceRecognitionResult.PartialResult -> {
                        _uiState.update {
                            it.copy(
                                state = VoiceInputState.LISTENING,
                                partialText = result.text
                            )
                        }
                    }
                    is VoiceRecognitionResult.Success -> {
                        val allSelected = result.items.indices.toSet()
                        _uiState.update {
                            it.copy(
                                state = VoiceInputState.RESULTS,
                                recognizedText = result.text,
                                parsedItems = result.items,
                                selectedItems = allSelected // Select all by default
                            )
                        }
                    }
                    is VoiceRecognitionResult.Error -> {
                        _uiState.update {
                            it.copy(
                                state = VoiceInputState.ERROR,
                                errorMessage = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Stop voice recognition
     */
    fun stopListening() {
        recognitionJob?.cancel()
        recognitionJob = null
        _uiState.update { it.copy(state = VoiceInputState.IDLE) }
    }

    /**
     * Toggle item selection
     */
    fun toggleItemSelection(index: Int) {
        _uiState.update { state ->
            val newSelection = if (index in state.selectedItems) {
                state.selectedItems - index
            } else {
                state.selectedItems + index
            }
            state.copy(selectedItems = newSelection)
        }
    }

    /**
     * Select all items
     */
    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedItems = state.parsedItems.indices.toSet())
        }
    }

    /**
     * Deselect all items
     */
    fun deselectAll() {
        _uiState.update { it.copy(selectedItems = emptySet()) }
    }

    /**
     * Edit a parsed item
     */
    fun editItem(index: Int, name: String, quantity: Double, unit: String?) {
        _uiState.update { state ->
            val updatedItems = state.parsedItems.toMutableList()
            if (index in updatedItems.indices) {
                updatedItems[index] = updatedItems[index].copy(
                    name = name,
                    quantity = quantity,
                    unit = unit
                )
            }
            state.copy(parsedItems = updatedItems)
        }
    }

    /**
     * Remove a parsed item
     */
    fun removeItem(index: Int) {
        _uiState.update { state ->
            val updatedItems = state.parsedItems.toMutableList()
            if (index in updatedItems.indices) {
                updatedItems.removeAt(index)
            }
            // Update selected indices
            val newSelection = state.selectedItems
                .filter { it != index }
                .map { if (it > index) it - 1 else it }
                .toSet()
            state.copy(
                parsedItems = updatedItems,
                selectedItems = newSelection
            )
        }
    }

    /**
     * Add selected items to shopping list
     */
    fun addSelectedItemsToList() {
        val state = _uiState.value
        val listId = state.listId

        if (listId == null) {
            _uiState.update {
                it.copy(event = VoiceInputEvent.Error("No shopping list selected"))
            }
            return
        }

        if (state.selectedItems.isEmpty()) {
            _uiState.update {
                it.copy(event = VoiceInputEvent.Error("No items selected"))
            }
            return
        }

        viewModelScope.launch {
            try {
                val selectedItems = state.selectedItems.mapNotNull { index ->
                    state.parsedItems.getOrNull(index)
                }

                for (item in selectedItems) {
                    shoppingRepository.addItemToList(
                        listId = listId,
                        name = item.name,
                        quantity = item.quantity,
                        unit = item.unit ?: "each"
                    )
                }

                _uiState.update {
                    it.copy(
                        event = VoiceInputEvent.ItemsAdded(selectedItems.size),
                        state = VoiceInputState.IDLE,
                        parsedItems = emptyList(),
                        selectedItems = emptySet()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(event = VoiceInputEvent.Error(e.message ?: "Failed to add items"))
                }
            }
        }
    }

    /**
     * Parse manual text input (for testing or accessibility)
     */
    fun parseManualInput(text: String) {
        val items = voiceInputService.parseShoppingItems(text)
        val allSelected = items.indices.toSet()
        _uiState.update {
            it.copy(
                state = VoiceInputState.RESULTS,
                recognizedText = text,
                parsedItems = items,
                selectedItems = allSelected
            )
        }
    }

    fun clearEvent() {
        _uiState.update { it.copy(event = VoiceInputEvent.None) }
    }

    fun reset() {
        stopListening()
        _uiState.update {
            VoiceInputUiState(listId = it.listId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        recognitionJob?.cancel()
    }
}
