package com.pantrywise.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.entity.ProductEntity
import com.pantrywise.data.repository.InventoryRepository
import com.pantrywise.data.repository.ProductRepository
import com.pantrywise.domain.model.LocationType
import com.pantrywise.domain.model.SourceType
import com.pantrywise.domain.model.Unit
import com.pantrywise.domain.usecase.ScanProductUseCase
import com.pantrywise.domain.usecase.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScannerUiEvent {
    data object None : ScannerUiEvent()
    data class ProductFound(val product: ProductEntity, val isNew: Boolean) : ScannerUiEvent()
    data class NeedsConfirmation(val product: ProductEntity) : ScannerUiEvent()
    data class NotFound(val barcode: String) : ScannerUiEvent()
    data class PendingLookup(val barcode: String) : ScannerUiEvent()
    data class Error(val message: String) : ScannerUiEvent()
    data object ProductAdded : ScannerUiEvent()
}

data class ScannerUiState(
    val isScanning: Boolean = true,
    val lastScannedBarcode: String? = null,
    val event: ScannerUiEvent = ScannerUiEvent.None,
    val showManualEntry: Boolean = false,
    val showConfirmation: Boolean = false,
    val pendingProduct: ProductEntity? = null
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scanProductUseCase: ScanProductUseCase,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun onBarcodeScanned(barcode: String) {
        // Prevent duplicate scans
        if (barcode == _uiState.value.lastScannedBarcode) return

        _uiState.update {
            it.copy(
                isScanning = false,
                lastScannedBarcode = barcode
            )
        }

        viewModelScope.launch {
            when (val result = scanProductUseCase(barcode)) {
                is ScanResult.Success -> {
                    _uiState.update {
                        it.copy(
                            event = ScannerUiEvent.ProductFound(result.product, result.isNew),
                            pendingProduct = result.product
                        )
                    }
                }

                is ScanResult.NeedsConfirmation -> {
                    _uiState.update {
                        it.copy(
                            event = ScannerUiEvent.NeedsConfirmation(result.product),
                            showConfirmation = true,
                            pendingProduct = result.product
                        )
                    }
                }

                is ScanResult.NotFound -> {
                    _uiState.update {
                        it.copy(
                            event = ScannerUiEvent.NotFound(result.barcode),
                            showManualEntry = true
                        )
                    }
                }

                is ScanResult.PendingLookup -> {
                    _uiState.update {
                        it.copy(event = ScannerUiEvent.PendingLookup(result.lookup.barcode))
                    }
                }

                is ScanResult.Error -> {
                    _uiState.update {
                        it.copy(event = ScannerUiEvent.Error(result.message))
                    }
                }
            }
        }
    }

    fun confirmProduct(product: ProductEntity) {
        viewModelScope.launch {
            val confirmed = scanProductUseCase.confirmProduct(product)
            _uiState.update {
                it.copy(
                    showConfirmation = false,
                    event = ScannerUiEvent.ProductFound(confirmed, isNew = true),
                    pendingProduct = confirmed
                )
            }
        }
    }

    fun createManualProduct(
        barcode: String,
        name: String,
        brand: String?,
        category: String,
        defaultUnit: Unit
    ) {
        viewModelScope.launch {
            val product = ProductEntity(
                barcode = barcode,
                name = name,
                brand = brand,
                category = category,
                defaultUnit = defaultUnit,
                source = SourceType.USER_MANUAL,
                userConfirmed = true
            )
            val created = scanProductUseCase.createManualProduct(product)
            _uiState.update {
                it.copy(
                    showManualEntry = false,
                    event = ScannerUiEvent.ProductFound(created, isNew = true),
                    pendingProduct = created
                )
            }
        }
    }

    fun addToInventory(
        product: ProductEntity,
        quantity: Double,
        unit: Unit,
        location: LocationType,
        expirationDate: Long? = null
    ) {
        viewModelScope.launch {
            val item = com.pantrywise.data.local.entity.InventoryItemEntity(
                productId = product.id,
                quantityOnHand = quantity,
                unit = unit,
                location = location,
                expirationDate = expirationDate
            )
            inventoryRepository.addInventoryItem(item, SourceType.BARCODE_SCAN)
            _uiState.update {
                it.copy(event = ScannerUiEvent.ProductAdded)
            }
        }
    }

    fun resumeScanning() {
        _uiState.update {
            it.copy(
                isScanning = true,
                lastScannedBarcode = null,
                event = ScannerUiEvent.None,
                showManualEntry = false,
                showConfirmation = false,
                pendingProduct = null
            )
        }
    }

    fun dismissManualEntry() {
        _uiState.update {
            it.copy(showManualEntry = false)
        }
        resumeScanning()
    }

    fun dismissConfirmation() {
        _uiState.update {
            it.copy(showConfirmation = false)
        }
        resumeScanning()
    }

    fun clearEvent() {
        _uiState.update { it.copy(event = ScannerUiEvent.None) }
    }
}
