package com.pantrywise.wear.presentation.shopping

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Checkbox
import androidx.wear.compose.material.CheckboxDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.pantrywise.wear.data.WearShoppingItem
import com.pantrywise.wear.theme.PantryGreen

@Composable
fun ShoppingListScreen(
    viewModel: ShoppingListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (uiState.items.isEmpty()) {
            EmptyShoppingList()
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
                            text = "Shopping",
                            style = MaterialTheme.typography.title3
                        )
                        Text(
                            text = "${uiState.uncheckedCount} of ${uiState.items.size} left",
                            style = MaterialTheme.typography.caption3,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }
                }

                // Unchecked items first
                val uncheckedItems = uiState.items.filter { !it.isChecked }
                items(uncheckedItems, key = { it.id }) { item ->
                    ShoppingItemChip(
                        item = item,
                        onToggle = { viewModel.toggleItem(item.id, !item.isChecked) }
                    )
                }

                // Checked items
                val checkedItems = uiState.items.filter { it.isChecked }
                if (checkedItems.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Completed (${checkedItems.size})",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant
                        )
                    }

                    items(checkedItems, key = { it.id }) { item ->
                        ShoppingItemChip(
                            item = item,
                            onToggle = { viewModel.toggleItem(item.id, !item.isChecked) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemChip(
    item: WearShoppingItem,
    onToggle: () -> Unit
) {
    ToggleChip(
        modifier = Modifier.fillMaxWidth(0.9f),
        checked = item.isChecked,
        onCheckedChange = { onToggle() },
        label = {
            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                color = if (item.isChecked) {
                    MaterialTheme.colors.onSurfaceVariant
                } else {
                    MaterialTheme.colors.onSurface
                }
            )
        },
        secondaryLabel = {
            Text(
                text = item.displayQuantity,
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        },
        toggleControl = {
            Checkbox(
                checked = item.isChecked,
                colors = CheckboxDefaults.colors(
                    checkedBoxColor = PantryGreen
                )
            )
        },
        colors = ToggleChipDefaults.toggleChipColors(
            checkedStartBackgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f),
            checkedEndBackgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f)
        )
    )
}

@Composable
private fun EmptyShoppingList() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "All done!",
                style = MaterialTheme.typography.title3,
                color = PantryGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your shopping list is empty",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
