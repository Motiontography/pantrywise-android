package com.pantrywise.data.repository

import com.pantrywise.data.local.dao.ProductDao
import com.pantrywise.data.local.entity.ProductEntity
import com.pantrywise.data.remote.OpenFoodFactsService
import com.pantrywise.data.remote.ProductLookupResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

sealed class BarcodeScanResult {
    data class LocalProduct(val product: ProductEntity) : BarcodeScanResult()
    data class RemoteProduct(val product: ProductEntity) : BarcodeScanResult()
    data object NotFound : BarcodeScanResult()
    data class Error(val message: String) : BarcodeScanResult()
}

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao,
    private val openFoodFactsService: OpenFoodFactsService
) {
    fun getAllProducts(): Flow<List<ProductEntity>> = productDao.getAllProducts()

    suspend fun getProductById(id: String): ProductEntity? = productDao.getProductById(id)

    suspend fun getProductByBarcode(barcode: String): ProductEntity? = productDao.getProductByBarcode(barcode)

    fun searchProducts(query: String): Flow<List<ProductEntity>> = productDao.searchProducts(query)

    fun getProductsByCategory(category: String): Flow<List<ProductEntity>> = productDao.getProductsByCategory(category)

    suspend fun insertProduct(product: ProductEntity): Long = productDao.insert(product)

    suspend fun updateProduct(product: ProductEntity) = productDao.update(product)

    suspend fun deleteProduct(product: ProductEntity) = productDao.delete(product)

    suspend fun deleteProductById(id: String) = productDao.deleteById(id)

    suspend fun getProductCount(): Int = productDao.getProductCount()

    /**
     * Scans a barcode and returns the product if found locally or remotely.
     *
     * Priority:
     * 1. Local database (especially if user_confirmed = true)
     * 2. Open Food Facts API
     * 3. Not found
     */
    suspend fun scanBarcode(barcode: String): BarcodeScanResult {
        // First check local database
        val localProduct = productDao.getProductByBarcode(barcode)
        if (localProduct != null) {
            // If user confirmed, return immediately (highest authority)
            if (localProduct.userConfirmed) {
                return BarcodeScanResult.LocalProduct(localProduct)
            }
            // Otherwise, still return local but it could be updated
            return BarcodeScanResult.LocalProduct(localProduct)
        }

        // Try Open Food Facts API
        return when (val result = openFoodFactsService.lookupBarcode(barcode)) {
            is ProductLookupResult.Found -> BarcodeScanResult.RemoteProduct(result.product)
            is ProductLookupResult.NotFound -> BarcodeScanResult.NotFound
            is ProductLookupResult.Error -> BarcodeScanResult.Error(result.message)
        }
    }

    /**
     * Saves a product from remote lookup and marks it as user confirmed.
     */
    suspend fun saveAndConfirmProduct(product: ProductEntity): ProductEntity {
        val confirmedProduct = product.copy(
            userConfirmed = true,
            updatedAt = System.currentTimeMillis()
        )
        productDao.insert(confirmedProduct)
        return confirmedProduct
    }
}
