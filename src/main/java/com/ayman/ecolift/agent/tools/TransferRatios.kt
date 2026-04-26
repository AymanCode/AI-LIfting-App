package com.ayman.ecolift.agent.tools

import com.ayman.ecolift.ai.MovementPattern

/**
 * Transfer ratios between movement patterns.
 *
 * Ratio represents: targetWeight ~= sourceWeight x ratio
 * Values are conservative estimates based on common strength ratios.
 * Tune these over time as real user data accumulates.
 *
 * Key: Pair(sourcePattern, targetPattern)
 */
object TransferRatios {

    /**
     * Returns the estimated weight transfer ratio from [source] to [target] pattern.
     * Returns null if no known relationship (caller falls back to NO_DATA).
     */
    fun ratio(source: MovementPattern, target: MovementPattern): Double? {
        if (source == target) return 1.0
        return RATIOS[source to target] ?: RATIOS[target to source]?.let { 1.0 / it }
    }

    // Source -> Target : ratio (targetWeight = sourceWeight x ratio)
    private val RATIOS: Map<Pair<MovementPattern, MovementPattern>, Double> = mapOf(
        // Horizontal press family
        (MovementPattern.HorizontalPress to MovementPattern.InclinePress) to 0.85,
        (MovementPattern.HorizontalPress to MovementPattern.VerticalPress) to 0.65,
        (MovementPattern.HorizontalPress to MovementPattern.ChestFly) to 0.55,
        (MovementPattern.HorizontalPress to MovementPattern.Triceps) to 0.45,

        // Pull family
        (MovementPattern.VerticalPull to MovementPattern.HorizontalPull) to 0.90,

        // Squat / hinge
        (MovementPattern.Squat to MovementPattern.Hinge) to 1.20,

        // Curl
        (MovementPattern.HorizontalPull to MovementPattern.Curl) to 0.35,
    )
}
