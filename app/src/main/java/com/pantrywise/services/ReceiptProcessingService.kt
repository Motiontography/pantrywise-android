package com.pantrywise.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pantrywise.data.local.dao.ReceiptDao
import com.pantrywise.data.local.entity.ReceiptEntity
import com.pantrywise.data.local.entity.ReceiptLineItem
import com.pantrywise.data.local.entity.ReceiptStatus
import com.pantrywise.data.remote.OpenAIApi
import com.pantrywise.data.remote.OpenAIChatRequest
import com.pantrywise.data.remote.OpenAIContentPart
import com.pantrywise.data.remote.OpenAIImageUrl
import com.pantrywise.data.remote.OpenAIMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Result of receipt processing
 */
data class ReceiptProcessingResult(
    val receipt: ReceiptEntity,
    val lineItems: List<ReceiptLineItem>,
    val rawText: String,
    val confidence: Float
)

/**
 * Parsed receipt data from AI
 */
data class ParsedReceiptData(
    val storeName: String? = null,
    val storeAddress: String? = null,
    val receiptDate: String? = null,
    val receiptNumber: String? = null,
    val subtotal: Double? = null,
    val tax: Double? = null,
    val total: Double? = null,
    val paymentMethod: String? = null,
    val items: List<ParsedReceiptItem> = emptyList(),
    val confidence: Float = 0.5f
)

data class ParsedReceiptItem(
    val description: String,
    val quantity: Double = 1.0,
    val unitPrice: Double? = null,
    val totalPrice: Double,
    val isDiscount: Boolean = false,
    val isTax: Boolean = false,
    val category: String? = null,
    val confidence: Float = 1.0f
)

/**
 * Service for processing receipt images using ML Kit OCR and OpenAI
 */
