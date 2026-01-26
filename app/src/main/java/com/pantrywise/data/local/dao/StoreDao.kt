package com.pantrywise.data.local.dao

import androidx.room.*
import com.pantrywise.data.local.entity.StoreAisleMapEntity
import com.pantrywise.data.local.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    // Store operations
    @Query("SELECT * FROM stores ORDER BY isFavorite DESC, name ASC")
    fun getAllStores(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun getStoreById(id: String): StoreEntity?

    @Query("SELECT * FROM stores WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchStores(query: String): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteStores(): Flow<List<StoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStore(store: StoreEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStores(stores: List<StoreEntity>)

    @Update
    suspend fun updateStore(store: StoreEntity)

    @Delete
    suspend fun deleteStore(store: StoreEntity)

    @Query("DELETE FROM stores WHERE id = :id")
    suspend fun deleteStoreById(id: String)

    @Query("UPDATE stores SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE stores SET lastVisited = :lastVisited, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLastVisited(id: String, lastVisited: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    // Aisle map operations
    @Query("SELECT * FROM store_aisle_maps WHERE storeId = :storeId ORDER BY sortOrder ASC")
    fun getAisleMapsForStore(storeId: String): Flow<List<StoreAisleMapEntity>>

    @Query("SELECT * FROM store_aisle_maps WHERE storeId = :storeId AND categoryName = :categoryName")
    suspend fun getAisleForCategory(storeId: String, categoryName: String): StoreAisleMapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAisleMap(aisleMap: StoreAisleMapEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAisleMaps(aisleMaps: List<StoreAisleMapEntity>)

    @Update
    suspend fun updateAisleMap(aisleMap: StoreAisleMapEntity)

    @Delete
    suspend fun deleteAisleMap(aisleMap: StoreAisleMapEntity)

    @Query("DELETE FROM store_aisle_maps WHERE storeId = :storeId")
    suspend fun deleteAllAisleMapsForStore(storeId: String)

    // Stats
    @Query("SELECT COUNT(*) FROM stores")
    suspend fun getStoreCount(): Int
}
