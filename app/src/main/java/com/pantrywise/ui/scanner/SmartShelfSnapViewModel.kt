package com.pantrywise.ui.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.entity.ProductEntity
import com.pantrywise.data.local.entity.PriceRecordEntity
import com.pantrywise.data.remote.model.SmartShelfSnapResult
import com.pantrywise.data.repository.PriceRepository
import com.pantrywise.data.repository.ProductRepository
import com.pantrywise.data.repository.StoreRepository
import com.pantrywise.domain.model.SourceType
import com.pantrywise.domain.model.Unit
import com.pantrywise.services.OpenAIService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class SmartShelfSnapEvent {
    data object None : SmartShelfSnapEvent()
    data class ProductIdentified(val result: SmartShelfSnapResult) : SmartShelfSnapEvent()
    data class ProductSaved(val product: ProductEntity, val priceRecord: PriceRecordEntity?) : SmartShelfSnapEvent()
    data class Error(val message: String) : SmartShelfSnapEvent()
}

data class SmartShelfSnapUiState(
    val isCapturing: Boolean = true,
    val isProcessing: Boolean = false,
    val capturedImageUri: Uri? = null,
    val capturedImageBytes: ByteArray? = null,
    val identifiedResult: SmartShelfSnapResult? = null,
    val event: SmartShelfSnapEvent = SmartShelfSnapEvent.None,
    val isFlashlightOn: Boolean = false,
    val isEditing: Boolean = false,
    val editedName: String = "",
    val editedBrand: String = "",
    val editedCategory: String = "",
    val editedPrice: String = "",
    val editedPackageSize: String = "",
    val selectedStoreId: String? = null,
    val availableStores: List<StoreSelection> = emptyList()
)

data class StoreSelection(
    val id: String,
    val name: String
)

