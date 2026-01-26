package com.pantrywise.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.entity.ProductEntity
import com.pantrywise.data.repository.ProductRepository
import com.pantrywise.data.repository.ShoppingRepository
import com.pantrywise.domain.model.SourceType
import com.pantrywise.domain.model.Unit
import com.pantrywise.domain.usecase.ParseIngredientsUseCase
import com.pantrywise.domain.usecase.ParsedIngredient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeUiState(
    val isLoading: Boolean = false,
    val parsedIngredients: List<ParsedIngredient> = emptyList(),
    val hasParsedIngredients: Boolean = false,
    val matchedCount: Int = 0,
    val unmatchedCount: Int = 0,
    val addedToListCount: Int = 0,
    val error: String? = null
)

@HiltViewModel
class RecipeViewModel @Inject constructor(
    private val parseIngredientsUseCase: ParseIngredientsUseCase,
    private val shoppingRepository: ShoppingRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeUiState())
    val uiState: StateFlow<RecipeUiState> = _uiState.asStateFlow()

    fun parseIngredients(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = parseIngredientsUseCase(text)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    parsedIngredients = result.parsedIngredients,
                    hasParsedIngredients = true,
                    matchedCount = result.matchedCount,
                    unmatchedCount = result.unmatchedCount
                )
            }
        }
    }

    fun addToShoppingList(ingredient: ParsedIngredient) {
        viewModelScope.launch {
            val activeList = shoppingRepository.getActiveShoppingList()
                ?: shoppingRepository.createShoppingList("Shopping List")

            val product = ingredient.matchedProduct ?: createProductFromIngredient(ingredient)

            shoppingRepository.addItemToList(
                listId = activeList.id,
                productId = product.id,
                quantity = ingredient.quantity ?: 1.0,
                unit = ingredient.unit ?: Unit.EACH,
                reason = "From recipe",
                suggestedBy = SourceType.RECIPE_PARSE
            )

            // Update state
            val updatedIngredients = _uiState.value.parsedIngredients.toMutableList()
            val index = updatedIngredients.indexOfFirst { it.originalText == ingredient.originalText }
            if (index >= 0) {
                updatedIngredients[index] = ingredient.copy(matchedProduct = product)
            }

            _uiState.update { state ->
                state.copy(
                    parsedIngredients = updatedIngredients,
                    addedToListCount = state.addedToListCount + 1,
                    unmatchedCount = state.unmatchedCount - 1,
                    matchedCount = state.matchedCount + 1
                )
            }
        }
    }

    fun addAllUnmatchedToList() {
        viewModelScope.launch {
            val unmatched = _uiState.value.parsedIngredients.filter { it.matchedProduct == null }

            for (ingredient in unmatched) {
                addToShoppingList(ingredient)
            }
        }
    }

    private suspend fun createProductFromIngredient(ingredient: ParsedIngredient): ProductEntity {
        val product = ProductEntity(
            name = ingredient.ingredientName.replaceFirstChar { it.uppercase() },
            category = "Other",
            defaultUnit = ingredient.unit ?: Unit.EACH,
            source = SourceType.RECIPE_PARSE,
            userConfirmed = false
        )
        productRepository.insertProduct(product)
        return product
    }

    fun reset() {
        _uiState.update {
            RecipeUiState()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
