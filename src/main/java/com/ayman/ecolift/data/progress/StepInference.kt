package com.ayman.ecolift.data.progress

import kotlin.math.abs

private val PlausibleStoredSteps = listOf(25, 50, 100, 150, 200, 250)

internal fun inferWeightStepStored(
    distinctStoredWeights: List<Int>,
    exerciseName: String,
    topStoredWeight: Int?,
): Int {
    val weights = distinctStoredWeights
        .filter { it > 0 }
        .distinct()
        .sorted()

    if (weights.size >= 2) {
        val min = weights.first()
        val gcd = weights
            .map { it - min }
            .filter { it > 0 }
            .fold(0) { acc, diff -> gcd(acc, diff) }
        return plausibleStepAtOrBelow(gcd)
    }

    val name = exerciseName.lowercase()
    if (name.contains("dumbbell") || name.contains("db ")) return 50

    val looksBarbell = listOf("barbell", "bench", "squat", "deadlift", "rdl")
        .any { name.contains(it) }
    if (looksBarbell) {
        return if (topStoredWeight != null && topStoredWeight >= 1350) 100 else 50
    }

    return 50
}

private fun plausibleStepAtOrBelow(step: Int): Int {
    if (step < PlausibleStoredSteps.first()) return PlausibleStoredSteps.first()
    return PlausibleStoredSteps.lastOrNull { it <= step } ?: PlausibleStoredSteps.first()
}

private fun gcd(a: Int, b: Int): Int {
    var x = abs(a)
    var y = abs(b)
    while (y != 0) {
        val next = x % y
        x = y
        y = next
    }
    return x
}
