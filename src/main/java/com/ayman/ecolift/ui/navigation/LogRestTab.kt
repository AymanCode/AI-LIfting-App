package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.BodyPartRegion
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.EspressoType
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.rememberHeavyHaptic
import com.ayman.ecolift.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/*
 * The rest timer is a tab that grows out of the top edge of the bottom nav (Ayman picked concept A,
 * "nav tab", at 32 dp on 2026-09-24). It sits flush on the bar, so it never floats over a set, and
 * a 1.5 dp line in the same colour runs along the bar's edge. The colour is the lift you are
 * resting for. Swiping it down into the bar stops it; a tap does nothing, so a thumb on its way to
 * IronMind (directly below) cannot stop it by accident.
 */

internal val RestTabHeight = 32.dp

/** Room the Log list keeps free at its bottom while the tab is up: the tab plus 2 dp of air. */
internal val RestTabListInset = RestTabHeight + 2.dp

private val RestTabFoot = 10.dp
private val RestTabCorner = 11.dp
private val RestTabLine = 1.5.dp
private val RestTabStopDrag = 14.dp

/**
 * The tab's fill colour, from the muscle groups of the lift being rested for: the same colour as
 * its card and Log button. A lift with no known muscle, or a rest whose lift is no longer on the
 * day (null), keeps the "logged" green so the tab never turns into a grey label.
 */
internal fun restTabColor(muscleGroups: String?): Color =
    muscleGroups
        ?.let(::rotationPrimaryRegion)
        ?.takeIf { it != BodyPartRegion.UNKNOWN }
        ?.color
        ?: EspressoPalette.up

/** "1:24" or, past an hour of forgetting, "61:05". */
internal fun restTabTime(elapsedSeconds: Int): String {
    val seconds = elapsedSeconds.coerceAtLeast(0)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/**
 * The tab's outline: rounded top corners on the body and a concave foot on each side, so the body
 * reads as grown out of the bar. [foot] of the width on each side belongs to the feet.
 */
internal class RestTabShape(private val foot: Dp, private val corner: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val f = with(density) { foot.toPx() }
        val r = with(density) { corner.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h)
            // Left foot: a quarter circle centred off the body's lower-left, curving up into the wall.
            arcTo(Rect(-f, h - 2 * f, f, h), 90f, -90f, false)
            lineTo(f, r)
            arcTo(Rect(f, 0f, f + 2 * r, 2 * r), 180f, 90f, false)
            lineTo(w - f - r, 0f)
            arcTo(Rect(w - f - 2 * r, 0f, w - f, 2 * r), 270f, 90f, false)
            lineTo(w - f, h - f)
            // Right foot, mirrored.
            arcTo(Rect(w - f, h - 2 * f, w + f, h), 180f, -90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * The rest tab. [elapsedSeconds] holds null when no rest is running; the tab then sinks back into the
 * bar still showing the last time it had, rather than flashing 0:00 on the way down.
 */
@Composable
internal fun RestTab(
    elapsedSeconds: State<Int?>,
    color: Color,
    visible: Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read the ticking state here so the workout and its exercise cards do not recompose each second.
    val seconds = elapsedSeconds.value
    val last = remember { RestTabLastShown() }
    if (seconds != null) {
        last.seconds = seconds
        last.color = color
    }
    val reducedMotion = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible && seconds != null,
        modifier = modifier,
        // Rises out of the bar: the bar is drawn over the Log screen, so the part still below its
        // edge is hidden until it clears it.
        enter = slideInVertically(
            tween(if (reducedMotion) 0 else 260, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
        ) { it },
        exit = slideOutVertically(tween(if (reducedMotion) 0 else 180, easing = FastOutLinearInEasing)) { it },
    ) {
        RestTabBody(
            elapsedSeconds = seconds ?: last.seconds,
            color = if (seconds != null) color else last.color,
            reducedMotion = reducedMotion,
            onStop = onStop,
        )
    }
}

private class RestTabLastShown {
    var seconds: Int = 0
    var color: Color = EspressoPalette.up
}

@Composable
private fun RestTabBody(
    elapsedSeconds: Int,
    color: Color,
    reducedMotion: Boolean,
    onStop: () -> Unit,
) {
    val ink = EspressoPalette.onColor
    val time = restTabTime(elapsedSeconds)
    val density = LocalDensity.current
    val stopDragPx = with(density) { RestTabStopDrag.toPx() }
    val maxDragPx = with(density) { RestTabHeight.toPx() }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val heavyHaptic = rememberHeavyHaptic()
    // The tab recomposes every second; the gesture must not restart when the caller hands in a
    // fresh lambda, so it reads the latest one instead of being keyed on it.
    val currentOnStop by rememberUpdatedState(onStop)
    // Read in the draw phase only, so the pulse never recomposes the tab.
    val pulse: State<Float> = if (reducedMotion) {
        remember { mutableFloatStateOf(0.75f) }
    } else {
        rememberInfiniteTransition(label = "rest_tab_pulse").animateFloat(
            initialValue = 0.75f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
            label = "rest_tab_dot",
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(RestTabHeight),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.fillMaxWidth().height(RestTabLine).background(color))
        // The input sits on a box that does not move, so the drag is measured against the screen
        // rather than against the tab it is moving.
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (drag.value >= stopDragPx) {
                                heavyHaptic()
                                currentOnStop()
                            } else {
                                scope.launch { drag.animateTo(0f, spring()) }
                            }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(0f, spring()) } },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch { drag.snapTo((drag.value + dragAmount).coerceIn(0f, maxDragPx)) }
                    }
                }
                .clearAndSetSemantics {
                    contentDescription = "Rest timer $time"
                    customActions = listOf(CustomAccessibilityAction("Stop rest timer") { currentOnStop(); true })
                },
        ) {
            Row(
                modifier = Modifier
                    .graphicsLayer { translationY = drag.value }
                    .height(RestTabHeight)
                    .background(color, RestTabShape(RestTabFoot, RestTabCorner))
                    .padding(start = RestTabFoot + 12.dp, end = RestTabFoot + 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .graphicsLayer { alpha = pulse.value }
                        .background(ink, CircleShape)
                )
                Text(
                    text = "REST",
                    style = EspressoType.kicker.copy(fontSize = 9.sp, lineHeight = 10.sp, letterSpacing = 1.8.sp),
                    color = ink.copy(alpha = 0.62f),
                )
                RestTabDigits(
                    text = time,
                    style = TextStyle(
                        fontFamily = LogDisplayFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        lineHeight = 19.sp,
                    ),
                    color = ink,
                )
            }
        }
    }
}

/**
 * Clash Grotesk has no tabular figures ("1" is little more than half the width of "0"), so each
 * digit gets a slot as wide as the widest digit and the tab keeps its width as the seconds tick.
 */
@Composable
private fun RestTabDigits(text: String, style: TextStyle, color: Color) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val slot = remember(style, density) {
        with(density) { ('0'..'9').maxOf { measurer.measure(it.toString(), style).size.width }.toDp() }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        text.forEach { ch ->
            if (ch.isDigit()) {
                Box(Modifier.width(slot), contentAlignment = Alignment.Center) {
                    Text(ch.toString(), style = style, color = color)
                }
            } else {
                Text(ch.toString(), style = style, color = color)
            }
        }
    }
}
