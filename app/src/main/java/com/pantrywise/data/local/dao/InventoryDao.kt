package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.InventoryItemEntity
import com.pantrywise.domain.model.LocationType
import com.pantrywise.domain.model.StockStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY updatedAt DESC")
    fun getAllInventoryItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getInventoryItemById(id: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE productId = :productId")
    fun getInventoryItemsByProductId(productId: String): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE productId = :productId AND location = :location")
    suspend fun getInventoryItemByProductAndLocation(productId: String, location: LocationType): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE location = :location ORDER BY updatedAt DESC")
    fun getInventoryItemsByLocation(location: LocationType): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE stockStatus = :status ORDER BY updatedAt DESC")
    fun getInventoryItemsByStatus(status: StockStatus): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE quantityOnHand <= reorderThreshold AND quantityOnHand > 0 ORDER BY quantityOnHand ASC")
    fun getLowStockItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE quantityOnHand = 0 ORDER BY updatedAt DESC")
    fun getOutOfStockItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE expirationDate IS NOT NULL AND expirationDate <= :thresholdDate ORDER BY expirationDate ASC")
    fun getExpiringItems(thresholdDate: Long): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE expirationDate IS NOT NULL AND expirationDate >= :startDate AND expirationDate <= :endDate ORDER BY expirationDate ASC")
    fun getExpiringItemsBetween(startDate: Long, endDate: Long): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE expirationDate IS NOT NULL AND expirationDate < :today ORDER BY expirationDate ASC")
    fun getExpiredItems(today: Long): Flow<List<InventoryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(inventoryItem: InventoryItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(inventoryItems: List<InventoryItemEntity>)

    @Update
    suspend fun update(inventoryItem: InventoryItemEntity)

    @Delete
    suspend fun delete(inventoryItem: InventoryItemEntity)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM inventory_items WHERE productId = :productId")
    suspend fun deleteByProductId(productId: String)

    @Query("UPDATE inventory_items SET quantityOnHand = :quantity, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateQuantity(id: String, quantity: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE inventory_items SET location = :location, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLocation(id: String, location: LocationType, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE inventory_items SET stockStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStockStatus(id: String, status: StockStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM inventory_items")
    suspend fun getInventoryItemCount(): Int

    @Query("SELECT COUNT(*) FROM inventory_items WHERE location = :location")
    suspend fun getInventoryItemCountByLocation(location: LocationType): Int

    // Additional methods for audit functionality
    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getItemById(id: String): InventoryItemEntity?

    @Update
    suspend fun updateItem(item: InventoryItemEntity)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("SELECT * FROM inventory_items")
    suspend fun getAllItemsSnapshot(): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items WHERE location = :location")
    suspend fun getItemsByLocation(location: String): List<InventoryItemEntity>
}
