package com.pantrywise.wear.presentation.expiring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.pantrywise.wear.data.UrgencyLevel
import com.pantrywise.wear.data.WearExpiringItem
import com.pantrywise.wear.theme.PantryGreen
import com.pantrywise.wear.theme.PantryOrange
import com.pantrywise.wear.theme.PantryRed
import com.pantrywise.wear.theme.PantryYellow

@Composable
fun ExpiringItemsScreen(
    viewModel: ExpiringItemsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (uiState.items.isEmpty()) {
            EmptyExpiringList()
        } else {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Header
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Expiring Soon",
                            style = MaterialTheme.typography.title3
                        )
                        Text(
                            text = "${uiState.items.size} items this week",
                            style = MaterialTheme.typography.caption3,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                }

                items(uiState.items, key = { it.id }) { item ->
                    ExpiringItemChip(item = item)
                }
            }
        }
    }
}

@Composable
private fun ExpiringItemChip(item: WearExpiringItem) {
    val urgencyColor = when (item.urgencyLevel) {
        UrgencyLevel.EXPIRED -> PantryRed
        UrgencyLevel.URGENT -> PantryOrange
        UrgencyLevel.WARNING -> PantryYellow
        UrgencyLevel.NORMAL -> PantryGreen
    }

    Chip(
        modifier = Modifier.fillMaxWidth(0.9f),
        onClick = { /* Could navigate to item detail */ },
        colors = ChipDefaults.chipColors(
            backgroundColor = MaterialTheme.colors.surface
        ),
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Urgency indicator dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(urgencyColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = item.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.location,
                            style = MaterialTheme.typography.caption3,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = item.shortCountdown,
                    style = MaterialTheme.typography.caption2,
                    color = urgencyColor
                )
            }
        }
    )
}

@Composable
private fun EmptyExpiringList() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "All Clear!",
                style = MaterialTheme.typography.title3,
                color = PantryGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Nothing expiring soon",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
