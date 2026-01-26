package com.pantrywise.wear.presentation.home

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.pantrywise.wear.theme.PantryGreen
import com.pantrywise.wear.theme.PantryOrange
import com.pantrywise.wear.theme.PantryBlue

@Composable
fun HomeScreen(
    onNavigateToShopping: () -> Unit,
    onNavigateToExpiring: () -> Unit,
    onNavigateToQuickAdd: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()

    LaunchedEffect(Unit) {
        viewModel.requestSync()
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Title
            item {
                Text(
                    text = "My Pantry Buddy",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Shopping List Chip
            item {
                MenuChip(
                    label = "Shopping",
                    secondaryLabel = if (uiState.uncheckedCount > 0) {
                        "${uiState.uncheckedCount} items"
                    } else {
                        "All done!"
                    },
                    badgeCount = uiState.uncheckedCount,
                    badgeColor = PantryGreen,
                    onClick = onNavigateToShopping
                )
            }

            // Expiring Items Chip
            item {
                MenuChip(
                    label = "Expiring",
                    secondaryLabel = if (uiState.expiringCount > 0) {
                        "${uiState.expiringCount} items"
                    } else {
                        "Nothing expiring"
                    },
                    badgeCount = uiState.expiringCount,
                    badgeColor = PantryOrange,
                    onClick = onNavigateToExpiring
                )
            }

            // Quick Add Chip
            item {
                MenuChip(
                    label = "Quick Add",
                    secondaryLabel = "Voice or presets",
                    badgeCount = 0,
                    badgeColor = PantryBlue,
                    onClick = onNavigateToQuickAdd
                )
            }

            // Sync status
            item {
                Spacer(modifier = Modifier.height(8.dp))
                if (uiState.isSyncing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Syncing...",
                            style = MaterialTheme.typography.caption3,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = uiState.lastSyncText,
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuChip(
    label: String,
    secondaryLabel: String,
    badgeCount: Int,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Chip(
        modifier = Modifier.fillMaxWidth(0.9f),
        onClick = onClick,
        colors = ChipDefaults.chipColors(
            backgroundColor = MaterialTheme.colors.surface
        ),
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.button
                    )
                    Text(
                        text = secondaryLabel,
                        style = MaterialTheme.typography.caption3,
                        color = if (badgeCount == 0 && label == "Shopping") {
                            PantryGreen
                        } else {
                            MaterialTheme.colors.onSurfaceVariant
                        }
                    )
                }

                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(badgeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            style = MaterialTheme.typography.caption2,
                            color = Color.White
                        )
                    }
                }
            }
        }
    )
}
