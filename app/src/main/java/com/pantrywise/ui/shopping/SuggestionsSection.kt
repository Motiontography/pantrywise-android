package com.pantrywise.ui.shopping

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pantrywise.services.ml.SmartSuggestion
import com.pantrywise.services.ml.SuggestionPriority
import com.pantrywise.services.ml.SuggestionType

@Composable
fun SuggestionsSection(
    suggestions: List<SmartSuggestion>,
    onSuggestionClick: (SmartSuggestion) -> Unit,
    onDismiss: (SmartSuggestion) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    if (isLoading) {
        SuggestionsSectionSkeleton(modifier)
        return
    }

    if (suggestions.isEmpty()) return

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Smart Suggestions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "${suggestions.size} items",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(suggestions, key = { it.id }) { suggestion ->
                SuggestionCard(
                    suggestion = suggestion,
                    onClick = { onSuggestionClick(suggestion) },
                    onDismiss = { onDismiss(suggestion) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionCard(
    suggestion: SmartSuggestion,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val (icon, iconColor, backgroundColor) = getSuggestionStyle(suggestion.type)

    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Type icon with badge
                Surface(
                    color = backgroundColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }

                // Priority indicator
                if (suggestion.priority == SuggestionPriority.HIGH) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "!",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Dismiss button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                suggestion.productName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                suggestion.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action button
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    suggestion.actionLabel,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // Confidence indicator (subtle)
            if (suggestion.confidenceScore > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { suggestion.confidenceScore },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = iconColor.copy(alpha = 0.5f),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SuggestionsSectionSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .width(150.dp)
                    .height(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            ) {}
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(3) {
                Surface(
                    modifier = Modifier
                        .width(200.dp)
                        .height(140.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {}
            }
        }
    }
}

@Composable
fun SuggestionTypeChip(
    type: SuggestionType,
    modifier: Modifier = Modifier
) {
    val (icon, color, _) = getSuggestionStyle(type)

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                getSuggestionTypeLabel(type),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

private fun getSuggestionStyle(type: SuggestionType): Triple<ImageVector, Color, Color> {
    return when (type) {
        SuggestionType.LOW_STOCK -> Triple(
            Icons.Default.Warning,
            Color(0xFFE53935),
            Color(0xFFE53935)
        )
        SuggestionType.FREQUENTLY_BOUGHT -> Triple(
            Icons.Default.Repeat,
            Color(0xFF1E88E5),
            Color(0xFF1E88E5)
        )
        SuggestionType.EXPIRING_SOON -> Triple(
            Icons.Default.Schedule,
            Color(0xFFFF8F00),
            Color(0xFFFF8F00)
        )
        SuggestionType.MEAL_PLAN_NEEDED -> Triple(
            Icons.Default.Restaurant,
            Color(0xFF43A047),
            Color(0xFF43A047)
        )
        SuggestionType.SEASONAL -> Triple(
            Icons.Default.WbSunny,
            Color(0xFFFDD835),
            Color(0xFFFDD835)
        )
        SuggestionType.BUDGET_FRIENDLY -> Triple(
            Icons.Default.Savings,
            Color(0xFF00ACC1),
            Color(0xFF00ACC1)
        )
        SuggestionType.COMPANION_ITEM -> Triple(
            Icons.Default.Link,
            Color(0xFF8E24AA),
            Color(0xFF8E24AA)
        )
        SuggestionType.RESTOCK_PATTERN -> Triple(
            Icons.Default.TrendingUp,
            Color(0xFF5C6BC0),
            Color(0xFF5C6BC0)
        )
        SuggestionType.PRICE_DROP -> Triple(
            Icons.Default.LocalOffer,
            Color(0xFFEC407A),
            Color(0xFFEC407A)
        )
    }
}

private fun getSuggestionTypeLabel(type: SuggestionType): String {
    return when (type) {
        SuggestionType.LOW_STOCK -> "Low Stock"
        SuggestionType.FREQUENTLY_BOUGHT -> "Frequent"
        SuggestionType.EXPIRING_SOON -> "Expiring"
        SuggestionType.MEAL_PLAN_NEEDED -> "Meal Plan"
        SuggestionType.SEASONAL -> "Seasonal"
        SuggestionType.BUDGET_FRIENDLY -> "Budget"
        SuggestionType.COMPANION_ITEM -> "Goes With"
        SuggestionType.RESTOCK_PATTERN -> "Restock"
        SuggestionType.PRICE_DROP -> "On Sale"
    }
}
