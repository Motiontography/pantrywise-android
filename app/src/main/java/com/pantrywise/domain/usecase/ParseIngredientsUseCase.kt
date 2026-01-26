package com.pantrywise.domain.usecase

import com.pantrywise.data.local.dao.ProductDao
import com.pantrywise.data.local.entity.ProductEntity
import com.pantrywise.domain.model.Unit
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class ParsedIngredient(
    val originalText: String,
    val quantity: Double?,
    val unit: Unit?,
    val ingredientName: String,
    val matchedProduct: ProductEntity?,
    val confidence: Double
)

data class IngredientParseResult(
    val parsedIngredients: List<ParsedIngredient>,
    val matchedCount: Int,
    val unmatchedCount: Int
)

class ParseIngredientsUseCase @Inject constructor(
    private val productDao: ProductDao
) {
    // Common unit patterns for parsing
    private val unitPatterns = mapOf(
        Unit.CUP to listOf("cup", "cups", "c"),
        Unit.TABLESPOON to listOf("tablespoon", "tablespoons", "tbsp", "tbs", "tb"),
        Unit.TEASPOON to listOf("teaspoon", "teaspoons", "tsp", "ts"),
        Unit.OUNCE to listOf("ounce", "ounces", "oz"),
        Unit.POUND to listOf("pound", "pounds", "lb", "lbs"),
        Unit.GRAM to listOf("gram", "grams", "g"),
        Unit.KILOGRAM to listOf("kilogram", "kilograms", "kg"),
        Unit.MILLILITER to listOf("milliliter", "milliliters", "ml"),
        Unit.LITER to listOf("liter", "liters", "l"),
        Unit.EACH to listOf("each", "piece", "pieces", "whole"),
        Unit.PACK to listOf("pack", "packs", "package", "packages"),
        Unit.CAN to listOf("can", "cans"),
        Unit.JAR to listOf("jar", "jars"),
        Unit.BOTTLE to listOf("bottle", "bottles"),
        Unit.BAG to listOf("bag", "bags"),
        Unit.BOX to listOf("box", "boxes")
    )

    // Fraction patterns
    private val fractionMap = mapOf(
        "½" to 0.5,
        "¼" to 0.25,
        "¾" to 0.75,
        "⅓" to 0.333,
        "⅔" to 0.666,
        "⅛" to 0.125,
        "1/2" to 0.5,
        "1/4" to 0.25,
        "3/4" to 0.75,
        "1/3" to 0.333,
        "2/3" to 0.666,
        "1/8" to 0.125
    )

    /**
     * Parses ingredient text and matches against existing products.
     */
    suspend operator fun invoke(ingredientText: String): IngredientParseResult {
        val lines = ingredientText
            .split("\n", ",", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val parsedIngredients = lines.map { line ->
            parseIngredientLine(line)
        }

        val matchedCount = parsedIngredients.count { it.matchedProduct != null }
        val unmatchedCount = parsedIngredients.size - matchedCount

        return IngredientParseResult(
            parsedIngredients = parsedIngredients,
            matchedCount = matchedCount,
            unmatchedCount = unmatchedCount
        )
    }

    private suspend fun parseIngredientLine(line: String): ParsedIngredient {
        val cleanLine = line.lowercase().trim()
            .replace(Regex("^[-•*]\\s*"), "") // Remove bullet points

        // Try to extract quantity
        val (quantity, remainingAfterQty) = extractQuantity(cleanLine)

        // Try to extract unit
        val (unit, remainingAfterUnit) = extractUnit(remainingAfterQty)

        // The rest is the ingredient name
        val ingredientName = cleanIngredientName(remainingAfterUnit)

        // Try to match against existing products
        val matchedProduct = findMatchingProduct(ingredientName)

        val confidence = calculateConfidence(quantity, unit, matchedProduct)

        return ParsedIngredient(
            originalText = line,
            quantity = quantity,
            unit = unit,
            ingredientName = ingredientName,
            matchedProduct = matchedProduct,
            confidence = confidence
        )
    }

    private fun extractQuantity(text: String): Pair<Double?, String> {
        // Check for fractions first
        for ((fraction, value) in fractionMap) {
            if (text.startsWith(fraction)) {
                return value to text.removePrefix(fraction).trim()
            }
        }

        // Check for number with fraction (e.g., "1 1/2")
        val mixedFractionRegex = Regex("^(\\d+)\\s+(\\d+/\\d+)")
        mixedFractionRegex.find(text)?.let { match ->
            val whole = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val fractionPart = fractionMap[match.groupValues[2]] ?: 0.0
            return (whole + fractionPart) to text.removeRange(match.range).trim()
        }

        // Check for simple number
        val numberRegex = Regex("^(\\d+\\.?\\d*)")
        numberRegex.find(text)?.let { match ->
            val number = match.groupValues[1].toDoubleOrNull()
            return number to text.removeRange(match.range).trim()
        }

        return null to text
    }

    private fun extractUnit(text: String): Pair<Unit?, String> {
        val words = text.split(Regex("\\s+"))
        if (words.isEmpty()) return null to text

        val firstWord = words[0].lowercase().replace(".", "")

        for ((unit, patterns) in unitPatterns) {
            if (firstWord in patterns) {
                return unit to words.drop(1).joinToString(" ")
            }
        }

        return null to text
    }

    private fun cleanIngredientName(text: String): String {
        return text
            .replace(Regex("\\(.*?\\)"), "") // Remove parenthetical notes
            .replace(Regex(",.*$"), "") // Remove everything after comma
            .replace(Regex("^of\\s+"), "") // Remove leading "of"
            .trim()
    }

    private suspend fun findMatchingProduct(ingredientName: String): ProductEntity? {
        if (ingredientName.isBlank()) return null

        // Search for exact match first
        val searchResults = productDao.searchProducts(ingredientName).first()

        // Return best match based on name similarity
        return searchResults.firstOrNull { product ->
            product.name.lowercase().contains(ingredientName.lowercase()) ||
                    ingredientName.lowercase().contains(product.name.lowercase())
        } ?: searchResults.firstOrNull()
    }

    private fun calculateConfidence(
        quantity: Double?,
        unit: Unit?,
        matchedProduct: ProductEntity?
    ): Double {
        var confidence = 0.0

        if (quantity != null) confidence += 0.2
        if (unit != null) confidence += 0.2
        if (matchedProduct != null) confidence += 0.6

        return confidence
    }
}
