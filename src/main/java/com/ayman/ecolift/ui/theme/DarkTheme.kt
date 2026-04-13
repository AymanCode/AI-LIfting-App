package com.ayman.ecolift.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    primary = AccentTeal,
    onPrimary = Color(0xFF00302A),
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
