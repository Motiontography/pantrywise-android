package com.pantrywise.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pantrywise.data.local.entity.BudgetPeriod
import com.pantrywise.data.local.entity.BudgetStatus
import com.pantrywise.data.local.entity.BudgetTargetEntity
import com.pantrywise.data.local.entity.PurchaseTransactionEntity
import com.pantrywise.data.repository.BudgetRepository
import com.pantrywise.data.repository.SpendingSummary
import com.pantrywise.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TimePeriod {
    DAILY, WEEKLY, MONTHLY
}

data class FinanceUiState(
    val isLoading: Boolean = true,
    val selectedPeriod: TimePeriod = TimePeriod.MONTHLY,
    val currentSummary: SpendingSummary? = null,
    val recentTransactions: List<PurchaseTransactionEntity> = emptyList(),
    val stores: List<String> = emptyList(),
    val budgetStatus: BudgetStatus? = null,
    val allBudgets: List<BudgetTargetEntity> = emptyList(),
    val showBudgetSetup: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState: StateFlow<FinanceUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load summary based on selected period
            loadSummary()

            // Load budget status
            loadBudgetStatus()

            // Load recent transactions
            transactionRepository.getAllTransactions()
                .collect { transactions ->
                    _uiState.update {
                        it.copy(
                            recentTransactions = transactions.take(10),
                            isLoading = false
                        )
                    }
                }
        }

        viewModelScope.launch {
            transactionRepository.getAllStores()
                .collect { stores ->
                    _uiState.update { it.copy(stores = stores) }
                }
        }

        viewModelScope.launch {
            budgetRepository.getAllBudgets()
                .collect { budgets ->
                    _uiState.update { it.copy(allBudgets = budgets) }
                }
        }
    }

    private suspend fun loadSummary() {
        val summary = when (_uiState.value.selectedPeriod) {
            TimePeriod.DAILY -> transactionRepository.getDailySpendingSummary()
            TimePeriod.WEEKLY -> transactionRepository.getWeeklySpendingSummary()
            TimePeriod.MONTHLY -> transactionRepository.getMonthlySpendingSummary()
        }
        _uiState.update { it.copy(currentSummary = summary) }
    }

    private suspend fun loadBudgetStatus() {
        val status = budgetRepository.getBudgetStatus()
        _uiState.update { it.copy(budgetStatus = status) }
    }

    fun setPeriod(period: TimePeriod) {
        _uiState.update { it.copy(selectedPeriod = period) }
        viewModelScope.launch {
            loadSummary()
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadData()
    }

    fun showBudgetSetup() {
        _uiState.update { it.copy(showBudgetSetup = true) }
    }

    fun hideBudgetSetup() {
        _uiState.update { it.copy(showBudgetSetup = false) }
    }

    fun createBudget(
        name: String,
        amount: Double,
        period: BudgetPeriod,
        alertThreshold: Double = 0.8
    ) {
        viewModelScope.launch {
            budgetRepository.createBudget(
                name = name,
                amount = amount,
                period = period,
                alertThreshold = alertThreshold
            )
            loadBudgetStatus()
            _uiState.update { it.copy(showBudgetSetup = false) }
        }
    }

    fun updateBudget(budget: BudgetTargetEntity) {
        viewModelScope.launch {
            budgetRepository.updateBudget(budget)
            loadBudgetStatus()
        }
    }

    fun deleteBudget(budget: BudgetTargetEntity) {
        viewModelScope.launch {
            budgetRepository.deleteBudget(budget)
            loadBudgetStatus()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
