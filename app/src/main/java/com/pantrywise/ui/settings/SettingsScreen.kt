package com.pantrywise.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pantrywise.domain.model.LocationType
import com.pantrywise.domain.usecase.ExportFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToHousehold: () -> Unit = {},
    onNavigateToApiKeySettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLocationDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Refresh notification status when returning to screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkNotificationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Account & Household section
            item {
                Text(
                    "Account",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Home,
                    title = "Household",
                    subtitle = "Share pantry and lists with family",
                    onClick = onNavigateToHousehold
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Key,
                    title = "API Keys",
                    subtitle = "Configure OpenAI for AI features",
                    onClick = onNavigateToApiKeySettings
                )
            }

            // Notifications section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Notifications",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                val notificationSubtitle = when {
                    !uiState.hasNotificationPermission -> "Permission required"
                    !uiState.notificationsEnabled -> "Disabled in settings"
                    else -> "Enabled"
                }
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = "Notification Settings",
                    subtitle = notificationSubtitle,
                    onClick = {
                        // Open system notification settings for the app
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                        }
                        context.startActivity(intent)
                    },
                    tint = if (!uiState.notificationsEnabled || !uiState.hasNotificationPermission) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            // Preferences section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Preferences",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.LocationOn,
                    title = "Default Storage Location",
                    subtitle = uiState.defaultLocation.displayName,
                    onClick = { showLocationDialog = true }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.AttachMoney,
                    title = "Currency",
                    subtitle = uiState.defaultCurrency,
                    onClick = { /* TODO: Currency picker */ }
                )
            }

            // Data section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Data",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Category,
                    title = "Categories",
                    subtitle = "${uiState.categoryCount} categories",
                    onClick = { /* TODO: Category management */ }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.FileDownload,
                    title = "Export Data",
                    subtitle = "Export your data as JSON or CSV",
                    onClick = { showExportDialog = true }
                )
            }

            // About section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "About",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0",
                    onClick = { }
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.Storage,
                    title = "Storage",
                    subtitle = "${uiState.productCount} products, ${uiState.inventoryCount} inventory items",
                    onClick = { }
                )
            }

            // Danger zone
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Danger Zone",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Clear All Data",
                    subtitle = "This cannot be undone",
                    onClick = { /* TODO: Confirmation dialog */ },
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // Location picker dialog
    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Default Storage Location") },
            text = {
                Column {
                    LocationType.entries.forEach { location ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.defaultLocation == location,
                                onClick = {
                                    viewModel.setDefaultLocation(location)
                                    showLocationDialog = false
                                }
                            )
                            Text(location.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose export format:")

                    Button(
                        onClick = {
                            viewModel.exportData(ExportFormat.JSON)
                            showExportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export as JSON")
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.exportData(ExportFormat.CSV)
                            showExportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export as CSV")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Show export result
    uiState.exportResult?.let { result ->
        if (result.success) {
            Snackbar(
                action = {
                    TextButton(onClick = { viewModel.clearExportResult() }) {
                        Text("OK")
                    }
                }
            ) {
                Text("Exported to ${result.filePath}")
            }
        } else {
            Snackbar(
                containerColor = MaterialTheme.colorScheme.error
            ) {
                Text(result.errorMessage ?: "Export failed")
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
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
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tint
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
