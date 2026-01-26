package com.pantrywise.domain.usecase

import com.pantrywise.data.local.dao.PreferencesDao
import com.pantrywise.data.local.entity.PendingLookupEntity
import com.pantrywise.data.local.entity.ProductEntity
import com.pantrywise.data.repository.BarcodeScanResult
import com.pantrywise.data.repository.ProductRepository
import javax.inject.Inject

sealed class ScanResult {
    data class Success(val product: ProductEntity, val isNew: Boolean) : ScanResult()
    data class NeedsConfirmation(val product: ProductEntity) : ScanResult()
    data class NotFound(val barcode: String) : ScanResult()
    data class PendingLookup(val lookup: PendingLookupEntity) : ScanResult()
    data class Error(val message: String, val errorCode: String) : ScanResult()
}

class ScanProductUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val preferencesDao: PreferencesDao
) {
    /**
     * Scans a barcode and returns the appropriate result.
     *
     * Flow:
     * 1. Check local database for existing product
     * 2. If found locally with user_confirmed = true, return immediately
     * 3. If found locally but not confirmed, still return (local takes precedence)
     * 4. If not found locally, query Open Food Facts
     * 5. If found remotely, return for user confirmation
     * 6. If not found remotely and offline, create pending lookup
     * 7. If not found at all, return NotFound
     */
    suspend operator fun invoke(
        barcode: String,
        context: String? = null,
        isOffline: Boolean = false
    ): ScanResult {
        // Validate barcode format
        if (!isValidBarcode(barcode)) {
            return ScanResult.Error(
                "Barcode format not supported. Supported: UPC-A, UPC-E, EAN-13, EAN-8.",
                "SCAN_003"
            )
        }

        return when (val result = productRepository.scanBarcode(barcode)) {
            is BarcodeScanResult.LocalProduct -> {
                ScanResult.Success(result.product, isNew = false)
            }

            is BarcodeScanResult.RemoteProduct -> {
                // Product found in Open Food Facts, needs user confirmation
                ScanResult.NeedsConfirmation(result.product)
            }

            is BarcodeScanResult.NotFound -> {
                if (isOffline) {
                    // Store for later lookup
                    val pendingLookup = PendingLookupEntity(
                        barcode = barcode,
                        context = context
                    )
                    preferencesDao.insertPendingLookup(pendingLookup)
                    ScanResult.PendingLookup(pendingLookup)
                } else {
                    ScanResult.NotFound(barcode)
                }
            }

            is BarcodeScanResult.Error -> {
                if (isOffline || result.message.contains("network", ignoreCase = true)) {
                    // Network error, store for later
                    val pendingLookup = PendingLookupEntity(
                        barcode = barcode,
                        context = context
                    )
                    preferencesDao.insertPendingLookup(pendingLookup)
                    ScanResult.PendingLookup(pendingLookup)
                } else {
                    ScanResult.Error(
                        "Product lookup timed out. Check your connection and try again.",
                        "LOOKUP_003"
                    )
                }
            }
        }
    }

    /**
     * Confirms a product from remote lookup and saves it locally.
     */
    suspend fun confirmProduct(product: ProductEntity): ProductEntity {
        return productRepository.saveAndConfirmProduct(product)
    }

    /**
     * Creates a manual product entry for barcodes not found in any database.
     */
    suspend fun createManualProduct(product: ProductEntity): ProductEntity {
        val manualProduct = product.copy(
            userConfirmed = true,
            updatedAt = System.currentTimeMillis()
        )
        productRepository.insertProduct(manualProduct)
        return manualProduct
    }

    /**
     * Validates barcode format.
     * Supports: UPC-A (12 digits), UPC-E (8 digits), EAN-13 (13 digits), EAN-8 (8 digits)
     */
    private fun isValidBarcode(barcode: String): Boolean {
        val cleanBarcode = barcode.trim()

        // Check if it's all digits
        if (!cleanBarcode.all { it.isDigit() }) {
            return false
        }

        // Check length (common barcode formats)
        return when (cleanBarcode.length) {
            8 -> true   // UPC-E or EAN-8
            12 -> true  // UPC-A
            13 -> true  // EAN-13
            14 -> true  // ITF-14
            else -> false
        }
    }
}
