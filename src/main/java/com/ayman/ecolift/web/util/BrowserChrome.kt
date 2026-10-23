package com.ayman.ecolift.web.util

import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ayman.ecolift.android.platform.AndroidHost
import com.ayman.ecolift.ui.theme.GlassPalette

fun applyBrowserChrome(palette: GlassPalette) {
    applyBrowserChrome(topColor = palette.pageTop, bottomColor = palette.pageBottom)
}

/** Status bar takes [topColor]. Navigation bar takes [bottomColor]. No other chrome exists on Android. */
fun applyBrowserChrome(topColor: Color, bottomColor: Color = topColor) {
    val activity = AndroidHost.current() ?: return
    val top = topColor.toArgb()
    val bottom = bottomColor.toArgb()
    activity.enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(top),
        navigationBarStyle = SystemBarStyle.dark(bottom),
    )
}
