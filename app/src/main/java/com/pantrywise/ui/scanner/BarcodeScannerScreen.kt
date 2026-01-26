package com.pantrywise.ui.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.data.local.entity.ProductEntity
import com.pantrywise.domain.model.LocationType
import com.pantrywise.domain.model.Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    scanContext: String?,
    onProductScanned: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddToInventoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.event) {
        when (val event = uiState.event) {
            is ScannerUiEvent.ProductFound -> {
                showAddToInventoryDialog = true
            }
            is ScannerUiEvent.ProductAdded -> {
                onProductScanned(uiState.pendingProduct?.id ?: "")
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Barcode") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Camera preview placeholder
            // In a real implementation, this would use CameraX + ML Kit
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Point camera at barcode",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    // Manual barcode entry for testing
                    var manualBarcode by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = manualBarcode,
                        onValueChange = { manualBarcode = it },
                        label = { Text("Enter barcode manually") },
                        singleLine = true,
                        modifier = Modifier.width(250.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (manualBarcode.isNotBlank()) {
                                viewModel.onBarcodeScanned(manualBarcode)
                            }
                        },
                        enabled = manualBarcode.isNotBlank()
                    ) {
                        Text("Search")
                    }
                }
            }

            // Status messages
            when (val event = uiState.event) {
                is ScannerUiEvent.Error -> {
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Text(event.message)
                    }
                }
                is ScannerUiEvent.PendingLookup -> {
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Text("Barcode saved for later lookup")
                    }
                }
                else -> {}
            }
        }
    }

    // Manual entry dialog
    if (uiState.showManualEntry) {
        ManualEntryDialog(
            barcode = uiState.lastScannedBarcode ?: "",
            onDismiss = { viewModel.dismissManualEntry() },
            onSave = { name, brand, category, unit ->
                viewModel.createManualProduct(
                    barcode = uiState.lastScannedBarcode ?: "",
                    name = name,
                    brand = brand,
                    category = category,
                    defaultUnit = unit
                )
            }
        )
    }

    // Confirmation dialog
    if (uiState.showConfirmation && uiState.pendingProduct != null) {
        ProductConfirmationDialog(
            product = uiState.pendingProduct!!,
            onDismiss = { viewModel.dismissConfirmation() },
            onConfirm = { viewModel.confirmProduct(uiState.pendingProduct!!) },
            onEdit = { viewModel.dismissConfirmation() }
        )
    }

    // Add to inventory dialog
    if (showAddToInventoryDialog && uiState.pendingProduct != null) {
        AddToInventoryDialog(
            product = uiState.pendingProduct!!,
            onDismiss = {
                showAddToInventoryDialog = false
                viewModel.resumeScanning()
            },
            onAdd = { quantity, unit, location, expirationDate ->
                viewModel.addToInventory(
                    product = uiState.pendingProduct!!,
                    quantity = quantity,
                    unit = unit,
                    location = location,
                    expirationDate = expirationDate
                )
                showAddToInventoryDialog = false
            }
        )
    }
}

@Composable
fun ManualEntryDialog(
    barcode: String,
    onDismiss: () -> Unit,
    onSave: (name: String, brand: String?, category: String, unit: Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Other") }
    var selectedUnit by remember { mutableStateOf(Unit.EACH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Product Not Found") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Barcode: $barcode", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        brand.takeIf { it.isNotBlank() },
                        category,
                        selectedUnit
                    )
                },
                enabled = name.isNotBlank() && category.isNotBlank()
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

@Composable
fun ProductConfirmationDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Name: ${product.name}", style = MaterialTheme.typography.bodyLarge)
                product.brand?.let {
                    Text("Brand: $it", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Category: ${product.category}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Source: Open Food Facts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
            }
        }
    )
}

@Composable
fun AddToInventoryDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onAdd: (quantity: Double, unit: Unit, location: LocationType, expirationDate: Long?) -> Unit
) {
    var quantity by remember { mutableStateOf("1") }
    var selectedUnit by remember { mutableStateOf(product.defaultUnit) }
    var selectedLocation by remember { mutableStateOf(LocationType.PANTRY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Inventory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Quantity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Location", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LocationType.entries.take(3).forEach { location ->
                        FilterChip(
                            selected = selectedLocation == location,
                            onClick = { selectedLocation = location },
                            label = { Text(location.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        quantity.toDoubleOrNull() ?: 1.0,
                        selectedUnit,
                        selectedLocation,
                        null
                    )
                },
                enabled = quantity.toDoubleOrNull() != null
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
