package com.pantrywise.services

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.pantrywise.data.remote.OpenAIApi
import com.pantrywise.data.remote.OpenAIChatRequest
import com.pantrywise.data.remote.OpenAIContentPart
import com.pantrywise.data.remote.OpenAIImageUrl
import com.pantrywise.data.remote.OpenAIMessage
import com.pantrywise.data.remote.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI service for AI-powered features:
 * - AI Recipe Discovery
 * - Ingredient parsing
 * - Product vision recognition
 * - Smart shelf snap (product + price)
 * - Voice shopping list parsing
 * - Product comparison
 * - Pantry organization suggestions
 */
@Singleton
class OpenAIService @Inject constructor(
    private val openAIApi: OpenAIApi,
    private val secureStorageService: SecureStorageService
) {
    companion object {
        private const val TAG = "OpenAIService"
        private const val MODEL = "gpt-5.2"
    }

    private val gson = Gson()

    sealed class OpenAIError : Exception() {
        object NotConfigured : OpenAIError()
        object InvalidResponse : OpenAIError()
        object InvalidAPIKey : OpenAIError()
        object RateLimited : OpenAIError()
        data class NetworkError(override val cause: Throwable) : OpenAIError()
        data class ApiError(override val message: String) : OpenAIError()
        data class ParsingError(override val message: String) : OpenAIError()
    }

    val isConfigured: Boolean
        get() {
            val key = secureStorageService.getApiKey()
            return !key.isNullOrEmpty() && key.startsWith("sk-")
        }

    private fun getAuthHeader(): String {
        val apiKey = secureStorageService.getApiKey()
            ?: throw OpenAIError.NotConfigured
        return "Bearer $apiKey"
    }

    /**
     * Discover a recipe based on a natural language query
     */
    suspend fun discoverRecipe(query: String): AIRecipeResult = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are a professional chef and recipe expert for a grocery shopping app.
            The user wants to discover a recipe. Generate a complete, practical recipe based on their query.

            IMPORTANT: Return accurate, tested recipes with realistic ingredient quantities.

            Return a JSON object with:
            - name: Recipe name (e.g., "Classic Chicken Parmesan")
            - description: 1-2 sentence description of the dish
            - prepTimeMinutes: Preparation time in minutes (null if unknown)
            - cookTimeMinutes: Cooking time in minutes (null if unknown)
            - servings: Number of servings (default 4)
            - ingredients: Array of ingredients, each with:
              - name: Ingredient name (e.g., "chicken breast", "parmesan cheese")
              - quantity: Numeric quantity (e.g., 2, 0.5, 1)
              - unit: Unit of measurement (e.g., "lbs", "cups", "each", "oz", "cloves")
              - notes: Optional notes like "boneless, skinless" or "freshly grated" (null if none)
            - instructions: Array of step-by-step instruction strings
            - cuisine: Cuisine type (e.g., "Italian", "Mexican", "American", null if general)
            - tags: Array of tags (e.g., ["dinner", "comfort food", "kid-friendly"])

            INGREDIENT GUIDELINES:
            - Use standard grocery store quantities (1 lb chicken, not 453g)
            - Include all ingredients including oil, salt, pepper, etc.
            - Use practical units: "each" for items like eggs, lemons; "lbs" for meat; "cups" for liquids
            - Be specific: "garlic cloves" not just "garlic"

            Return ONLY valid JSON, no explanation or markdown.
        """.trimIndent()

        val request = OpenAIChatRequest(
            model = MODEL,
            messages = listOf(
                OpenAIMessage(role = "system", content = systemPrompt),
                OpenAIMessage(role = "user", content = "I want to make: $query")
            ),
            temperature = 0.3,
            max_completion_tokens = 3000
        )

        val response = makeRequest(request)
        parseJsonResponse(response)
    }

    /**
     * Parse recipe ingredients from text
     */
    suspend fun parseRecipeIngredients(text: String): List<ParsedIngredient> = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are a recipe ingredient parser. Parse the given ingredients into structured JSON format.
            For each ingredient extract:
            - name: the ingredient name (e.g., "butter", "all-purpose flour")
            - quantity: numeric amount as a number (e.g., 2, 0.5), null if not specified
            - unit: measurement unit (e.g., "cups", "tablespoons", "each"), null if not specified
            - original: the original text exactly as provided

            Return ONLY a valid JSON array, no explanation or markdown.
        """.trimIndent()

        val request = OpenAIChatRequest(
            model = MODEL,
            messages = listOf(
                OpenAIMessage(role = "system", content = systemPrompt),
                OpenAIMessage(role = "user", content = "Parse these recipe ingredients:\n$text")
            ),
            temperature = 0.1,
            max_completion_tokens = 2000
        )

        val response = makeRequest(request)
        parseJsonArrayResponse(response)
    }

    /**
     * Analyze a product image using vision
     */
    suspend fun analyzeProductImage(imageData: ByteArray): ProductVisionResult = withContext(Dispatchers.IO) {
        val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)

        val systemPrompt = """
            You are a product identification assistant for a grocery/pantry management app.
            Analyze the product image and extract information.

            For each product, identify:
            - name: The product name
            - brand: The brand name if visible, null if not visible
            - category: Product category (Produce, Dairy, Meat, Bakery, Frozen, Beverages, etc.)
            - quantity: Package size/quantity if visible, null if not visible
            - estimatedPrice: Price if visible on tag/label, null if not visible
            - isFood: true if it's a food/beverage item, false for non-food
            - suggestedLocation: Where this should be stored - "pantry", "fridge", "freezer", "garage", or "other"
            - confidence: Your confidence in the identification (0.0 to 1.0)

            Return ONLY valid JSON, no explanation or markdown.
        """.trimIndent()

        val content = listOf(
            OpenAIContentPart(
                type = "image_url",
                image_url = OpenAIImageUrl(
                    url = "data:image/jpeg;base64,$base64Image",
                    detail = "high"
                )
            ),
            OpenAIContentPart(
                type = "text",
                text = "Identify this product and extract all visible information."
            )
        )

        val request = OpenAIChatRequest(
            model = MODEL,
            messages = listOf(
                OpenAIMessage(role = "system", content = systemPrompt),
                OpenAIMessage(role = "user", content = content)
            ),
            temperature = 0.1,
            max_completion_tokens = 500
        )

        val response = makeRequest(request)
        parseJsonResponse(response)
    }

    /**
     * Smart Shelf Snap - analyze product with price tag
     */
    suspend fun analyzeShelfSnap(imageData: ByteArray): SmartShelfSnapResult = withContext(Dispatchers.IO) {
        val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)

        val systemPrompt = """
            You are an expert retail price tag reader and product identifier.
            The user has photographed a product on a store shelf WITH its price tag visible.

            YOUR PRIMARY GOAL: Extract the EXACT price from the shelf price tag.

            PRICE TAG READING RULES:
            1. LOOK FOR THE SHELF PRICE TAG - usually a white/yellow label below or near the product
            2. The LARGE NUMBER on the price tag is the current selling price
            3. Price format is typically: dollars.cents (e.g., 5.99 means ${"$"}5.99)
            4. UNIT PRICE is smaller text, often says "per oz", "per lb", etc.
            5. SALE TAGS: Look for red/yellow backgrounds, "SALE", crossed-out prices

            Return a JSON object with these fields:
            - name, brand, category (product identification)
            - price, pricePerUnit, pricePerUnitLabel, originalPrice, isOnSale (pricing)
            - packageSize, itemCount, isMultiPack, packCount (quantity)
            - isFood, suggestedLocation (storage)
            - confidence, priceConfidence (0.0-1.0)

            Return ONLY valid JSON, no explanation or markdown.
        """.trimIndent()

        val content = listOf(
            OpenAIContentPart(
                type = "image_url",
                image_url = OpenAIImageUrl(
                    url = "data:image/jpeg;base64,$base64Image",
                    detail = "high"
                )
            ),
            OpenAIContentPart(
                type = "text",
                text = "Read the price tag in this photo. What is the EXACT price shown? Also identify the product name, brand, and size."
            )
        )

        val request = OpenAIChatRequest(
            model = MODEL,
            messages = listOf(
                OpenAIMessage(role = "system", content = systemPrompt),
                OpenAIMessage(role = "user", content = content)
            ),
            temperature = 0.1,
            max_completion_tokens = 800
        )

        val response = makeRequest(request)
        parseJsonResponse(response)
    }

    /**
     * Parse shopping list from voice input
     */
    suspend fun parseShoppingListFromVoice(text: String): List<ParsedShoppingItem> = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are a shopping list parser. Parse the spoken text into individual shopping items.

            IMPORTANT: Only extract items that are explicitly mentioned. Do NOT add items that weren't said.

            For each item extract:
            - name: the item name (e.g., "Chicken Breast", "Orange Juice", "Eggs")
            - quantity: numeric amount - "a dozen" = 12, "half dozen" = 6, "couple" = 2, "a few" = 3. Default to 1 if not specified.
            - unit: measurement unit if mentioned (e.g., "lbs", "gallons"), null if just counting items.

            Return ONLY a valid JSON array, no explanation or markdown.
        """.trimIndent()

        val request = OpenAIChatRequest(
            model = MODEL,
            messages = listOf(
                OpenAIMessage(role = "system", content = systemPrompt),
                OpenAIMessage(role = "user", content = "Parse this spoken shopping list:\n$text")
            ),
            temperature = 0.1,
            max_completion_tokens = 2000
        )

        val response = makeRequest(request)
        parseJsonArrayResponse(response)
    }

    /**
     * Compare two products
     */
    suspend fun compareProducts(
        product1Name: String, product1Price: Double, product1Size: String, product1Nutrition: String?,
        product2Name: String, product2Price: Double, product2Size: String, product2Nutrition: String?
    ): ProductComparisonResult = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are a shopping assistant helping compare two products.
            Analyze the products and determine:
            1. Which is the better deal (price per unit)
            2. Which is the healthier option (if nutrition info provided)

            Return JSON with:
            - betterDeal: "product1", "product2", or "equal"
            - healthierOption: "product1", "product2", "equal", or "unknown"
            - pricePerUnit1, pricePerUnit2: calculated price per standard unit
            - unitUsed: the unit used for comparison
            - nutritionSummary: brief comparison
            - recommendation: 1-2 sentence overall recommendation

            Return ONLY valid JSON, no explanation or markdown.
        """.trimIndent()

        var userMessage = """
            Compare these two products:

            Product 1: $product1Name
            - Price: ${"$"}${String.format("%.2f", product1Price)}
            - Size: $product1Size
        """.trimIndent()
        if (product1Nutrition != null) {
            userMessage += "\n- Nutrition: $product1Nutrition"
        }
        userMessage += "\n\nProduct 2: $product2Name\n- Price: ${"$"}${String.format("%.2f", product2Price)}\n- Size: $product2Size"
        if (product2Nutrition != null) {
            userMessage += "\n- Nutrition: $product2Nutrition"
        }

        val request = OpenAIChatRequest(
            model = MODEL,
            messages = listOf(
                OpenAIMessage(role = "system", content = systemPrompt),
                OpenAIMessage(role = "user", content = userMessage)
            ),
            temperature = 0.1,
            max_completion_tokens = 500
        )

        val response = makeRequest(request)
        parseJsonResponse(response)
    }

    /**
     * Suggest pantry organization
     */
    suspend fun suggestPantryOrganization(items: List<String>): PantryOrganizationResponse = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are a professional home organizer specializing in kitchen and pantry organization.
            Given a list of pantry items, suggest how to organize them into logical zones/sections.

            Return a JSON object with:
            - zones: array of organization zones, each with:
              - zoneName: name of the zone
              - description: brief description
              - items: array of item names for this zone
              - tips: 1-2 specific tips
            - generalTips: 2-3 general pantry organization tips

            Return ONLY valid JSON, no explanation or markdown.
        """.trimIndent()

        val request = OpenAIChatRequest(
            model = MODEL,
            messages = listOf(
                OpenAIMessage(role = "system", content = systemPrompt),
                OpenAIMessage(role = "user", content = "Organize these pantry items into zones:\n${items.joinToString(", ")}")
            ),
            temperature = 0.3,
            max_completion_tokens = 3000
        )

        val response = makeRequest(request)
        parseJsonResponse(response)
    }

    /**
     * Test if an API key is valid
     */
    suspend fun testApiKey(apiKey: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val request = OpenAIChatRequest(
                model = MODEL,
                messages = listOf(
                    OpenAIMessage(role = "user", content = "Say 'OK' if this works")
                ),
                max_completion_tokens = 5
            )

            val response = openAIApi.createChatCompletion("Bearer $apiKey", request)

            if (response.isSuccessful) {
                Pair(true, null)
            } else {
                val errorBody = response.errorBody()?.string()
                Pair(false, "HTTP ${response.code()}: $errorBody")
            }
        } catch (e: Exception) {
            Pair(false, "Network error: ${e.localizedMessage}")
        }
    }

    private suspend fun makeRequest(request: OpenAIChatRequest): String {
        try {
            val response = openAIApi.createChatCompletion(getAuthHeader(), request)

            when (response.code()) {
                200 -> {
                    val body = response.body()
                        ?: throw OpenAIError.InvalidResponse
                    return body.choices.firstOrNull()?.message?.content
                        ?: throw OpenAIError.InvalidResponse
                }
                401 -> throw OpenAIError.InvalidAPIKey
                429 -> throw OpenAIError.RateLimited
                else -> {
                    val errorBody = response.errorBody()?.string()
                    throw OpenAIError.ApiError("HTTP ${response.code()}: $errorBody")
                }
            }
        } catch (e: OpenAIError) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Network error", e)
            throw OpenAIError.NetworkError(e)
        }
    }

    private inline fun <reified T> parseJsonResponse(content: String): T {
        val cleanedJson = cleanJsonResponse(content)
        return try {
            gson.fromJson(cleanedJson, T::class.java)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON parsing error: $cleanedJson", e)
            throw OpenAIError.ParsingError("Failed to parse JSON: ${e.localizedMessage}")
        }
    }

    private inline fun <reified T> parseJsonArrayResponse(content: String): List<T> {
        val cleanedJson = cleanJsonResponse(content)
        return try {
            val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, T::class.java).type
            gson.fromJson(cleanedJson, type)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON array parsing error: $cleanedJson", e)
            throw OpenAIError.ParsingError("Failed to parse JSON array: ${e.localizedMessage}")
        }
    }

    private fun cleanJsonResponse(content: String): String {
        var json = content.trim()
        if (json.startsWith("```json")) {
            json = json.removePrefix("```json")
        }
        if (json.startsWith("```")) {
            json = json.removePrefix("```")
        }
        if (json.endsWith("```")) {
            json = json.removeSuffix("```")
        }
        return json.trim()
    }
}
