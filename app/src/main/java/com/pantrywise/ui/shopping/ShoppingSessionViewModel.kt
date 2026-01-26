package com.pantrywise.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pantrywise.data.local.dao.ProductDao
import com.pantrywise.data.local.dao.ShoppingListDao
import com.pantrywise.data.local.entity.CartItem
import com.pantrywise.data.local.entity.ShoppingSessionEntity
import com.pantrywise.data.repository.ShoppingRepository
import com.pantrywise.domain.model.CartMatchType
import com.pantrywise.domain.model.Unit
import com.pantrywise.domain.usecase.ReconcileCartUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShoppingSessionUiState(
    val isLoading: Boolean = true,
    val session: ShoppingSessionEntity? = null,
    val cartItems: List<CartItem> = emptyList(),
    val productNames: Map<String, String> = emptyMap(),
    val isScanning: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ShoppingSessionViewModel @Inject constructor(
    private val shoppingRepository: ShoppingRepository,
    private val shoppingListDao: ShoppingListDao,
    private val productDao: ProductDao,
    private val reconcileCartUseCase: ReconcileCartUseCase
) : ViewModel() {

    private val gson = Gson()

    private val _uiState = MutableStateFlow(ShoppingSessionUiState())
    val uiState: StateFlow<ShoppingSessionUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null
    private var currentListId: String? = null

    fun loadSession(sessionId: String) {
        currentSessionId = sessionId
        viewModelScope.launch {
            val session = shoppingListDao.getShoppingSessionById(sessionId)
            if (session == null) {
                _uiState.update { it.copy(isLoading = false, error = "Session not found") }
                return@launch
            }

            // Get active shopping list
            val activeList = shoppingListDao.getActiveShoppingList()
            currentListId = activeList?.id

            // Parse cart items
            val cartItems = try {
                gson.fromJson(session.cartItemsJson, Array<CartItem>::class.java)?.toList() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            // Load product names
            val productNames = cartItems.associate { cartItem ->
                val product = productDao.getProductById(cartItem.productId)
                cartItem.productId to (product?.name ?: "Unknown Product")
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    session = session,
                    cartItems = cartItems,
                    productNames = productNames
                )
            }
        }
    }

    fun startScanning() {
        _uiState.update { it.copy(isScanning = true) }
    }

    fun stopScanning() {
        _uiState.update { it.copy(isScanning = false) }
    }

    fun addToCart(productId: String, quantity: Double = 1.0, unit: Unit = Unit.EACH, unitPrice: Double? = null) {
        val sessionId = currentSessionId ?: return

        viewModelScope.launch {
            val result = reconcileCartUseCase.addToCart(
                sessionId = sessionId,
                listId = currentListId,
                productId = productId,
                quantity = quantity,
                unit = unit,
                unitPrice = unitPrice
            )

            // Reload session to get updated cart
            loadSession(sessionId)

            // Show warning if already stocked
            if (result.matchType == CartMatchType.ALREADY_STOCKED) {
                _uiState.update {
                    it.copy(error = "Warning: This item is already well-stocked in your pantry")
                }
            }
        }
    }

    fun removeFromCart(productId: String) {
        val sessionId = currentSessionId ?: return

        viewModelScope.launch {
            reconcileCartUseCase.removeFromCart(sessionId, productId)
            loadSession(sessionId)
        }
    }

    fun adjustCartItemQuantity(productId: String, adjustment: Double) {
        val sessionId = currentSessionId ?: return

        viewModelScope.launch {
            val currentCart = _uiState.value.cartItems.toMutableList()
            val index = currentCart.indexOfFirst { it.productId == productId }

            if (index >= 0) {
                val item = currentCart[index]
                val newQuantity = (item.quantity + adjustment).coerceAtLeast(1.0)
                currentCart[index] = item.copy(quantity = newQuantity)

                // Update session cart
                shoppingListDao.updateSessionCart(sessionId, gson.toJson(currentCart))
                loadSession(sessionId)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
