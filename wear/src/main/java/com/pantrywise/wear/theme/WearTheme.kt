package com.pantrywise.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val PantryGreen = Color(0xFF4CAF50)
val PantryOrange = Color(0xFFFF9800)
val PantryRed = Color(0xFFF44336)
val PantryYellow = Color(0xFFFFC107)
val PantryBlue = Color(0xFF2196F3)

internal val wearColorPalette: Colors = Colors(
    primary = PantryGreen,
    primaryVariant = Color(0xFF388E3C),
    secondary = PantryOrange,
    secondaryVariant = Color(0xFFF57C00),
    error = PantryRed,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onError = Color.Black
)

@Composable
fun PantryWiseWearTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = wearColorPalette,
        content = content
    )
}
