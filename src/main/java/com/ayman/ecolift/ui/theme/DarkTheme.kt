package com.ayman.ecolift.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkColorScheme = lightColorScheme(
    primary = AccentTeal,
    onPrimary = Color.White,
    background = BackgroundPrimary,
    onBackground = TextPrimary,
    surface = BackgroundSurface,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundElevated,
    onSurfaceVariant = TextSecondary,
    secondary = BackgroundSubtle,
    onSecondary = TextPrimary,
    outline = BorderDefault,
    outlineVariant = BorderSubtle,
    tertiary = AccentTeal,
    onTertiary = Color.White
)

@Composable
fun DarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
