package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pantrywise.domain.model.SourceType
import com.pantrywise.domain.model.Unit
import java.util.Date
import java.util.UUID

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val category: String,
    val defaultUnit: Unit = Unit.EACH,
    val typicalPrice: Double? = null,
    val currency: String = "USD",
    val imageUrl: String? = null,
    val tags: String = "", // Stored as comma-separated
    val notes: String? = null,
    val source: SourceType = SourceType.USER_MANUAL,
    val userConfirmed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() = if (brand.isNullOrBlank()) name else "$brand $name"

    val tagsList: List<String>
        get() = if (tags.isBlank()) emptyList() else tags.split(",")
}
