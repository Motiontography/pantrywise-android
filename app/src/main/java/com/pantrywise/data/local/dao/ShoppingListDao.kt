package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.ShoppingListEntity
import com.pantrywise.data.local.entity.ShoppingListItemEntity
import com.pantrywise.data.local.entity.ShoppingSessionEntity
import com.pantrywise.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {
    // Shopping Lists
    @Query("SELECT * FROM shopping_lists WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllShoppingLists(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists WHERE isArchived = 1 ORDER BY archivedAt DESC")
    fun getArchivedShoppingLists(): Flow<List<ShoppingListEntity>>

    @Query("DELETE FROM shopping_lists WHERE isArchived = 1 AND archivedAt < :timestamp")
    suspend fun deleteArchivedListsOlderThan(timestamp: Long)

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    suspend fun getShoppingListById(id: String): ShoppingListEntity?

    @Query("SELECT * FROM shopping_lists WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getActiveShoppingList(): ShoppingListEntity?

    @Query("SELECT * FROM shopping_lists WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getActiveShoppingLists(): Flow<List<ShoppingListEntity>>

    // Get all items from the active shopping list
    @Query("""
        SELECT sli.* FROM shopping_list_items sli
        INNER JOIN shopping_lists sl ON sli.listId = sl.id
        WHERE sl.isActive = 1
        ORDER BY sli.isChecked ASC, sli.priority DESC, sli.createdAt DESC
    """)
    fun getActiveShoppingListItems(): Flow<List<ShoppingListItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingList(shoppingList: ShoppingListEntity): Long

    @Update
    suspend fun updateShoppingList(shoppingList: ShoppingListEntity)

    @Delete
    suspend fun deleteShoppingList(shoppingList: ShoppingListEntity)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteShoppingListById(id: String)

    // Shopping List Items
    @Query("SELECT * FROM shopping_list_items ORDER BY createdAt DESC")
    fun getAllShoppingListItems(): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT * FROM shopping_list_items WHERE listId = :listId ORDER BY priority DESC, createdAt DESC")
    fun getItemsByListId(listId: String): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT * FROM shopping_list_items WHERE id = :id")
    suspend fun getShoppingListItemById(id: String): ShoppingListItemEntity?

    @Query("SELECT * FROM shopping_list_items WHERE listId = :listId AND productId = :productId")
    suspend fun getShoppingListItemByProductId(listId: String, productId: String): ShoppingListItemEntity?

    @Query("SELECT * FROM shopping_list_items WHERE listId = :listId AND isChecked = 0 ORDER BY priority DESC")
    fun getUncheckedItems(listId: String): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT * FROM shopping_list_items WHERE listId = :listId AND isChecked = 1 ORDER BY updatedAt DESC")
    fun getCheckedItems(listId: String): Flow<List<ShoppingListItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingListItem(item: ShoppingListItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingListItems(items: List<ShoppingListItemEntity>)

    @Update
    suspend fun updateShoppingListItem(item: ShoppingListItemEntity)

    @Delete
    suspend fun deleteShoppingListItem(item: ShoppingListItemEntity)

    @Query("DELETE FROM shopping_list_items WHERE id = :id")
    suspend fun deleteShoppingListItemById(id: String)

    @Query("DELETE FROM shopping_list_items WHERE listId = :listId")
    suspend fun deleteAllItemsFromList(listId: String)

    @Query("UPDATE shopping_list_items SET isChecked = :isChecked, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateItemCheckedStatus(id: String, isChecked: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE shopping_list_items SET isChecked = 0, updatedAt = :updatedAt WHERE listId = :listId")
    suspend fun uncheckAllItems(listId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM shopping_list_items WHERE listId = :listId")
    suspend fun getItemCountForList(listId: String): Int

    @Query("SELECT COUNT(*) FROM shopping_list_items WHERE listId = :listId AND isChecked = 0")
    suspend fun getUncheckedItemCountForList(listId: String): Int

    // Shopping Sessions
    @Query("SELECT * FROM shopping_sessions ORDER BY startedAt DESC")
    fun getAllShoppingSessions(): Flow<List<ShoppingSessionEntity>>

    @Query("SELECT * FROM shopping_sessions WHERE id = :id")
    suspend fun getShoppingSessionById(id: String): ShoppingSessionEntity?

    @Query("SELECT * FROM shopping_sessions WHERE status = :status ORDER BY startedAt DESC LIMIT 1")
    suspend fun getShoppingSessionByStatus(status: SessionStatus): ShoppingSessionEntity?

    @Query("SELECT * FROM shopping_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveShoppingSession(): ShoppingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingSession(session: ShoppingSessionEntity): Long

    @Update
    suspend fun updateShoppingSession(session: ShoppingSessionEntity)

    @Delete
    suspend fun deleteShoppingSession(session: ShoppingSessionEntity)

    @Query("DELETE FROM shopping_sessions WHERE id = :id")
    suspend fun deleteShoppingSessionById(id: String)

    @Query("UPDATE shopping_sessions SET status = :status, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSessionStatus(
        id: String,
        status: SessionStatus,
        completedAt: Long? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE shopping_sessions SET cartItemsJson = :cartItemsJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSessionCart(id: String, cartItemsJson: String, updatedAt: Long = System.currentTimeMillis())
}
