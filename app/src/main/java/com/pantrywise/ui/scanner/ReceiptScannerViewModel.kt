package com.pantrywise.ui.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.entity.ReceiptEntity
import com.pantrywise.data.local.entity.ReceiptLineItem
import com.pantrywise.data.local.entity.ReceiptStatus
import com.pantrywise.data.repository.ReceiptRepository
import com.pantrywise.services.ReceiptProcessingResult
import com.pantrywise.services.ReceiptProcessingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ReceiptScannerEvent {
    data object None : ReceiptScannerEvent()
    data class ProcessingComplete(val result: ReceiptProcessingResult) : ReceiptScannerEvent()
    data class Error(val message: String) : ReceiptScannerEvent()
    data object ReceiptSaved : ReceiptScannerEvent()
    data object ReceiptDeleted : ReceiptScannerEvent()
}

data class ReceiptScannerUiState(
    val isCapturing: Boolean = true,
    val isProcessing: Boolean = false,
    val capturedImageUri: Uri? = null,
    val processedReceipt: ReceiptEntity? = null,
    val lineItems: List<ReceiptLineItem> = emptyList(),
    val rawText: String? = null,
    val event: ReceiptScannerEvent = ReceiptScannerEvent.None,
    val isEditing: Boolean = false,
    val editedStoreName: String = "",
    val editedTotal: String = "",
    val editedTax: String = "",
    val recentReceipts: List<ReceiptEntity> = emptyList(),
    val showRecentReceipts: Boolean = false
)

@HiltViewModel
class ReceiptScannerViewModel @Inject constructor(
    private val receiptProcessingService: ReceiptProcessingService,
    private val receiptRepository: ReceiptRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiptScannerUiState())
    val uiState: StateFlow<ReceiptScannerUiState> = _uiState.asStateFlow()

    val isAIConfigured: Boolean
        get() = receiptProcessingService.isAIConfigured

    init {
        loadRecentReceipts()
    }

    private fun loadRecentReceipts() {
        viewModelScope.launch {
            val recent = receiptRepository.getRecentReceipts(5)
            _uiState.update { it.copy(recentReceipts = recent) }
        }
    }

    fun onImageCaptured(imageUri: Uri) {
        _uiState.update {
            it.copy(
                isCapturing = false,
                capturedImageUri = imageUri,
                isProcessing = true
            )
        }
        processReceipt(imageUri)
    }

    fun onImageSelected(imageUri: Uri) {
        _uiState.update {
            it.copy(
                isCapturing = false,
                capturedImageUri = imageUri,
                isProcessing = true
            )
        }
        processReceipt(imageUri)
    }

    private fun processReceipt(imageUri: Uri) {
        viewModelScope.launch {
            try {
                val result = receiptProcessingService.processReceipt(imageUri)

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processedReceipt = result.receipt,
                        lineItems = result.lineItems,
                        rawText = result.rawText,
                        editedStoreName = result.receipt.storeName ?: "",
                        editedTotal = result.receipt.total?.toString() ?: "",
                        editedTax = result.receipt.tax?.toString() ?: "",
                        event = ReceiptScannerEvent.ProcessingComplete(result)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        event = ReceiptScannerEvent.Error(e.message ?: "Failed to process receipt")
                    )
                }
            }
        }
    }

    fun retakePhoto() {
        _uiState.update {
            it.copy(
                isCapturing = true,
                capturedImageUri = null,
                processedReceipt = null,
                lineItems = emptyList(),
                rawText = null,
                isEditing = false,
                event = ReceiptScannerEvent.None
            )
        }
    }

    fun toggleEditing() {
        _uiState.update { it.copy(isEditing = !it.isEditing) }
    }

    fun updateStoreName(name: String) {
        _uiState.update { it.copy(editedStoreName = name) }
    }

    fun updateTotal(total: String) {
        _uiState.update { it.copy(editedTotal = total) }
    }

    fun updateTax(tax: String) {
        _uiState.update { it.copy(editedTax = tax) }
    }

    fun updateLineItem(index: Int, updatedItem: ReceiptLineItem) {
        val currentItems = _uiState.value.lineItems.toMutableList()
        if (index in currentItems.indices) {
            currentItems[index] = updatedItem
            _uiState.update { it.copy(lineItems = currentItems) }
        }
    }

    fun removeLineItem(index: Int) {
        val currentItems = _uiState.value.lineItems.toMutableList()
        if (index in currentItems.indices) {
            currentItems.removeAt(index)
            _uiState.update { it.copy(lineItems = currentItems) }
        }
    }

    fun addLineItem(item: ReceiptLineItem) {
        val currentItems = _uiState.value.lineItems.toMutableList()
        currentItems.add(item)
        _uiState.update { it.copy(lineItems = currentItems) }
    }

    fun saveChanges() {
        val receipt = _uiState.value.processedReceipt ?: return

        viewModelScope.launch {
            try {
                val total = _uiState.value.editedTotal.toDoubleOrNull()
                val tax = _uiState.value.editedTax.toDoubleOrNull()

                receiptProcessingService.updateReceiptManually(
                    receiptId = receipt.id,
                    storeName = _uiState.value.editedStoreName.ifBlank { null },
                    total = total,
                    tax = tax,
                    lineItems = _uiState.value.lineItems
                )

                val updatedReceipt = receipt.copy(
                    storeName = _uiState.value.editedStoreName.ifBlank { null },
                    total = total,
                    tax = tax,
                    isVerified = true,
                    status = ReceiptStatus.COMPLETED
                )

                _uiState.update {
                    it.copy(
                        processedReceipt = updatedReceipt,
                        isEditing = false,
                        event = ReceiptScannerEvent.ReceiptSaved
                    )
                }

                loadRecentReceipts()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(event = ReceiptScannerEvent.Error(e.message ?: "Failed to save changes"))
                }
            }
        }
    }

    fun deleteReceipt() {
        val receipt = _uiState.value.processedReceipt ?: return

        viewModelScope.launch {
            try {
                receiptProcessingService.deleteReceipt(receipt.id)

                _uiState.update {
                    it.copy(
                        isCapturing = true,
                        capturedImageUri = null,
                        processedReceipt = null,
                        lineItems = emptyList(),
                        rawText = null,
                        isEditing = false,
                        event = ReceiptScannerEvent.ReceiptDeleted
                    )
                }

                loadRecentReceipts()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(event = ReceiptScannerEvent.Error(e.message ?: "Failed to delete receipt"))
                }
            }
        }
    }

    fun toggleRecentReceipts() {
        _uiState.update { it.copy(showRecentReceipts = !it.showRecentReceipts) }
    }

    fun loadReceipt(receipt: ReceiptEntity) {
        viewModelScope.launch {
            val lineItems = receiptProcessingService.getLineItemsFromJson(receipt.itemsJson)

            _uiState.update {
                it.copy(
                    isCapturing = false,
                    capturedImageUri = Uri.parse(receipt.imageUri),
                    processedReceipt = receipt,
                    lineItems = lineItems,
                    rawText = receipt.rawText,
                    editedStoreName = receipt.storeName ?: "",
                    editedTotal = receipt.total?.toString() ?: "",
                    editedTax = receipt.tax?.toString() ?: "",
                    showRecentReceipts = false
                )
            }
        }
    }

    fun clearEvent() {
        _uiState.update { it.copy(event = ReceiptScannerEvent.None) }
    }
}
