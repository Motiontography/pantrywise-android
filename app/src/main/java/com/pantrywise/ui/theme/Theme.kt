package com.pantrywise.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// PantryWise Brand Colors
val PantryGreen = Color(0xFF4CAF50)
val PantryGreenDark = Color(0xFF388E3C)
val PantryGreenLight = Color(0xFF81C784)

val PantryOrange = Color(0xFFFF9800)
val PantryRed = Color(0xFFF44336)
val PantryYellow = Color(0xFFFFC107)
val PantryBlue = Color(0xFF2196F3)

// Status Colors
val InStockColor = Color(0xFF4CAF50)
val LowStockColor = Color(0xFFFFC107)
val OutOfStockColor = Color(0xFF9E9E9E)
val ExpiredColor = Color(0xFFF44336)
val ExpiringSoonColor = Color(0xFFFF9800)

// Cart Match Type Colors
val PlannedColor = Color(0xFF4CAF50)
val ExtraColor = Color(0xFF2196F3)
val AlreadyStockedColor = Color(0xFFFF9800)

private val LightColorScheme = lightColorScheme(
    primary = PantryGreen,
    onPrimary = Color.White,
    primaryContainer = PantryGreenLight,
    onPrimaryContainer = Color(0xFF002106),
    secondary = PantryOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF2E1500),
    tertiary = PantryBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB3E5FC),
    onTertiaryContainer = Color(0xFF001F29),
    error = PantryRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFDF7),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFBFDF7),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDEE5D9),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F)
)

private val DarkColorScheme = darkColorScheme(
    primary = PantryGreenLight,
    onPrimary = Color(0xFF003910),
    primaryContainer = PantryGreenDark,
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFFFFCC80),
    onSecondary = Color(0xFF4E2600),
    secondaryContainer = Color(0xFF6F3800),
    onSecondaryContainer = Color(0xFFFFE0B2),
    tertiary = Color(0xFF81D4FA),
    onTertiary = Color(0xFF003545),
    tertiaryContainer = Color(0xFF004D63),
    onTertiaryContainer = Color(0xFFB3E5FC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C19),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF1A1C19),
    onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388)
)

@Composable
fun PantryWiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
