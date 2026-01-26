package com.pantrywise.ui.waste

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pantrywise.data.local.entity.WasteReason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WasteLogSheet(
    onDismiss: () -> Unit,
    onLogWaste: (
        productName: String,
        category: String,
        quantity: Double,
        unit: String,
        reason: WasteReason,
        estimatedCost: Double?,
        notes: String?
    ) -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("item") }
    var selectedReason by remember { mutableStateOf<WasteReason?>(null) }
    var estimatedCost by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showUnitDropdown by remember { mutableStateOf(false) }

    val categories = listOf(
        "Produce", "Dairy", "Meat", "Bakery", "Frozen",
        "Pantry", "Beverages", "Deli", "Prepared Foods", "Other"
    )

    val units = listOf(
        "item", "lb", "oz", "kg", "g", "cup", "serving", "package"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Log Wasted Food",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Product name
            OutlinedTextField(
                value = productName,
                onValueChange = { productName = it },
                label = { Text("What did you waste?") },
                placeholder = { Text("e.g., Spinach, Bread, Milk") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category and Quantity row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category
                ExposedDropdownMenuBox(
                    expanded = showCategoryDropdown,
                    onExpandedChange = { showCategoryDropdown = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = category.ifEmpty { "Category" },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    showCategoryDropdown = false
                                }
                            )
                        }
                    }
                }

                // Quantity
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(80.dp)
                )

                // Unit
                ExposedDropdownMenuBox(
                    expanded = showUnitDropdown,
                    onExpandedChange = { showUnitDropdown = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUnitDropdown) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showUnitDropdown,
                        onDismissRequest = { showUnitDropdown = false }
                    ) {
                        units.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(u) },
                                onClick = {
                                    unit = u
                                    showUnitDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reason selection
            Text(
                "Why was it wasted?",
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(WasteReason.entries) { reason ->
                    ReasonChip(
                        reason = reason,
                        isSelected = selectedReason == reason,
                        onClick = { selectedReason = reason }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cost and notes row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = estimatedCost,
                    onValueChange = { estimatedCost = it },
                    label = { Text("Est. Cost ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    singleLine = true,
                    modifier = Modifier.weight(2f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit button
            Button(
                onClick = {
                    if (productName.isNotBlank() && selectedReason != null) {
                        onLogWaste(
                            productName,
                            category.ifEmpty { "Other" },
                            quantity.toDoubleOrNull() ?: 1.0,
                            unit,
                            selectedReason!!,
                            estimatedCost.toDoubleOrNull(),
                            notes.ifBlank { null }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = productName.isNotBlank() && selectedReason != null
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Waste")
            }
        }
    }
}

@Composable
private fun ReasonChip(
    reason: WasteReason,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (icon, color) = getReasonIconAndColor(reason)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) color.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) ButtonDefaults.outlinedButtonBorder
        else null
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                getReasonLabel(reason),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getReasonIconAndColor(reason: WasteReason): Pair<ImageVector, Color> {
    return when (reason) {
        WasteReason.EXPIRED -> Icons.Default.Schedule to Color(0xFFE53935)
        WasteReason.SPOILED -> Icons.Default.Warning to Color(0xFF8E24AA)
        WasteReason.MOLDY -> Icons.Default.Coronavirus to Color(0xFF43A047)
        WasteReason.FREEZER_BURN -> Icons.Default.AcUnit to Color(0xFF1E88E5)
        WasteReason.STALE -> Icons.Default.BakeryDining to Color(0xFFFDD835)
        WasteReason.TASTE_OFF -> Icons.Default.SentimentDissatisfied to Color(0xFFFF8F00)
        WasteReason.FORGOT_ABOUT_IT -> Icons.Default.QuestionMark to Color(0xFF6D4C41)
        WasteReason.TOO_MUCH_COOKED -> Icons.Default.Restaurant to Color(0xFF00ACC1)
        WasteReason.DIDNT_LIKE_IT -> Icons.Default.ThumbDown to Color(0xFFEC407A)
        WasteReason.CHANGED_PLANS -> Icons.Default.EventBusy to Color(0xFF7CB342)
        WasteReason.DAMAGED -> Icons.Default.BrokenImage to Color(0xFF5C6BC0)
        WasteReason.OTHER -> Icons.Default.MoreHoriz to Color(0xFF78909C)
    }
}

private fun getReasonLabel(reason: WasteReason): String {
    return when (reason) {
        WasteReason.EXPIRED -> "Expired"
        WasteReason.SPOILED -> "Spoiled"
        WasteReason.MOLDY -> "Moldy"
        WasteReason.FREEZER_BURN -> "Freezer Burn"
        WasteReason.STALE -> "Stale"
        WasteReason.TASTE_OFF -> "Taste Off"
        WasteReason.FORGOT_ABOUT_IT -> "Forgot"
        WasteReason.TOO_MUCH_COOKED -> "Too Much"
        WasteReason.DIDNT_LIKE_IT -> "Didn't Like"
        WasteReason.CHANGED_PLANS -> "Plans Changed"
        WasteReason.DAMAGED -> "Damaged"
        WasteReason.OTHER -> "Other"
    }
}
