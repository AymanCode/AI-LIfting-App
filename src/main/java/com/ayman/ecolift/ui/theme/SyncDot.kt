package com.ayman.ecolift.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ayman.ecolift.web.supabase.sync.SyncStatus

/**
 * The signed-in app provides this; demo leaves it null, so no dot is composed there. Dynamic, not
 * static: the value changes whenever a write is queued or drained, and only the mastheads that
 * read it should recompose, not everything under the provider.
 */
val LocalSyncStatus = compositionLocalOf<SyncStatus?> { null }

/** The mirror label, so the harness and screen readers can read the dot. No words on screen. */
fun syncDotLabel(status: SyncStatus): String = when (status) {
    SyncStatus.UpToDate -> "Sync up to date"
    is SyncStatus.Waiting -> "Sync waiting · ${status.pending}"
    is SyncStatus.Offline -> "Sync offline"
}

/**
 * A 6 dp dot (spec 2026-10-08 §The dot). UpToDate: nothing drawn, slot kept. Waiting: amber (the
 * palette's warm delta colour), scale 1.0 to 1.25 over 1.2 s, reversing, while Waiting lasts.
 * Offline: faint ink, still. Appear and disappear with a 200 ms fade and scale.
 */
@Composable
fun SyncDot(status: SyncStatus, modifier: Modifier = Modifier) {
    val visible = status != SyncStatus.UpToDate
    // Keep the last visible colour while fading out so the dot never flips hue mid-fade.
    var lastColor by remember { mutableStateOf(EspressoPalette.faint) }
    if (status is SyncStatus.Waiting) lastColor = EspressoPalette.down
    if (status is SyncStatus.Offline) lastColor = EspressoPalette.faint
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "syncDotAlpha")
    val appear by animateFloatAsState(if (visible) 1f else 0.6f, tween(200), label = "syncDotAppear")
    // The pulse only exists while Waiting: no hidden animation frames the rest of the time. It is
    // held as State and read inside the layer lambda, so each frame invalidates the layer only.
    val pulse: State<Float> = if (status is SyncStatus.Waiting) {
        rememberInfiniteTransition(label = "syncDotPulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "syncDotPulseScale",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    Box(
        modifier = modifier
            .size(6.dp)
            .semantics { contentDescription = syncDotLabel(status) }
            .graphicsLayer {
                this.alpha = alpha
                val scale = appear * pulse.value
                scaleX = scale
                scaleY = scale
            }
            .background(lastColor, CircleShape),
    )
}
