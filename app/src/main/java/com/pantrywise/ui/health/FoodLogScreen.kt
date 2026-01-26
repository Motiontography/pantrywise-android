package com.pantrywise.ui.health

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.data.local.entity.NutritionLogEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLogScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProductSearch: () -> Unit,
    viewModel: FoodLogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    // Handle events
    LaunchedEffect(uiState.event) {
        when (val event = uiState.event) {
            is FoodLogEvent.EntryAdded -> {
                Toast.makeText(context, "Food logged successfully", Toast.LENGTH_SHORT).show()
            }
            is FoodLogEvent.EntryDeleted -> {
                Toast.makeText(context, "Entry deleted", Toast.LENGTH_SHORT).show()
            }
            is FoodLogEvent.Error -> {
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
        if (uiState.event != FoodLogEvent.None) {
            viewModel.clearEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food Log") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Log Food")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Date selector
            DateSelector(
                selectedDate = uiState.selectedDate,
                onDateChange = { viewModel.selectDate(it) }
            )

            // Daily summary
            DailySummaryRow(
                entries = uiState.entries,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider()

            // Entries by meal
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No food logged today",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { showAddDialog = true }) {
                            Text("Log Food")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Group by meal type
                    val grouped = uiState.entries.groupBy { it.mealType ?: "Other" }
                    val mealOrder = listOf("breakfast", "lunch", "dinner", "snack", "Other")

                    mealOrder.forEach { mealType ->
                        val mealEntries = grouped[mealType] ?: emptyList()
                        if (mealEntries.isNotEmpty()) {
                            item {
                                MealHeader(mealType = mealType)
                            }
                            items(mealEntries) { entry ->
                                FoodEntryCard(
                                    entry = entry,
                                    onDelete = { viewModel.deleteEntry(entry.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add food dialog
    if (showAddDialog) {
        AddFoodDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, calories, protein, carbs, fat, servings, mealType ->
                viewModel.addEntry(name, calories, protein, carbs, fat, servings, mealType)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun DateSelector(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onDateChange(selectedDate.minusDays(1)) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day")
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (selectedDate == LocalDate.now()) "Today"
                else if (selectedDate == LocalDate.now().minusDays(1)) "Yesterday"
                else selectedDate.format(formatter),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (selectedDate != LocalDate.now()) {
                Text(
                    selectedDate.format(formatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = { onDateChange(selectedDate.plusDays(1)) },
            enabled = selectedDate < LocalDate.now()
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next day")
        }
    }
}

@Composable
private fun DailySummaryRow(
    entries: List<NutritionLogEntry>,
    modifier: Modifier = Modifier
) {
    val totalCalories = entries.sumOf { (it.calories ?: 0.0) * it.servings }
    val totalProtein = entries.sumOf { (it.protein ?: 0.0) * it.servings }
    val totalCarbs = entries.sumOf { (it.carbohydrates ?: 0.0) * it.servings }
    val totalFat = entries.sumOf { (it.fat ?: 0.0) * it.servings }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SummaryItem(label = "Calories", value = "${totalCalories.toInt()}")
        SummaryItem(label = "Protein", value = "${totalProtein.toInt()}g")
        SummaryItem(label = "Carbs", value = "${totalCarbs.toInt()}g")
        SummaryItem(label = "Fat", value = "${totalFat.toInt()}g")
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MealHeader(mealType: String) {
    val icon = when (mealType.lowercase()) {
        "breakfast" -> Icons.Default.WbSunny
        "lunch" -> Icons.Default.LunchDining
        "dinner" -> Icons.Default.DinnerDining
        "snack" -> Icons.Default.Cookie
        else -> Icons.Default.Restaurant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            mealType.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FoodEntryCard(
    entry: NutritionLogEntry,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Navigate to detail */ }
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
                    entry.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    buildString {
                        append("${entry.servings} serving")
                        if (entry.servings != 1.0) append("s")
                        entry.calories?.let { append(" • ${(it * entry.servings).toInt()} cal") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Entry?") },
            text = { Text("Remove ${entry.productName} from your food log?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFoodDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, calories: Double?, protein: Double?, carbs: Double?, fat: Double?, servings: Double, mealType: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("1") }
    var selectedMealType by remember { mutableStateOf("breakfast") }
    var expanded by remember { mutableStateOf(false) }

    val mealTypes = listOf("breakfast", "lunch", "dinner", "snack")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Food") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Food Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Meal type dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedMealType.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        label = { Text("Meal") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        mealTypes.forEach { meal ->
                            DropdownMenuItem(
                                text = { Text(meal.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    selectedMealType = meal
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = calories,
                        onValueChange = { calories = it },
                        label = { Text("Calories") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = servings,
                        onValueChange = { servings = it },
                        label = { Text("Servings") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("Carbs (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
                        label = { Text("Fat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(
                            name,
                            calories.toDoubleOrNull(),
                            protein.toDoubleOrNull(),
                            carbs.toDoubleOrNull(),
                            fat.toDoubleOrNull(),
                            servings.toDoubleOrNull() ?: 1.0,
                            selectedMealType
                        )
                    }
                },
                enabled = name.isNotBlank()
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
