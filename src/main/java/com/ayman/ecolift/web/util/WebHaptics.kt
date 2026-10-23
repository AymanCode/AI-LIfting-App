package com.ayman.ecolift.web.util

import android.view.HapticFeedbackConstants
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ayman.ecolift.android.platform.AndroidHost

/**
 * Compose's default haptic is a no-op until a real implementation is provided.
 * Light is a clock tick (weight and rep wheels). Grab is a context click (the wheel picked up).
 * Heavy is a long press (a set logged).
 */
class WebHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        val view = AndroidHost.current()?.window?.decorView ?: return
        view.performHapticFeedback(
            hapticConstant(hapticFeedbackType),
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }
}

internal fun hapticConstant(hapticFeedbackType: HapticFeedbackType): Int = when (hapticFeedbackType) {
    HapticFeedbackType.LongPress -> HapticFeedbackConstants.LONG_PRESS
    HapticFeedbackType.GestureThresholdActivate -> HapticFeedbackConstants.CONTEXT_CLICK
    else -> HapticFeedbackConstants.CLOCK_TICK
}
