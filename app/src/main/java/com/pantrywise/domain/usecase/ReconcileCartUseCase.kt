package com.pantrywise.domain.usecase

import com.pantrywise.data.local.entity.CartItem
import com.pantrywise.data.local.entity.PurchaseTransactionEntity
import com.pantrywise.data.local.entity.ShoppingListItemEntity
import com.pantrywise.data.repository.PreferencesRepository
import com.pantrywise.data.repository.ReconciliationResult
import com.pantrywise.data.repository.ShoppingRepository
import com.pantrywise.domain.model.CartMatchType
import com.pantrywise.domain.model.Unit
import javax.inject.Inject

data class CartScanResult(
    val cartItem: CartItem,
    val matchType: CartMatchType,
    val alreadyStockedQuantity: Double?
)

class ReconcileCartUseCase @Inject constructor(
    private val shoppingRepository: ShoppingRepository,
    private val preferencesRepository: PreferencesRepository
) {
    /**
     * Adds a scanned product to the shopping cart.
     * Automatically determines the match type based on shopping list and inventory.
     */
    suspend fun addToCart(
        sessionId: String,
        listId: String?,
        productId: String,
        quantity: Double,
        unit: Unit,
        unitPrice: Double? = null
    ): CartScanResult {
        val matchType = shoppingRepository.determineMatchType(productId, listId)

        shoppingRepository.addItemToCart(
            sessionId = sessionId,
            productId = productId,
            quantity = quantity,
            unit = unit,
            unitPrice = unitPrice,
            matchType = matchType
        )

        val cartItem = CartItem(
            productId = productId,
            quantity = quantity,
            unit = unit,
            unitPrice = unitPrice,
            matchType = matchType
        )

        return CartScanResult(
            cartItem = cartItem,
            matchType = matchType,
            alreadyStockedQuantity = if (matchType == CartMatchType.ALREADY_STOCKED) quantity else null
        )
    }

    /**
     * Removes an item from the shopping cart.
     */
    suspend fun removeFromCart(sessionId: String, productId: String) {
        shoppingRepository.removeItemFromCart(sessionId, productId)
    }

    /**
     * Gets the reconciliation summary for the current session.
     *
     * Returns four categories:
     * - PLANNED: Items from list, successfully scanned
     * - MISSING: Items still on list, not scanned
     * - EXTRA: Scanned items not on list
     * - WARNINGS: Items scanned but already well-stocked
     */
    suspend fun getReconciliation(sessionId: String, listId: String?): ReconciliationResult {
        return shoppingRepository.reconcileSession(sessionId, listId)
    }

    /**
     * Completes the shopping session:
     * - Updates inventory for all cart items
     * - Removes planned items from shopping list
     * - Creates purchase transaction
     * - Logs action event
     */
    suspend fun completeSession(sessionId: String, listId: String?): Result<PurchaseTransactionEntity> {
        return try {
            val preferences = preferencesRepository.getUserPreferences()
            val transaction = shoppingRepository.completeSession(sessionId, listId, preferences)
            Result.success(transaction)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        }
    }

    /**
     * Abandons the current shopping session without updating inventory.
     */
    suspend fun abandonSession(sessionId: String) {
        shoppingRepository.abandonSession(sessionId)
    }

    /**
     * Gets a summary of what will happen when completing the session.
     */
    suspend fun getCompletionSummary(sessionId: String, listId: String?): CompletionSummary {
        val reconciliation = shoppingRepository.reconcileSession(sessionId, listId)

        val totalItems = reconciliation.plannedItems.size +
                reconciliation.extraItems.size +
                reconciliation.alreadyStockedItems.size

        val totalCost = (reconciliation.plannedItems + reconciliation.extraItems + reconciliation.alreadyStockedItems)
            .sumOf { it.totalPrice ?: 0.0 }

        return CompletionSummary(
            totalItems = totalItems,
            plannedItemsCount = reconciliation.plannedItems.size,
            extraItemsCount = reconciliation.extraItems.size,
            alreadyStockedCount = reconciliation.alreadyStockedItems.size,
            missingItemsCount = reconciliation.missingItems.size,
            estimatedTotal = totalCost
        )
    }
}

data class CompletionSummary(
    val totalItems: Int,
    val plannedItemsCount: Int,
    val extraItemsCount: Int,
    val alreadyStockedCount: Int,
    val missingItemsCount: Int,
    val estimatedTotal: Double
)