@Singleton
class ReceiptProcessingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val receiptDao: ReceiptDao,
    private val openAIApi: OpenAIApi,
    private val secureStorageService: SecureStorageService
) {
    companion object {
        private const val TAG = "ReceiptProcessingService"
        private const val MODEL = "gpt-5.2"
        private const val MAX_IMAGE_SIZE = 1024
    }

    private val textRecognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val gson = Gson()

    val isAIConfigured: Boolean
        get() {
            val key = secureStorageService.getApiKey()
            return !key.isNullOrEmpty() && key.startsWith("sk-")
        }

    /**
     * Process a receipt image and extract data
     */
    suspend fun processReceipt(imageUri: Uri): ReceiptProcessingResult = withContext(Dispatchers.IO) {
        // Create initial receipt record
        val receipt = ReceiptEntity(
            id = UUID.randomUUID().toString(),
            imageUri = imageUri.toString(),
            status = ReceiptStatus.PROCESSING
        )
        receiptDao.insertReceipt(receipt)

        try {
            // Load and resize the image
            val bitmap = loadAndResizeBitmap(imageUri)

            // Step 1: Extract text using ML Kit OCR
            val rawText = extractTextFromImage(bitmap)
            Log.d(TAG, "Extracted raw text: ${rawText.take(500)}...")

            // Step 2: Parse the receipt using AI (if available) or local parsing
            val parsedData = if (isAIConfigured) {
                parseReceiptWithAI(bitmap, rawText)
            } else {
                parseReceiptLocally(rawText)
            }

            // Step 3: Convert to entities
            val lineItems = parsedData.items.map { item ->
                ReceiptLineItem(
                    description = item.description,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    totalPrice = item.totalPrice,
                    isDiscount = item.isDiscount,
                    isTax = item.isTax,
                    category = item.category,
                    confidence = item.confidence
                )
            }

            // Parse date
            val receiptDate = parseReceiptDate(parsedData.receiptDate)

            // Update receipt with extracted data
            val updatedReceipt = receipt.copy(
                storeName = parsedData.storeName,
                storeAddress = parsedData.storeAddress,
                subtotal = parsedData.subtotal,
                tax = parsedData.tax,
                total = parsedData.total,
                paymentMethod = parsedData.paymentMethod,
                receiptDate = receiptDate,
                receiptNumber = parsedData.receiptNumber,
                itemsJson = gson.toJson(lineItems),
                rawText = rawText,
                status = if (parsedData.confidence >= 0.7f) ReceiptStatus.COMPLETED else ReceiptStatus.NEEDS_REVIEW,
                confidence = parsedData.confidence,
                updatedAt = System.currentTimeMillis()
            )

            receiptDao.updateReceipt(updatedReceipt)

            ReceiptProcessingResult(
                receipt = updatedReceipt,
                lineItems = lineItems,
                rawText = rawText,
                confidence = parsedData.confidence
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing receipt", e)
            receiptDao.updateReceiptStatus(
                id = receipt.id,
                status = ReceiptStatus.FAILED,
                error = e.message
            )
            throw e
        }
    }

    /**
     * Extract text from bitmap using ML Kit
     */
    private suspend fun extractTextFromImage(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        textRecognizer.process(inputImage)
            .addOnSuccessListener { result ->
                val extractedText = result.text
                continuation.resume(extractedText)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }

    /**
     * Parse receipt using OpenAI Vision
     */
    private suspend fun parseReceiptWithAI(bitmap: Bitmap, rawText: String): ParsedReceiptData {
        val base64Image = bitmapToBase64(bitmap)

        val systemPrompt = """
            You are a receipt parsing expert. Analyze the receipt image and extracted text to identify:

            1. Store Information:
               - Store name
               - Store address (if visible)
               - Receipt number/transaction ID

            2. Financial Information:
               - Subtotal (before tax)
               - Tax amount
               - Total amount
               - Payment method (cash, credit card, etc.)

            3. Date and Time:
               - Receipt date (in ISO format if possible, e.g., "2024-01-15")

            4. Line Items:
               - Item description
               - Quantity (default 1 if not shown)
               - Unit price (if shown)
               - Total price for the item
               - Whether it's a discount (negative amount)
               - Category (Produce, Dairy, Meat, Bakery, Frozen, Beverages, Household, etc.)

            IMPORTANT:
            - Extract ALL line items, including discounts
            - Prices should be positive numbers (use isDiscount=true for discounts)
            - Match item totals to the receipt total when possible
            - Return confidence between 0 and 1 based on legibility

            Return ONLY valid JSON matching this structure:
            {
                "storeName": "string or null",
                "storeAddress": "string or null",
                "receiptDate": "YYYY-MM-DD or null",
                "receiptNumber": "string or null",
                "subtotal": number or null,
                "tax": number or null,
                "total": number or null,
                "paymentMethod": "string or null",
                "items": [
                    {
                        "description": "string",
                        "quantity": number,
                        "unitPrice": number or null,
                        "totalPrice": number,
                        "isDiscount": boolean,
                        "isTax": boolean,
                        "category": "string or null",
                        "confidence": number
                    }
                ],
                "confidence": number
            }
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
                text = "Parse this receipt. Here's the OCR text to help:\n\n$rawText"
            )
        )

        val request = OpenAIChatRequest(
            model = MODEL,
            messages = listOf(
                OpenAIMessage(role = "system", content = systemPrompt),
                OpenAIMessage(role = "user", content = content)
            ),
            temperature = 0.1,
            max_completion_tokens = 4000
        )

        try {
            val apiKey = secureStorageService.getApiKey() ?: throw Exception("API key not configured")
            val response = openAIApi.createChatCompletion("Bearer $apiKey", request)

            if (response.isSuccessful) {
                val responseText = response.body()?.choices?.firstOrNull()?.message?.content
                    ?: throw Exception("Empty response")

                return parseJsonResponse(responseText)
            } else {
                Log.e(TAG, "AI API error: ${response.code()}")
                return parseReceiptLocally(rawText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling AI API", e)
            return parseReceiptLocally(rawText)
        }
    }

    /**
     * Fallback local parsing without AI
     */
    private fun parseReceiptLocally(rawText: String): ParsedReceiptData {
        val lines = rawText.lines().filter { it.isNotBlank() }

        // Try to extract store name (usually first non-empty line)
        val storeName = lines.firstOrNull()?.take(50)

        // Try to find total
        val totalRegex = Regex("""(?:TOTAL|Total|GRAND TOTAL|Amount Due)[:\s]*\$?(\d+\.?\d*)""", RegexOption.IGNORE_CASE)
        val total = totalRegex.find(rawText)?.groupValues?.get(1)?.toDoubleOrNull()

        // Try to find tax
        val taxRegex = Regex("""(?:TAX|Tax|Sales Tax)[:\s]*\$?(\d+\.?\d*)""", RegexOption.IGNORE_CASE)
        val tax = taxRegex.find(rawText)?.groupValues?.get(1)?.toDoubleOrNull()

        // Try to find date
        val dateRegex = Regex("""(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""")
        val dateString = dateRegex.find(rawText)?.value

        // Extract line items (simple pattern: text followed by price)
        val itemRegex = Regex("""(.+?)\s+\$?(\d+\.\d{2})\s*$""")
        val items = lines.mapNotNull { line ->
            itemRegex.find(line)?.let { match ->
                val description = match.groupValues[1].trim()
                val price = match.groupValues[2].toDoubleOrNull() ?: return@let null

                // Skip if it looks like a total/subtotal/tax line
                if (description.contains(Regex("(?:total|tax|subtotal|change|cash|credit)", RegexOption.IGNORE_CASE))) {
                    return@let null
                }

                ParsedReceiptItem(
                    description = description,
                    totalPrice = price,
                    confidence = 0.5f
                )
            }
        }

        return ParsedReceiptData(
            storeName = storeName,
            receiptDate = dateString,
            total = total,
            tax = tax,
            subtotal = total?.let { t -> tax?.let { tx -> t - tx } },
            items = items,
            confidence = 0.4f  // Lower confidence for local parsing
        )
    }

    /**
     * Parse receipt date string to timestamp
     */
    private fun parseReceiptDate(dateString: String?): Long? {
        if (dateString.isNullOrBlank()) return null

        val dateFormats = listOf(
            "yyyy-MM-dd",
            "MM/dd/yyyy",
            "MM-dd-yyyy",
            "dd/MM/yyyy",
            "MM/dd/yy",
            "M/d/yyyy",
            "M/d/yy"
        )

        for (format in dateFormats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.isLenient = false
                val date = sdf.parse(dateString)
                if (date != null) {
                    return date.time
                }
            } catch (e: Exception) {
                // Try next format
            }
        }
        return null
    }

    /**
     * Load and resize bitmap from URI
     */
    private fun loadAndResizeBitmap(uri: Uri): Bitmap {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open image URI")

        // Decode bounds first
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        // Calculate sample size
        val sampleSize = calculateInSampleSize(options, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE)

        // Decode with sample size
        val newInputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot reopen image URI")
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val bitmap = BitmapFactory.decodeStream(newInputStream, null, decodeOptions)
        newInputStream.close()

        return bitmap ?: throw Exception("Failed to decode bitmap")
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Convert bitmap to base64 string
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Parse JSON response from AI
     */
    private fun parseJsonResponse(content: String): ParsedReceiptData {
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
        json = json.trim()

        return try {
            gson.fromJson(json, ParsedReceiptData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI response: $json", e)
            ParsedReceiptData(confidence = 0.3f)
        }
    }

    /**
     * Get line items from receipt JSON
     */
    fun getLineItemsFromJson(itemsJson: String): List<ReceiptLineItem> {
        return try {
            val type = object : TypeToken<List<ReceiptLineItem>>() {}.type
            gson.fromJson(itemsJson, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse line items JSON", e)
            emptyList()
        }
    }

    /**
     * Update receipt with manual edits
     */
    suspend fun updateReceiptManually(
        receiptId: String,
        storeName: String?,
        total: Double?,
        tax: Double?,
        lineItems: List<ReceiptLineItem>
    ) = withContext(Dispatchers.IO) {
        val receipt = receiptDao.getReceiptById(receiptId) ?: return@withContext

        val subtotal = total?.let { t -> tax?.let { tx -> t - tx } }

        val updatedReceipt = receipt.copy(
            storeName = storeName,
            total = total,
            tax = tax,
            subtotal = subtotal,
            itemsJson = gson.toJson(lineItems),
            isVerified = true,
            status = ReceiptStatus.COMPLETED,
            updatedAt = System.currentTimeMillis()
        )

        receiptDao.updateReceipt(updatedReceipt)
    }

    /**
     * Delete a receipt
     */
    suspend fun deleteReceipt(receiptId: String) = withContext(Dispatchers.IO) {
        receiptDao.deleteReceiptById(receiptId)
    }
}
