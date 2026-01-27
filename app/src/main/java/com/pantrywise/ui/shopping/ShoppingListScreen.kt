package com.pantrywise.ui.shopping

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.data.local.entity.ShoppingListEntity
import com.pantrywise.domain.usecase.ShoppingSuggestion
import com.pantrywise.domain.usecase.SuggestionType
import com.pantrywise.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    onStartSession: (String) -> Unit,
    onNavigateToScanner: () -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showStoreDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // History state
    var showHistory by remember { mutableStateOf(false) }
    var expandedWeeks by remember { mutableStateOf(setOf(0)) } // Week 0 (This Week) expanded by default
    var listToDelete by remember { mutableStateOf<ShoppingListEntity?>(null) }
    var showHistoryDeleteConfirmation by remember { mutableStateOf(false) }

    // Group archived lists by week
    val groupedHistory = remember(uiState.archivedLists) {
        groupListsByWeek(uiState.archivedLists)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.activeList?.name ?: "Shopping List") },
                actions = {
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            if (uiState.activeList != null) {
                                DropdownMenuItem(
                                    text = { Text("Delete List") },
                                    onClick = {
                                        showMoreMenu = false
                                        showDeleteConfirmation = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showStoreDialog = true },
                icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                text = { Text("Start Shopping") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Shopping list items
                    if (uiState.items.isNotEmpty()) {
                        item {
                            Text(
                                "Shopping List",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(
                            items = uiState.items,
                            key = { it.listItem.id }
                        ) { item ->
                            ShoppingListItemCard(
                                item = item,
                                onCheckedChange = { checked ->
                                    viewModel.toggleItemChecked(item.listItem.id, checked)
                                },
                                onDelete = { viewModel.removeItem(item.listItem.id) }
                            )
                        }
                    }

                    // Suggestions section
                    if (uiState.suggestions.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Suggestions",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(
                            items = uiState.suggestions.take(5),
                            key = { it.product.id }
                        ) { suggestion ->
                            SuggestionCard(
                                suggestion = suggestion,
                                onAdd = { viewModel.addSuggestionToList(suggestion) }
                            )
                        }
                    }

                    // Empty state
                    if (uiState.items.isEmpty() && uiState.suggestions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "Your shopping list is empty",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Scan items or add from suggestions",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Shopping History Section
                    if (uiState.archivedLists.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            ShoppingHistoryHeader(
                                isExpanded = showHistory,
                                count = uiState.archivedLists.size,
                                onClick = { showHistory = !showHistory }
                            )
                        }

                        if (showHistory) {
                            // Week sections
                            groupedHistory.forEach { (weekIndex, lists) ->
                                item(key = "week_header_$weekIndex") {
                                    WeekHeader(
                                        weekIndex = weekIndex,
                                        isExpanded = expandedWeeks.contains(weekIndex),
                                        count = lists.size,
                                        onClick = {
                                            expandedWeeks = if (expandedWeeks.contains(weekIndex)) {
                                                expandedWeeks - weekIndex
                                            } else {
                                                expandedWeeks + weekIndex
                                            }
                                        }
                                    )
                                }

                                if (expandedWeeks.contains(weekIndex)) {
                                    items(
                                        items = lists,
                                        key = { "history_${it.id}" }
                                    ) { list ->
                                        HistoryListRow(
                                            list = list,
                                            onDelete = {
                                                listToDelete = list
                                                showHistoryDeleteConfirmation = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom padding for FAB
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // Store selection dialog
    if (showStoreDialog) {
        var storeName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showStoreDialog = false },
            title = { Text("Start Shopping Session") },
            text = {
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("Store name (optional)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStoreDialog = false
                        scope.launch {
                            val sessionId = viewModel.startShoppingSession(
                                store = storeName.takeIf { it.isNotBlank() }
                            )
                            onStartSession(sessionId)
                        }
                    }
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete List?") },
            text = {
                Text("Are you sure you want to delete \"${uiState.activeList?.name}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        uiState.activeList?.let { list ->
                            viewModel.deleteShoppingList(list.id)
                        }
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // History delete confirmation dialog
    if (showHistoryDeleteConfirmation && listToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showHistoryDeleteConfirmation = false
                listToDelete = null
            },
            title = { Text("Delete Shopping List?") },
            text = {
                Text("Are you sure you want to delete \"${listToDelete?.name}\"? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        listToDelete?.let { list ->
                            viewModel.deleteShoppingList(list.id)
                        }
                        showHistoryDeleteConfirmation = false
                        listToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHistoryDeleteConfirmation = false
                        listToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ShoppingListItemCard(
    item: ShoppingListItemWithProduct,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.listItem.isChecked,
                onCheckedChange = onCheckedChange
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.listItem.isChecked) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.listItem.quantityNeeded.toInt()} ${item.listItem.unit.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.listItem.reason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SuggestionCard(
    suggestion: ShoppingSuggestion,
    onAdd: () -> Unit
) {
    val (icon, color) = when (suggestion.type) {
        SuggestionType.OUT_OF_STOCK -> Icons.Default.RemoveCircle to OutOfStockColor
        SuggestionType.LOW_STOCK -> Icons.Default.Warning to LowStockColor
        SuggestionType.EXPIRING_SOON -> Icons.Default.Schedule to ExpiringSoonColor
        SuggestionType.FREQUENT_PURCHASE -> Icons.Default.TrendingUp to PantryBlue
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = suggestion.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }

            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }
    }
}

@Composable
fun ShoppingHistoryHeader(
    isExpanded: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevron_rotation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Shopping History",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(rotationAngle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WeekHeader(
    weekIndex: Int,
    isExpanded: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "week_chevron_rotation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = getWeekTitle(weekIndex),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "($count)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotationAngle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListRow(
    list: ShoppingListEntity,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                false // Don't dismiss yet, wait for confirmation
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        modifier = Modifier.padding(start = 16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
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
                        text = list.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        list.completedStore?.let { store ->
                            Text(
                                text = store,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        list.archivedAt?.let { timestamp ->
                            Text(
                                text = formatDate(timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                list.completedTotal?.let { total ->
                    if (total > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "$${String.format("%.2f", total)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Groups shopping lists by week based on their archived date
 * Returns a map of weekIndex (0 = this week, 1 = last week, etc.) to lists
 */
private fun groupListsByWeek(lists: List<ShoppingListEntity>): Map<Int, List<ShoppingListEntity>> {
    val calendar = Calendar.getInstance()
    val now = calendar.timeInMillis

    // Get start of this week (Sunday)
    calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startOfThisWeek = calendar.timeInMillis

    return lists
        .filter { it.archivedAt != null }
        .groupBy { list ->
            val archivedAt = list.archivedAt!!
            val daysDiff = (startOfThisWeek - archivedAt) / (24 * 60 * 60 * 1000)
            when {
                archivedAt >= startOfThisWeek -> 0 // This week
                daysDiff < 7 -> 1 // Last week
                daysDiff < 14 -> 2 // 2-3 weeks ago
                else -> 3 // 3-4 weeks ago
            }
        }
        .toSortedMap()
}

private fun getWeekTitle(weekIndex: Int): String {
    return when (weekIndex) {
        0 -> "This Week"
        1 -> "Last Week"
        2 -> "2-3 Weeks Ago"
        3 -> "3-4 Weeks Ago"
        else -> "$weekIndex Weeks Ago"
    }
}

private fun formatDate(timestamp: Long): String {
    val format = SimpleDateFormat("MMM d", Locale.getDefault())
    return format.format(Date(timestamp))
}
