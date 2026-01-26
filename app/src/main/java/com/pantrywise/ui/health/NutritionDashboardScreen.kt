package com.pantrywise.ui.health

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.services.DailyNutritionSummary
import com.pantrywise.services.HealthConnectManager
import com.pantrywise.services.HealthConnectStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFoodLog: () -> Unit,
    onNavigateToGoals: () -> Unit,
    viewModel: NutritionDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.getPermissionContract()
    ) { granted ->
        if (granted.containsAll(HealthConnectManager.REQUIRED_PERMISSIONS)) {
            viewModel.loadData()
        } else {
            Toast.makeText(context, "Health Connect permission required", Toast.LENGTH_LONG).show()
        }
    }

    // Handle events
    LaunchedEffect(uiState.event) {
        when (val event = uiState.event) {
            is NutritionDashboardEvent.Error -> {
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
        if (uiState.event != NutritionDashboardEvent.None) {
            viewModel.clearEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nutrition") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToGoals) {
                        Icon(Icons.Default.Settings, contentDescription = "Goals")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.healthConnectStatus == HealthConnectStatus.Available && uiState.hasPermission) {
                FloatingActionButton(onClick = onNavigateToFoodLog) {
                    Icon(Icons.Default.Add, contentDescription = "Log Food")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Health Connect status
            when (uiState.healthConnectStatus) {
                HealthConnectStatus.NotInstalled -> {
                    item {
                        HealthConnectNotInstalledCard(
                            onInstall = {
                                context.startActivity(viewModel.getInstallIntent())
                            }
                        )
                    }
                }
                HealthConnectStatus.NotSupported -> {
                    item {
                        HealthConnectNotSupportedCard()
                    }
                }
                HealthConnectStatus.Available -> {
                    if (!uiState.hasPermission) {
                        item {
                            PermissionRequestCard(
                                onRequestPermission = {
                                    permissionLauncher.launch(HealthConnectManager.REQUIRED_PERMISSIONS)
                                }
                            )
                        }
                    } else {
                        // Today's summary
                        item {
                            TodaySummaryCard(
                                summary = uiState.todaySummary,
                                goals = uiState.goals,
                                isLoading = uiState.isLoading
                            )
                        }

                        // Macro breakdown
                        item {
                            MacroBreakdownCard(
                                summary = uiState.todaySummary,
                                goals = uiState.goals
                            )
                        }

                        // Weekly overview
                        item {
                            WeeklyOverviewCard(
                                summaries = uiState.weeklySummaries,
                                goals = uiState.goals,
                                selectedDate = uiState.selectedDate,
                                onDateSelected = { viewModel.selectDate(it) }
                            )
                        }

                        // Water intake
                        item {
                            WaterIntakeCard(
                                currentMl = uiState.todaySummary?.totalWater ?: 0.0,
                                goalMl = uiState.goals.waterGoal,
                                onAddWater = { viewModel.logWater(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthConnectNotInstalledCard(onInstall: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.HealthAndSafety,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Health Connect Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Install Health Connect to sync nutrition data across apps",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onInstall) {
                Text("Install Health Connect")
            }
        }
    }
}

@Composable
private fun HealthConnectNotSupportedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Health Connect Not Supported",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your device doesn't support Health Connect. Nutrition tracking will be stored locally only.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PermissionRequestCard(onRequestPermission: () -> Unit) {
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
                Icons.Default.HealthAndSafety,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Health Connect Permission",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Allow PantryWise to read and write nutrition data to sync with other health apps",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(
    summary: DailyNutritionSummary?,
    goals: NutritionGoals,
    isLoading: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Calories circle
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CalorieProgressRing(
                    current = summary?.totalCalories?.toInt() ?: 0,
                    goal = goals.caloriesGoal.toInt(),
                    modifier = Modifier.size(180.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "${summary?.recordCount ?: 0} items logged",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalorieProgressRing(
    current: Int,
    goal: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (goal > 0) (current.toFloat() / goal).coerceIn(0f, 1.5f) else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val overColor = MaterialTheme.colorScheme.error

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val strokeWidth = 24.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)

            // Background ring
            drawCircle(
                color = backgroundColor,
                radius = radius,
                center = center,
                style = Stroke(strokeWidth)
            )

            // Progress arc
            val sweepAngle = animatedProgress * 360f
            val color = if (animatedProgress > 1f) overColor else primaryColor

            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweepAngle.coerceAtMost(360f),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                current.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (progress > 1f) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "of $goal kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MacroBreakdownCard(
    summary: DailyNutritionSummary?,
    goals: NutritionGoals
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Macros",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MacroCircle(
                    label = "Protein",
                    current = summary?.totalProtein ?: 0.0,
                    goal = goals.proteinGoal,
                    color = Color(0xFF4CAF50),
                    unit = "g"
                )
                MacroCircle(
                    label = "Carbs",
                    current = summary?.totalCarbs ?: 0.0,
                    goal = goals.carbsGoal,
                    color = Color(0xFF2196F3),
                    unit = "g"
                )
                MacroCircle(
                    label = "Fat",
                    current = summary?.totalFat ?: 0.0,
                    goal = goals.fatGoal,
                    color = Color(0xFFFF9800),
                    unit = "g"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Additional nutrients
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MiniMacroItem(
                    label = "Fiber",
                    current = summary?.totalFiber ?: 0.0,
                    goal = goals.fiberGoal,
                    unit = "g"
                )
                MiniMacroItem(
                    label = "Sugar",
                    current = summary?.totalSugar ?: 0.0,
                    goal = goals.sugarLimit,
                    unit = "g",
                    isLimit = true
                )
                MiniMacroItem(
                    label = "Sodium",
                    current = summary?.totalSodium ?: 0.0,
                    goal = goals.sodiumLimit,
                    unit = "mg",
                    isLimit = true
                )
            }
        }
    }
}

@Composable
private fun MacroCircle(
    label: String,
    current: Double,
    goal: Double,
    color: Color,
    unit: String
) {
    val progress = if (goal > 0) (current / goal).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "macro")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(70.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 8.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)

                // Background
                drawCircle(
                    color = color.copy(alpha = 0.2f),
                    radius = radius,
                    center = center,
                    style = Stroke(strokeWidth)
                )

                // Progress
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }

            Text(
                "${current.toInt()}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${goal.toInt()}$unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun MiniMacroItem(
    label: String,
    current: Double,
    goal: Double,
    unit: String,
    isLimit: Boolean = false
) {
    val progress = if (goal > 0) (current / goal).toFloat() else 0f
    val isOver = progress > 1f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${current.toInt()}$unit",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isLimit && isOver) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
        LinearProgressIndicator(
            progress = { progress.coerceAtMost(1f) },
            modifier = Modifier
                .width(60.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (isLimit && isOver) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun WeeklyOverviewCard(
    summaries: List<DailyNutritionSummary>,
    goals: NutritionGoals,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "This Week",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(summaries) { summary ->
                    DayColumn(
                        summary = summary,
                        goal = goals.caloriesGoal,
                        isSelected = summary.date == selectedDate,
                        onClick = { onDateSelected(summary.date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    summary: DailyNutritionSummary,
    goal: Double,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val progress = if (goal > 0) (summary.totalCalories / goal).toFloat().coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Day label
        Text(
            summary.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Progress bar (vertical)
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Date
        Text(
            summary.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WaterIntakeCard(
    currentMl: Double,
    goalMl: Double,
    onAddWater: (Double) -> Unit
) {
    val progress = if (goalMl > 0) (currentMl / goalMl).toFloat().coerceIn(0f, 1f) else 0f
    val glasses = (currentMl / 250).toInt()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Water",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "$glasses glasses",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = Color(0xFF2196F3)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "${currentMl.toInt()} / ${goalMl.toInt()} ml",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick add buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(250 to "Glass", 500 to "Bottle", 1000 to "Liter").forEach { (ml, label) ->
                    OutlinedButton(
                        onClick = { onAddWater(ml.toDouble()) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("+$label", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * Nutrition goals data class
 */
data class NutritionGoals(
    val caloriesGoal: Double = 2000.0,
    val proteinGoal: Double = 50.0,
    val carbsGoal: Double = 275.0,
    val fatGoal: Double = 78.0,
    val fiberGoal: Double = 28.0,
    val sugarLimit: Double = 50.0,
    val sodiumLimit: Double = 2300.0,
    val waterGoal: Double = 2000.0
)
