package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Drives the scroll-linked fade of the log screen's chrome.
 */
@Stable
class ChromeRevealState(initial: Float = 1f) {
    var reveal by mutableFloatStateOf(initial)
        private set

    /** Distance in px over which a full hide/show happens while scrolling. */
    var hideDistancePx: Float = 200f

    /** When locked, scroll deltas are ignored because an overlay owns the chrome. */
    var locked by mutableStateOf(false)

    fun applyScrollDelta(deltaY: Float) {
        if (locked) return
        reveal = (reveal + deltaY / hideDistancePx).coerceIn(0f, 1f)
    }

    fun snap(value: Float) {
        reveal = value.coerceIn(0f, 1f)
    }

    suspend fun animateTo(target: Float, durationMillis: Int = 180) {
        animate(reveal, target.coerceIn(0f, 1f), animationSpec = tween(durationMillis)) { v, _ ->
            reveal = v
        }
    }
}
