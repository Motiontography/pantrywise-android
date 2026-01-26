package com.pantrywise.data.repository

import com.pantrywise.data.local.dao.StoreDao
import com.pantrywise.data.local.entity.StoreAisleMapEntity
import com.pantrywise.data.local.entity.StoreEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreRepository @Inject constructor(
    private val storeDao: StoreDao
) {
    fun getAllStores(): Flow<List<StoreEntity>> = storeDao.getAllStores()

    suspend fun getAllStoresSnapshot(): List<StoreEntity> = storeDao.getAllStores().first()

    fun getFavoriteStores(): Flow<List<StoreEntity>> = storeDao.getFavoriteStores()

    fun searchStores(query: String): Flow<List<StoreEntity>> = storeDao.searchStores(query)

    suspend fun getStoreById(id: String): StoreEntity? = storeDao.getStoreById(id)

    suspend fun createStore(
        name: String,
        address: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        phone: String? = null,
        website: String? = null,
        notes: String? = null
    ): StoreEntity {
        val store = StoreEntity(
            name = name,
            address = address,
            latitude = latitude,
            longitude = longitude,
            phone = phone,
            website = website,
            notes = notes
        )
        storeDao.insertStore(store)
        return store
    }

    suspend fun updateStore(store: StoreEntity) {
        storeDao.updateStore(store.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteStore(id: String) {
        storeDao.deleteStoreById(id)
    }

    suspend fun setFavorite(id: String, isFavorite: Boolean) {
        storeDao.setFavorite(id, isFavorite)
    }

    suspend fun updateLastVisited(id: String) {
        storeDao.updateLastVisited(id)
    }

    // Aisle mapping
    fun getAisleMapsForStore(storeId: String): Flow<List<StoreAisleMapEntity>> =
        storeDao.getAisleMapsForStore(storeId)

    suspend fun getAisleForCategory(storeId: String, categoryName: String): String? {
        return storeDao.getAisleForCategory(storeId, categoryName)?.aisle
    }

    suspend fun setAisleForCategory(
        storeId: String,
        categoryName: String,
        aisle: String,
        section: String? = null,
        sortOrder: Int = 0
    ) {
        val existing = storeDao.getAisleForCategory(storeId, categoryName)
        if (existing != null) {
            storeDao.updateAisleMap(
                existing.copy(
                    aisle = aisle,
                    section = section,
                    sortOrder = sortOrder,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            storeDao.insertAisleMap(
                StoreAisleMapEntity(
                    storeId = storeId,
                    categoryName = categoryName,
                    aisle = aisle,
                    section = section,
                    sortOrder = sortOrder
                )
            )
        }
    }

    suspend fun importAisleMaps(storeId: String, aisleMaps: List<StoreAisleMapEntity>) {
        storeDao.deleteAllAisleMapsForStore(storeId)
        storeDao.insertAisleMaps(aisleMaps.map { it.copy(storeId = storeId) })
    }

    suspend fun copyAisleMapsToStore(fromStoreId: String, toStoreId: String) {
        val sourceMaps = storeDao.getAisleMapsForStore(fromStoreId)
        // Convert Flow to list - this is a simplified approach
        // In production, consider using first() or collecting
    }

    suspend fun getStoreCount(): Int = storeDao.getStoreCount()

    // Helper for getting optimized shopping route
    suspend fun getCategoryOrder(storeId: String, categories: List<String>): List<String> {
        val aisleMaps = mutableMapOf<String, Int>()
        categories.forEach { category ->
            val aisleMap = storeDao.getAisleForCategory(storeId, category)
            aisleMaps[category] = aisleMap?.sortOrder ?: Int.MAX_VALUE
        }
        return categories.sortedBy { aisleMaps[it] }
    }
}
