package com.pantrywise.ui.stores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.dao.StoreDao
import com.pantrywise.data.local.entity.StoreAisleMapEntity
import com.pantrywise.data.local.entity.StoreEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class StoreManagementUiState(
    val stores: List<StoreEntity> = emptyList(),
    val selectedStore: StoreEntity? = null,
    val aisleMaps: List<StoreAisleMapEntity> = emptyList(),
    val isLoading: Boolean = false,
    val showAddStoreDialog: Boolean = false,
    val showEditStoreDialog: Boolean = false,
    val showAddAisleDialog: Boolean = false,
    val editingAisleMap: StoreAisleMapEntity? = null,
    val error: String? = null
)

@HiltViewModel
class StoreManagementViewModel @Inject constructor(
    private val storeDao: StoreDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreManagementUiState())
    val uiState: StateFlow<StoreManagementUiState> = _uiState.asStateFlow()

    init {
        loadStores()
    }

    private fun loadStores() {
        viewModelScope.launch {
            storeDao.getAllStores().collect { stores ->
                _uiState.update { it.copy(stores = stores) }
            }
        }
    }

    fun showAddStoreDialog() {
        _uiState.update { it.copy(showAddStoreDialog = true) }
    }

    fun hideAddStoreDialog() {
        _uiState.update { it.copy(showAddStoreDialog = false) }
    }

    fun showEditStoreDialog(store: StoreEntity) {
        _uiState.update { it.copy(selectedStore = store, showEditStoreDialog = true) }
    }

    fun hideEditStoreDialog() {
        _uiState.update { it.copy(showEditStoreDialog = false, selectedStore = null) }
    }

    fun addStore(
        name: String,
        address: String?,
        phone: String?,
        notes: String?
    ) {
        viewModelScope.launch {
            try {
                val store = StoreEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    address = address?.ifEmpty { null },
                    phone = phone?.ifEmpty { null },
                    notes = notes?.ifEmpty { null }
                )
                storeDao.insertStore(store)
                _uiState.update { it.copy(showAddStoreDialog = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to add store: ${e.message}") }
            }
        }
    }

    fun updateStore(
        store: StoreEntity,
        name: String,
        address: String?,
        phone: String?,
        notes: String?
    ) {
        viewModelScope.launch {
            try {
                val updatedStore = store.copy(
                    name = name,
                    address = address?.ifEmpty { null },
                    phone = phone?.ifEmpty { null },
                    notes = notes?.ifEmpty { null },
                    updatedAt = System.currentTimeMillis()
                )
                storeDao.updateStore(updatedStore)
                _uiState.update { it.copy(showEditStoreDialog = false, selectedStore = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to update store: ${e.message}") }
            }
        }
    }

    fun deleteStore(store: StoreEntity) {
        viewModelScope.launch {
            try {
                storeDao.deleteStore(store)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete store: ${e.message}") }
            }
        }
    }

    fun toggleFavorite(store: StoreEntity) {
        viewModelScope.launch {
            storeDao.setFavorite(store.id, !store.isFavorite)
        }
    }

    fun selectStoreForAisles(store: StoreEntity) {
        _uiState.update { it.copy(selectedStore = store) }
        loadAisleMaps(store.id)
    }

    fun clearSelectedStore() {
        _uiState.update { it.copy(selectedStore = null, aisleMaps = emptyList()) }
    }

    private fun loadAisleMaps(storeId: String) {
        viewModelScope.launch {
            storeDao.getAisleMapsForStore(storeId).collect { maps ->
                _uiState.update { it.copy(aisleMaps = maps) }
            }
        }
    }

    fun showAddAisleDialog() {
        _uiState.update { it.copy(showAddAisleDialog = true) }
    }

    fun hideAddAisleDialog() {
        _uiState.update { it.copy(showAddAisleDialog = false, editingAisleMap = null) }
    }

    fun editAisleMap(aisleMap: StoreAisleMapEntity) {
        _uiState.update { it.copy(editingAisleMap = aisleMap, showAddAisleDialog = true) }
    }

    fun addOrUpdateAisleMap(
        categoryName: String,
        aisle: String,
        section: String?
    ) {
        viewModelScope.launch {
            val storeId = _uiState.value.selectedStore?.id ?: return@launch
            val existingMap = _uiState.value.editingAisleMap

            try {
                if (existingMap != null) {
                    val updatedMap = existingMap.copy(
                        categoryName = categoryName,
                        aisle = aisle,
                        section = section?.ifEmpty { null },
                        updatedAt = System.currentTimeMillis()
                    )
                    storeDao.updateAisleMap(updatedMap)
                } else {
                    val newMap = StoreAisleMapEntity(
                        storeId = storeId,
                        categoryName = categoryName,
                        aisle = aisle,
                        section = section?.ifEmpty { null },
                        sortOrder = _uiState.value.aisleMaps.size
                    )
                    storeDao.insertAisleMap(newMap)
                }
                _uiState.update { it.copy(showAddAisleDialog = false, editingAisleMap = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save aisle map: ${e.message}") }
            }
        }
    }

    fun deleteAisleMap(aisleMap: StoreAisleMapEntity) {
        viewModelScope.launch {
            try {
                storeDao.deleteAisleMap(aisleMap)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete aisle map: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // Common categories for suggestions
    val commonCategories = listOf(
        "Produce",
        "Dairy",
        "Meat & Seafood",
        "Bakery",
        "Frozen Foods",
        "Canned Goods",
        "Snacks",
        "Beverages",
        "Condiments",
        "Breakfast",
        "Pasta & Rice",
        "International",
        "Health & Beauty",
        "Cleaning Supplies",
        "Pet Supplies",
        "Baby",
        "Deli"
    )
}
