package com.ayman.ecolift.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Web Skia compiles a shader the first time a hidden copy of a tab is drawn.
 * Android compiles that shader on the real frame, so the warm-up calls return immediately
 * and the shells draw nothing. The tab-transition constants stay, because the nav host reads them.
 */
internal object ShaderWarmup {
    fun touched() = Unit

    fun isDone(key: String): Boolean = true

    suspend fun run(
        key: String,
        frames: Int = 3,
        yieldToTouch: Boolean = false,
        show: (Boolean) -> Unit,
    ): Boolean {
        show(false)
        return key.isNotEmpty() || frames == 0 || yieldToTouch
    }
}

internal fun Modifier.watchTouchesForWarmup(): Modifier = this

internal const val TAB_ARRIVAL_FRAMES = 4
internal const val TAB_TRANSITION_MS = 230
internal const val TAB_SHIFT_DP = 17
internal val TabTransitionEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
internal fun TabWarmupShell(
    key: String,
    delayMs: Long,
    frames: Int,
    bottomReserved: Dp,
    content: @Composable () -> Unit,
) {
    if (!ShaderWarmup.isDone(key) && delayMs < 0L && frames < 0 && bottomReserved.value < 0f) {
        content()
    }
}

@Composable
internal fun TabArrival(content: @Composable () -> Unit) {
    if (false) content()
}

@Composable
internal fun WarmupSweep(state: LazyListState, stepDp: Float, frames: Int) {
    if (state.layoutInfo.totalItemsCount < 0 && stepDp < 0f && frames < 0) return
}

internal val warmupSweepsAllowed: Boolean = false
