package com.pantrywise.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID
import java.util.Calendar

/**
 * Budget period options
 */
enum class BudgetPeriod(val displayName: String, val days: Int) {
    WEEKLY("Weekly", 7),
    BIWEEKLY("Bi-weekly", 14),
    MONTHLY("Monthly", 30)
}

/**
 * Budget target entity for tracking spending goals
 */
@Entity(tableName = "budget_targets")
data class BudgetTargetEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Grocery Budget",
    val amount: Double = 0.0,
    val period: BudgetPeriod = BudgetPeriod.WEEKLY,
    val alertThreshold: Double = 0.8, // Alert when spending reaches 80% of budget
    val isActive: Boolean = true,
    val startDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * The amount at which an alert should be triggered
     */
    val alertAmount: Double
        get() = amount * alertThreshold

    /**
     * Daily budget allocation
     */
    val dailyBudget: Double
        get() = amount / period.days

    /**
     * Get the start date of the current budget period
     */
    fun periodStartDate(forDate: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = forDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (period) {
            BudgetPeriod.WEEKLY -> {
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.timeInMillis
            }
            BudgetPeriod.BIWEEKLY -> {
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR)
                if (weekOfYear % 2 != 0) {
                    calendar.add(Calendar.WEEK_OF_YEAR, -1)
                }
                calendar.timeInMillis
            }
            BudgetPeriod.MONTHLY -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.timeInMillis
            }
        }
    }

    /**
     * Get the end date of the current budget period
     */
    fun periodEndDate(forDate: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = periodStartDate(forDate)
            add(Calendar.DAY_OF_YEAR, period.days - 1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return calendar.timeInMillis
    }

    /**
     * Calculate remaining days in the current period
     */
    fun remainingDays(forDate: Long = System.currentTimeMillis()): Int {
        val endDate = periodEndDate(forDate)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = forDate
        }
        val endCalendar = Calendar.getInstance().apply {
            timeInMillis = endDate
        }

        val diffMs = endCalendar.timeInMillis - calendar.timeInMillis
        return ((diffMs / (1000 * 60 * 60 * 24)) + 1).toInt().coerceAtLeast(0)
    }
}

/**
 * Budget status for UI display
 */
data class BudgetStatus(
    val budget: BudgetTargetEntity,
    val spent: Double,
    val remaining: Double,
    val percentUsed: Double,
    val daysRemaining: Int,
    val dailyRemaining: Double,
    val isOverBudget: Boolean,
    val isNearAlert: Boolean
) {
    companion object {
        fun from(budget: BudgetTargetEntity, spent: Double): BudgetStatus {
            val remaining = budget.amount - spent
            val percentUsed = if (budget.amount > 0) spent / budget.amount else 0.0
            val daysRemaining = budget.remainingDays()
            val dailyRemaining = if (daysRemaining > 0) remaining / daysRemaining else 0.0

            return BudgetStatus(
                budget = budget,
                spent = spent,
                remaining = remaining,
                percentUsed = percentUsed,
                daysRemaining = daysRemaining,
                dailyRemaining = dailyRemaining,
                isOverBudget = spent > budget.amount,
                isNearAlert = spent >= budget.alertAmount
            )
        }
    }
}
