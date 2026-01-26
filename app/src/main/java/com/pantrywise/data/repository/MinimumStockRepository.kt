package com.pantrywise.data.repository

import com.pantrywise.data.local.dao.InventoryDao
import com.pantrywise.data.local.dao.MinimumStockRuleDao
import com.pantrywise.data.local.entity.MinimumStockRuleEntity
import com.pantrywise.data.local.entity.StockAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MinimumStockRepository @Inject constructor(
    private val minimumStockRuleDao: MinimumStockRuleDao,
    private val inventoryDao: InventoryDao
) {
    // Get all active rules
    fun getAllActiveRules(): Flow<List<MinimumStockRuleEntity>> =
        minimumStockRuleDao.getAllActiveRules()

    // Get staple rules (household essentials)
    fun getStapleRules(): Flow<List<MinimumStockRuleEntity>> =
        minimumStockRuleDao.getStapleRules()

    // Get staple count
    fun getStapleCount(): Flow<Int> = minimumStockRuleDao.getStapleCount()

    // Get rule by ID
    suspend fun getRuleById(id: String): MinimumStockRuleEntity? =
        minimumStockRuleDao.getRuleById(id)

    // Get rule by product ID
    suspend fun getRuleByProductId(productId: String): MinimumStockRuleEntity? =
        minimumStockRuleDao.getRuleByProductId(productId)

    // Create a new minimum stock rule
    suspend fun createRule(
        productId: String,
        productName: String,
        minimumQuantity: Double = 1.0,
        reorderQuantity: Double = 1.0,
        autoAddToList: Boolean = true,
        isStaple: Boolean = false
    ): MinimumStockRuleEntity {
        val rule = MinimumStockRuleEntity(
            productId = productId,
            productName = productName,
            minimumQuantity = minimumQuantity,
            reorderQuantity = reorderQuantity,
            autoAddToList = autoAddToList,
            isStaple = isStaple
        )
        minimumStockRuleDao.insertRule(rule)
        return rule
    }

    // Update a rule
    suspend fun updateRule(rule: MinimumStockRuleEntity) {
        minimumStockRuleDao.updateRule(rule.copy(updatedAt = System.currentTimeMillis()))
    }

    // Delete a rule
    suspend fun deleteRule(rule: MinimumStockRuleEntity) {
        minimumStockRuleDao.deleteRule(rule)
    }

    // Delete rule by ID
    suspend fun deleteRuleById(id: String) {
        minimumStockRuleDao.deleteRuleById(id)
    }

    // Toggle staple status
    suspend fun toggleStapleStatus(id: String, isStaple: Boolean) {
        minimumStockRuleDao.updateStapleStatus(id, isStaple)
    }

    // Check stock levels and return alerts for items below minimum
    suspend fun checkStockLevels(): List<StockAlert> {
        val rules = minimumStockRuleDao.getAllActiveRules().first()
        val alerts = mutableListOf<StockAlert>()

        for (rule in rules) {
            val inventoryItems = inventoryDao.getInventoryItemsByProductId(rule.productId).first()
            val currentQuantity = inventoryItems.sumOf { it.quantityOnHand }

            if (rule.shouldTrigger(currentQuantity)) {
                val suggestedQuantity = if (currentQuantity < rule.minimumQuantity) {
                    rule.reorderQuantity + (rule.minimumQuantity - currentQuantity)
                } else {
                    rule.reorderQuantity
                }

                alerts.add(
                    StockAlert(
                        rule = rule,
                        currentQuantity = currentQuantity,
                        suggestedQuantity = suggestedQuantity
                    )
                )
            }
        }

        return alerts
    }

    // Check stock levels for staples only
    suspend fun checkStapleStockLevels(): List<StockAlert> {
        val staples = minimumStockRuleDao.getStapleRules().first()
        val alerts = mutableListOf<StockAlert>()

        for (rule in staples) {
            val inventoryItems = inventoryDao.getInventoryItemsByProductId(rule.productId).first()
            val currentQuantity = inventoryItems.sumOf { it.quantityOnHand }

            if (rule.shouldTrigger(currentQuantity)) {
                val suggestedQuantity = if (currentQuantity < rule.minimumQuantity) {
                    rule.reorderQuantity + (rule.minimumQuantity - currentQuantity)
                } else {
                    rule.reorderQuantity
                }

                alerts.add(
                    StockAlert(
                        rule = rule,
                        currentQuantity = currentQuantity,
                        suggestedQuantity = suggestedQuantity
                    )
                )
            }
        }

        return alerts
    }

    // Get well-stocked staples (staples not triggering alerts)
    suspend fun getWellStockedStaples(): List<MinimumStockRuleEntity> {
        val staples = minimumStockRuleDao.getStapleRules().first()
        val alerts = checkStapleStockLevels()
        val alertedProductIds = alerts.map { it.rule.productId }.toSet()

        return staples.filter { it.productId !in alertedProductIds }
    }

    // Mark a rule as triggered
    suspend fun markRuleTriggered(id: String) {
        minimumStockRuleDao.markTriggered(id)
    }
}
