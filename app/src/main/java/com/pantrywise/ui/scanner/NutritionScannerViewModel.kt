package com.pantrywise.ui.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pantrywise.data.local.entity.NutritionEntity
import com.pantrywise.data.repository.NutritionRepository
import com.pantrywise.services.LabelFormat
import com.pantrywise.services.NutritionParser
import com.pantrywise.services.ParsedNutrition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class NutritionScannerEvent {
    data object None : NutritionScannerEvent()
    data class NutritionParsed(val nutrition: ParsedNutrition) : NutritionScannerEvent()
    data class NutritionSaved(val entity: NutritionEntity) : NutritionScannerEvent()
    data class Error(val message: String) : NutritionScannerEvent()
}

data class NutritionScannerUiState(
    val isCapturing: Boolean = true,
    val isProcessing: Boolean = false,
    val capturedImageUri: Uri? = null,
    val capturedImageBytes: ByteArray? = null,
    val parsedNutrition: ParsedNutrition? = null,
    val event: NutritionScannerEvent = NutritionScannerEvent.None,
    val isFlashlightOn: Boolean = false,
    val isEditing: Boolean = false,
    val ocrRawText: String = "",

    // Editable fields
    val editedServingSize: String = "",
    val editedServingUnit: String = "",
    val editedServingsPerContainer: String = "",
    val editedCalories: String = "",
    val editedTotalFat: String = "",
    val editedSaturatedFat: String = "",
    val editedTransFat: String = "",
    val editedCholesterol: String = "",
    val editedSodium: String = "",
    val editedTotalCarbs: String = "",
    val editedFiber: String = "",
    val editedSugars: String = "",
    val editedAddedSugars: String = "",
    val editedProtein: String = "",
    val editedVitaminD: String = "",
    val editedCalcium: String = "",
    val editedIron: String = "",
    val editedPotassium: String = "",

    // Product association
    val productId: String? = null,
    val productName: String? = null
)

