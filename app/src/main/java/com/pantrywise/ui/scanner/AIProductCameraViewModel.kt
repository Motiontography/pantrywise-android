package com.pantrywise.ui.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.entity.ProductEntity
import com.pantrywise.data.remote.model.ProductVisionResult
import com.pantrywise.data.repository.ProductRepository
import com.pantrywise.domain.model.LocationType
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

sealed class AIProductCameraEvent {
    data object None : AIProductCameraEvent()
    data class ProductIdentified(val result: ProductVisionResult) : AIProductCameraEvent()
    data class ProductSaved(val product: ProductEntity) : AIProductCameraEvent()
    data class Error(val message: String) : AIProductCameraEvent()
}

data class AIProductCameraUiState(
    val isCapturing: Boolean = true,
    val isProcessing: Boolean = false,
    val capturedImageUri: Uri? = null,
    val capturedImageBytes: ByteArray? = null,
    val identifiedProduct: ProductVisionResult? = null,
    val event: AIProductCameraEvent = AIProductCameraEvent.None,
    val isFlashlightOn: Boolean = false,
    val isEditing: Boolean = false,
    val editedName: String = "",
    val editedBrand: String = "",
    val editedCategory: String = "",
    val editedLocation: LocationType = LocationType.PANTRY
)

@HiltViewModel
class AIProductCameraViewModel @Inject constructor(
    private val openAIService: OpenAIService,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIProductCameraUiState())
    val uiState: StateFlow<AIProductCameraUiState> = _uiState.asStateFlow()

    val isAIConfigured: Boolean
        get() = openAIService.isConfigured

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
                val result = openAIService.analyzeProductImage(imageBytes)

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        identifiedProduct = result,
                        editedName = result.name ?: "",
                        editedBrand = result.brand ?: "",
                        editedCategory = result.category ?: "Other",
                        editedLocation = parseLocation(result.suggestedLocation),
                        event = AIProductCameraEvent.ProductIdentified(result)
                    )
                }
            } catch (e: OpenAIService.OpenAIError.NotConfigured) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        event = AIProductCameraEvent.Error("Please configure your OpenAI API key in Settings")
                    )
                }
            } catch (e: OpenAIService.OpenAIError.InvalidAPIKey) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        event = AIProductCameraEvent.Error("Invalid API key. Please check your settings")
                    )
                }
            } catch (e: OpenAIService.OpenAIError.RateLimited) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        event = AIProductCameraEvent.Error("Rate limited. Please try again later")
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        event = AIProductCameraEvent.Error(e.message ?: "Failed to identify product")
                    )
                }
            }
        }
    }

    private fun parseLocation(location: String?): LocationType {
        return when (location?.lowercase()) {
            "fridge", "refrigerator" -> LocationType.FRIDGE
            "freezer" -> LocationType.FREEZER
            "garage" -> LocationType.GARAGE
            "other" -> LocationType.OTHER
            else -> LocationType.PANTRY
        }
    }

    /**
     * Retake photo
     */
    fun retakePhoto() {
        _uiState.update {
            AIProductCameraUiState(
                isCapturing = true,
                isFlashlightOn = it.isFlashlightOn
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
     * Update edited name
     */
    fun updateName(name: String) {
        _uiState.update { it.copy(editedName = name) }
    }

    /**
     * Update edited brand
     */
    fun updateBrand(brand: String) {
        _uiState.update { it.copy(editedBrand = brand) }
    }

    /**
     * Update edited category
     */
    fun updateCategory(category: String) {
        _uiState.update { it.copy(editedCategory = category) }
    }

    /**
     * Update storage location
     */
    fun updateLocation(location: LocationType) {
        _uiState.update { it.copy(editedLocation = location) }
    }

    /**
     * Toggle flashlight
     */
    fun toggleFlashlight() {
        _uiState.update { it.copy(isFlashlightOn = !it.isFlashlightOn) }
    }

    /**
     * Save the identified product
     */
    fun saveProduct() {
        val state = _uiState.value
        if (state.editedName.isBlank()) {
            _uiState.update {
                it.copy(event = AIProductCameraEvent.Error("Product name is required"))
            }
            return
        }

        viewModelScope.launch {
            try {
                val product = ProductEntity(
                    id = UUID.randomUUID().toString(),
                    barcode = null,
                    name = state.editedName,
                    brand = state.editedBrand.ifBlank { null },
                    category = state.editedCategory,
                    defaultUnit = Unit.EACH,
                    source = SourceType.AI_VISION,
                    userConfirmed = true
                )

                productRepository.insertProduct(product)

                _uiState.update {
                    it.copy(
                        isEditing = false,
                        event = AIProductCameraEvent.ProductSaved(product)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(event = AIProductCameraEvent.Error(e.message ?: "Failed to save product"))
                }
            }
        }
    }

    /**
     * Clear event
     */
    fun clearEvent() {
        _uiState.update { it.copy(event = AIProductCameraEvent.None) }
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
