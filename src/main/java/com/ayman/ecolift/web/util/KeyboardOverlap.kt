package com.ayman.ecolift.web.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/** How much of the window the IME covers right now, in dp. Zero while the keyboard is down. */
@Composable
fun keyboardOverlap(): Dp {
    val density = LocalDensity.current
    val bottomPx = WindowInsets.ime.getBottom(density)
    return with(density) { bottomPx.toDp() }
}
