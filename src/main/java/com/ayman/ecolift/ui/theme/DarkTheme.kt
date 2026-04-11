package com.ayman.ecolift.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    background = Color(0xFF0F0F10),
    surface = Color(0xFF1A1A1D),
    onSurface = Color(0xFFE8E8EA),
    primary = Color(0xFF00C2A8)
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
