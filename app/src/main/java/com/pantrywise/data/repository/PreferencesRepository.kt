package com.pantrywise.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pantrywise.data.local.dao.PreferencesDao
import com.pantrywise.data.local.entity.ActionEventEntity
import com.pantrywise.data.local.entity.PendingLookupEntity
import com.pantrywise.data.local.entity.UserPreferencesEntity
import com.pantrywise.domain.model.ActionType
import com.pantrywise.domain.model.LocationType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class BrandPreference(
    val category: String,
    val brand: String
)

@Singleton
class PreferencesRepository @Inject constructor(
    private val preferencesDao: PreferencesDao
) {
    private val gson = Gson()

    // User Preferences
    suspend fun getUserPreferences(): UserPreferencesEntity {
        return preferencesDao.getUserPreferences() ?: createDefaultPreferences()
    }

    fun getUserPreferencesFlow(): Flow<UserPreferencesEntity?> = preferencesDao.getUserPreferencesFlow()

    private suspend fun createDefaultPreferences(): UserPreferencesEntity {
        val preferences = UserPreferencesEntity()
        preferencesDao.insertUserPreferences(preferences)
        return preferences
    }

    suspend fun updateDefaultLocation(location: LocationType) {
        val preferences = getUserPreferences()
        val updated = preferences.copy(
            defaultLocation = location,
            updatedAt = System.currentTimeMillis()
        )
        preferencesDao.updateUserPreferences(updated)

        logActionEvent(ActionType.PREFERENCE_UPDATED, "UserPreferences", preferences.id)
    }

    suspend fun updateDefaultCurrency(currency: String) {
        val preferences = getUserPreferences()
        val updated = preferences.copy(
            defaultCurrency = currency,
            updatedAt = System.currentTimeMillis()
        )
        preferencesDao.updateUserPreferences(updated)

        logActionEvent(ActionType.PREFERENCE_UPDATED, "UserPreferences", preferences.id)
    }

    suspend fun addPreferredBrand(category: String, brand: String) {
        val preferences = getUserPreferences()
        val brands = parsePreferredBrands(preferences.preferredBrandsJson).toMutableList()

        if (brands.none { it.category == category && it.brand == brand }) {
            brands.add(BrandPreference(category, brand))
            val updated = preferences.copy(
                preferredBrandsJson = gson.toJson(brands),
                updatedAt = System.currentTimeMillis()
            )
            preferencesDao.updateUserPreferences(updated)
        }
    }

    suspend fun removePreferredBrand(category: String, brand: String) {
        val preferences = getUserPreferences()
        val brands = parsePreferredBrands(preferences.preferredBrandsJson).toMutableList()

        brands.removeAll { it.category == category && it.brand == brand }
        val updated = preferences.copy(
            preferredBrandsJson = gson.toJson(brands),
            updatedAt = System.currentTimeMillis()
        )
        preferencesDao.updateUserPreferences(updated)
    }

    suspend fun addDislikedBrand(category: String, brand: String) {
        val preferences = getUserPreferences()
        val brands = parsePreferredBrands(preferences.dislikedBrandsJson).toMutableList()

        if (brands.none { it.category == category && it.brand == brand }) {
            brands.add(BrandPreference(category, brand))
            val updated = preferences.copy(
                dislikedBrandsJson = gson.toJson(brands),
                updatedAt = System.currentTimeMillis()
            )
            preferencesDao.updateUserPreferences(updated)
        }
    }

    suspend fun addAllergen(allergen: String) {
        val preferences = getUserPreferences()
        val allergens = parseStringList(preferences.allergensJson).toMutableList()

        if (!allergens.contains(allergen)) {
            allergens.add(allergen)
            val updated = preferences.copy(
                allergensJson = gson.toJson(allergens),
                updatedAt = System.currentTimeMillis()
            )
            preferencesDao.updateUserPreferences(updated)
        }
    }

    suspend fun removeAllergen(allergen: String) {
        val preferences = getUserPreferences()
        val allergens = parseStringList(preferences.allergensJson).toMutableList()

        allergens.remove(allergen)
        val updated = preferences.copy(
            allergensJson = gson.toJson(allergens),
            updatedAt = System.currentTimeMillis()
        )
        preferencesDao.updateUserPreferences(updated)
    }

    suspend fun addDietaryRestriction(restriction: String) {
        val preferences = getUserPreferences()
        val restrictions = parseStringList(preferences.dietaryRestrictionsJson).toMutableList()

        if (!restrictions.contains(restriction)) {
            restrictions.add(restriction)
            val updated = preferences.copy(
                dietaryRestrictionsJson = gson.toJson(restrictions),
                updatedAt = System.currentTimeMillis()
            )
            preferencesDao.updateUserPreferences(updated)
        }
    }

    suspend fun removeDietaryRestriction(restriction: String) {
        val preferences = getUserPreferences()
        val restrictions = parseStringList(preferences.dietaryRestrictionsJson).toMutableList()

        restrictions.remove(restriction)
        val updated = preferences.copy(
            dietaryRestrictionsJson = gson.toJson(restrictions),
            updatedAt = System.currentTimeMillis()
        )
        preferencesDao.updateUserPreferences(updated)
    }

    private fun parsePreferredBrands(json: String): List<BrandPreference> {
        return try {
            val type = object : TypeToken<List<BrandPreference>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseStringList(json: String): List<String> {
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Action Events
    fun getAllActionEvents(): Flow<List<ActionEventEntity>> = preferencesDao.getAllActionEvents()

    fun getActionEventsByType(type: ActionType): Flow<List<ActionEventEntity>> =
        preferencesDao.getActionEventsByType(type)

    fun getActionEventsByDateRange(startDate: Long, endDate: Long): Flow<List<ActionEventEntity>> =
        preferencesDao.getActionEventsByDateRange(startDate, endDate)

    suspend fun logActionEvent(
        type: ActionType,
        entityType: String? = null,
        entityId: String? = null,
        payload: Map<String, Any>? = null
    ) {
        val event = ActionEventEntity(
            type = type,
            entityType = entityType,
            entityId = entityId,
            payloadJson = payload?.let { gson.toJson(it) }
        )
        preferencesDao.insertActionEvent(event)
    }

    suspend fun cleanupOldActionEvents(daysToKeep: Int = 90) {
        val cutoffDate = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        preferencesDao.deleteOldActionEvents(cutoffDate)
    }

    // Pending Lookups (offline barcode handling)
    fun getUnresolvedPendingLookups(): Flow<List<PendingLookupEntity>> =
        preferencesDao.getUnresolvedPendingLookups()

    suspend fun addPendingLookup(barcode: String, context: String? = null): PendingLookupEntity {
        // Check if already pending
        val existing = preferencesDao.getPendingLookupByBarcode(barcode)
        if (existing != null) {
            return existing
        }

        val lookup = PendingLookupEntity(
            barcode = barcode,
            context = context
        )
        preferencesDao.insertPendingLookup(lookup)
        return lookup
    }

    suspend fun resolvePendingLookup(id: String) {
        preferencesDao.markPendingLookupResolved(id)
    }

    suspend fun incrementRetryCount(id: String) {
        preferencesDao.incrementRetryCount(id)
    }

    suspend fun getRetryablePendingLookups(): List<PendingLookupEntity> =
        preferencesDao.getRetryablePendingLookups()

    suspend fun cleanupResolvedLookups() {
        preferencesDao.deleteResolvedPendingLookups()
    }
}
