package com.ayman.ecolift.data

internal fun epleyEstimate(loadLbs: Double, reps: Int): Double =
    loadLbs * (1.0 + reps / 30.0)

internal fun epleyEstimate(loadLbs: Float, reps: Int): Float =
    loadLbs * (1f + reps / 30f)
