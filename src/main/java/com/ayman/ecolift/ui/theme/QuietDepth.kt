package com.ayman.ecolift.ui.theme

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import com.ayman.ecolift.R

/**
 * Quiet shell tokens. Values match the web espresso steps.
 * The bottom token stays the page background. The shader may blend toward the navigation colour.
 */
object QuietDepth {
    val top = Color(0xFF1C1816)
    val middle = Color(0xFF1C1816)
    val bottom = Color(0xFF1C1816)
    val navigation = Color(0xFF151210)
    val ink = Color(0xFFF3EDE4)
    val muted = Color(0xFFA89C90)
    val subtle = Color(0xFF6F655C)
    val accent = Color(0xFFF0643C)
    val line = Color(0xFF2E2925)
    val negative = Color(0xFFF08A7E)
}

var QuietUiFontFamily: FontFamily = FontFamily.SansSerif

private val quietFallbackBrush = Brush.verticalGradient(
    colors = listOf(QuietDepth.top, QuietDepth.navigation),
)

@Composable
fun QuietDepthBackground() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val shaderBrush = QuietDepthShader.brush(size.width, size.height)
                onDrawBehind {
                    drawRect(shaderBrush ?: quietFallbackBrush)
                }
            },
        )
    } else {
        Box(Modifier.fillMaxSize().background(quietFallbackBrush))
    }
}

enum class QuietIconName {
    Calendar,
    Progress,
    Sparkles,
    Heart,
    Split,
    Search,
    Left,
    Right,
    Down,
    Trophy,
    Dumbbell,
    Arrow,
}

@Composable
fun QuietIcon(
    name: QuietIconName,
    description: String?,
    modifier: Modifier = Modifier,
    tint: Color = QuietDepth.muted,
    selected: Boolean = false,
) {
    val resource = when (name) {
        QuietIconName.Calendar ->
            if (selected) R.drawable.quiet_calendar_active else R.drawable.quiet_calendar
        QuietIconName.Progress ->
            if (selected) R.drawable.quiet_progress_active else R.drawable.quiet_progress
        QuietIconName.Sparkles ->
            if (selected) R.drawable.quiet_sparkles_active else R.drawable.quiet_sparkles
        QuietIconName.Heart ->
            if (selected) R.drawable.quiet_heart_active else R.drawable.quiet_heart
        QuietIconName.Split ->
            if (selected) R.drawable.quiet_split_active else R.drawable.quiet_split
        QuietIconName.Search -> R.drawable.quiet_search
        QuietIconName.Left -> R.drawable.quiet_left
        QuietIconName.Right -> R.drawable.quiet_right
        QuietIconName.Down -> R.drawable.quiet_down
        QuietIconName.Trophy -> R.drawable.quiet_trophy
        QuietIconName.Dumbbell -> R.drawable.quiet_dumbbell
        QuietIconName.Arrow -> R.drawable.quiet_arrow
    }
    Icon(painterResource(resource), description, modifier, tint)
}

/** True when the system animator scale or the transition scale is zero. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduced by remember { mutableStateOf(animationScaleIsZero(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = animationScaleIsZero(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun animationScaleIsZero(resolver: android.content.ContentResolver): Boolean {
    val animator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val transition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    return animator == 0f || transition == 0f
}
