package com.pantrywise.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pantrywise.MainActivity
import com.pantrywise.data.local.PantryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class NutritionSummaryWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val nutritionData = fetchNutritionData(context)

        provideContent {
            NutritionWidgetContent(nutritionData)
        }
    }

    private suspend fun fetchNutritionData(context: Context): NutritionWidgetData {
        return withContext(Dispatchers.IO) {
            try {
                val database = PantryDatabase.getInstance(context)
                val nutritionDao = database.nutritionDao()

                val today = LocalDate.now()
                val startOfDay = today.atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val endOfDay = today.plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                // Get today's entries
                val todayEntries = nutritionDao.getNutritionLogEntriesForDay(startOfDay, endOfDay)

                // Get active goals
                val goals = nutritionDao.getActiveNutritionGoals()

                // Calculate totals
                val caloriesConsumed = todayEntries.sumOf { it.calories?.toDouble() ?: 0.0 }
                val proteinConsumed = todayEntries.sumOf { it.protein ?: 0.0 }
                val carbsConsumed = todayEntries.sumOf { it.carbohydrates ?: 0.0 }
                val fatConsumed = todayEntries.sumOf { it.fat ?: 0.0 }

                val calorieGoal = goals?.caloriesGoal ?: 2000.0
                val proteinGoal = goals?.proteinGoal ?: 50.0
                val carbsGoal = goals?.carbsGoal ?: 250.0
                val fatGoal = goals?.fatGoal ?: 65.0

                NutritionWidgetData(
                    caloriesConsumed = caloriesConsumed.toInt(),
                    calorieGoal = calorieGoal.toInt(),
                    proteinConsumed = proteinConsumed,
                    proteinGoal = proteinGoal,
                    carbsConsumed = carbsConsumed,
                    carbsGoal = carbsGoal,
                    fatConsumed = fatConsumed,
                    fatGoal = fatGoal,
                    mealsLogged = todayEntries.size
                )
            } catch (e: Exception) {
                NutritionWidgetData(
                    caloriesConsumed = 0,
                    calorieGoal = 2000,
                    proteinConsumed = 0.0,
                    proteinGoal = 50.0,
                    carbsConsumed = 0.0,
                    carbsGoal = 250.0,
                    fatConsumed = 0.0,
                    fatGoal = 65.0,
                    mealsLogged = 0
                )
            }
        }
    }
}

data class NutritionWidgetData(
    val caloriesConsumed: Int,
    val calorieGoal: Int,
    val proteinConsumed: Double,
    val proteinGoal: Double,
    val carbsConsumed: Double,
    val carbsGoal: Double,
    val fatConsumed: Double,
    val fatGoal: Double,
    val mealsLogged: Int
)

@Composable
private fun NutritionWidgetContent(data: NutritionWidgetData) {
    val calorieProgress = (data.caloriesConsumed.toFloat() / data.calorieGoal).coerceIn(0f, 1f)
    val remaining = data.calorieGoal - data.caloriesConsumed

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Nutrition",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Calories main display
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${data.caloriesConsumed}",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "/ ${data.calorieGoal} cal",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Progress bar
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(ColorProvider(day = Color(0xFFE0E0E0), night = Color(0xFF424242)))
                    .cornerRadius(4.dp)
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .width((calorieProgress * 200).dp.coerceAtMost(200.dp))
                        .background(
                            if (calorieProgress >= 1f) ColorProvider(day = Color(0xFFE53935), night = Color(0xFFEF5350))
                            else GlanceTheme.colors.primary
                        )
                        .cornerRadius(4.dp)
                ) {}
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            Text(
                text = if (remaining > 0) "$remaining cal remaining" else "${-remaining} cal over",
                style = TextStyle(
                    color = if (remaining >= 0) GlanceTheme.colors.onSurfaceVariant
                    else ColorProvider(day = Color(0xFFE53935), night = Color(0xFFEF5350)),
                    fontSize = 11.sp
                )
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Macros row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MacroItem(
                    label = "Protein",
                    value = data.proteinConsumed.toInt(),
                    goal = data.proteinGoal.toInt(),
                    color = Color(0xFF4CAF50),
                    modifier = GlanceModifier.defaultWeight()
                )
                MacroItem(
                    label = "Carbs",
                    value = data.carbsConsumed.toInt(),
                    goal = data.carbsGoal.toInt(),
                    color = Color(0xFF2196F3),
                    modifier = GlanceModifier.defaultWeight()
                )
                MacroItem(
                    label = "Fat",
                    value = data.fatConsumed.toInt(),
                    goal = data.fatGoal.toInt(),
                    color = Color(0xFFFF9800),
                    modifier = GlanceModifier.defaultWeight()
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Action button
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(8.dp)
                    .clickable(actionRunCallback<LogFoodAction>())
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Log Food",
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun MacroItem(
    label: String,
    value: Int,
    goal: Int,
    color: Color,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$value",
            style = TextStyle(
                color = ColorProvider(day = color, night = color),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = "/ ${goal}g",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 10.sp
            )
        )
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 10.sp
            )
        )
    }
}

class LogFoodAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Launch MainActivity with intent to open food logging
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", "food_log")
        }
        context.startActivity(intent)
    }
}

class NutritionSummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NutritionSummaryWidget()
}
