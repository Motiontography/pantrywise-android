package com.pantrywise.services

import com.pantrywise.data.local.entity.NutritionEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parsed nutrition data from OCR text
 */
data class ParsedNutrition(
    val servingSize: Double? = null,
    val servingSizeUnit: String? = null,
    val servingsPerContainer: Double? = null,
    val calories: Double? = null,
    val totalFat: Double? = null,
    val saturatedFat: Double? = null,
    val transFat: Double? = null,
    val cholesterol: Double? = null,
    val sodium: Double? = null,
    val totalCarbohydrates: Double? = null,
    val dietaryFiber: Double? = null,
    val totalSugars: Double? = null,
    val addedSugars: Double? = null,
    val protein: Double? = null,
    val vitaminD: Double? = null,
    val calcium: Double? = null,
    val iron: Double? = null,
    val potassium: Double? = null,
    val vitaminA: Double? = null,
    val vitaminC: Double? = null,
    val labelFormat: LabelFormat = LabelFormat.US,
    val confidence: Float = 0f,
    val rawText: String = ""
) {
    fun toNutritionEntity(productId: String): NutritionEntity {
        return NutritionEntity(
            productId = productId,
            servingSize = servingSize,
            servingSizeUnit = servingSizeUnit,
            servingsPerContainer = servingsPerContainer,
            calories = calories,
            totalFat = totalFat,
            saturatedFat = saturatedFat,
            transFat = transFat,
            cholesterol = cholesterol,
            sodium = sodium,
            totalCarbohydrates = totalCarbohydrates,
            dietaryFiber = dietaryFiber,
            totalSugars = totalSugars,
            addedSugars = addedSugars,
            protein = protein,
            vitaminD = vitaminD,
            calcium = calcium,
            iron = iron,
            potassium = potassium,
            vitaminA = vitaminA,
            vitaminC = vitaminC,
            labelFormat = labelFormat.name,
            confidence = confidence
        )
    }

    val hasMinimumData: Boolean
        get() = calories != null || totalFat != null || protein != null || totalCarbohydrates != null

    val fieldCount: Int
        get() = listOfNotNull(
            servingSize, calories, totalFat, saturatedFat, transFat,
            cholesterol, sodium, totalCarbohydrates, dietaryFiber,
            totalSugars, addedSugars, protein, vitaminD, calcium,
            iron, potassium, vitaminA, vitaminC
        ).size
}

enum class LabelFormat(val displayName: String) {
    US("US FDA"),
    EU("European"),
    CANADIAN("Canadian"),
    UK("UK"),
    AUSTRALIAN("Australian"),
    UNKNOWN("Unknown")
}

@Singleton
class NutritionParser @Inject constructor() {

