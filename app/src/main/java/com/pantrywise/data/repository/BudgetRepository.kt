package com.pantrywise.data.repository

import com.pantrywise.data.local.dao.BudgetTargetDao
import com.pantrywise.data.local.dao.DailySpendingData
import com.pantrywise.data.local.dao.TransactionDao
import com.pantrywise.data.local.entity.BudgetPeriod
import com.pantrywise.data.local.entity.BudgetStatus
import com.pantrywise.data.local.entity.BudgetTargetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetTargetDao: BudgetTargetDao,
    private val transactionDao: TransactionDao
) {

    // Get all budgets
    fun getAllBudgets(): Flow<List<BudgetTargetEntity>> =
        budgetTargetDao.getAllBudgets()

    // Get active budgets
    fun getActiveBudgets(): Flow<List<BudgetTargetEntity>> =
        budgetTargetDao.getActiveBudgets()

    // Get the primary active budget
    suspend fun getActiveBudget(): BudgetTargetEntity? =
        budgetTargetDao.getActiveBudget()

    // Get budget by ID
    suspend fun getBudgetById(id: String): BudgetTargetEntity? =
        budgetTargetDao.getBudgetById(id)

    // Create a new budget
    suspend fun createBudget(
        name: String = "Grocery Budget",
        amount: Double,
        period: BudgetPeriod = BudgetPeriod.WEEKLY,
        alertThreshold: Double = 0.8,
        isActive: Boolean = true
    ): BudgetTargetEntity {
        val budget = BudgetTargetEntity(
            name = name,
            amount = amount,
            period = period,
            alertThreshold = alertThreshold,
            isActive = isActive
        )
        budgetTargetDao.insertBudget(budget)
        return budget
    }

    // Update a budget
    suspend fun updateBudget(budget: BudgetTargetEntity) {
        budgetTargetDao.updateBudget(budget.copy(updatedAt = System.currentTimeMillis()))
    }

    // Delete a budget
    suspend fun deleteBudget(budget: BudgetTargetEntity) {
        budgetTargetDao.deleteBudget(budget)
    }

    // Delete budget by ID
    suspend fun deleteBudgetById(id: String) {
        budgetTargetDao.deleteBudgetById(id)
    }

    // Toggle active status
    suspend fun toggleActiveStatus(id: String, isActive: Boolean) {
        budgetTargetDao.updateActiveStatus(id, isActive)
    }

    // Get the current budget status (with spending calculated)
    suspend fun getBudgetStatus(): BudgetStatus? {
        val budget = getActiveBudget() ?: return null
        return getBudgetStatus(budget)
    }

    // Get budget status for a specific budget
    suspend fun getBudgetStatus(budget: BudgetTargetEntity): BudgetStatus {
        val periodStart = budget.periodStartDate()
        val periodEnd = budget.periodEndDate()

        // Get total spent in this period
        val spent = transactionDao.getTotalSpentInDateRange(periodStart, periodEnd) ?: 0.0

        return BudgetStatus.from(budget, spent)
    }

    // Check if any budget alerts should be triggered
    suspend fun checkBudgetAlerts(): List<BudgetStatus> {
        val activeBudgets = budgetTargetDao.getActiveBudgets().first()
        val alertStatuses = mutableListOf<BudgetStatus>()

        for (budget in activeBudgets) {
            val status = getBudgetStatus(budget)
            if (status.isNearAlert || status.isOverBudget) {
                alertStatuses.add(status)
            }
        }

        return alertStatuses
    }

    // Get spending progress for a budget
    suspend fun getSpendingProgress(budget: BudgetTargetEntity): SpendingProgress {
        val status = getBudgetStatus(budget)
        val periodStart = budget.periodStartDate()
        val periodEnd = budget.periodEndDate()

        // Get daily spending breakdown
        val dailySpending = transactionDao.getDailySpendingInDateRange(periodStart, periodEnd)

        return SpendingProgress(
            status = status,
            dailySpending = dailySpending,
            periodStart = periodStart,
            periodEnd = periodEnd
        )
    }
}

/**
 * Spending progress data for charts and detailed display
 */
data class SpendingProgress(
    val status: BudgetStatus,
    val dailySpending: List<DailySpendingData>,
    val periodStart: Long,
    val periodEnd: Long
)
