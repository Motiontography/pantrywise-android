package com.pantrywise.ui.waste

import android.widget.Toast
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.data.local.dao.MonthlyWasteTrend
import com.pantrywise.data.local.dao.TopWastedProduct
import com.pantrywise.data.local.entity.WasteByCategory
import com.pantrywise.data.local.entity.WasteByReason
import com.pantrywise.data.local.entity.WasteReason
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteDashboardScreen(
    onNavigateBack: () -> Unit,
    onLogWaste: () -> Unit,
    viewModel: WasteDashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showLogSheet by remember { mutableStateOf(false) }

    // Handle events
    LaunchedEffect(uiState.event) {
        when (val event = uiState.event) {
            is WasteDashboardEvent.WasteLogged -> {
                Toast.makeText(context, "Waste logged", Toast.LENGTH_SHORT).show()
            }
            is WasteDashboardEvent.Error -> {
                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
        if (uiState.event != WasteDashboardEvent.None) {
            viewModel.clearEvent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Waste Tracker") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showLogSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Log Waste")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Period selector
                item {
                    PeriodSelector(
                        selectedPeriod = uiState.selectedPeriod,
                        onPeriodSelected = { viewModel.selectPeriod(it) }
                    )
                }

                // Summary card
                item {
                    WasteSummaryCard(
                        totalItems = uiState.totalItems,
                        totalCost = uiState.totalCost,
                        periodLabel = uiState.selectedPeriod.label
                    )
                }

                // Pie chart by reason
                if (uiState.wasteByReason.isNotEmpty()) {
                    item {
                        WasteByReasonCard(
                            wasteByReason = uiState.wasteByReason,
                            totalItems = uiState.totalItems
                        )
                    }
                }

                // Monthly trend chart
                if (uiState.monthlyTrend.isNotEmpty()) {
                    item {
                        MonthlyTrendCard(monthlyTrend = uiState.monthlyTrend)
                    }
                }

                // Top wasted products
                if (uiState.topWastedProducts.isNotEmpty()) {
                    item {
                        TopWastedProductsCard(products = uiState.topWastedProducts)
                    }
                }

                // By category breakdown
                if (uiState.wasteByCategory.isNotEmpty()) {
                    item {
                        WasteByCategoryCard(wasteByCategory = uiState.wasteByCategory)
                    }
                }

                // Tips card
                item {
                    WasteReductionTipsCard(
                        topReason = uiState.wasteByReason.firstOrNull()?.reason
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Log waste sheet
    if (showLogSheet) {
        WasteLogSheet(
            onDismiss = { showLogSheet = false },
            onLogWaste = { productName, category, quantity, unit, reason, cost, notes ->
                viewModel.logWaste(productName, category, quantity, unit, reason, cost, notes)
                showLogSheet = false
            }
        )
    }
}

enum class WastePeriod(val label: String, val months: Int) {
    WEEK("This Week", 0),
    MONTH("This Month", 1),
    THREE_MONTHS("3 Months", 3),
    YEAR("This Year", 12)
}

@Composable
private fun PeriodSelector(
    selectedPeriod: WastePeriod,
    onPeriodSelected: (WastePeriod) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(WastePeriod.entries) { period ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.label) }
            )
        }
    }
}

@Composable
private fun WasteSummaryCard(
    totalItems: Int,
    totalCost: Double,
    periodLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    totalItems.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    "Items Wasted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            VerticalDivider(modifier = Modifier.height(80.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.AttachMoney,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    String.format("$%.2f", totalCost),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    "Lost Value",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WasteByReasonCard(
    wasteByReason: List<WasteByReason>,
    totalItems: Int
) {
    val reasonColors = mapOf(
        WasteReason.EXPIRED to Color(0xFFE53935),
        WasteReason.SPOILED to Color(0xFF8E24AA),
        WasteReason.MOLDY to Color(0xFF43A047),
        WasteReason.FREEZER_BURN to Color(0xFF1E88E5),
        WasteReason.STALE to Color(0xFFFDD835),
        WasteReason.TASTE_OFF to Color(0xFFFF8F00),
        WasteReason.FORGOT_ABOUT_IT to Color(0xFF6D4C41),
        WasteReason.TOO_MUCH_COOKED to Color(0xFF00ACC1),
        WasteReason.DIDNT_LIKE_IT to Color(0xFFEC407A),
        WasteReason.CHANGED_PLANS to Color(0xFF7CB342),
        WasteReason.DAMAGED to Color(0xFF5C6BC0),
        WasteReason.OTHER to Color(0xFF78909C)
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Waste by Reason",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Pie chart
                Box(
                    modifier = Modifier.size(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PieChart(
                        data = wasteByReason.map { it.count.toFloat() },
                        colors = wasteByReason.map { reasonColors[it.reason] ?: Color.Gray },
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        "$totalItems\nitems",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center
                    )
                }

                // Legend
                Column(
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    wasteByReason.take(5).forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(reasonColors[item.reason] ?: Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                item.reason.name.lowercase().replace('_', ' ')
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${item.count}",
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

@Composable
private fun PieChart(
    data: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val total = data.sum()
    val angles = data.map { (it / total) * 360f }

    Canvas(modifier = modifier.padding(8.dp)) {
        var startAngle = -90f
        val strokeWidth = 30.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)

        angles.forEachIndexed { index, sweepAngle ->
            drawArc(
                color = colors.getOrElse(index) { Color.Gray },
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun MonthlyTrendCard(monthlyTrend: List<MonthlyWasteTrend>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Monthly Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Line chart
            val maxCount = monthlyTrend.maxOfOrNull { it.count } ?: 1

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val pointSpacing = width / (monthlyTrend.size - 1).coerceAtLeast(1)

                    // Draw line
                    val path = Path()
                    monthlyTrend.forEachIndexed { index, trend ->
                        val x = index * pointSpacing
                        val y = height - (trend.count.toFloat() / maxCount * height * 0.8f) - height * 0.1f

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFFE53935),
                        style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw points
                    monthlyTrend.forEachIndexed { index, trend ->
                        val x = index * pointSpacing
                        val y = height - (trend.count.toFloat() / maxCount * height * 0.8f) - height * 0.1f

                        drawCircle(
                            color = Color(0xFFE53935),
                            radius = 6.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }

            // Month labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                monthlyTrend.takeLast(6).forEach { trend ->
                    Text(
                        trend.month.takeLast(2), // Just month number
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TopWastedProductsCard(products: List<TopWastedProduct>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Most Wasted Items",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            products.take(5).forEachIndexed { index, product ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            product.productName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "${product.wasteCount}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WasteByCategoryCard(wasteByCategory: List<WasteByCategory>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Waste by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            val maxCount = wasteByCategory.maxOfOrNull { it.count } ?: 1

            wasteByCategory.take(5).forEach { item ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            item.category,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${item.count} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { item.count.toFloat() / maxCount },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun WasteReductionTipsCard(topReason: WasteReason?) {
    val tips = when (topReason) {
        WasteReason.EXPIRED -> listOf(
            "Check expiration dates when shopping",
            "Practice FIFO (First In, First Out)",
            "Plan meals around items close to expiring"
        )
        WasteReason.FORGOT_ABOUT_IT -> listOf(
            "Organize your fridge with clear containers",
            "Use the PantryWise expiration alerts",
            "Do a weekly fridge check"
        )
        WasteReason.TOO_MUCH_COOKED -> listOf(
            "Plan portions before cooking",
            "Freeze leftovers immediately",
            "Repurpose leftovers creatively"
        )
        WasteReason.SPOILED -> listOf(
            "Store items at proper temperatures",
            "Check produce before buying",
            "Use produce bags to extend freshness"
        )
        else -> listOf(
            "Plan your meals for the week",
            "Make a shopping list and stick to it",
            "Store food properly to maximize freshness"
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Tips to Reduce Waste",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            tips.forEach { tip ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        tip,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
