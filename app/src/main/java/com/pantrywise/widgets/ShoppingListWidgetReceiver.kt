package com.pantrywise.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
 * Shopping List Widget for Android home screen
 */
class ShoppingListWidget : GlanceAppWidget() {

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
            dataProvider.getShoppingItems()
        }
        val summary = ShoppingListSummary(
            totalCount = items.size,
            uncheckedCount = items.count { !it.isChecked },
            checkedCount = items.count { it.isChecked }
        )

        provideContent {
            ShoppingListContent(items = items, summary = summary)
        }
    }
}

@Composable
private fun ShoppingListContent(
    items: List<WidgetShoppingItem>,
    summary: ShoppingListSummary
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
                size.width < 150.dp -> SmallShoppingWidget(summary)
                size.width < 250.dp -> MediumShoppingWidget(items, summary)
                else -> LargeShoppingWidget(items, summary)
            }
        }
    }
}

@Composable
private fun SmallShoppingWidget(summary: ShoppingListSummary) {
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
                provider = ImageProvider(android.R.drawable.ic_menu_agenda),
                contentDescription = "Shopping",
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "Shopping",
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
                text = "List empty",
                style = TextStyle(
                    fontSize = 16.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        } else {
            Text(
                text = "${summary.uncheckedCount}",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = ColorProvider(Color(0xFF4CAF50))
                )
            )
            Text(
                text = "items to get",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Progress bar
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(ColorProvider(Color.Gray.copy(alpha = 0.3f)))
                    .cornerRadius(3.dp)
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .background(ColorProvider(Color(0xFF4CAF50)))
                        .cornerRadius(3.dp)
                ) {}
            }
        }
    }
}

@Composable
private fun MediumShoppingWidget(
    items: List<WidgetShoppingItem>,
    summary: ShoppingListSummary
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Left side - count and progress
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.Top
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(android.R.drawable.ic_menu_agenda),
                    contentDescription = "Shopping",
                    modifier = GlanceModifier.size(18.dp)
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "Shopping",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = GlanceTheme.colors.onSurface
                    )
                )
            }

            Text(
                text = "${summary.uncheckedCount}",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = ColorProvider(Color(0xFF4CAF50))
                )
            )

            Text(
                text = "of ${summary.totalCount} left",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Progress bar
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(ColorProvider(Color.Gray.copy(alpha = 0.3f)))
                    .cornerRadius(3.dp)
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .background(ColorProvider(Color(0xFF4CAF50)))
                        .cornerRadius(3.dp)
                ) {}
            }
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Right side - item list
        Column(modifier = GlanceModifier.defaultWeight()) {
            items.filter { !it.isChecked }.take(4).forEach { item ->
                ShoppingItemRow(item, compact = true)
            }
            val remaining = items.count { !it.isChecked } - 4
            if (remaining > 0) {
                Text(
                    text = "+$remaining more",
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
private fun LargeShoppingWidget(
    items: List<WidgetShoppingItem>,
    summary: ShoppingListSummary
) {
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
                provider = ImageProvider(android.R.drawable.ic_menu_agenda),
                contentDescription = "Shopping",
                modifier = GlanceModifier.size(20.dp)
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "Shopping List",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "${summary.checkedCount}/${summary.totalCount}",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Progress bar
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(8.dp)
                .background(ColorProvider(Color.Gray.copy(alpha = 0.3f)))
                .cornerRadius(4.dp)
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .background(ColorProvider(Color(0xFF4CAF50)))
                    .cornerRadius(4.dp)
            ) {}
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        if (items.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Your list is empty",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        } else {
            LazyColumn {
                val uncheckedItems = items.filter { !it.isChecked }.take(6)
                items(uncheckedItems) { item ->
                    ShoppingItemRow(item, compact = false)
                }

                val remaining = items.count { !it.isChecked } - 6
                if (remaining > 0) {
                    item {
                        Text(
                            text = "View all ${items.count { !it.isChecked }} items in app",
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
private fun ShoppingItemRow(item: WidgetShoppingItem, compact: Boolean) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(
                if (item.isChecked) android.R.drawable.checkbox_on_background
                else android.R.drawable.checkbox_off_background
            ),
            contentDescription = if (item.isChecked) "Checked" else "Unchecked",
            modifier = GlanceModifier.size(if (compact) 14.dp else 18.dp)
        )

        Spacer(modifier = GlanceModifier.width(4.dp))

        Text(
            text = item.name,
            style = TextStyle(
                fontSize = if (compact) 11.sp else 13.sp,
                color = if (item.isChecked) GlanceTheme.colors.onSurfaceVariant
                        else GlanceTheme.colors.onSurface
            ),
            maxLines = 1
        )

        Spacer(modifier = GlanceModifier.defaultWeight())

        if (item.quantity > 1) {
            Text(
                text = "x${item.quantity}",
                style = TextStyle(
                    fontSize = if (compact) 10.sp else 11.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Widget receiver for Shopping List
 */
class ShoppingListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShoppingListWidget()
}
