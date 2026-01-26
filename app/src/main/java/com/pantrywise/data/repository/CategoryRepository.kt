package com.pantrywise.data.repository

import com.pantrywise.data.local.dao.CategoryDao
import com.pantrywise.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    fun getAllCategories(): Flow<List<CategoryEntity>> = categoryDao.getAllCategories()

    suspend fun getCategoryById(id: String): CategoryEntity? = categoryDao.getCategoryById(id)

    suspend fun getCategoryByName(name: String): CategoryEntity? = categoryDao.getCategoryByName(name)

    fun getDefaultCategories(): Flow<List<CategoryEntity>> = categoryDao.getDefaultCategories()

    fun getCustomCategories(): Flow<List<CategoryEntity>> = categoryDao.getCustomCategories()

    suspend fun createCategory(
        name: String,
        icon: String? = null,
        color: String? = null
    ): CategoryEntity {
        val maxSortOrder = categoryDao.getMaxSortOrder() ?: 0

        val category = CategoryEntity(
            name = name,
            icon = icon,
            color = color,
            sortOrder = maxSortOrder + 1,
            isDefault = false
        )

        categoryDao.insert(category)
        return category
    }

    suspend fun updateCategory(category: CategoryEntity) {
        categoryDao.update(category)
    }

    suspend fun deleteCategory(id: String) {
        val category = categoryDao.getCategoryById(id) ?: return

        // Only allow deleting non-default categories
        if (!category.isDefault) {
            categoryDao.deleteById(id)
        }
    }

    suspend fun reorderCategories(categoryIds: List<String>) {
        categoryIds.forEachIndexed { index, id ->
            categoryDao.updateSortOrder(id, index)
        }
    }

    suspend fun getCategoryCount(): Int = categoryDao.getCategoryCount()
}
