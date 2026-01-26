package com.pantrywise.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.pantrywise.services.DeviceCalendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CalendarSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: CalendarSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Calendar permissions
    val calendarPermissions = rememberMultiplePermissionsState(
        permissions = buildList {
            add(Manifest.permission.READ_CALENDAR)
            add(Manifest.permission.WRITE_CALENDAR)
        }
    )

    // Handle events
    LaunchedEffect(uiState.event) {
        when (val event = uiState.event) {
            is CalendarSettingsEvent.SyncComplete -> {
                Toast.makeText(context, "Synced ${event.count} events", Toast.LENGTH_SHORT).show()
            }
            is CalendarSettingsEvent.Error -> {
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
        if (uiState.event != CalendarSettingsEvent.None) {
            viewModel.clearEvent()
        }
    }

    // Check permissions when screen loads
    LaunchedEffect(calendarPermissions.allPermissionsGranted) {
        if (calendarPermissions.allPermissionsGranted) {
            viewModel.loadCalendars()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar Sync") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission request
            if (!calendarPermissions.allPermissionsGranted) {
                item {
                    PermissionCard(
                        onRequestPermission = { calendarPermissions.launchMultiplePermissionRequest() }
                    )
                }
            } else {
                // Main sync toggle
                item {
                    SyncEnabledCard(
                        isEnabled = uiState.syncEnabled,
                        onToggle = { viewModel.toggleSyncEnabled() }
                    )
                }

                // Calendar selection
                if (uiState.syncEnabled) {
                    item {
                        CalendarSelectionCard(
                            calendars = uiState.availableCalendars,
                            selectedCalendarId = uiState.selectedCalendarId,
                            usePantryWiseCalendar = uiState.usePantryWiseCalendar,
                            onSelectCalendar = { viewModel.selectCalendar(it) },
                            onUsePantryWiseCalendar = { viewModel.usePantryWiseCalendar() },
                            isLoading = uiState.isLoadingCalendars
                        )
                    }

                    // Expiration sync settings
                    item {
                        ExpirationSyncCard(
                            enabled = uiState.syncExpirations,
                            reminderDays = uiState.expirationReminderDays,
                            onToggle = { viewModel.toggleExpirationSync() },
                            onReminderDaysChange = { viewModel.setExpirationReminderDays(it) }
                        )
                    }

                    // Meal plan sync settings
                    item {
                        MealPlanSyncCard(
                            enabled = uiState.syncMealPlans,
                            reminderMinutes = uiState.mealPlanReminderMinutes,
                            onToggle = { viewModel.toggleMealPlanSync() },
                            onReminderMinutesChange = { viewModel.setMealPlanReminderMinutes(it) }
                        )
                    }

                    // Sync actions
                    item {
                        SyncActionsCard(
                            isSyncing = uiState.isSyncing,
                            onSyncNow = { viewModel.syncNow() },
                            onClearEvents = { viewModel.clearAllEvents() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Calendar Permission Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "To sync expirations and meal plans to your calendar, please grant calendar access.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
private fun SyncEnabledCard(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Calendar Sync",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Sync expirations and meal plans to your calendar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun CalendarSelectionCard(
    calendars: List<DeviceCalendar>,
    selectedCalendarId: Long?,
    usePantryWiseCalendar: Boolean,
    onSelectCalendar: (Long) -> Unit,
    onUsePantryWiseCalendar: () -> Unit,
    isLoading: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Calendar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // PantryWise calendar option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUsePantryWiseCalendar)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = usePantryWiseCalendar,
                    onClick = onUsePantryWiseCalendar
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("PantryWise Calendar")
                    Text(
                        "Create a dedicated calendar for PantryWise events",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Existing calendar selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { expanded = true })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = !usePantryWiseCalendar,
                    onClick = { expanded = true }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Existing Calendar")
                    if (!usePantryWiseCalendar && selectedCalendarId != null) {
                        val selectedCalendar = calendars.find { it.id == selectedCalendarId }
                        Text(
                            selectedCalendar?.name ?: "Select...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }

            // Calendar dropdown
            AnimatedVisibility(visible = expanded && !usePantryWiseCalendar) {
                Column {
                    calendars.forEach { calendar ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectCalendar(calendar.id)
                                    expanded = false
                                }
                                .padding(vertical = 8.dp, horizontal = 32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(end = 4.dp)
                            ) {
                                Surface(
                                    color = Color(calendar.color),
                                    shape = RoundedCornerShape(2.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {}
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(calendar.name)
                                Text(
                                    calendar.accountName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpirationSyncCard(
    enabled: Boolean,
    reminderDays: Int,
    onToggle: () -> Unit,
    onReminderDaysChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Expiration Reminders",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Add calendar events when items expire",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle() }
                )
            }

            AnimatedVisibility(visible = enabled) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Remind me",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ReminderDaysSelector(
                        selectedDays = reminderDays,
                        options = listOf(1, 2, 3, 5, 7),
                        onSelect = onReminderDaysChange
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderDaysSelector(
    selectedDays: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { days ->
            FilterChip(
                selected = selectedDays == days,
                onClick = { onSelect(days) },
                label = {
                    Text(if (days == 1) "1 day" else "$days days")
                }
            )
        }
    }
}

@Composable
private fun MealPlanSyncCard(
    enabled: Boolean,
    reminderMinutes: Int,
    onToggle: () -> Unit,
    onReminderMinutesChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Meal Plan Events",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Add calendar events for planned meals",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle() }
                )
            }

            AnimatedVisibility(visible = enabled) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Remind me before meal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ReminderMinutesSelector(
                        selectedMinutes = reminderMinutes,
                        options = listOf(30, 60, 120, 1440),
                        onSelect = onReminderMinutesChange
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderMinutesSelector(
    selectedMinutes: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { minutes ->
            FilterChip(
                selected = selectedMinutes == minutes,
                onClick = { onSelect(minutes) },
                label = {
                    Text(
                        when (minutes) {
                            30 -> "30 min"
                            60 -> "1 hour"
                            120 -> "2 hours"
                            1440 -> "1 day"
                            else -> "$minutes min"
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun SyncActionsCard(
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onClearEvents: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSyncNow,
                    modifier = Modifier.weight(1f),
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text("Sync Now")
                }

                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = !isSyncing
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All")
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Calendar Events?") },
            text = { Text("This will remove all PantryWise events from the calendar. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearEvents()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
