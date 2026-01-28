package com.pantrywise.ui.finance

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.data.local.entity.BudgetPeriod
import com.pantrywise.data.local.entity.BudgetStatus
import com.pantrywise.data.local.entity.PurchaseTransactionEntity
import com.pantrywise.ui.theme.PantryGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingSummaryScreen(
    onNavigateToReceiptScanner: () -> Unit = {},
    viewModel: FinanceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spending") },
                actions = {
                    IconButton(onClick = onNavigateToReceiptScanner) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = "Scan Receipt")
                    }
                    IconButton(onClick = { viewModel.showBudgetSetup() }) {
                        Icon(Icons.Default.Savings, contentDescription = "Budget")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Budget Progress Card (if budget exists)
                uiState.budgetStatus?.let { status ->
                    item {
                        BudgetProgressCard(
                            status = status,
                            onEditBudget = { viewModel.showBudgetSetup() }
                        )
                    }
                }

                // No budget prompt
                if (uiState.budgetStatus == null && !uiState.isLoading) {
                    item {
                        NoBudgetPrompt(onSetupBudget = { viewModel.showBudgetSetup() })
                    }
                }

                // Period selector
                item {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TimePeriod.entries.forEachIndexed { index, period ->
                            SegmentedButton(
                                selected = uiState.selectedPeriod == period,
                                onClick = { viewModel.setPeriod(period) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = TimePeriod.entries.size
                                )
                            ) {
                                Text(period.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                }

                // Summary card
                item {
                    uiState.currentSummary?.let { summary ->
                        SummaryCard(summary = summary, period = uiState.selectedPeriod)
                    }
                }

                // Stats row
                item {
                    uiState.currentSummary?.let { summary ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = "Transactions",
                                value = summary.transactionCount.toString(),
                                icon = Icons.Default.Receipt,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Average",
                                value = "$${String.format("%.2f", summary.averageTransaction)}",
                                icon = Icons.Default.TrendingUp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Recent transactions header
                if (uiState.recentTransactions.isNotEmpty()) {
                    item {
                        Text(
                            "Recent Transactions",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(uiState.recentTransactions) { transaction ->
                        TransactionCard(transaction = transaction)
                    }
                }

                // Empty state
                if (uiState.recentTransactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Receipt,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No transactions yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Complete a shopping session to see spending",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Budget Setup Sheet
    if (uiState.showBudgetSetup) {
        BudgetSetupSheet(
            existingBudget = uiState.budgetStatus?.budget,
            onDismiss = { viewModel.hideBudgetSetup() },
            onSave = { name, amount, period, threshold ->
                viewModel.createBudget(name, amount, period, threshold)
            },
            onDelete = { budget ->
                viewModel.deleteBudget(budget)
                viewModel.hideBudgetSetup()
            }
        )
    }
}

@Composable
fun SummaryCard(
    summary: com.pantrywise.data.repository.SpendingSummary,
    period: TimePeriod
) {
    val periodLabel = when (period) {
        TimePeriod.DAILY -> "Today"
        TimePeriod.WEEKLY -> "This Week"
        TimePeriod.MONTHLY -> "This Month"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = PantryGreen
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = periodLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$${String.format("%.2f", summary.totalSpent)}",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = "total spent",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TransactionCard(transaction: PurchaseTransactionEntity) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.store ?: "Unknown Store",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(transaction.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$${String.format("%.2f", transaction.total)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun BudgetProgressCard(
    status: BudgetStatus,
    onEditBudget: () -> Unit
) {
    val progressColor = when {
        status.isOverBudget -> MaterialTheme.colorScheme.error
        status.isNearAlert -> Color(0xFFFF9800) // Orange
        else -> PantryGreen
    }

    val animatedProgress by animateFloatAsState(
        targetValue = status.percentUsed.toFloat().coerceIn(0f, 1f),
        label = "budget_progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = null,
                        tint = progressColor
                    )
                    Text(
                        text = status.budget.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onEditBudget) {
                    Icon(Icons.Default.Edit, "Edit budget")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(progressColor)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Amounts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Spent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$${String.format("%.2f", status.spent)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Budget",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$${String.format("%.2f", status.budget.amount)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Remaining info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (status.isOverBudget) "Over budget by $${String.format("%.2f", -status.remaining)}"
                           else "$${String.format("%.2f", status.remaining)} remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${status.daysRemaining} days left",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Daily budget guidance
            if (!status.isOverBudget && status.daysRemaining > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$${String.format("%.2f", status.dailyRemaining)}/day to stay on track",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun NoBudgetPrompt(onSetupBudget: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Savings,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Set a Budget",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Track your spending against a weekly or monthly budget",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onSetupBudget) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create Budget")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSetupSheet(
    existingBudget: com.pantrywise.data.local.entity.BudgetTargetEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, period: BudgetPeriod, alertThreshold: Double) -> Unit,
    onDelete: (com.pantrywise.data.local.entity.BudgetTargetEntity) -> Unit
) {
    var budgetName by remember { mutableStateOf(existingBudget?.name ?: "Grocery Budget") }
    var budgetAmount by remember { mutableStateOf(existingBudget?.amount?.toString() ?: "") }
    var selectedPeriod by remember { mutableStateOf(existingBudget?.period ?: BudgetPeriod.WEEKLY) }
    var alertThreshold by remember { mutableStateOf(existingBudget?.alertThreshold ?: 0.8) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = if (existingBudget != null) "Edit Budget" else "Create Budget",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = budgetName,
                onValueChange = { budgetName = it },
                label = { Text("Budget Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = budgetAmount,
                onValueChange = { budgetAmount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") },
                prefix = { Text("$") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Budget Period",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BudgetPeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = BudgetPeriod.entries.size
                        )
                    ) {
                        Text(period.displayName)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Alert at ${(alertThreshold * 100).toInt()}% of budget",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = alertThreshold.toFloat(),
                onValueChange = { alertThreshold = it.toDouble() },
                valueRange = 0.5f..1f,
                steps = 4
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val amount = budgetAmount.toDoubleOrNull() ?: 0.0
                    if (amount > 0) {
                        onSave(budgetName, amount, selectedPeriod, alertThreshold)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = budgetName.isNotBlank() && (budgetAmount.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text(if (existingBudget != null) "Update Budget" else "Create Budget")
            }

            if (existingBudget != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onDelete(existingBudget) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Budget")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
