package com.ayman.ecolift.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioActivityType.*

object DawnPalette {
    val skyTop = Color(0xFFFAE6D3)
    val skyMiddle = Color(0xFFF4D9DB)
    val skyBottom = Color(0xFFE7D7EE)
    val ink = Color(0xFF2B1B33)
    val muted = Color(0xFF6B5270)
    val line = Color(0xFFE4CBD0)
    val accent = Color(0xFFE8643C)
    val accentText = Color(0xFFB8401C)
    val accentSoft = Color(0x22E8643C)
    val panel = Color(0x85FFFFFF)
    val sheet = Color(0xFFFFF8F4)
    val pillText = Color(0xFFFFF7F2)
    val navBackground = Color(0xFFFBEFE9)
    val navLine = Color(0xFFEDD8D8)
    val navInk = muted

    fun machine(type: CardioActivityType): Color = when (type) {
        TREADMILL, RUN, WALK -> Color(0xFFE8643C)
        BIKE, ELLIPTICAL -> Color(0xFF7B4B8F)
        ROW, SWIM -> Color(0xFFD9A23C)
        STAIR_CLIMBER, OTHER -> Color(0xFFC9556F)
    }
}

val LocalDawn = staticCompositionLocalOf { false }          // true inside cardio routes
val LocalDawnPalette = staticCompositionLocalOf { DawnPalette }

@Composable
fun DawnBackground() {
    // vertical gradient skyTop → skyMiddle (42%) → skyBottom, fillMaxSize
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to DawnPalette.skyTop,
                    0.42f to DawnPalette.skyMiddle,
                    1f to DawnPalette.skyBottom,
                )
            )
    )
}

@Composable
fun DawnTheme(content: @Composable () -> Unit) {
    // provides LocalDawn = true and MaterialTheme(typography = LogMaterialTypography)
    CompositionLocalProvider(
        LocalDawn provides true,
        LocalDawnPalette provides DawnPalette,
    ) {
        MaterialTheme(typography = LogMaterialTypography) {
            content()
        }
    }
}
