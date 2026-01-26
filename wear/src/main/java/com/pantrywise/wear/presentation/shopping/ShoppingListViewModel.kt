package com.pantrywise.wear.presentation.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.wear.data.WearDataRepository
import com.pantrywise.wear.data.WearShoppingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingListUiState(
    val items: List<WearShoppingItem> = emptyList(),
    val isLoading: Boolean = false
) {
    val uncheckedCount: Int
        get() = items.count { !it.isChecked }
}

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val repository: WearDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            repository.syncData.collect { data ->
                _uiState.update {
                    it.copy(
                        items = data.shoppingItems.sortedWith(
                            compareBy({ it.isChecked }, { -it.priority })
                        )
                    )
                }
            }
        }
    }

    fun toggleItem(itemId: String, isChecked: Boolean) {
        repository.toggleItemChecked(itemId, isChecked)
    }
}
