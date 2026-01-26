package com.pantrywise.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.remote.model.AIRecipeResult
import com.pantrywise.data.repository.ShoppingRepository
import com.pantrywise.services.OpenAIService
import com.pantrywise.services.SecureStorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeDiscoveryState(
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val discoveredRecipe: AIRecipeResult? = null,
    val error: String? = null,
    val isApiConfigured: Boolean = false,
    val showApiKeyPrompt: Boolean = false,
    val shoppingListCreated: Boolean = false,
    val createdShoppingListId: String? = null,
    val recipeSaved: Boolean = false
)

@HiltViewModel
class RecipeDiscoveryViewModel @Inject constructor(
    private val openAIService: OpenAIService,
    private val secureStorageService: SecureStorageService,
    private val shoppingRepository: ShoppingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeDiscoveryState())
    val state: StateFlow<RecipeDiscoveryState> = _state.asStateFlow()

    init {
        checkApiConfiguration()
    }

    private fun checkApiConfiguration() {
        _state.update { it.copy(isApiConfigured = secureStorageService.hasApiKey()) }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query, error = null) }
    }

    fun searchRecipe() {
        val query = _state.value.searchQuery.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(error = "Please enter what you want to make") }
            return
        }

        if (!secureStorageService.hasApiKey()) {
            _state.update { it.copy(showApiKeyPrompt = true) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, discoveredRecipe = null) }

            try {
                val recipe = openAIService.discoverRecipe(query)
                _state.update {
                    it.copy(
                        isLoading = false,
                        discoveredRecipe = recipe,
                        shoppingListCreated = false,
                        recipeSaved = false
                    )
                }
            } catch (e: OpenAIService.OpenAIError.NotConfigured) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        showApiKeyPrompt = true,
                        error = "API key not configured"
                    )
                }
            } catch (e: OpenAIService.OpenAIError.InvalidAPIKey) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Invalid API key. Please check your OpenAI API key in settings."
                    )
                }
            } catch (e: OpenAIService.OpenAIError.RateLimited) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Rate limited. Please try again later."
                    )
                }
            } catch (e: OpenAIService.OpenAIError) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "An error occurred"
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to discover recipe: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun createShoppingList() {
        val recipe = _state.value.discoveredRecipe ?: return

        viewModelScope.launch {
            try {
                // Create a new shopping list with the recipe name
                val listId = shoppingRepository.createList(recipe.name)

                // Add all ingredients to the list
                for (ingredient in recipe.ingredients) {
                    val displayQuantity = if (ingredient.quantity == ingredient.quantity.toLong().toDouble()) {
                        ingredient.quantity.toLong().toString()
                    } else {
                        ingredient.quantity.toString()
                    }

                    val itemName = if (ingredient.notes != null) {
                        "${ingredient.name} (${ingredient.notes})"
                    } else {
                        ingredient.name
                    }

                    shoppingRepository.addItemToList(
                        listId = listId,
                        name = itemName,
                        quantity = ingredient.quantity,
                        unit = ingredient.unit
                    )
                }

                _state.update {
                    it.copy(
                        shoppingListCreated = true,
                        createdShoppingListId = listId
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Failed to create shopping list: ${e.localizedMessage}")
                }
            }
        }
    }

    fun saveRecipeToLibrary() {
        val recipe = _state.value.discoveredRecipe ?: return

        viewModelScope.launch {
            try {
                // TODO: Save recipe to local Recipe database
                // This would involve creating a RecipeEntity and saving it
                _state.update { it.copy(recipeSaved = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Failed to save recipe: ${e.localizedMessage}")
                }
            }
        }
    }

    fun dismissApiKeyPrompt() {
        _state.update { it.copy(showApiKeyPrompt = false) }
    }

    fun clearRecipe() {
        _state.update {
            it.copy(
                discoveredRecipe = null,
                searchQuery = "",
                shoppingListCreated = false,
                recipeSaved = false
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    // Suggestion chips for popular recipe searches
    val suggestions = listOf(
        "Chicken Parmesan",
        "Beef Tacos",
        "Pasta Carbonara",
        "Stir Fry",
        "Homemade Pizza",
        "Chocolate Chip Cookies",
        "Caesar Salad",
        "Grilled Salmon"
    )
}
