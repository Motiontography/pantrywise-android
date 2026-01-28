package com.pantrywise.ui.pantry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.domain.model.LocationType
import com.pantrywise.domain.model.StockStatus
import com.pantrywise.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(
    onNavigateToScanner: () -> Unit,
    onNavigateToStaples: () -> Unit,
    viewModel: PantryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pantry") },
                actions = {
                    if (uiState.expiringCount > 0) {
                        BadgedBox(
                            badge = { Badge { Text(uiState.expiringCount.toString()) } }
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Expiring items")
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Essentials") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToStaples()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Star, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToScanner,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            var searchQuery by remember { mutableStateOf("") }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.setSearchQuery(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search products...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )

            // Location filter chips
            LocationFilterChips(
                selectedLocation = uiState.selectedLocation,
                onLocationSelected = { viewModel.setLocationFilter(it) }
            )

            // Inventory list
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No items in your pantry",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap + to add items",
                            style = MaterialTheme.typography.bodyMedium,
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
                    items(
                        items = uiState.filteredItems,
                        key = { it.inventoryItem.id }
                    ) { item ->
                        InventoryItemCard(
                            item = item,
                            onQuantityChange = { adjustment ->
                                viewModel.adjustQuantity(item.inventoryItem.id, adjustment)
                            },
                            onDelete = { viewModel.deleteItem(item.inventoryItem.id) },
                            onMove = { location ->
                                viewModel.moveItem(item.inventoryItem.id, location)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocationFilterChips(
    selectedLocation: LocationType?,
    onLocationSelected: (LocationType?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedLocation == null,
                onClick = { onLocationSelected(null) },
                label = { Text("All", maxLines = 1) }
            )
        }
        items(LocationType.entries.size) { index ->
            val location = LocationType.entries[index]
            FilterChip(
                selected = selectedLocation == location,
                onClick = { onLocationSelected(location) },
                label = { Text(location.displayName, maxLines = 1) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryItemCard(
    item: InventoryItemWithProduct,
    onQuantityChange: (Double) -> Unit,
    onDelete: () -> Unit,
    onMove: (LocationType) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            StatusBadge(status = item.inventoryItem.stockStatus)

            Spacer(modifier = Modifier.width(12.dp))

            // Product info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.product.brand != null) {
                    Text(
                        text = item.product.brand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${item.inventoryItem.location.displayName} • ${item.product.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.inventoryItem.expirationStatusText?.let { expirationText ->
                    Text(
                        text = expirationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (item.inventoryItem.stockStatus) {
                            StockStatus.EXPIRED -> ExpiredColor
                            StockStatus.EXPIRING_SOON -> ExpiringSoonColor
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Quantity controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onQuantityChange(-1.0) },
                    enabled = item.inventoryItem.quantityOnHand > 0
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }

                Text(
                    text = "${item.inventoryItem.quantityOnHand.toInt()} ${item.inventoryItem.unit.displayName}",
                    style = MaterialTheme.typography.titleMedium
                )

                IconButton(onClick = { onQuantityChange(1.0) }) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Move") },
                            onClick = {
                                showMenu = false
                                showMoveDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.MoveUp, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }

    // Move dialog
    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move to") },
            text = {
                Column {
                    LocationType.entries.forEach { location ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = item.inventoryItem.location == location,
                                onClick = {
                                    onMove(location)
                                    showMoveDialog = false
                                }
                            )
                            Text(location.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatusBadge(status: StockStatus) {
    val (color, icon) = when (status) {
        StockStatus.IN_STOCK -> InStockColor to Icons.Default.CheckCircle
        StockStatus.LOW -> LowStockColor to Icons.Default.Warning
        StockStatus.OUT_OF_STOCK -> OutOfStockColor to Icons.Default.RemoveCircle
        StockStatus.EXPIRED -> ExpiredColor to Icons.Default.Error
        StockStatus.EXPIRING_SOON -> ExpiringSoonColor to Icons.Default.Schedule
        StockStatus.UNKNOWN -> Color.Gray to Icons.Default.HelpOutline
    }

    Icon(
        imageVector = icon,
        contentDescription = status.displayName,
        tint = color,
        modifier = Modifier.size(24.dp)
    )
}
