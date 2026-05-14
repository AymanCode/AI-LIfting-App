package com.ayman.ecolift.ui.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * A modifier that applies a spring-based scale-down on press and springs back on release.
 * Gives buttons a tactile "bounce" feel.
 *
 * @param scaleDown The scale factor on press (e.g. 0.92f = shrink to 92%)
 * @param onClick The click callback to invoke on tap
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.92f,
    onClick: () -> Unit = {},
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = 800f,
        ),
        label = "bounce_scale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                    onClick()
                },
            )
        }
}

/**
 * A modifier that only applies the bounce visual effect without handling clicks.
 * Useful when the click handler is already managed by the parent composable.
 */
fun Modifier.bounceEffect(
    scaleDown: Float = 0.92f,
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = 800f,
        ),
        label = "bounce_scale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
            )
        }
}

/**
 * Animated counter that rolls digits up/down like an odometer.
 * Each digit transitions independently with a vertical slide.
 */
@Composable
fun AnimatedCounter(
    targetValue: Int,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    val text = targetValue.toString()
    Row {
        text.forEachIndexed { index, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInVertically { -it } togetherWith slideOutVertically { it }
                    } else {
                        slideInVertically { it } togetherWith slideOutVertically { -it }
                    }
                },
                label = "digit_$index",
            ) { digit ->
                Text(
                    text = digit.toString(),
                    style = style,
                    fontWeight = fontWeight,
                )
            }
        }
    }
}

/**
 * Animated counter that renders a formatted volume string (e.g. "3.3k").
 * The numeric portion animates while the suffix stays static.
 */
@Composable
fun AnimatedVolumeCounter(
    targetValue: Int,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    val formatted = formatVolumeForAnimation(targetValue)
    Row {
        formatted.forEachIndexed { index, char ->
            if (char.isDigit()) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInVertically { -it } togetherWith slideOutVertically { it }
                        } else {
                            slideInVertically { it } togetherWith slideOutVertically { -it }
                        }
                    },
                    label = "vol_digit_$index",
                ) { digit ->
                    Text(
                        text = digit.toString(),
                        style = style,
                        fontWeight = fontWeight,
                    )
                }
            } else {
                Text(
                    text = char.toString(),
                    style = style,
                    fontWeight = fontWeight,
                )
            }
        }
    }
}

private fun formatVolumeForAnimation(v: Int): String =
    if (v >= 1000) "${v / 1000}.${(v % 1000) / 100}k" else "$v"

/**
 * Performs a light haptic tick — ideal for stepper buttons and frequent interactions.
 */
@Composable
fun rememberLightHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

/**
 * Performs a heavy haptic burst — ideal for completing workouts and terminal actions.
 */
@Composable
fun rememberHeavyHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}
