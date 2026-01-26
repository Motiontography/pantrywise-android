package com.pantrywise.ui.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.data.local.entity.AuditAction
import com.pantrywise.data.local.entity.AuditItemEntity
import com.pantrywise.data.local.entity.AuditSessionEntity
import com.pantrywise.data.local.entity.AuditStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditSessionScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.activeSession != null) "Inventory Audit" else "Audit History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.activeSession == null) {
                        IconButton(onClick = { viewModel.showStartDialog() }) {
                            Icon(Icons.Default.Add, contentDescription = "Start Audit")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.activeSession == null && uiState.sessions.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showStartDialog() },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = { Text("Start Audit") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.activeSession != null) {
                ActiveAuditContent(
                    session = uiState.activeSession!!,
                    pendingItems = uiState.pendingItems,
                    completedItems = uiState.completedItems,
                    summary = uiState.summary,
                    onConfirmItem = viewModel::confirmItem,
                    onSelectItem = viewModel::selectItem,
                    onSkipItem = viewModel::skipItem,
                    onCompleteAudit = { viewModel.showCompleteDialog() },
                    onCancelAudit = viewModel::cancelAudit
                )
            } else {
                AuditHistoryContent(
                    sessions = uiState.sessions,
                    onSessionClick = viewModel::loadSession,
                    onDeleteSession = viewModel::deleteSession,
                    onStartAudit = { viewModel.showStartDialog() }
                )
            }
        }
    }

    // Start Audit Dialog
    if (uiState.showStartDialog) {
        StartAuditDialog(
            onDismiss = { viewModel.hideStartDialog() },
            onStart = { name -> viewModel.startNewAudit(name) }
        )
    }

    // Complete Audit Dialog
    if (uiState.showCompleteDialog) {
        CompleteAuditDialog(
            summary = uiState.summary,
            onDismiss = { viewModel.hideCompleteDialog() },
            onConfirm = { viewModel.completeAudit() }
        )
    }

    // Item Adjustment Dialog
    uiState.selectedItem?.let { item ->
        AuditItemDialog(
            item = item,
            onDismiss = { viewModel.clearSelectedItem() },
            onConfirm = { viewModel.confirmItem(item) },
            onAdjust = { quantity, notes -> viewModel.adjustItem(item, quantity, notes) },
            onRemove = { notes -> viewModel.removeItem(item, notes) },
            onSkip = { viewModel.skipItem(item) }
        )
    }

    // Error Snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error then clear
            viewModel.clearError()
        }
    }
}

@Composable
private fun ActiveAuditContent(
    session: AuditSessionEntity,
    pendingItems: List<AuditItemEntity>,
    completedItems: List<AuditItemEntity>,
    summary: com.pantrywise.data.local.dao.AuditSummaryData?,
    onConfirmItem: (AuditItemEntity) -> Unit,
    onSelectItem: (AuditItemEntity) -> Unit,
    onSkipItem: (AuditItemEntity) -> Unit,
    onCompleteAudit: () -> Unit,
    onCancelAudit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Progress Card
        item {
            AuditProgressCard(
                session = session,
                onComplete = onCompleteAudit,
                onCancel = onCancelAudit
            )
        }

        // Summary Card
        summary?.let {
            item {
                AuditSummaryCard(summary = it)
            }
        }

        // Pending Items Section
        if (pendingItems.isNotEmpty()) {
            item {
                Text(
                    "Pending Items (${pendingItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(pendingItems) { item ->
                AuditItemRow(
                    item = item,
                    onConfirm = { onConfirmItem(item) },
                    onClick = { onSelectItem(item) },
                    onSkip = { onSkipItem(item) }
                )
            }
        }

        // Completed Items Section
        if (completedItems.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Completed (${completedItems.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(completedItems) { item ->
                CompletedAuditItemRow(item = item)
            }
        }
    }
}

@Composable
private fun AuditProgressCard(
    session: AuditSessionEntity,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        session.name ?: "Audit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${session.auditedItems} of ${session.totalItems} items",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "${session.progressPercent}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { session.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onComplete,
                    modifier = Modifier.weight(1f),
                    enabled = session.auditedItems > 0
                ) {
                    Text("Complete")
                }
            }
        }
    }
}

