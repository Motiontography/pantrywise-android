package com.pantrywise.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parsed shopping item from voice input
 */
data class VoiceParsedItem(
    val name: String,
    val quantity: Double,
    val unit: String?,
    val rawText: String
)

/**
 * Voice recognition result
 */
sealed class VoiceRecognitionResult {
    data class Success(val text: String, val items: List<VoiceParsedItem>) : VoiceRecognitionResult()
    data class PartialResult(val text: String) : VoiceRecognitionResult()
    data class Error(val message: String, val errorCode: Int) : VoiceRecognitionResult()
    data object Ready : VoiceRecognitionResult()
    data object Listening : VoiceRecognitionResult()
    data object Processing : VoiceRecognitionResult()
}

@Singleton
class VoiceInputService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Quantity words to numbers
        private val QUANTITY_WORDS = mapOf(
            "a" to 1.0,
            "an" to 1.0,
            "one" to 1.0,
            "two" to 2.0,
            "three" to 3.0,
            "four" to 4.0,
            "five" to 5.0,
            "six" to 6.0,
            "seven" to 7.0,
            "eight" to 8.0,
            "nine" to 9.0,
            "ten" to 10.0,
            "eleven" to 11.0,
            "twelve" to 12.0,
            "dozen" to 12.0,
            "half" to 0.5,
            "quarter" to 0.25,
            "couple" to 2.0,
            "few" to 3.0,
            "several" to 4.0,
            "some" to 2.0
        )

        // Unit mappings
        private val UNIT_PATTERNS = mapOf(
            // Weight
            "pounds?" to "lb",
            "lbs?" to "lb",
            "ounces?" to "oz",
            "oz" to "oz",
            "grams?" to "g",
            "kilograms?" to "kg",
            "kg" to "kg",
            // Volume
            "gallons?" to "gal",
            "gal" to "gal",
            "liters?" to "L",
            "litres?" to "L",
            "milliliters?" to "ml",
            "ml" to "ml",
            "cups?" to "cup",
            "tablespoons?" to "tbsp",
            "tbsp" to "tbsp",
            "teaspoons?" to "tsp",
            "tsp" to "tsp",
            // Packaging
            "packs?" to "pack",
            "packages?" to "pack",
            "boxes?" to "box",
            "bags?" to "bag",
            "cans?" to "can",
            "jars?" to "jar",
            "bottles?" to "bottle",
            "bunches?" to "bunch",
            "loaves?" to "loaf",
            "slices?" to "slice",
            "pieces?" to "piece"
        )

        // Separators between items
        private val SEPARATORS = listOf(
            "and",
            "also",
            "plus",
            "with",
            "then",
            ","
        )

        // Words to ignore at start
        private val IGNORE_PREFIXES = listOf(
            "add",
            "get",
            "buy",
            "need",
            "want",
            "put",
            "i need",
            "i want",
            "we need",
            "we want",
            "please add",
            "please get",
            "can you add",
            "could you add"
        )
    }

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Start listening for voice input
     */
    fun startListening(): Flow<VoiceRecognitionResult> = callbackFlow {
        if (!isAvailable) {
            trySend(VoiceRecognitionResult.Error("Speech recognition not available", -1))
            close()
            return@callbackFlow
        }

        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(VoiceRecognitionResult.Ready)
            }

            override fun onBeginningOfSpeech() {
                trySend(VoiceRecognitionResult.Listening)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed - can be used for visualization
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                trySend(VoiceRecognitionResult.Processing)
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                trySend(VoiceRecognitionResult.Error(message, error))
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                val items = parseShoppingItems(text)
                trySend(VoiceRecognitionResult.Success(text, items))
                close()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                trySend(VoiceRecognitionResult.PartialResult(text))
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer.startListening(intent)

        awaitClose {
            speechRecognizer.cancel()
            speechRecognizer.destroy()
        }
    }

    /**
     * Parse spoken text into shopping items
     */
    fun parseShoppingItems(text: String): List<VoiceParsedItem> {
        if (text.isBlank()) return emptyList()

        var cleanedText = text.lowercase().trim()

        // Remove common prefixes
        for (prefix in IGNORE_PREFIXES) {
            if (cleanedText.startsWith(prefix)) {
                cleanedText = cleanedText.removePrefix(prefix).trim()
                break
            }
        }

        // Split by separators
        val itemTexts = splitBySeparators(cleanedText)

        return itemTexts.mapNotNull { parseItem(it) }
    }

    private fun splitBySeparators(text: String): List<String> {
        var remaining = text
        val items = mutableListOf<String>()

        // Replace separators with a unique delimiter
        for (separator in SEPARATORS) {
            remaining = remaining.replace(" $separator ", " ||| ")
            remaining = remaining.replace("$separator ", " ||| ")
        }

        // Split by delimiter
        val parts = remaining.split("|||")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return parts
    }

    private fun parseItem(text: String): VoiceParsedItem? {
        val words = text.split("\\s+".toRegex())
        if (words.isEmpty()) return null

        var quantity = 1.0
        var unit: String? = null
        val nameWords = mutableListOf<String>()
        var i = 0

        // Try to parse quantity at the start
        while (i < words.size) {
            val word = words[i]

            // Check for numeric quantity
            val numericQuantity = word.toDoubleOrNull()
            if (numericQuantity != null && i == 0) {
                quantity = numericQuantity
                i++
                continue
            }

            // Check for word quantity
            val wordQuantity = QUANTITY_WORDS[word]
            if (wordQuantity != null && i == 0) {
                quantity = wordQuantity
                i++
                continue
            }

            // Check for fraction like "1/2" or "one half"
            if (word == "half" && i == 1 && words[0] == "one") {
                quantity = 1.5
                i++
                continue
            }

            // Check for "and a half"
            if (word == "and" && i + 2 < words.size &&
                words[i + 1] == "a" && words[i + 2] == "half") {
                quantity += 0.5
                i += 3
                continue
            }

            // Check for unit
            if (unit == null) {
                val matchedUnit = matchUnit(word)
                if (matchedUnit != null) {
                    unit = matchedUnit
                    i++
                    // Skip "of" after unit
                    if (i < words.size && words[i] == "of") {
                        i++
                    }
                    continue
                }
            }

            // Add to name
            nameWords.add(word)
            i++
        }

        if (nameWords.isEmpty()) return null

        val name = nameWords.joinToString(" ")
            .trim()
            .replaceFirstChar { it.uppercase() }

        return VoiceParsedItem(
            name = name,
            quantity = quantity,
            unit = unit,
            rawText = text
        )
    }

    private fun matchUnit(word: String): String? {
        for ((pattern, unit) in UNIT_PATTERNS) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            if (regex.matches(word)) {
                return unit
            }
        }
        return null
    }

    /**
     * Format parsed items for confirmation display
     */
    fun formatItemsForDisplay(items: List<VoiceParsedItem>): String {
        return items.joinToString("\n") { item ->
            buildString {
                append("${item.quantity.let { if (it == it.toLong().toDouble()) it.toLong() else it }}")
                item.unit?.let { append(" $it") }
                append(" ${item.name}")
            }
        }
    }
}
