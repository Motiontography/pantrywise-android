package com.pantrywise.ui.price

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pantrywise.data.local.entity.PriceRecordEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceHistoryScreen(
    productId: String,
    productName: String,
    onNavigateBack: () -> Unit,
    viewModel: PriceHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(productId) {
        viewModel.loadPriceHistory(productId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Price History")
                        Text(
                            productName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleAlert(productId) }) {
                        Icon(
                            if (uiState.hasAlert) Icons.Default.NotificationsActive
                            else Icons.Default.NotificationsNone,
                            contentDescription = "Set Price Alert",
                            tint = if (uiState.hasAlert) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
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
        } else if (uiState.priceRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No price history",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Scan this product at stores to track prices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Price summary card
                item {
                    PriceSummaryCard(
                        currentPrice = uiState.currentPrice,
                        lowestPrice = uiState.lowestPrice,
                        highestPrice = uiState.highestPrice,
                        averagePrice = uiState.averagePrice,
                        priceChange = uiState.priceChange
                    )
                }

                // Price trend chart
                item {
                    PriceTrendChart(
                        priceRecords = uiState.priceRecords,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }

                // Store comparison
                if (uiState.storeComparison.isNotEmpty()) {
                    item {
                        Text(
                            "Price by Store",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(uiState.storeComparison) { storePrice ->
                        StoreComparisonCard(
                            storeName = storePrice.storeName,
                            price = storePrice.price,
                            lastUpdated = storePrice.recordedAt,
                            isLowest = storePrice.price == uiState.lowestPrice,
                            onStoreClick = { /* Navigate to store */ }
                        )
                    }
                }

                // Price history list
                item {
                    Text(
                        "Price History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(uiState.priceRecords.take(20)) { record ->
                    PriceRecordRow(record = record)
                }
            }
        }
    }
}

@Composable
private fun PriceSummaryCard(
    currentPrice: Double?,
    lowestPrice: Double?,
    highestPrice: Double?,
    averagePrice: Double?,
    priceChange: Double?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        "Current Price",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        currentPrice?.let { String.format("$%.2f", it) } ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                priceChange?.let { change ->
                    val isIncrease = change > 0
                    Surface(
                        color = if (isIncrease) MaterialTheme.colorScheme.errorContainer
                        else Color(0xFF4CAF50).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isIncrease) Icons.AutoMirrored.Filled.TrendingUp
                                else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isIncrease) MaterialTheme.colorScheme.error
                                else Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${if (isIncrease) "+" else ""}${String.format("%.1f", change)}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isIncrease) MaterialTheme.colorScheme.error
                                else Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PriceStat(
                    label = "Lowest",
                    value = lowestPrice?.let { String.format("$%.2f", it) } ?: "—",
                    color = Color(0xFF4CAF50)
                )
                PriceStat(
                    label = "Average",
                    value = averagePrice?.let { String.format("$%.2f", it) } ?: "—",
                    color = MaterialTheme.colorScheme.primary
                )
                PriceStat(
                    label = "Highest",
                    value = highestPrice?.let { String.format("$%.2f", it) } ?: "—",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PriceStat(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun PriceTrendChart(
    priceRecords: List<PriceRecordEntity>,
    modifier: Modifier = Modifier
) {
    if (priceRecords.size < 2) return

    val sortedRecords = priceRecords.sortedBy { it.recordedAt }
    val prices = sortedRecords.map { it.price }
    val minPrice = prices.minOrNull() ?: 0.0
    val maxPrice = prices.maxOrNull() ?: 1.0
    val priceRange = (maxPrice - minPrice).coerceAtLeast(0.01)

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Price Trend",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val padding = 16.dp.toPx()
                    val chartWidth = width - padding * 2
                    val chartHeight = height - padding * 2

                    val pointSpacing = chartWidth / (sortedRecords.size - 1).coerceAtLeast(1)

                    // Draw grid lines
                    val gridColor = Color.Gray.copy(alpha = 0.2f)
                    for (i in 0..4) {
                        val y = padding + (chartHeight * i / 4)
                        drawLine(
                            color = gridColor,
                            start = Offset(padding, y),
                            end = Offset(width - padding, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Draw line chart
                    val path = Path()
                    sortedRecords.forEachIndexed { index, record ->
                        val x = padding + index * pointSpacing
                        val normalizedPrice = (record.price - minPrice) / priceRange
                        val y = padding + chartHeight * (1 - normalizedPrice.toFloat())

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF2196F3),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw points
                    sortedRecords.forEachIndexed { index, record ->
                        val x = padding + index * pointSpacing
                        val normalizedPrice = (record.price - minPrice) / priceRange
                        val y = padding + chartHeight * (1 - normalizedPrice.toFloat())

                        drawCircle(
                            color = Color(0xFF2196F3),
                            radius = 5.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }

                // Y-axis labels
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        String.format("$%.2f", maxPrice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        String.format("$%.2f", minPrice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreComparisonCard(
    storeName: String,
    price: Double,
    lastUpdated: Long,
    isLowest: Boolean,
    onStoreClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onStoreClick),
        colors = if (isLowest) CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
        ) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Store,
                    contentDescription = null,
                    tint = if (isLowest) Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            storeName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (isLowest) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFF4CAF50),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "BEST",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "Updated ${formatDate(lastUpdated)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                String.format("$%.2f", price),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isLowest) Color(0xFF4CAF50)
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PriceRecordRow(record: PriceRecordEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                formatDate(record.recordedAt),
                style = MaterialTheme.typography.bodyMedium
            )
            if (record.isOnSale) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "On Sale",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Text(
            record.formattedPrice,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}
