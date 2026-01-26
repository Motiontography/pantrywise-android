package com.pantrywise.ui.health

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyGoalsSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DailyGoalsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Handle events
    LaunchedEffect(uiState.event) {
        when (val event = uiState.event) {
            is DailyGoalsEvent.Saved -> {
                Toast.makeText(context, "Goals saved", Toast.LENGTH_SHORT).show()
            }
            is DailyGoalsEvent.Error -> {
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
        if (uiState.event != DailyGoalsEvent.None) {
            viewModel.clearEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Goals") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveGoals() },
                        enabled = !uiState.isSaving
                    ) {
                        Text("Save")
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
            // Calorie goal
            item {
                GoalCard(
                    title = "Calories",
                    subtitle = "Daily calorie target",
                    icon = Icons.Default.LocalFireDepartment,
                    iconColor = Color(0xFFFF5722),
                    value = uiState.caloriesGoal,
                    onValueChange = { viewModel.updateCaloriesGoal(it) },
                    unit = "kcal",
                    step = 100.0,
                    min = 1000.0,
                    max = 5000.0
                )
            }

            // Macronutrients section
            item {
                Text(
                    "Macronutrients",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                GoalCard(
                    title = "Protein",
                    subtitle = "Builds and repairs muscle",
                    icon = Icons.Default.FitnessCenter,
                    iconColor = Color(0xFF4CAF50),
                    value = uiState.proteinGoal,
                    onValueChange = { viewModel.updateProteinGoal(it) },
                    unit = "g",
                    step = 5.0,
                    min = 20.0,
                    max = 300.0
                )
            }

            item {
                GoalCard(
                    title = "Carbohydrates",
                    subtitle = "Primary energy source",
                    icon = Icons.Default.Grain,
                    iconColor = Color(0xFF2196F3),
                    value = uiState.carbsGoal,
                    onValueChange = { viewModel.updateCarbsGoal(it) },
                    unit = "g",
                    step = 10.0,
                    min = 50.0,
                    max = 500.0
                )
            }

            item {
                GoalCard(
                    title = "Fat",
                    subtitle = "Essential fatty acids",
                    icon = Icons.Default.Opacity,
                    iconColor = Color(0xFFFF9800),
                    value = uiState.fatGoal,
                    onValueChange = { viewModel.updateFatGoal(it) },
                    unit = "g",
                    step = 5.0,
                    min = 20.0,
                    max = 200.0
                )
            }

            // Other nutrients section
            item {
                Text(
                    "Other Nutrients",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                GoalCard(
                    title = "Fiber",
                    subtitle = "Digestive health",
                    icon = Icons.Default.Grass,
                    iconColor = Color(0xFF8BC34A),
                    value = uiState.fiberGoal,
                    onValueChange = { viewModel.updateFiberGoal(it) },
                    unit = "g",
                    step = 1.0,
                    min = 10.0,
                    max = 50.0
                )
            }

            // Limits section
            item {
                Text(
                    "Daily Limits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                GoalCard(
                    title = "Sugar",
                    subtitle = "Added sugars limit",
                    icon = Icons.Default.Cake,
                    iconColor = Color(0xFFE91E63),
                    value = uiState.sugarLimit,
                    onValueChange = { viewModel.updateSugarLimit(it) },
                    unit = "g",
                    step = 5.0,
                    min = 20.0,
                    max = 100.0,
                    isLimit = true
                )
            }

            item {
                GoalCard(
                    title = "Sodium",
                    subtitle = "Daily sodium limit",
                    icon = Icons.Default.WaterDrop,
                    iconColor = Color(0xFF9C27B0),
                    value = uiState.sodiumLimit,
                    onValueChange = { viewModel.updateSodiumLimit(it) },
                    unit = "mg",
                    step = 100.0,
                    min = 1000.0,
                    max = 5000.0,
                    isLimit = true
                )
            }

            // Water section
            item {
                Text(
                    "Hydration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                GoalCard(
                    title = "Water",
                    subtitle = "Daily water intake",
                    icon = Icons.Default.WaterDrop,
                    iconColor = Color(0xFF03A9F4),
                    value = uiState.waterGoal,
                    onValueChange = { viewModel.updateWaterGoal(it) },
                    unit = "ml",
                    step = 250.0,
                    min = 1000.0,
                    max = 5000.0
                )
            }

            // Preset buttons
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Presets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetButton(
                        label = "Weight Loss",
                        onClick = { viewModel.applyPreset(NutritionPreset.WEIGHT_LOSS) },
                        modifier = Modifier.weight(1f)
                    )
                    PresetButton(
                        label = "Maintenance",
                        onClick = { viewModel.applyPreset(NutritionPreset.MAINTENANCE) },
                        modifier = Modifier.weight(1f)
                    )
                    PresetButton(
                        label = "Muscle Gain",
                        onClick = { viewModel.applyPreset(NutritionPreset.MUSCLE_GAIN) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun GoalCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    value: Double,
    onValueChange: (Double) -> Unit,
    unit: String,
    step: Double,
    min: Double,
    max: Double,
    isLimit: Boolean = false
) {
    var textValue by remember(value) { mutableStateOf(value.toInt().toString()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isLimit) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "LIMIT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val newValue = (value - step).coerceAtLeast(min)
                        onValueChange(newValue)
                        textValue = newValue.toInt().toString()
                    }
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }

                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        it.toDoubleOrNull()?.let { newValue ->
                            onValueChange(newValue.coerceIn(min, max))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(120.dp),
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    suffix = { Text(unit) }
                )

                IconButton(
                    onClick = {
                        val newValue = (value + step).coerceAtMost(max)
                        onValueChange(newValue)
                        textValue = newValue.toInt().toString()
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }

            // Slider
            Slider(
                value = value.toFloat(),
                onValueChange = {
                    onValueChange(it.toDouble())
                    textValue = it.toInt().toString()
                },
                valueRange = min.toFloat()..max.toFloat(),
                steps = ((max - min) / step).toInt() - 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PresetButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

enum class NutritionPreset {
    WEIGHT_LOSS,
    MAINTENANCE,
    MUSCLE_GAIN
}
