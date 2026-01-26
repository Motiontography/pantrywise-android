package com.pantrywise.ui.stores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.data.local.entity.StoreAisleMapEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AisleMappingScreen(
    storeId: String,
    onNavigateBack: () -> Unit,
    viewModel: StoreManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(storeId) {
        val store = uiState.stores.find { it.id == storeId }
        if (store != null) {
            viewModel.selectStoreForAisles(store)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelectedStore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Aisle Mapping")
                        uiState.selectedStore?.let {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddAisleDialog() }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Aisle Mapping")
            }
        }
    ) { padding ->
        if (uiState.aisleMaps.isEmpty()) {
            EmptyAisleMapView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onAddMapping = { viewModel.showAddAisleDialog() }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Map categories to aisles for optimized shopping routes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(uiState.aisleMaps) { aisleMap ->
                    AisleMapCard(
                        aisleMap = aisleMap,
                        onEdit = { viewModel.editAisleMap(aisleMap) },
                        onDelete = { viewModel.deleteAisleMap(aisleMap) }
                    )
                }

                // Suggestions section
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    val unmappedCategories = viewModel.commonCategories.filter { category ->
                        uiState.aisleMaps.none { it.categoryName.equals(category, ignoreCase = true) }
                    }
                    if (unmappedCategories.isNotEmpty()) {
                        SuggestedCategoriesSection(
                            categories = unmappedCategories,
                            onCategoryClick = { category ->
                                // Pre-fill the dialog with this category
                            }
                        )
                    }
                }
            }
        }
    }

    // Add/Edit Aisle Dialog
    if (uiState.showAddAisleDialog) {
        AisleMapDialog(
            aisleMap = uiState.editingAisleMap,
            commonCategories = viewModel.commonCategories,
            existingCategories = uiState.aisleMaps.map { it.categoryName },
            onDismiss = { viewModel.hideAddAisleDialog() },
            onSave = { category, aisle, section ->
                viewModel.addOrUpdateAisleMap(category, aisle, section)
            }
        )
    }
}

@Composable
private fun EmptyAisleMapView(
    modifier: Modifier = Modifier,
    onAddMapping: () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Map,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No aisle mappings yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Map product categories to aisles for shopping optimization",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onAddMapping) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Mapping")
            }
        }
    }
}

@Composable
private fun AisleMapCard(
    aisleMap: StoreAisleMapEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Aisle badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        aisleMap.aisle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        aisleMap.categoryName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    aisleMap.section?.let { section ->
                        Text(
                            "Section: $section",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Mapping") },
            text = { Text("Remove the aisle mapping for \"${aisleMap.categoryName}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestedCategoriesSection(
    categories: List<String>,
    onCategoryClick: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "Suggested Categories",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.take(8).forEach { category ->
                    SuggestionChip(
                        onClick = { onCategoryClick(category) },
                        label = { Text(category) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AisleMapDialog(
    aisleMap: StoreAisleMapEntity?,
    commonCategories: List<String>,
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (category: String, aisle: String, section: String?) -> Unit
) {
    var category by remember { mutableStateOf(aisleMap?.categoryName ?: "") }
    var aisle by remember { mutableStateOf(aisleMap?.aisle ?: "") }
    var section by remember { mutableStateOf(aisleMap?.section ?: "") }
    var categoryError by remember { mutableStateOf(false) }
    var aisleError by remember { mutableStateOf(false) }
    var showCategorySuggestions by remember { mutableStateOf(false) }

    val availableCategories = commonCategories.filter { cat ->
        (aisleMap != null && cat.equals(aisleMap.categoryName, ignoreCase = true)) ||
        existingCategories.none { it.equals(cat, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (aisleMap != null) "Edit Mapping" else "Add Aisle Mapping") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category field with suggestions
                ExposedDropdownMenuBox(
                    expanded = showCategorySuggestions,
                    onExpandedChange = { showCategorySuggestions = it }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {
                            category = it
                            categoryError = false
                            showCategorySuggestions = true
                        },
                        label = { Text("Category *") },
                        singleLine = true,
                        isError = categoryError,
                        supportingText = if (categoryError) {
                            { Text("Category is required") }
                        } else null,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategorySuggestions) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    val filteredCategories = availableCategories.filter {
                        it.contains(category, ignoreCase = true)
                    }

                    if (filteredCategories.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = showCategorySuggestions,
                            onDismissRequest = { showCategorySuggestions = false }
                        ) {
                            filteredCategories.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion) },
                                    onClick = {
                                        category = suggestion
                                        showCategorySuggestions = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = aisle,
                    onValueChange = {
                        aisle = it
                        aisleError = false
                    },
                    label = { Text("Aisle *") },
                    placeholder = { Text("e.g., 1, A, Frozen") },
                    singleLine = true,
                    isError = aisleError,
                    supportingText = if (aisleError) {
                        { Text("Aisle is required") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = section,
                    onValueChange = { section = it },
                    label = { Text("Section (optional)") },
                    placeholder = { Text("e.g., Left side, Top shelf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var hasError = false
                    if (category.isBlank()) {
                        categoryError = true
                        hasError = true
                    }
                    if (aisle.isBlank()) {
                        aisleError = true
                        hasError = true
                    }
                    if (!hasError) {
                        onSave(category, aisle, section.ifEmpty { null })
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