@HiltViewModel
class NutritionScannerViewModel @Inject constructor(
    private val nutritionParser: NutritionParser,
    private val nutritionRepository: NutritionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NutritionScannerUiState())
    val uiState: StateFlow<NutritionScannerUiState> = _uiState.asStateFlow()

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun setProduct(productId: String, productName: String?) {
        _uiState.update {
            it.copy(productId = productId, productName = productName)
        }
    }

    /**
     * Process captured image with ML Kit OCR and parse nutrition
     */
    fun onImageCaptured(imageUri: Uri, imageBytes: ByteArray, context: android.content.Context) {
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
                // Run OCR
                val inputImage = InputImage.fromFilePath(context, imageUri)
                val result = withContext(Dispatchers.Default) {
                    textRecognizer.process(inputImage).await()
                }

                val ocrText = result.text

                // Parse nutrition from OCR text
                val parsed = nutritionParser.parseNutritionLabel(ocrText)
                val validated = nutritionParser.validateNutrition(parsed)

                _uiState.update { state ->
                    state.copy(
                        isProcessing = false,
                        parsedNutrition = validated,
                        ocrRawText = ocrText,
                        editedServingSize = validated.servingSize?.toString() ?: "",
                        editedServingUnit = validated.servingSizeUnit ?: "",
                        editedServingsPerContainer = validated.servingsPerContainer?.toString() ?: "",
                        editedCalories = validated.calories?.toInt()?.toString() ?: "",
                        editedTotalFat = validated.totalFat?.toString() ?: "",
                        editedSaturatedFat = validated.saturatedFat?.toString() ?: "",
                        editedTransFat = validated.transFat?.toString() ?: "",
                        editedCholesterol = validated.cholesterol?.toString() ?: "",
                        editedSodium = validated.sodium?.toInt()?.toString() ?: "",
                        editedTotalCarbs = validated.totalCarbohydrates?.toString() ?: "",
                        editedFiber = validated.dietaryFiber?.toString() ?: "",
                        editedSugars = validated.totalSugars?.toString() ?: "",
                        editedAddedSugars = validated.addedSugars?.toString() ?: "",
                        editedProtein = validated.protein?.toString() ?: "",
                        editedVitaminD = validated.vitaminD?.toString() ?: "",
                        editedCalcium = validated.calcium?.toString() ?: "",
                        editedIron = validated.iron?.toString() ?: "",
                        editedPotassium = validated.potassium?.toString() ?: "",
                        event = NutritionScannerEvent.NutritionParsed(validated)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        event = NutritionScannerEvent.Error(e.message ?: "Failed to process nutrition label")
                    )
                }
            }
        }
    }

    fun retakePhoto() {
        _uiState.update {
            NutritionScannerUiState(
                isCapturing = true,
                isFlashlightOn = it.isFlashlightOn,
                productId = it.productId,
                productName = it.productName
            )
        }
    }

    fun toggleEditing() {
        _uiState.update { it.copy(isEditing = !it.isEditing) }
    }

    fun toggleFlashlight() {
        _uiState.update { it.copy(isFlashlightOn = !it.isFlashlightOn) }
    }

    // Edit field handlers
    fun updateServingSize(value: String) = _uiState.update { it.copy(editedServingSize = value) }
    fun updateServingUnit(value: String) = _uiState.update { it.copy(editedServingUnit = value) }
    fun updateServingsPerContainer(value: String) = _uiState.update { it.copy(editedServingsPerContainer = value) }
    fun updateCalories(value: String) = _uiState.update { it.copy(editedCalories = value) }
    fun updateTotalFat(value: String) = _uiState.update { it.copy(editedTotalFat = value) }
    fun updateSaturatedFat(value: String) = _uiState.update { it.copy(editedSaturatedFat = value) }
    fun updateTransFat(value: String) = _uiState.update { it.copy(editedTransFat = value) }
    fun updateCholesterol(value: String) = _uiState.update { it.copy(editedCholesterol = value) }
    fun updateSodium(value: String) = _uiState.update { it.copy(editedSodium = value) }
    fun updateTotalCarbs(value: String) = _uiState.update { it.copy(editedTotalCarbs = value) }
    fun updateFiber(value: String) = _uiState.update { it.copy(editedFiber = value) }
    fun updateSugars(value: String) = _uiState.update { it.copy(editedSugars = value) }
    fun updateAddedSugars(value: String) = _uiState.update { it.copy(editedAddedSugars = value) }
    fun updateProtein(value: String) = _uiState.update { it.copy(editedProtein = value) }
    fun updateVitaminD(value: String) = _uiState.update { it.copy(editedVitaminD = value) }
    fun updateCalcium(value: String) = _uiState.update { it.copy(editedCalcium = value) }
    fun updateIron(value: String) = _uiState.update { it.copy(editedIron = value) }
    fun updatePotassium(value: String) = _uiState.update { it.copy(editedPotassium = value) }

    /**
     * Save the parsed/edited nutrition data
     */
    fun saveNutrition() {
        val state = _uiState.value
        val productId = state.productId

        if (productId == null) {
            _uiState.update {
                it.copy(event = NutritionScannerEvent.Error("No product selected"))
            }
            return
        }

        viewModelScope.launch {
            try {
                val entity = NutritionEntity(
                    productId = productId,
                    servingSize = state.editedServingSize.toDoubleOrNull(),
                    servingSizeUnit = state.editedServingUnit.ifBlank { null },
                    servingsPerContainer = state.editedServingsPerContainer.toDoubleOrNull(),
                    calories = state.editedCalories.toDoubleOrNull(),
                    totalFat = state.editedTotalFat.toDoubleOrNull(),
                    saturatedFat = state.editedSaturatedFat.toDoubleOrNull(),
                    transFat = state.editedTransFat.toDoubleOrNull(),
                    cholesterol = state.editedCholesterol.toDoubleOrNull(),
                    sodium = state.editedSodium.toDoubleOrNull(),
                    totalCarbohydrates = state.editedTotalCarbs.toDoubleOrNull(),
                    dietaryFiber = state.editedFiber.toDoubleOrNull(),
                    totalSugars = state.editedSugars.toDoubleOrNull(),
                    addedSugars = state.editedAddedSugars.toDoubleOrNull(),
                    protein = state.editedProtein.toDoubleOrNull(),
                    vitaminD = state.editedVitaminD.toDoubleOrNull(),
                    calcium = state.editedCalcium.toDoubleOrNull(),
                    iron = state.editedIron.toDoubleOrNull(),
                    potassium = state.editedPotassium.toDoubleOrNull(),
                    labelFormat = state.parsedNutrition?.labelFormat?.name ?: LabelFormat.UNKNOWN.name,
                    confidence = state.parsedNutrition?.confidence ?: 0f,
                    sourceApi = "NUTRITION_SCANNER",
                    isUserEdited = state.isEditing
                )

                nutritionRepository.saveNutrition(entity)

                _uiState.update {
                    it.copy(
                        isEditing = false,
                        event = NutritionScannerEvent.NutritionSaved(entity)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(event = NutritionScannerEvent.Error(e.message ?: "Failed to save nutrition"))
                }
            }
        }
    }

    fun clearEvent() {
        _uiState.update { it.copy(event = NutritionScannerEvent.None) }
    }

    override fun onCleared() {
        super.onCleared()
        textRecognizer.close()
    }

    companion object {
        val SERVING_UNITS = listOf("g", "oz", "ml", "cup", "tbsp", "tsp", "piece", "slice")
    }
}
