package com.pantrywise.data.repository

import com.google.gson.Gson
import com.pantrywise.data.local.dao.InventoryDao
import com.pantrywise.data.local.dao.PreferencesDao
import com.pantrywise.data.local.dao.ShoppingListDao
import com.pantrywise.data.local.dao.TransactionDao
import com.pantrywise.data.local.entity.*
import com.pantrywise.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ReconciliationResult(
    val plannedItems: List<CartItem>,
    val missingItems: List<ShoppingListItemEntity>,
    val extraItems: List<CartItem>,
    val alreadyStockedItems: List<CartItem>
)

@Singleton
class ShoppingRepository @Inject constructor(
    private val shoppingListDao: ShoppingListDao,
    private val inventoryDao: InventoryDao,
    private val transactionDao: TransactionDao,
    private val preferencesDao: PreferencesDao
) {
    private val gson = Gson()

    // Shopping Lists
    fun getAllShoppingLists(): Flow<List<ShoppingListEntity>> = shoppingListDao.getAllShoppingLists()

    suspend fun getShoppingListById(id: String): ShoppingListEntity? = shoppingListDao.getShoppingListById(id)

    suspend fun getActiveShoppingList(): ShoppingListEntity? = shoppingListDao.getActiveShoppingList()

    fun getActiveShoppingLists(): Flow<List<ShoppingListEntity>> = shoppingListDao.getActiveShoppingLists()

    suspend fun createShoppingList(name: String = "Shopping List"): ShoppingListEntity {
        val list = ShoppingListEntity(name = name)
        shoppingListDao.insertShoppingList(list)
        return list
    }

    /**
     * Creates a shopping list and returns its ID
     */
    suspend fun createList(name: String): String {
        val list = createShoppingList(name)
        return list.id
    }

    /**
     * Adds an item to a shopping list by name (for AI Recipe Discovery)
     * Creates a placeholder product if none exists
     */
    suspend fun addItemToList(
        listId: String,
        name: String,
        quantity: Double,
        unit: String
    ) {
        // Create a temporary item with the ingredient name
        // In a real implementation, this would search for or create a product
        val item = ShoppingListItemEntity(
            listId = listId,
            productId = UUID.randomUUID().toString(), // Temporary ID
            quantityNeeded = quantity,
            unit = stringToUnit(unit),
            reason = name, // Store the full ingredient name/description
            suggestedBy = SourceType.AI_SUGGESTION
        )
        shoppingListDao.insertShoppingListItem(item)

        // Log event
        preferencesDao.insertActionEvent(
            ActionEventEntity(
                type = ActionType.SHOPPING_LIST_ITEM_ADDED,
                entityType = "ShoppingListItem",
                entityId = item.id
            )
        )
    }

    private fun stringToUnit(unitStr: String): Unit {
        return when (unitStr.lowercase()) {
            "lbs", "lb", "pound", "pounds" -> Unit.POUND
            "oz", "ounce", "ounces" -> Unit.OUNCE
            "kg", "kilogram", "kilograms" -> Unit.KILOGRAM
            "g", "gram", "grams" -> Unit.GRAM
            "ml", "milliliter", "milliliters" -> Unit.MILLILITER
            "l", "liter", "liters" -> Unit.LITER
            "cups", "cup" -> Unit.CUP
            "tbsp", "tablespoon", "tablespoons" -> Unit.TABLESPOON
            "tsp", "teaspoon", "teaspoons" -> Unit.TEASPOON
            "gal", "gallon", "gallons" -> Unit.GALLON
            "each", "item", "items" -> Unit.EACH
            "bunch" -> Unit.BUNCH
            "can", "cans" -> Unit.CAN
            "box", "boxes" -> Unit.BOX
            "bag", "bags" -> Unit.BAG
            "bottle", "bottles" -> Unit.BOTTLE
            "pack", "packs", "package", "packages" -> Unit.PACK
            "jar", "jars" -> Unit.JAR
            "cloves", "clove" -> Unit.EACH // Treat cloves as each
            else -> Unit.EACH
        }
    }

    suspend fun updateShoppingList(list: ShoppingListEntity) = shoppingListDao.updateShoppingList(list)

    suspend fun deleteShoppingList(id: String) = shoppingListDao.deleteShoppingListById(id)

    // Shopping List Items
    fun getItemsByListId(listId: String): Flow<List<ShoppingListItemEntity>> =
        shoppingListDao.getItemsByListId(listId)

    suspend fun addItemToList(
        listId: String,
        productId: String,
        quantity: Double,
        unit: Unit,
        priority: Int = 5,
        reason: String? = null,
        suggestedBy: SourceType? = null
    ): ShoppingListItemEntity {
        // Check if item already exists
        val existingItem = shoppingListDao.getShoppingListItemByProductId(listId, productId)

        if (existingItem != null) {
            // Update quantity
            val updatedItem = existingItem.copy(
                quantityNeeded = existingItem.quantityNeeded + quantity,
                updatedAt = System.currentTimeMillis()
            )
            shoppingListDao.updateShoppingListItem(updatedItem)

            // Log event
            preferencesDao.insertActionEvent(
                ActionEventEntity(
                    type = ActionType.SHOPPING_LIST_ITEM_ADDED,
                    entityType = "ShoppingListItem",
                    entityId = existingItem.id
                )
            )

            return updatedItem
        }

        val item = ShoppingListItemEntity(
            listId = listId,
            productId = productId,
            quantityNeeded = quantity,
            unit = unit,
            priority = priority,
            reason = reason,
            suggestedBy = suggestedBy
        )
        shoppingListDao.insertShoppingListItem(item)

        // Log event
        preferencesDao.insertActionEvent(
            ActionEventEntity(
                type = ActionType.SHOPPING_LIST_ITEM_ADDED,
                entityType = "ShoppingListItem",
                entityId = item.id
            )
        )

        return item
    }

    suspend fun removeItemFromList(itemId: String) {
        shoppingListDao.deleteShoppingListItemById(itemId)

        // Log event
        preferencesDao.insertActionEvent(
            ActionEventEntity(
                type = ActionType.SHOPPING_LIST_ITEM_REMOVED,
                entityType = "ShoppingListItem",
                entityId = itemId
            )
        )
    }

    suspend fun toggleItemChecked(itemId: String, isChecked: Boolean) {
        shoppingListDao.updateItemCheckedStatus(itemId, isChecked)
    }

    // Shopping Sessions
    fun getAllShoppingSessions(): Flow<List<ShoppingSessionEntity>> = shoppingListDao.getAllShoppingSessions()

    suspend fun getActiveShoppingSession(): ShoppingSessionEntity? = shoppingListDao.getActiveShoppingSession()

    suspend fun startShoppingSession(store: String? = null): ShoppingSessionEntity {
        // Check for existing active session
        val existingSession = shoppingListDao.getActiveShoppingSession()
        if (existingSession != null) {
            throw IllegalStateException("Shopping session already in progress")
        }

        val session = ShoppingSessionEntity(store = store)
        shoppingListDao.insertShoppingSession(session)

        // Log event
        preferencesDao.insertActionEvent(
            ActionEventEntity(
                type = ActionType.SHOPPING_SESSION_STARTED,
                entityType = "ShoppingSession",
                entityId = session.id
            )
        )

        return session
    }

    suspend fun addItemToCart(
        sessionId: String,
        productId: String,
        quantity: Double,
        unit: Unit,
        unitPrice: Double? = null,
        matchType: CartMatchType
    ) {
        val session = shoppingListDao.getShoppingSessionById(sessionId) ?: return

        val cartItems = gson.fromJson(session.cartItemsJson, Array<CartItem>::class.java)?.toMutableList()
            ?: mutableListOf()

        // Check if product already in cart
        val existingIndex = cartItems.indexOfFirst { it.productId == productId }
        if (existingIndex >= 0) {
            val existing = cartItems[existingIndex]
            cartItems[existingIndex] = existing.copy(
                quantity = existing.quantity + quantity,
                unitPrice = unitPrice ?: existing.unitPrice
            )
        } else {
            cartItems.add(CartItem(
                productId = productId,
                quantity = quantity,
                unit = unit,
                unitPrice = unitPrice,
                matchType = matchType
            ))
        }

        shoppingListDao.updateSessionCart(sessionId, gson.toJson(cartItems))
    }

    suspend fun removeItemFromCart(sessionId: String, productId: String) {
        val session = shoppingListDao.getShoppingSessionById(sessionId) ?: return

        val cartItems = gson.fromJson(session.cartItemsJson, Array<CartItem>::class.java)?.toMutableList()
            ?: mutableListOf()

        cartItems.removeAll { it.productId == productId }

        shoppingListDao.updateSessionCart(sessionId, gson.toJson(cartItems))
    }

    /**
     * Determines the match type for a scanned product during shopping.
     */
    suspend fun determineMatchType(productId: String, listId: String?): CartMatchType {
        // Check if on shopping list
        if (listId != null) {
            val listItem = shoppingListDao.getShoppingListItemByProductId(listId, productId)
            if (listItem != null && !listItem.isChecked) {
                return CartMatchType.PLANNED
            }
        }

        // Check if already well-stocked in pantry
        val inventoryItems = inventoryDao.getInventoryItemsByProductId(productId).first()
        val totalQuantity = inventoryItems.sumOf { it.quantityOnHand }
        val avgReorderThreshold = inventoryItems.map { it.reorderThreshold }.average().takeIf { !it.isNaN() } ?: 1.0

        if (totalQuantity > avgReorderThreshold * 2) {
            return CartMatchType.ALREADY_STOCKED
        }

        return CartMatchType.EXTRA
    }

    /**
     * Generates reconciliation data for the shopping session.
     */
    suspend fun reconcileSession(sessionId: String, listId: String?): ReconciliationResult {
        val session = shoppingListDao.getShoppingSessionById(sessionId)
            ?: throw IllegalStateException("Session not found")

        val cartItems = gson.fromJson(session.cartItemsJson, Array<CartItem>::class.java)?.toList()
            ?: emptyList()

        val plannedItems = cartItems.filter { it.matchType == CartMatchType.PLANNED }
        val extraItems = cartItems.filter { it.matchType == CartMatchType.EXTRA }
        val alreadyStockedItems = cartItems.filter { it.matchType == CartMatchType.ALREADY_STOCKED }

        val missingItems = if (listId != null) {
            val listItems = shoppingListDao.getItemsByListId(listId).first()
            val cartProductIds = cartItems.map { it.productId }.toSet()
            listItems.filter { !it.isChecked && it.productId !in cartProductIds }
        } else {
            emptyList()
        }

        return ReconciliationResult(
            plannedItems = plannedItems,
            missingItems = missingItems,
            extraItems = extraItems,
            alreadyStockedItems = alreadyStockedItems
        )
    }

    /**
     * Completes the shopping session and updates inventory.
     */
    suspend fun completeSession(
        sessionId: String,
        listId: String?,
        userPreferences: UserPreferencesEntity?
    ): PurchaseTransactionEntity {
        val session = shoppingListDao.getShoppingSessionById(sessionId)
            ?: throw IllegalStateException("Session not found")

        val cartItems = gson.fromJson(session.cartItemsJson, Array<CartItem>::class.java)?.toList()
            ?: emptyList()

        if (cartItems.isEmpty()) {
            throw IllegalStateException("Cannot complete session with empty cart")
        }

        val defaultLocation = userPreferences?.defaultLocation ?: LocationType.PANTRY

        // Update inventory for each cart item
        for (cartItem in cartItems) {
            val existingInventory = inventoryDao.getInventoryItemByProductAndLocation(
                cartItem.productId,
                defaultLocation
            )

            if (existingInventory != null) {
                val updatedQuantity = existingInventory.quantityOnHand + cartItem.quantity
                val updatedItem = existingInventory.copy(
                    quantityOnHand = updatedQuantity,
                    updatedAt = System.currentTimeMillis()
                )
                inventoryDao.update(updatedItem.copy(stockStatus = updatedItem.calculateStockStatus()))
            } else {
                val newItem = InventoryItemEntity(
                    productId = cartItem.productId,
                    location = defaultLocation,
                    quantityOnHand = cartItem.quantity,
                    unit = cartItem.unit
                )
                inventoryDao.insert(newItem.copy(stockStatus = newItem.calculateStockStatus()))
            }

            // Remove from shopping list if was planned
            if (listId != null && cartItem.matchType == CartMatchType.PLANNED) {
                val listItem = shoppingListDao.getShoppingListItemByProductId(listId, cartItem.productId)
                if (listItem != null) {
                    shoppingListDao.deleteShoppingListItem(listItem)
                }
            }
        }

        // Create purchase transaction
        val purchaseItems = cartItems.map { cartItem ->
            PurchaseItem(
                productId = cartItem.productId,
                quantity = cartItem.quantity,
                unit = cartItem.unit,
                unitPrice = cartItem.unitPrice,
                totalPrice = cartItem.totalPrice ?: 0.0,
                category = "Other" // Would need product lookup for actual category
            )
        }

        val total = purchaseItems.sumOf { it.totalPrice }

        val transaction = PurchaseTransactionEntity(
            store = session.store,
            date = System.currentTimeMillis(),
            itemsJson = gson.toJson(purchaseItems),
            total = total,
            sessionId = sessionId
        )
        transactionDao.insertTransaction(transaction)

        // Mark session as completed
        shoppingListDao.updateSessionStatus(
            sessionId,
            SessionStatus.COMPLETED,
            completedAt = System.currentTimeMillis()
        )

        // Log event
        preferencesDao.insertActionEvent(
            ActionEventEntity(
                type = ActionType.SHOPPING_SESSION_COMPLETED,
                entityType = "ShoppingSession",
                entityId = sessionId
            )
        )

        return transaction
    }

    suspend fun abandonSession(sessionId: String) {
        shoppingListDao.updateSessionStatus(sessionId, SessionStatus.ABANDONED)
    }
}
