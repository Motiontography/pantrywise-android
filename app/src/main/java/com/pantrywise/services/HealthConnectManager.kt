package com.pantrywise.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Health Connect availability status
 */
sealed class HealthConnectStatus {
    data object Available : HealthConnectStatus()
    data object NotInstalled : HealthConnectStatus()
    data object NotSupported : HealthConnectStatus()
}

/**
 * Result wrapper for Health Connect operations
 */
sealed class HealthResult<T> {
    data class Success<T>(val data: T) : HealthResult<T>()
    data class Error<T>(val message: String, val exception: Exception? = null) : HealthResult<T>()
}

/**
 * Daily nutrition summary from Health Connect
 */
data class DailyNutritionSummary(
    val date: LocalDate,
    val totalCalories: Double = 0.0,
    val totalProtein: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val totalFat: Double = 0.0,
    val totalFiber: Double = 0.0,
    val totalSugar: Double = 0.0,
    val totalSodium: Double = 0.0,
    val totalWater: Double = 0.0,  // ml
    val recordCount: Int = 0
)

/**
 * Nutrition record to write to Health Connect
 */
data class NutritionInput(
    val name: String,
    val mealType: Int = MealType.MEAL_TYPE_UNKNOWN,
    val calories: Double? = null,
    val protein: Double? = null,
    val carbohydrates: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val sodium: Double? = null,
    val time: Instant = Instant.now()
)

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Required permissions for nutrition tracking
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(NutritionRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.getReadPermission(HydrationRecord::class),
            HealthPermission.getWritePermission(HydrationRecord::class)
        )

        // Play Store URI for Health Connect
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        private const val PLAY_STORE_URI = "market://details?id=$HEALTH_CONNECT_PACKAGE"
    }

    private var healthConnectClient: HealthConnectClient? = null

    /**
     * Check Health Connect availability
     */
    fun checkAvailability(): HealthConnectStatus {
        val status = HealthConnectClient.getSdkStatus(context)
        return when (status) {
            HealthConnectClient.SDK_AVAILABLE -> {
                healthConnectClient = HealthConnectClient.getOrCreate(context)
                HealthConnectStatus.Available
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                HealthConnectStatus.NotInstalled
            }
            else -> HealthConnectStatus.NotSupported
        }
    }

    /**
     * Get intent to install Health Connect from Play Store
     */
    fun getInstallIntent(): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(PLAY_STORE_URI)
            setPackage("com.android.vending")
        }
    }

    /**
     * Get permission controller contract for requesting permissions
     */
    fun getPermissionContract() = PermissionController.createRequestPermissionResultContract()

    /**
     * Check if all required permissions are granted
     */
    suspend fun hasAllPermissions(): Boolean = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext false
        try {
            val granted = client.permissionController.getGrantedPermissions()
            REQUIRED_PERMISSIONS.all { it in granted }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Write a nutrition record to Health Connect
     */
    suspend fun writeNutritionRecord(
        input: NutritionInput
    ): HealthResult<String> = withContext(Dispatchers.IO) {
        val client = healthConnectClient
            ?: return@withContext HealthResult.Error("Health Connect not available")

        try {
            val startTime = input.time
            val endTime = startTime.plusSeconds(1) // Duration-based record

            val nutritionRecord = NutritionRecord(
                startTime = startTime,
                endTime = endTime,
                startZoneOffset = ZonedDateTime.now().offset,
                endZoneOffset = ZonedDateTime.now().offset,
                name = input.name,
                mealType = input.mealType,
                energy = input.calories?.let {
                    androidx.health.connect.client.units.Energy.kilocalories(it)
                },
                protein = input.protein?.let {
                    androidx.health.connect.client.units.Mass.grams(it)
                },
                totalCarbohydrate = input.carbohydrates?.let {
                    androidx.health.connect.client.units.Mass.grams(it)
                },
                totalFat = input.fat?.let {
                    androidx.health.connect.client.units.Mass.grams(it)
                },
                dietaryFiber = input.fiber?.let {
                    androidx.health.connect.client.units.Mass.grams(it)
                },
                sugar = input.sugar?.let {
                    androidx.health.connect.client.units.Mass.grams(it)
                },
                sodium = input.sodium?.let {
                    androidx.health.connect.client.units.Mass.milligrams(it)
                }
            )

            val response = client.insertRecords(listOf(nutritionRecord))
            val recordId = response.recordIdsList.firstOrNull() ?: "unknown"

            HealthResult.Success(recordId)
        } catch (e: Exception) {
            HealthResult.Error("Failed to write nutrition: ${e.message}", e)
        }
    }

    /**
     * Write hydration record to Health Connect
     */
    suspend fun writeHydrationRecord(
        volumeMilliliters: Double,
        time: Instant = Instant.now()
    ): HealthResult<String> = withContext(Dispatchers.IO) {
        val client = healthConnectClient
            ?: return@withContext HealthResult.Error("Health Connect not available")

        try {
            val hydrationRecord = HydrationRecord(
                startTime = time,
                endTime = time.plusSeconds(1),
                startZoneOffset = ZonedDateTime.now().offset,
                endZoneOffset = ZonedDateTime.now().offset,
                volume = androidx.health.connect.client.units.Volume.milliliters(volumeMilliliters)
            )

            val response = client.insertRecords(listOf(hydrationRecord))
            val recordId = response.recordIdsList.firstOrNull() ?: "unknown"

            HealthResult.Success(recordId)
        } catch (e: Exception) {
            HealthResult.Error("Failed to write hydration: ${e.message}", e)
        }
    }

    /**
     * Read nutrition records for a date range
     */
    suspend fun readNutritionRecords(
        startTime: Instant,
        endTime: Instant
    ): HealthResult<List<NutritionRecord>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient
            ?: return@withContext HealthResult.Error("Health Connect not available")

        try {
            val request = ReadRecordsRequest(
                recordType = NutritionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val response = client.readRecords(request)
            HealthResult.Success(response.records)
        } catch (e: Exception) {
            HealthResult.Error("Failed to read nutrition: ${e.message}", e)
        }
    }

    /**
     * Read hydration records for a date range
     */
    suspend fun readHydrationRecords(
        startTime: Instant,
        endTime: Instant
    ): HealthResult<List<HydrationRecord>> = withContext(Dispatchers.IO) {
        val client = healthConnectClient
            ?: return@withContext HealthResult.Error("Health Connect not available")

        try {
            val request = ReadRecordsRequest(
                recordType = HydrationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val response = client.readRecords(request)
            HealthResult.Success(response.records)
        } catch (e: Exception) {
            HealthResult.Error("Failed to read hydration: ${e.message}", e)
        }
    }

    /**
     * Get daily nutrition summary for a specific date
     */
    suspend fun getDailyNutritionSummary(
        date: LocalDate
    ): HealthResult<DailyNutritionSummary> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()

        // Read nutrition records
        val nutritionResult = readNutritionRecords(startOfDay, endOfDay)
        val hydrationResult = readHydrationRecords(startOfDay, endOfDay)

        if (nutritionResult is HealthResult.Error) {
            return@withContext HealthResult.Error(nutritionResult.message)
        }

        val nutritionRecords = (nutritionResult as HealthResult.Success).data
        val hydrationRecords = (hydrationResult as? HealthResult.Success)?.data ?: emptyList()

        var totalCalories = 0.0
        var totalProtein = 0.0
        var totalCarbs = 0.0
        var totalFat = 0.0
        var totalFiber = 0.0
        var totalSugar = 0.0
        var totalSodium = 0.0
        var totalWater = 0.0

        for (record in nutritionRecords) {
            totalCalories += record.energy?.inKilocalories ?: 0.0
            totalProtein += record.protein?.inGrams ?: 0.0
            totalCarbs += record.totalCarbohydrate?.inGrams ?: 0.0
            totalFat += record.totalFat?.inGrams ?: 0.0
            totalFiber += record.dietaryFiber?.inGrams ?: 0.0
            totalSugar += record.sugar?.inGrams ?: 0.0
            totalSodium += record.sodium?.inMilligrams ?: 0.0
        }

        for (record in hydrationRecords) {
            totalWater += record.volume.inMilliliters
        }

        HealthResult.Success(
            DailyNutritionSummary(
                date = date,
                totalCalories = totalCalories,
                totalProtein = totalProtein,
                totalCarbs = totalCarbs,
                totalFat = totalFat,
                totalFiber = totalFiber,
                totalSugar = totalSugar,
                totalSodium = totalSodium,
                totalWater = totalWater,
                recordCount = nutritionRecords.size
            )
        )
    }

    /**
     * Get nutrition summaries for multiple days
     */
    suspend fun getWeeklyNutritionSummaries(
        startDate: LocalDate = LocalDate.now().minusDays(6)
    ): HealthResult<List<DailyNutritionSummary>> = withContext(Dispatchers.IO) {
        val summaries = mutableListOf<DailyNutritionSummary>()
        var currentDate = startDate

        while (!currentDate.isAfter(LocalDate.now())) {
            when (val result = getDailyNutritionSummary(currentDate)) {
                is HealthResult.Success -> summaries.add(result.data)
                is HealthResult.Error -> {
                    // Add empty summary for failed days
                    summaries.add(DailyNutritionSummary(date = currentDate))
                }
            }
            currentDate = currentDate.plusDays(1)
        }

        HealthResult.Success(summaries)
    }

    /**
     * Delete a nutrition record
     */
    suspend fun deleteNutritionRecord(recordId: String): HealthResult<Boolean> = withContext(Dispatchers.IO) {
        val client = healthConnectClient
            ?: return@withContext HealthResult.Error("Health Connect not available")

        try {
            client.deleteRecords(
                NutritionRecord::class,
                listOf(recordId),
                emptyList()
            )
            HealthResult.Success(true)
        } catch (e: Exception) {
            HealthResult.Error("Failed to delete record: ${e.message}", e)
        }
    }

    /**
     * Sync local nutrition log entry to Health Connect
     */
    suspend fun syncNutritionLogEntry(
        productName: String,
        servings: Double,
        calories: Double?,
        protein: Double?,
        carbohydrates: Double?,
        fat: Double?,
        fiber: Double?,
        sugar: Double?,
        sodium: Double?,
        mealType: String?,
        loggedAt: Long
    ): HealthResult<String> {
        val mealTypeInt = when (mealType?.lowercase()) {
            "breakfast" -> MealType.MEAL_TYPE_BREAKFAST
            "lunch" -> MealType.MEAL_TYPE_LUNCH
            "dinner" -> MealType.MEAL_TYPE_DINNER
            "snack" -> MealType.MEAL_TYPE_SNACK
            else -> MealType.MEAL_TYPE_UNKNOWN
        }

        return writeNutritionRecord(
            NutritionInput(
                name = productName,
                mealType = mealTypeInt,
                calories = calories?.times(servings),
                protein = protein?.times(servings),
                carbohydrates = carbohydrates?.times(servings),
                fat = fat?.times(servings),
                fiber = fiber?.times(servings),
                sugar = sugar?.times(servings),
                sodium = sodium?.times(servings),
                time = Instant.ofEpochMilli(loggedAt)
            )
        )
    }
}