@Composable
private fun AuditSummaryCard(summary: com.pantrywise.data.local.dao.AuditSummaryData) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryItem(
                label = "Confirmed",
                value = summary.confirmedItems.toString(),
                color = Color(0xFF4CAF50)
            )
            SummaryItem(
                label = "Adjusted",
                value = summary.adjustedItems.toString(),
                color = Color(0xFFFF9800)
            )
            SummaryItem(
                label = "Removed",
                value = summary.removedItems.toString(),
                color = Color(0xFFF44336)
            )
            SummaryItem(
                label = "Skipped",
                value = summary.skippedItems.toString(),
                color = Color(0xFF9E9E9E)
            )
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AuditItemRow(
    item: AuditItemEntity,
    onConfirm: () -> Unit,
    onClick: () -> Unit,
    onSkip: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${item.expectedQuantity.toInt()} ${item.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        item.location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onSkip) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Skip",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onConfirm) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedAuditItemRow(item: AuditItemEntity) {
    val actionColor = when (item.action) {
        AuditAction.CONFIRMED -> Color(0xFF4CAF50)
        AuditAction.ADJUSTED -> Color(0xFFFF9800)
        AuditAction.REMOVED -> Color(0xFFF44336)
        AuditAction.SKIPPED -> Color(0xFF9E9E9E)
        null -> Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.action == AuditAction.ADJUSTED) {
                    Text(
                        "${item.expectedQuantity.toInt()} -> ${item.actualQuantity?.toInt()} ${item.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = actionColor
                    )
                }
            }

            Surface(
                color = actionColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    item.displayAction ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = actionColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AuditHistoryContent(
    sessions: List<AuditSessionEntity>,
    onSessionClick: (String) -> Unit,
    onDeleteSession: (AuditSessionEntity) -> Unit,
    onStartAudit: () -> Unit
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Checklist,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No audits yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Start an audit to reconcile your inventory",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sessions) { session ->
                AuditSessionCard(
                    session = session,
                    onClick = {
                        if (session.status == AuditStatus.IN_PROGRESS) {
                            onSessionClick(session.id)
                        }
                    },
                    onDelete = { onDeleteSession(session) }
                )
            }
        }
    }
}

@Composable
private fun AuditSessionCard(
    session: AuditSessionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = session.status == AuditStatus.IN_PROGRESS,
                onClick = onClick
            )
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
                    session.name ?: "Audit",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    dateFormat.format(Date(session.startedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusColor = when (session.status) {
                        AuditStatus.IN_PROGRESS -> Color(0xFF2196F3)
                        AuditStatus.COMPLETED -> Color(0xFF4CAF50)
                        AuditStatus.CANCELLED -> Color(0xFF9E9E9E)
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            session.displayStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        "${session.auditedItems}/${session.totalItems} items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StartAuditDialog(
    onDismiss: () -> Unit,
    onStart: (String?) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start Inventory Audit") },
        text = {
            Column {
                Text(
                    "This will create a checklist of all items in your inventory for you to verify.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Audit Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onStart(name.ifEmpty { null }) }) {
                Text("Start Audit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CompleteAuditDialog(
    summary: com.pantrywise.data.local.dao.AuditSummaryData?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete Audit") },
        text = {
            Column {
                Text("Are you sure you want to complete this audit?")
                summary?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Summary:", fontWeight = FontWeight.Bold)
                    Text("- Confirmed: ${it.confirmedItems}")
                    Text("- Adjusted: ${it.adjustedItems}")
                    Text("- Removed: ${it.removedItems}")
                    Text("- Skipped: ${it.skippedItems}")
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Complete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AuditItemDialog(
    item: AuditItemEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onAdjust: (Double, String?) -> Unit,
    onRemove: (String?) -> Unit,
    onSkip: () -> Unit
) {
    var actualQuantity by remember { mutableStateOf(item.expectedQuantity.toString()) }
    var notes by remember { mutableStateOf("") }
    var showAdjust by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.productName) },
        text = {
            Column {
                Text(
                    "Expected: ${item.expectedQuantity.toInt()} ${item.unit}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Location: ${item.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (showAdjust) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = actualQuantity,
                        onValueChange = { actualQuantity = it },
                        label = { Text("Actual Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (showAdjust) {
                Button(
                    onClick = {
                        val qty = actualQuantity.toDoubleOrNull() ?: item.expectedQuantity
                        onAdjust(qty, notes.ifEmpty { null })
                    }
                ) {
                    Text("Save")
                }
            } else {
                Button(onClick = onConfirm) {
                    Text("Confirm")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!showAdjust) {
                    TextButton(onClick = { showAdjust = true }) {
                        Text("Adjust")
                    }
                    TextButton(onClick = { onRemove(null) }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
