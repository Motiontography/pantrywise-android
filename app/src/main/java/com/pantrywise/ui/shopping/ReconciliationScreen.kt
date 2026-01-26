package com.pantrywise.ui.shopping

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.data.local.entity.CartItem
import com.pantrywise.data.local.entity.ShoppingListItemEntity
import com.pantrywise.domain.model.CartMatchType
import com.pantrywise.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconciliationScreen(
    sessionId: String,
    onSessionComplete: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ReconciliationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        viewModel.loadReconciliation(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Cart") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                viewModel.abandonSession()
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Discard")
                    }
                    Button(
                        onClick = { showConfirmDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.summary != null && uiState.summary!!.totalItems > 0
                    ) {
                        Text("Complete")
                    }
                }
            }
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
                // Summary card
                item {
                    uiState.summary?.let { summary ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    "Summary",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total items:")
                                    Text("${summary.totalItems}")
                                }
                                if (summary.estimatedTotal > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Estimated total:")
                                        Text("$${String.format("%.2f", summary.estimatedTotal)}")
                                    }
                                }
                            }
                        }
                    }
                }

                // Planned items (green)
                if (uiState.plannedItems.isNotEmpty()) {
                    item {
                        ReconciliationSection(
                            title = "Planned Items",
                            subtitle = "From your shopping list",
                            color = PlannedColor,
                            icon = Icons.Default.CheckCircle
                        )
                    }
                    items(uiState.plannedItems) { item ->
                        ReconciliationItemCard(
                            productName = uiState.productNames[item.productId] ?: "Unknown",
                            quantity = item.quantity,
                            unit = item.unit.displayName,
                            color = PlannedColor
                        )
                    }
                }

                // Missing items (yellow)
                if (uiState.missingItems.isNotEmpty()) {
                    item {
                        ReconciliationSection(
                            title = "Missing Items",
                            subtitle = "Still on your list",
                            color = LowStockColor,
                            icon = Icons.Default.Warning
                        )
                    }
                    items(uiState.missingItems) { item ->
                        ReconciliationItemCard(
                            productName = uiState.productNames[item.productId] ?: "Unknown",
                            quantity = item.quantityNeeded,
                            unit = item.unit.displayName,
                            color = LowStockColor
                        )
                    }
                }

                // Extra items (blue)
                if (uiState.extraItems.isNotEmpty()) {
                    item {
                        ReconciliationSection(
                            title = "Extra Items",
                            subtitle = "Not on your list",
                            color = ExtraColor,
                            icon = Icons.Default.AddCircle
                        )
                    }
                    items(uiState.extraItems) { item ->
                        ReconciliationItemCard(
                            productName = uiState.productNames[item.productId] ?: "Unknown",
                            quantity = item.quantity,
                            unit = item.unit.displayName,
                            color = ExtraColor
                        )
                    }
                }

                // Already stocked warnings (orange)
                if (uiState.alreadyStockedItems.isNotEmpty()) {
                    item {
                        ReconciliationSection(
                            title = "Already Stocked",
                            subtitle = "You may have enough",
                            color = AlreadyStockedColor,
                            icon = Icons.Default.Info
                        )
                    }
                    items(uiState.alreadyStockedItems) { item ->
                        ReconciliationItemCard(
                            productName = uiState.productNames[item.productId] ?: "Unknown",
                            quantity = item.quantity,
                            unit = item.unit.displayName,
                            color = AlreadyStockedColor
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Complete Shopping?") },
            text = {
                Text("This will update your inventory with the items in your cart and remove completed items from your shopping list.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        scope.launch {
                            viewModel.completeSession()
                            onSessionComplete()
                        }
                    }
                ) {
                    Text("Complete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ReconciliationSection(
    title: String,
    subtitle: String,
    color: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ReconciliationItemCard(
    productName: String,
    quantity: Double,
    unit: String,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = productName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${quantity.toInt()} $unit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