    companion object {
        // Serving size patterns
        private val SERVING_SIZE_PATTERNS = listOf(
            Regex("""Serving\s*Size[:\s]+(\d+(?:\.\d+)?)\s*(\w+)""", RegexOption.IGNORE_CASE),
            Regex("""Serving[:\s]+(\d+(?:\.\d+)?)\s*(\w+)""", RegexOption.IGNORE_CASE),
            Regex("""Per\s+(\d+(?:\.\d+)?)\s*(\w+)""", RegexOption.IGNORE_CASE),
            Regex("""Portion[:\s]+(\d+(?:\.\d+)?)\s*(\w+)""", RegexOption.IGNORE_CASE)
        )

        private val SERVINGS_PER_CONTAINER_PATTERNS = listOf(
            Regex("""(?:About\s+)?(\d+(?:\.\d+)?)\s*servings?\s*per\s*container""", RegexOption.IGNORE_CASE),
            Regex("""Servings?\s*[Pp]er\s*[Cc]ontainer[:\s]+(?:about\s+)?(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:\.\d+)?)\s*portions?""", RegexOption.IGNORE_CASE)
        )

        // Calorie patterns
        private val CALORIE_PATTERNS = listOf(
            Regex("""Calories[:\s]+(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""Energy[:\s]+(\d+)\s*(?:kcal|Cal)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+)\s*Cal(?:ories)?""", RegexOption.IGNORE_CASE),
            Regex("""(\d+)\s*kcal""", RegexOption.IGNORE_CASE)
        )

        // Macronutrient patterns (with g suffix)
        private val TOTAL_FAT_PATTERNS = listOf(
            Regex("""Total\s*Fat[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""Fat[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""Fats?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE)
        )

        private val SATURATED_FAT_PATTERNS = listOf(
            Regex("""Saturated\s*Fat[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""Sat\.?\s*Fat[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""of\s*which\s*saturates[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE)
        )

        private val TRANS_FAT_PATTERNS = listOf(
            Regex("""Trans\s*Fat[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""Trans-Fat[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE)
        )

        private val CHOLESTEROL_PATTERNS = listOf(
            Regex("""Cholesterol[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE),
            Regex("""Cholest\.?[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE)
        )

        private val SODIUM_PATTERNS = listOf(
            Regex("""Sodium[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE),
            Regex("""Salt[:\s]+(\d+(?:\.\d+)?)\s*(?:mg|g)""", RegexOption.IGNORE_CASE),
            Regex("""Na[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE)
        )

        private val TOTAL_CARBS_PATTERNS = listOf(
            Regex("""Total\s*Carbohydrate[s]?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""Carbohydrate[s]?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""Total\s*Carbs?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""Carbs?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE)
        )

        private val FIBER_PATTERNS = listOf(
            Regex("""Dietary\s*Fibre?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""Fibre?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""of\s*which\s*fibre[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE)
        )

        private val SUGAR_PATTERNS = listOf(
            Regex("""Total\s*Sugars?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""Sugars?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""of\s*which\s*sugars?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE)
        )

        private val ADDED_SUGARS_PATTERNS = listOf(
            Regex("""(?:Incl(?:udes)?\.?\s*)?Added\s*Sugars?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""Add(?:ed)?\s*Sugars?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE)
        )

        private val PROTEIN_PATTERNS = listOf(
            Regex("""Protein[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE),
            Regex("""Prot\.?[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE)
        )

        // Vitamin and mineral patterns (mg, mcg, or %DV)
        private val VITAMIN_D_PATTERNS = listOf(
            Regex("""Vitamin\s*D[:\s]+(\d+(?:\.\d+)?)\s*(?:mcg|µg|IU)""", RegexOption.IGNORE_CASE),
            Regex("""Vit\.?\s*D[:\s]+(\d+(?:\.\d+)?)\s*(?:mcg|µg|IU)""", RegexOption.IGNORE_CASE)
        )

        private val CALCIUM_PATTERNS = listOf(
            Regex("""Calcium[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE),
            Regex("""Ca[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE)
        )

        private val IRON_PATTERNS = listOf(
            Regex("""Iron[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE),
            Regex("""Fe[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE)
        )

        private val POTASSIUM_PATTERNS = listOf(
            Regex("""Potassium[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE),
            Regex("""K[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE)
        )

        private val VITAMIN_A_PATTERNS = listOf(
            Regex("""Vitamin\s*A[:\s]+(\d+(?:\.\d+)?)\s*(?:mcg|µg|IU|%|RAE)""", RegexOption.IGNORE_CASE),
            Regex("""Vit\.?\s*A[:\s]+(\d+(?:\.\d+)?)\s*(?:mcg|µg|IU|%)""", RegexOption.IGNORE_CASE)
        )

        private val VITAMIN_C_PATTERNS = listOf(
            Regex("""Vitamin\s*C[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE),
            Regex("""Vit\.?\s*C[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE),
            Regex("""Ascorbic\s*Acid[:\s]+(\d+(?:\.\d+)?)\s*mg""", RegexOption.IGNORE_CASE)
        )

        // Label format detection patterns
        private val US_LABEL_INDICATORS = listOf(
            Regex("""Nutrition\s*Facts""", RegexOption.IGNORE_CASE),
            Regex("""Amount\s*per\s*serving""", RegexOption.IGNORE_CASE),
            Regex("""%\s*Daily\s*Value""", RegexOption.IGNORE_CASE)
        )

        private val EU_LABEL_INDICATORS = listOf(
            Regex("""Nutrition(?:al)?\s*(?:Information|Declaration)""", RegexOption.IGNORE_CASE),
            Regex("""per\s*100\s*(?:g|ml)""", RegexOption.IGNORE_CASE),
            Regex("""Energy.*kJ""", RegexOption.IGNORE_CASE),
            Regex("""of\s*which\s*saturates""", RegexOption.IGNORE_CASE)
        )

        private val CANADIAN_LABEL_INDICATORS = listOf(
            Regex("""Valeur\s*nutritive""", RegexOption.IGNORE_CASE),
            Regex("""Nutrition\s*Facts.*Valeur""", RegexOption.IGNORE_CASE)
        )

        private val UK_LABEL_INDICATORS = listOf(
            Regex("""Reference\s*intake""", RegexOption.IGNORE_CASE),
            Regex("""RI\s*\(""", RegexOption.IGNORE_CASE),
            Regex("""Traffic\s*light""", RegexOption.IGNORE_CASE)
        )
    }

    /**
     * Parse nutrition information from OCR text
     */
    fun parseNutritionLabel(text: String): ParsedNutrition {
        val normalizedText = normalizeText(text)
        val labelFormat = detectLabelFormat(normalizedText)

        var fieldsFound = 0
        var totalPatternMatches = 0

        // Parse serving information
        val (servingSize, servingUnit) = parseServingSize(normalizedText)
        val servingsPerContainer = parseServingsPerContainer(normalizedText)
        if (servingSize != null) fieldsFound++
        if (servingsPerContainer != null) fieldsFound++

        // Parse calories
        val calories = parseWithPatterns(normalizedText, CALORIE_PATTERNS)
        if (calories != null) fieldsFound++

        // Parse macros
        val totalFat = parseWithPatterns(normalizedText, TOTAL_FAT_PATTERNS)
        val saturatedFat = parseWithPatterns(normalizedText, SATURATED_FAT_PATTERNS)
        val transFat = parseWithPatterns(normalizedText, TRANS_FAT_PATTERNS)
        val cholesterol = parseWithPatterns(normalizedText, CHOLESTEROL_PATTERNS)
        val sodium = parseSodium(normalizedText, labelFormat)
        val totalCarbs = parseWithPatterns(normalizedText, TOTAL_CARBS_PATTERNS)
        val fiber = parseWithPatterns(normalizedText, FIBER_PATTERNS)
        val sugars = parseWithPatterns(normalizedText, SUGAR_PATTERNS)
        val addedSugars = parseWithPatterns(normalizedText, ADDED_SUGARS_PATTERNS)
        val protein = parseWithPatterns(normalizedText, PROTEIN_PATTERNS)

        listOfNotNull(totalFat, saturatedFat, transFat, cholesterol, sodium,
            totalCarbs, fiber, sugars, addedSugars, protein).forEach { fieldsFound++ }

        // Parse vitamins and minerals
        val vitaminD = parseWithPatterns(normalizedText, VITAMIN_D_PATTERNS)
        val calcium = parseWithPatterns(normalizedText, CALCIUM_PATTERNS)
        val iron = parseWithPatterns(normalizedText, IRON_PATTERNS)
        val potassium = parseWithPatterns(normalizedText, POTASSIUM_PATTERNS)
        val vitaminA = parseWithPatterns(normalizedText, VITAMIN_A_PATTERNS)
        val vitaminC = parseWithPatterns(normalizedText, VITAMIN_C_PATTERNS)

        listOfNotNull(vitaminD, calcium, iron, potassium, vitaminA, vitaminC).forEach { fieldsFound++ }

        // Calculate confidence based on fields found and label format detection
        val maxExpectedFields = 18
        val fieldConfidence = (fieldsFound.toFloat() / maxExpectedFields).coerceIn(0f, 1f)
        val formatConfidence = if (labelFormat != LabelFormat.UNKNOWN) 0.2f else 0f
        val confidence = ((fieldConfidence * 0.8f) + formatConfidence).coerceIn(0f, 1f)

        return ParsedNutrition(
            servingSize = servingSize,
            servingSizeUnit = servingUnit,
            servingsPerContainer = servingsPerContainer,
            calories = calories,
            totalFat = totalFat,
            saturatedFat = saturatedFat,
            transFat = transFat,
            cholesterol = cholesterol,
            sodium = sodium,
            totalCarbohydrates = totalCarbs,
            dietaryFiber = fiber,
            totalSugars = sugars,
            addedSugars = addedSugars,
            protein = protein,
            vitaminD = vitaminD,
            calcium = calcium,
            iron = iron,
            potassium = potassium,
            vitaminA = vitaminA,
            vitaminC = vitaminC,
            labelFormat = labelFormat,
            confidence = confidence,
            rawText = text
        )
    }

    private fun normalizeText(text: String): String {
        return text
            .replace("\n", " ")
            .replace("\r", " ")
            .replace(Regex("""\s+"""), " ")
            .replace("O", "0") // Common OCR error
            .replace("l", "1") // Common OCR error in numbers
            .replace("I", "1") // Common OCR error
    }

    private fun parseWithPatterns(text: String, patterns: List<Regex>): Double? {
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val value = match.groupValues.getOrNull(1)?.toDoubleOrNull()
                if (value != null && value >= 0) {
                    return value
                }
            }
        }
        return null
    }

    private fun parseServingSize(text: String): Pair<Double?, String?> {
        for (pattern in SERVING_SIZE_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val size = match.groupValues.getOrNull(1)?.toDoubleOrNull()
                val unit = match.groupValues.getOrNull(2)
                if (size != null) {
                    return Pair(size, normalizeUnit(unit))
                }
            }
        }
        return Pair(null, null)
    }

    private fun parseServingsPerContainer(text: String): Double? {
        return parseWithPatterns(text, SERVINGS_PER_CONTAINER_PATTERNS)
    }

    private fun parseSodium(text: String, labelFormat: LabelFormat): Double? {
        // EU labels show salt, need to convert to sodium (salt * 0.4)
        val sodiumValue = parseWithPatterns(text, SODIUM_PATTERNS)
        if (sodiumValue != null) return sodiumValue

        // Check for salt value (EU format)
        val saltPattern = Regex("""Salt[:\s]+(\d+(?:\.\d+)?)\s*g""", RegexOption.IGNORE_CASE)
        val saltMatch = saltPattern.find(text)
        if (saltMatch != null) {
            val saltG = saltMatch.groupValues[1].toDoubleOrNull()
            if (saltG != null) {
                // Convert salt grams to sodium mg
                return saltG * 1000 * 0.4
            }
        }

        return null
    }

    private fun normalizeUnit(unit: String?): String? {
        if (unit == null) return null
        return when (unit.lowercase()) {
            "g", "grams", "gram" -> "g"
            "mg", "milligrams", "milligram" -> "mg"
            "oz", "ounces", "ounce" -> "oz"
            "ml", "milliliters", "milliliter", "millilitres" -> "ml"
            "cup", "cups" -> "cup"
            "tbsp", "tablespoon", "tablespoons" -> "tbsp"
            "tsp", "teaspoon", "teaspoons" -> "tsp"
            "piece", "pieces", "pcs" -> "piece"
            "slice", "slices" -> "slice"
            else -> unit
        }
    }

    private fun detectLabelFormat(text: String): LabelFormat {
        // Check for Canadian first (bilingual)
        for (pattern in CANADIAN_LABEL_INDICATORS) {
            if (pattern.containsMatchIn(text)) return LabelFormat.CANADIAN
        }

        // Check US format
        var usScore = 0
        for (pattern in US_LABEL_INDICATORS) {
            if (pattern.containsMatchIn(text)) usScore++
        }

        // Check EU format
        var euScore = 0
        for (pattern in EU_LABEL_INDICATORS) {
            if (pattern.containsMatchIn(text)) euScore++
        }

        // Check UK format
        for (pattern in UK_LABEL_INDICATORS) {
            if (pattern.containsMatchIn(text)) return LabelFormat.UK
        }

        return when {
            usScore > euScore -> LabelFormat.US
            euScore > usScore -> LabelFormat.EU
            usScore > 0 -> LabelFormat.US
            euScore > 0 -> LabelFormat.EU
            else -> LabelFormat.UNKNOWN
        }
    }

    /**
     * Validate and clean parsed nutrition values
     */
    fun validateNutrition(parsed: ParsedNutrition): ParsedNutrition {
        return parsed.copy(
            calories = parsed.calories?.takeIf { it in 0.0..10000.0 },
            totalFat = parsed.totalFat?.takeIf { it in 0.0..500.0 },
            saturatedFat = parsed.saturatedFat?.takeIf { it in 0.0..200.0 },
            transFat = parsed.transFat?.takeIf { it in 0.0..100.0 },
            cholesterol = parsed.cholesterol?.takeIf { it in 0.0..2000.0 },
            sodium = parsed.sodium?.takeIf { it in 0.0..10000.0 },
            totalCarbohydrates = parsed.totalCarbohydrates?.takeIf { it in 0.0..500.0 },
            dietaryFiber = parsed.dietaryFiber?.takeIf { it in 0.0..100.0 },
            totalSugars = parsed.totalSugars?.takeIf { it in 0.0..200.0 },
            addedSugars = parsed.addedSugars?.takeIf { it in 0.0..200.0 },
            protein = parsed.protein?.takeIf { it in 0.0..500.0 },
            calcium = parsed.calcium?.takeIf { it in 0.0..5000.0 },
            iron = parsed.iron?.takeIf { it in 0.0..100.0 },
            potassium = parsed.potassium?.takeIf { it in 0.0..10000.0 }
        )
    }
}
