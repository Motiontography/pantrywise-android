package com.pantrywise.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pantrywise.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Expiring Items Widget for Android home screen
 */
class ExpiringItemsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(100.dp, 100.dp),  // Small
            DpSize(200.dp, 100.dp),  // Medium
            DpSize(300.dp, 200.dp)   // Large
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dataProvider = WidgetDataProvider.getInstance(context)
        val items = withContext(Dispatchers.IO) {
            dataProvider.getExpiringItems()
        }
        val summary = ExpiringSummary(
            totalCount = items.count { it.daysUntilExpiration in 0..7 },
            urgentCount = items.count { it.daysUntilExpiration <= 2 }
        )

        provideContent {
            ExpiringItemsContent(items = items, summary = summary)
        }
    }
}

@Composable
private fun ExpiringItemsContent(
    items: List<WidgetExpiringItem>,
    summary: ExpiringSummary
) {
    val size = LocalSize.current

    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .appWidgetBackground()
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            when {
                size.width < 150.dp -> SmallExpiringWidget(summary)
                size.width < 250.dp -> MediumExpiringWidget(items, summary)
                else -> LargeExpiringWidget(items, summary)
            }
        }
    }
}

@Composable
private fun SmallExpiringWidget(summary: ExpiringSummary) {
    val alertColor = if (summary.urgentCount > 0) Color(0xFFF44336) else Color(0xFFFF9800)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start
        ) {
            Image(
                provider = ImageProvider(android.R.drawable.ic_dialog_alert),
                contentDescription = "Expiring",
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "Expiring",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (summary.totalCount == 0) {
            Text(
                text = "All clear!",
                style = TextStyle(
                    fontSize = 16.sp,
                    color = ColorProvider(Color(0xFF4CAF50))
                )
            )
        } else {
            Text(
                text = "${summary.totalCount}",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = ColorProvider(alertColor)
                )
            )
            Text(
                text = "items this week",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun MediumExpiringWidget(
    items: List<WidgetExpiringItem>,
    summary: ExpiringSummary
) {
    val alertColor = if (summary.urgentCount > 0) Color(0xFFF44336) else Color(0xFFFF9800)

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Left side - count
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.Top
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(android.R.drawable.ic_dialog_alert),
                    contentDescription = "Expiring",
                    modifier = GlanceModifier.size(18.dp)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "Expiring Soon",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onSurface
                    )
                )
            }

            Text(
                text = "${summary.totalCount}",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = ColorProvider(alertColor)
                )
            )

            Text(
                text = "items this week",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Right side - item list
        Column(modifier = GlanceModifier.defaultWeight()) {
            items.take(3).forEach { item ->
                ExpiringItemRow(item, compact = true)
            }
            if (items.size > 3) {
                Text(
                    text = "+${items.size - 3} more",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun LargeExpiringWidget(
    items: List<WidgetExpiringItem>,
    summary: ExpiringSummary
) {
    val alertColor = if (summary.urgentCount > 0) Color(0xFFF44336) else Color(0xFFFF9800)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(android.R.drawable.ic_dialog_alert),
                contentDescription = "Expiring",
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "Expiring Soon",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "${summary.totalCount} items",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (items.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        provider = ImageProvider(android.R.drawable.ic_menu_agenda),
                        contentDescription = "All clear",
                        modifier = GlanceModifier.size(32.dp)
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "Nothing expiring soon!",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = ColorProvider(Color(0xFF4CAF50))
                        )
                    )
                }
            }
        } else {
            LazyColumn {
                items(items.take(6)) { item ->
                    ExpiringItemRow(item, compact = false)
                }

                if (items.size > 6) {
                    item {
                        Text(
                            text = "View all ${items.size} items in app",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = GlanceTheme.colors.onSurfaceVariant
                            ),
                            modifier = GlanceModifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiringItemRow(item: WidgetExpiringItem, compact: Boolean) {
    val dotColor = colorForDays(item.daysUntilExpiration)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = GlanceModifier
                .size(if (compact) 8.dp else 10.dp)
                .background(ColorProvider(dotColor))
                .cornerRadius(if (compact) 4.dp else 5.dp)
        ) {}

        Spacer(modifier = GlanceModifier.width(6.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.name,
                style = TextStyle(
                    fontSize = if (compact) 11.sp else 13.sp,
                    fontWeight = if (compact) FontWeight.Normal else FontWeight.Medium,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )

            if (!compact) {
                Row {
                    Text(
                        text = item.location,
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "Qty: ${item.quantity}",
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            }
        }

        Text(
            text = daysText(item.daysUntilExpiration),
            style = TextStyle(
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Medium,
                color = ColorProvider(dotColor)
            )
        )
    }
}

private fun colorForDays(days: Int): Color {
    return when {
        days < 0 -> Color(0xFFF44336)  // Red - expired
        days <= 2 -> Color(0xFFF44336) // Red - urgent
        days <= 5 -> Color(0xFFFF9800) // Orange - soon
        else -> Color(0xFFFFC107)      // Yellow - this week
    }
}

private fun daysText(days: Int): String {
    return when {
        days < 0 -> "Expired"
        days == 0 -> "Today"
        days == 1 -> "Tomorrow"
        else -> "$days days"
    }
}

/**
 * Widget receiver for Expiring Items
 */
class ExpiringItemsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpiringItemsWidget()
}
