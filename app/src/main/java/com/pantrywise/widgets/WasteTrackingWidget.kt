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

class WasteTrackingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val wasteData = fetchWasteData(context)

        provideContent {
            WasteWidgetContent(wasteData)
        }
    }

    private suspend fun fetchWasteData(context: Context): WasteWidgetData {
        return withContext(Dispatchers.IO) {
            try {
                val database = PantryDatabase.getInstance(context)
                val wasteDao = database.wasteDao()

                val now = LocalDate.now()
                val startOfMonth = now.withDayOfMonth(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val endOfMonth = now.plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val itemsThisMonth = wasteDao.getWasteCountInRange(startOfMonth, endOfMonth)
                val costThisMonth = wasteDao.getTotalWasteCostInRange(startOfMonth, endOfMonth) ?: 0.0

                // Compare to last month
                val startOfLastMonth = now.minusMonths(1).withDayOfMonth(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val endOfLastMonth = startOfMonth

                val itemsLastMonth = wasteDao.getWasteCountInRange(startOfLastMonth, endOfLastMonth)

                val changePercentage = if (itemsLastMonth > 0) {
                    ((itemsThisMonth - itemsLastMonth).toFloat() / itemsLastMonth) * 100
                } else null

                WasteWidgetData(
                    itemsThisMonth = itemsThisMonth,
                    costThisMonth = costThisMonth,
                    changePercentage = changePercentage
                )
            } catch (e: Exception) {
                WasteWidgetData(0, 0.0, null)
            }
        }
    }
}

data class WasteWidgetData(
    val itemsThisMonth: Int,
    val costThisMonth: Double,
    val changePercentage: Float?
)

@Composable
private fun WasteWidgetContent(data: WasteWidgetData) {
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
                    text = "Food Waste",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Main stat
            Text(
                text = "${data.itemsThisMonth}",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Text(
                text = "items this month",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Cost
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("$%.2f", data.costThisMonth),
                    style = TextStyle(
                        color = ColorProvider(day = Color(0xFFE53935), night = Color(0xFFEF5350)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = GlanceModifier.width(8.dp))

                data.changePercentage?.let { change ->
                    val isDown = change < 0
                    val changeColor = if (isDown) Color(0xFF4CAF50) else Color(0xFFE53935)
                    val arrow = if (isDown) "\u2193" else "\u2191"
                    Text(
                        text = "$arrow${String.format("%.0f", kotlin.math.abs(change))}%",
                        style = TextStyle(
                            color = ColorProvider(day = changeColor, night = changeColor),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Action button
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(8.dp)
                    .clickable(actionRunCallback<LogWasteAction>())
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Log Waste",
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

class LogWasteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Launch MainActivity with intent to open waste logging
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("destination", "waste_dashboard")
        }
        context.startActivity(intent)
    }
}

class WasteTrackingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WasteTrackingWidget()
}