@HiltViewModel
class SmartShelfSnapViewModel @Inject constructor(
    private val openAIService: OpenAIService,
    private val productRepository: ProductRepository,
    private val priceRepository: PriceRepository,
    private val storeRepository: StoreRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmartShelfSnapUiState())
    val uiState: StateFlow<SmartShelfSnapUiState> = _uiState.asStateFlow()

    val isAIConfigured: Boolean
        get() = openAIService.isConfigured

    init {
        loadStores()
    }

    private fun loadStores() {
        viewModelScope.launch {
            val stores = storeRepository.getAllStoresSnapshot()
            _uiState.update { state ->
                state.copy(
                    availableStores = stores.map { StoreSelection(it.id, it.name) },
                    selectedStoreId = stores.firstOrNull()?.id
                )
            }
        }
    }

    /**
     * Process captured image with AI
     */
    fun onImageCaptured(imageUri: Uri, imageBytes: ByteArray) {
        _uiState.update {
            it.copy(
                isCapturing = false,
                isProcessing = true,
                capturedImageUri = imageUri,
                capturedImageBytes = imageBytes
            )
        }

        viewModelScope.launch {
            try {
                val result = openAIService.analyzeShelfSnap(imageBytes)

                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        identifiedResult = result,
                        editedName = result.name,
                        editedBrand = result.brand ?: "",
                        editedCategory = result.category ?: "Other",
                        editedPrice = result.price?.let { "%.2f".format(it) } ?: "",
                        editedPackageSize = result.packageSize ?: "",
                        event = SmartShelfSnapEvent.ProductIdentified(result)
                    )
                }
            } catch (e: OpenAIService.OpenAIError.NotConfigured) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        event = SmartShelfSnapEvent.Error("Please configure your OpenAI API key in Settings")
                    )
                }
            } catch (e: OpenAIService.OpenAIError.InvalidAPIKey) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        event = SmartShelfSnapEvent.Error("Invalid API key. Please check your settings")
                    )
                }
            } catch (e: OpenAIService.OpenAIError.RateLimited) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        event = SmartShelfSnapEvent.Error("Rate limited. Please try again later")
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        event = SmartShelfSnapEvent.Error(e.message ?: "Failed to analyze shelf snap")
                    )
                }
            }
        }
    }

    /**
     * Retake photo
     */
    fun retakePhoto() {
        _uiState.update {
            SmartShelfSnapUiState(
                isCapturing = true,
                isFlashlightOn = it.isFlashlightOn,
                availableStores = it.availableStores,
                selectedStoreId = it.selectedStoreId
            )
        }
    }

    /**
     * Toggle edit mode
     */
    fun toggleEditing() {
        _uiState.update { it.copy(isEditing = !it.isEditing) }
    }

    /**
     * Update edited fields
     */
    fun updateName(name: String) = _uiState.update { it.copy(editedName = name) }
    fun updateBrand(brand: String) = _uiState.update { it.copy(editedBrand = brand) }
    fun updateCategory(category: String) = _uiState.update { it.copy(editedCategory = category) }
    fun updatePrice(price: String) = _uiState.update { it.copy(editedPrice = price) }
    fun updatePackageSize(size: String) = _uiState.update { it.copy(editedPackageSize = size) }
    fun selectStore(storeId: String) = _uiState.update { it.copy(selectedStoreId = storeId) }

    /**
     * Toggle flashlight
     */
    fun toggleFlashlight() {
        _uiState.update { it.copy(isFlashlightOn = !it.isFlashlightOn) }
    }

    /**
     * Save the identified product and price
     */
    fun saveProduct() {
        val state = _uiState.value
        if (state.editedName.isBlank()) {
            _uiState.update {
                it.copy(event = SmartShelfSnapEvent.Error("Product name is required"))
            }
            return
        }

        viewModelScope.launch {
            try {
                // Create product
                val product = ProductEntity(
                    id = UUID.randomUUID().toString(),
                    barcode = null,
                    name = state.editedName,
                    brand = state.editedBrand.ifBlank { null },
                    category = state.editedCategory,
                    defaultUnit = Unit.EACH,
                    source = SourceType.SMART_SHELF_SNAP,
                    userConfirmed = true
                )
                productRepository.insertProduct(product)

                // Create price record if price was captured
                var priceRecord: PriceRecordEntity? = null
                val price = state.editedPrice.toDoubleOrNull()
                if (price != null && state.selectedStoreId != null) {
                    val packageInfo = state.editedPackageSize.ifBlank { null }
                    val notes = buildString {
                        append("Captured via Smart Shelf Snap")
                        if (packageInfo != null) {
                            append(" | Size: $packageInfo")
                        }
                        state.identifiedResult?.originalPrice?.let { original ->
                            append(" | Was: $${String.format("%.2f", original)}")
                        }
                    }

                    priceRecord = priceRepository.recordPrice(
                        productId = product.id,
                        storeId = state.selectedStoreId,
                        price = price,
                        unitType = state.identifiedResult?.pricePerUnitLabel,
                        isOnSale = state.identifiedResult?.isOnSale ?: false,
                        notes = notes
                    )
                }

                _uiState.update {
                    it.copy(
                        isEditing = false,
                        event = SmartShelfSnapEvent.ProductSaved(product, priceRecord)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(event = SmartShelfSnapEvent.Error(e.message ?: "Failed to save product"))
                }
            }
        }
    }

    /**
     * Clear event
     */
    fun clearEvent() {
        _uiState.update { it.copy(event = SmartShelfSnapEvent.None) }
    }

    companion object {
        val CATEGORIES = listOf(
            "Produce",
            "Dairy",
            "Meat",
            "Seafood",
            "Bakery",
            "Frozen",
            "Canned Goods",
            "Beverages",
            "Snacks",
            "Condiments",
            "Spices",
            "Pasta & Grains",
            "Breakfast",
            "Health & Beauty",
            "Household",
            "Other"
        )
    }
}
