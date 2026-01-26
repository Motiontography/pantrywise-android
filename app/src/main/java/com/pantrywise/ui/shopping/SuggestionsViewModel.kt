package com.pantrywise.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.dao.ShoppingListDao
import com.pantrywise.data.local.entity.ShoppingListItemEntity
import com.pantrywise.domain.model.SourceType
import com.pantrywise.domain.model.Unit
import com.pantrywise.services.ml.SmartSuggestion
import com.pantrywise.services.ml.SmartSuggestionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SuggestionsUiState(
    val suggestions: List<SmartSuggestion> = emptyList(),
    val companionSuggestions: List<SmartSuggestion> = emptyList(),
    val isLoading: Boolean = false,
    val lastAddedItem: String? = null,
    val showAddedSnackbar: Boolean = false
)

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    private val suggestionEngine: SmartSuggestionEngine,
    private val shoppingListDao: ShoppingListDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SuggestionsUiState())
    val uiState: StateFlow<SuggestionsUiState> = _uiState.asStateFlow()

    private val dismissedSuggestions = mutableSetOf<String>()

    init {
        loadSuggestions()
    }

    fun loadSuggestions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val suggestions = suggestionEngine.generateSuggestions()
                    .filterNot { it.id in dismissedSuggestions }

                _uiState.update {
                    it.copy(
                        suggestions = suggestions,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadCompanionSuggestions(productId: String) {
        viewModelScope.launch {
            try {
                val companions = suggestionEngine.getCompanionSuggestions(productId)
                _uiState.update { it.copy(companionSuggestions = companions) }
            } catch (e: Exception) {
                // Silently fail for companion suggestions
            }
        }
    }

    fun addSuggestionToList(suggestion: SmartSuggestion) {
        viewModelScope.launch {
            try {
                // Get active shopping list or use default
                val activeList = shoppingListDao.getActiveShoppingList()
                val listId = activeList?.id ?: "default"

                // Parse unit from suggestion
                val unit = try {
                    Unit.valueOf(suggestion.unit.uppercase())
                } catch (e: Exception) {
                    Unit.EACH
                }

                // Generate reason based on suggestion type
                val reason = when (suggestion.type) {
                    com.pantrywise.services.ml.SuggestionType.MEAL_PLAN_NEEDED ->
                        suggestion.metadata["recipeName"]?.let { "For: $it" }
                    com.pantrywise.services.ml.SuggestionType.EXPIRING_SOON ->
                        "Replace expiring item"
                    com.pantrywise.services.ml.SuggestionType.LOW_STOCK ->
                        "Low stock"
                    else -> "Smart suggestion"
                }

                val item = ShoppingListItemEntity(
                    id = UUID.randomUUID().toString(),
                    listId = listId,
                    productId = suggestion.productId ?: suggestion.productName.hashCode().toString(),
                    quantityNeeded = suggestion.quantity,
                    unit = unit,
                    reason = reason,
                    suggestedBy = SourceType.AI_SUGGESTION,
                    isChecked = false
                )

                shoppingListDao.insertShoppingListItem(item)

                // Remove from suggestions
                _uiState.update { state ->
                    state.copy(
                        suggestions = state.suggestions.filterNot { it.id == suggestion.id },
                        lastAddedItem = suggestion.productName,
                        showAddedSnackbar = true
                    )
                }

                // Load companion suggestions
                suggestion.productId?.let { loadCompanionSuggestions(it) }

            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun dismissSuggestion(suggestion: SmartSuggestion) {
        dismissedSuggestions.add(suggestion.id)

        viewModelScope.launch {
            suggestionEngine.dismissSuggestion(suggestion.id)
        }

        _uiState.update { state ->
            state.copy(
                suggestions = state.suggestions.filterNot { it.id == suggestion.id }
            )
        }
    }

    fun dismissCompanionSuggestion(suggestion: SmartSuggestion) {
        _uiState.update { state ->
            state.copy(
                companionSuggestions = state.companionSuggestions.filterNot { it.id == suggestion.id }
            )
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(showAddedSnackbar = false) }
    }

    fun clearCompanionSuggestions() {
        _uiState.update { it.copy(companionSuggestions = emptyList()) }
    }
}
