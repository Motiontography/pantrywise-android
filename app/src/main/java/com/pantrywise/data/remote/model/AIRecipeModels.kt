package com.pantrywise.data.remote.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * AI Recipe Discovery response models
 */
@Serializable
data class AIRecipeResult(
    val name: String,
    val description: String,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val servings: Int = 4,
    val ingredients: List<AIRecipeIngredient>,
    val instructions: List<String>,
    val cuisine: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class AIRecipeIngredient(
    val name: String,
    val quantity: Double,
    val unit: String,
    val notes: String? = null
) {
    val id: String = UUID.randomUUID().toString()
}

/**
 * Parsed ingredient from recipe text
 */
@Serializable
data class ParsedIngredient(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val original: String
)

/**
 * Product vision result for AI camera recognition
 */
@Serializable
data class ProductVisionResult(
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    val quantity: String? = null,
    val estimatedPrice: Double? = null,
    val isFood: Boolean = true,
    val suggestedLocation: String? = null,
    val confidence: Double = 0.0
)

/**
 * Smart shelf snap result - captures product AND price tag
 */
@Serializable
data class SmartShelfSnapResult(
    val name: String,
    val brand: String? = null,
    val category: String? = null,
    val price: Double? = null,
    val pricePerUnit: Double? = null,
    val pricePerUnitLabel: String? = null,
    val originalPrice: Double? = null,
    val isOnSale: Boolean = false,
    val packageSize: String? = null,
    val itemCount: Int? = 1,
    val isMultiPack: Boolean = false,
    val packCount: Int? = null,
    val isFood: Boolean = true,
    val suggestedLocation: String? = null,
    val confidence: Double = 0.0,
    val priceConfidence: Double? = null
)

/**
 * Product comparison result
 */
@Serializable
data class ProductComparisonResult(
    val betterDeal: String,
    val healthierOption: String,
    val pricePerUnit1: Double? = null,
    val pricePerUnit2: Double? = null,
    val unitUsed: String? = null,
    val nutritionSummary: String,
    val recommendation: String
)

/**
 * Parsed shopping item from voice
 */
@Serializable
data class ParsedShoppingItem(
    val name: String,
    val quantity: Double = 1.0,
    val unit: String? = null
)

/**
 * Pantry organization suggestion
 */
@Serializable
data class PantryOrganizationResponse(
    val zones: List<PantryOrganizationZone>,
    val generalTips: List<String>
)

@Serializable
data class PantryOrganizationZone(
    val zoneName: String,
    val description: String,
    val items: List<String>,
    val tips: List<String>
)
