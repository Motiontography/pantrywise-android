package com.pantrywise.data.remote

import com.pantrywise.data.local.entity.ProductEntity
import com.pantrywise.domain.model.SourceType
import com.pantrywise.domain.model.Unit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class ProductLookupResult {
    data class Found(val product: ProductEntity) : ProductLookupResult()
    data object NotFound : ProductLookupResult()
    data class Error(val message: String) : ProductLookupResult()
}

@Singleton
class OpenFoodFactsService @Inject constructor(
    private val api: OpenFoodFactsApi
) {
    suspend fun lookupBarcode(barcode: String): ProductLookupResult = withContext(Dispatchers.IO) {
        try {
            val response = api.getProduct(barcode)

            if (!response.isSuccessful) {
                return@withContext ProductLookupResult.Error("Network error: ${response.code()}")
            }

            val body = response.body()

            if (body == null || body.status != 1 || body.product == null) {
                return@withContext ProductLookupResult.NotFound
            }

            val offProduct = body.product
            val productName = offProduct.product_name

            if (productName.isNullOrBlank()) {
                return@withContext ProductLookupResult.NotFound
            }

            val product = ProductEntity(
                barcode = barcode,
                name = productName,
                brand = offProduct.brands?.split(",")?.firstOrNull()?.trim(),
                category = parseCategory(offProduct.categories),
                defaultUnit = Unit.EACH,
                imageUrl = offProduct.image_front_url ?: offProduct.image_url,
                source = SourceType.OPEN_FOOD_FACTS,
                userConfirmed = false
            )

            ProductLookupResult.Found(product)
        } catch (e: Exception) {
            ProductLookupResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun parseCategory(categories: String?): String {
        if (categories.isNullOrBlank()) {
            return "Other"
        }

        val categoryList = categories.lowercase().split(",").map { it.trim() }

        // Map Open Food Facts categories to our predefined categories
        val categoryMapping = mapOf(
            "produce" to listOf("fresh vegetables", "fresh fruits", "vegetables", "fruits", "produce"),
            "Dairy & Eggs" to listOf("dairy", "eggs", "milk", "cheese", "yogurt", "butter"),
            "Meat & Seafood" to listOf("meat", "seafood", "fish", "chicken", "beef", "pork", "poultry"),
            "Frozen" to listOf("frozen", "ice cream", "frozen vegetables", "frozen meals"),
            "Pantry Staples" to listOf("pasta", "rice", "flour", "sugar", "oil", "vinegar", "grains", "cereals"),
            "Snacks" to listOf("snacks", "chips", "crackers", "cookies", "candy", "chocolate"),
            "Beverages" to listOf("beverages", "drinks", "juice", "soda", "water", "tea", "coffee"),
            "Bakery" to listOf("bread", "bakery", "pastries", "baked goods"),
            "Condiments & Sauces" to listOf("condiments", "sauces", "ketchup", "mustard", "mayonnaise", "dressing"),
            "Canned Goods" to listOf("canned", "preserved", "soups", "beans"),
            "Cleaning Supplies" to listOf("cleaning", "household", "detergent"),
            "Personal Care" to listOf("personal care", "hygiene", "soap", "shampoo"),
            "Baby & Kids" to listOf("baby", "infant", "kids"),
            "Pet Supplies" to listOf("pet", "dog", "cat", "animal")
        )

        for ((category, keywords) in categoryMapping) {
            if (categoryList.any { cat -> keywords.any { keyword -> cat.contains(keyword) } }) {
                return category
            }
        }

        return "Other"
    }
}
