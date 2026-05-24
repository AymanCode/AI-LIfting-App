package com.ayman.ecolift.ui.viewmodel

internal fun calculateSmartSuggestedReps(
    sourceNewReps: Int?,
    sourceBaselineReps: Int?,
    targetBaselineReps: Int?,
    sourceSetNumber: Int,
    targetSetNumber: Int,
): Int? {
    val newReps = sourceNewReps?.takeIf { it > 0 } ?: return null
    val sourceBaseline = sourceBaselineReps?.takeIf { it > 0 } ?: return newReps
    val targetBaseline = targetBaselineReps?.takeIf { it > 0 } ?: return newReps

    val ratioAdjusted = (newReps * targetBaseline) / sourceBaseline
    val capped = when {
        targetSetNumber > sourceSetNumber -> ratioAdjusted.coerceAtMost(newReps)
        targetSetNumber < sourceSetNumber -> ratioAdjusted.coerceAtMost(targetBaseline)
        else -> ratioAdjusted
    }
    return capped.coerceAtLeast(1)
}
