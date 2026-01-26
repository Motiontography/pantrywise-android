package com.pantrywise.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.pantrywise.data.local.entity.InventoryItemEntity
import com.pantrywise.data.repository.InventoryRepository
import com.pantrywise.data.repository.MinimumStockRepository
import com.pantrywise.data.repository.ProductRepository
import com.pantrywise.services.PantryNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Worker that checks for expiring items and low stock, sending notifications as needed.
 * Runs daily by default.
 */
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val inventoryRepository: InventoryRepository,
    private val minimumStockRepository: MinimumStockRepository,
    private val productRepository: ProductRepository,
    private val notificationManager: PantryNotificationManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            checkExpiringItems()
            checkLowStockItems()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun checkExpiringItems() {
        val expiringItems = inventoryRepository.getExpiringItems(daysAhead = 3).first()

        if (expiringItems.isEmpty()) return

        // Build a map of product names for all items
        val productNames = mutableMapOf<String, String>()
        expiringItems.forEach { item ->
            if (!productNames.containsKey(item.productId)) {
                val product = productRepository.getProductById(item.productId)
                productNames[item.productId] = product?.name ?: "Unknown Item"
            }
        }

        // Helper to get product name
        fun InventoryItemEntity.getProductName() = productNames[productId] ?: "Unknown Item"

        // Group by days until expiry
        val expiredItems = expiringItems.filter {
            it.daysUntilExpiration != null && it.daysUntilExpiration!! < 0
        }
        val expiringToday = expiringItems.filter { it.daysUntilExpiration == 0 }
        val expiringTomorrow = expiringItems.filter { it.daysUntilExpiration == 1 }
        val expiringLater = expiringItems.filter {
            it.daysUntilExpiration != null && it.daysUntilExpiration!! in 2..3
        }

        // Send individual notifications for items expiring today or expired
        expiredItems.forEach { item ->
            notificationManager.showExpirationAlert(
                itemName = item.getProductName(),
                daysUntilExpiry = item.daysUntilExpiration ?: -1,
                itemId = item.id
            )
        }

        expiringToday.forEach { item ->
            notificationManager.showExpirationAlert(
                itemName = item.getProductName(),
                daysUntilExpiry = 0,
                itemId = item.id
            )
        }

        // Send summary for items expiring tomorrow or later
        val laterItems = expiringTomorrow + expiringLater
        if (laterItems.isNotEmpty()) {
            notificationManager.showExpiringSummary(
                count = laterItems.size,
                itemNames = laterItems.map { it.getProductName() }
            )
        }
    }

    private suspend fun checkLowStockItems() {
        val alerts = minimumStockRepository.checkStapleStockLevels()

        if (alerts.isEmpty()) return

        // Send summary notification
        notificationManager.showLowStockSummary(
            count = alerts.size,
            itemNames = alerts.map { it.rule.productName }
        )
    }

    companion object {
        const val WORK_NAME = "notification_check_worker"

        /**
         * Schedule the notification worker to run daily
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
                1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS) // Don't run immediately on app start
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
        }

        /**
         * Run the notification check immediately (for testing)
         */
        fun runNow(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .build()

            WorkManager.getInstance(context)
                .enqueue(workRequest)
        }

        /**
         * Cancel scheduled notification checks
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
        }
    }
}
