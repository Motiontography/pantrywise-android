package com.pantrywise.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

data class OpenFoodFactsResponse(
    val status: Int,
    val status_verbose: String,
    val product: OpenFoodFactsProduct?
)

data class OpenFoodFactsProduct(
    val code: String?,
    val product_name: String?,
    val brands: String?,
    val categories: String?,
    val image_url: String?,
    val image_front_url: String?,
    val quantity: String?,
    val serving_size: String?,
    val nutriscore_grade: String?,
    val nova_group: Int?,
    val ingredients_text: String?
)

interface OpenFoodFactsApi {
    @GET("api/v0/product/{barcode}.json")
    suspend fun getProduct(@Path("barcode") barcode: String): Response<OpenFoodFactsResponse>

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"
    }
}
