package com.pantrywise.ui.price

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

enum class PriceBookSortOption {
    NAME,
    PRICE_LOW_HIGH,
    PRICE_HIGH_LOW,
    RECENTLY_UPDATED,
    BIGGEST_SAVINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceBookScreen(
    onNavigateBack: () -> Unit,
    onProductClick: (productId: String, productName: String) -> Unit,
    viewModel: PriceBookViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Price Book") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Badge(
                            modifier = Modifier.offset(x = 8.dp, y = (-8).dp)
                        ) {
                            if (uiState.activeFiltersCount > 0) {
                                Text(uiState.activeFiltersCount.toString())
                            }
                        }
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }

                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            PriceBookSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(getSortOptionLabel(option)) },
                                    onClick = {
                                        viewModel.setSortOption(option)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (uiState.sortOption == option) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.products.isEmpty()) {
            EmptyPriceBookView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary card
                item {
                    PriceBookSummaryCard(
                        totalProducts = uiState.totalProducts,
                        totalSavings = uiState.totalPotentialSavings,
                        alertsCount = uiState.activeAlertsCount
                    )
                }

                // Search bar
                item {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search products...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Products list
                items(uiState.products) { product ->
                    PriceBookProductCard(
                        product = product,
                        onClick = { onProductClick(product.productId, product.productName) }
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        PriceBookFilterSheet(
            selectedCategories = uiState.selectedCategories,
            selectedStores = uiState.selectedStores,
            availableCategories = uiState.availableCategories,
            availableStores = uiState.availableStores,
            onApplyFilters = { categories, stores ->
                viewModel.setFilters(categories, stores)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun EmptyPriceBookView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No prices tracked yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Scan products at stores to build your price book",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PriceBookSummaryCard(
    totalProducts: Int,
    totalSavings: Double,
    alertsCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryStatItem(
                label = "Products",
                value = totalProducts.toString(),
                icon = Icons.Default.Inventory
            )
            SummaryStatItem(
                label = "Potential Savings",
                value = String.format("$%.2f", totalSavings),
                icon = Icons.Default.Savings
            )
            SummaryStatItem(
                label = "Active Alerts",
                value = alertsCount.toString(),
                icon = Icons.Default.Notifications
            )
        }
    }
}

@Composable
private fun SummaryStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun PriceBookProductCard(
    product: PriceBookProduct,
    onClick: () -> Unit
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
                    product.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        product.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        " • ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${product.storeCount} stores",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (product.potentialSavings > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "Save ${String.format("$%.2f", product.potentialSavings)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        String.format("$%.2f", product.currentPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    product.priceChange?.let { change ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            if (change > 0) Icons.AutoMirrored.Filled.TrendingUp
                            else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (change > 0) MaterialTheme.colorScheme.error
                            else Color(0xFF4CAF50)
                        )
                    }
                }

                Text(
                    "Best: ${String.format("$%.2f", product.lowestPrice)} at ${product.lowestPriceStore}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceBookFilterSheet(
    selectedCategories: Set<String>,
    selectedStores: Set<String>,
    availableCategories: List<String>,
    availableStores: List<String>,
    onApplyFilters: (categories: Set<String>, stores: Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var categories by remember { mutableStateOf(selectedCategories) }
    var stores by remember { mutableStateOf(selectedStores) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Filter",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Categories
            Text(
                "Categories",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableCategories.forEach { category ->
                    FilterChip(
                        selected = categories.contains(category),
                        onClick = {
                            categories = if (categories.contains(category)) {
                                categories - category
                            } else {
                                categories + category
                            }
                        },
                        label = { Text(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Stores
            Text(
                "Stores",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableStores.forEach { store ->
                    FilterChip(
                        selected = stores.contains(store),
                        onClick = {
                            stores = if (stores.contains(store)) {
                                stores - store
                            } else {
                                stores + store
                            }
                        },
                        label = { Text(store) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        categories = emptySet()
                        stores = emptySet()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear All")
                }

                Button(
                    onClick = { onApplyFilters(categories, stores) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply Filters")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Simple flow row implementation using built-in FlowRow from Material3
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement
    ) {
        content()
    }
}

private fun getSortOptionLabel(option: PriceBookSortOption): String {
    return when (option) {
        PriceBookSortOption.NAME -> "Name A-Z"
        PriceBookSortOption.PRICE_LOW_HIGH -> "Price: Low to High"
        PriceBookSortOption.PRICE_HIGH_LOW -> "Price: High to Low"
        PriceBookSortOption.RECENTLY_UPDATED -> "Recently Updated"
        PriceBookSortOption.BIGGEST_SAVINGS -> "Biggest Savings"
    }
}
