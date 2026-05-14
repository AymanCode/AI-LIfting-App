package com.ayman.ecolift.ai

import java.util.concurrent.ConcurrentHashMap

enum class MovementPattern {
    HorizontalPress,
    InclinePress,
    VerticalPress,
    ChestFly,
    HorizontalPull,
    VerticalPull,
    Squat,
    Hinge,
    Curl,
    Triceps,
    Calves,
    Shoulders,
    Unknown,
}

object ExercisePatternMatcher {
    private val classificationCache = ConcurrentHashMap<String, MovementPattern>()

    fun classify(name: String): MovementPattern {
        val normalized = name.trim().lowercase()
        if (normalized.isEmpty()) {
            return MovementPattern.Unknown
        }
        return classificationCache.getOrPut(normalized) {
            when {
                normalized.contains("incline") && normalized.contains("press") -> MovementPattern.InclinePress
                normalized.contains("bench") || normalized.contains("chest press") || normalized.contains("machine press") -> MovementPattern.HorizontalPress
                normalized.contains("shoulder press") || normalized.contains("overhead press") -> MovementPattern.VerticalPress
                normalized.contains("fly") || normalized.contains("pec deck") -> MovementPattern.ChestFly
                normalized.contains("pull up") || normalized.contains("lat pulldown") || normalized.contains("chin up") || normalized.contains("vertical pull") -> MovementPattern.VerticalPull
                normalized.contains("row") || normalized.contains("tether") || normalized.contains("face pull") -> MovementPattern.HorizontalPull
                normalized.contains("squat") || normalized.contains("leg press") || normalized.contains("leg extension") -> MovementPattern.Squat
                normalized.contains("deadlift") || normalized.contains("rdl") || normalized.contains("hinge") -> MovementPattern.Hinge
                normalized.contains("curl") -> MovementPattern.Curl
                normalized.contains("pushdown") || normalized.contains("tricep") || normalized.contains("dip") || normalized.contains("skull crusher") || normalized.contains("skullcrusher") -> MovementPattern.Triceps
                normalized.contains("calf") -> MovementPattern.Calves
                normalized.contains("raise") || normalized.contains("lateral") || normalized.contains("front raise") -> MovementPattern.Shoulders
                else -> MovementPattern.Unknown
            }
        }
    }

    fun estimateRelativeLoad(
        machineName: String,
        benchmarkWeight: Int,
    ): RelativeLoadEstimate {
        val normalized = machineName.lowercase()
        val factor = when {
            normalized.contains("plate-loaded chest press") || normalized.contains("converging") -> 0.74
            normalized.contains("chest press") -> 0.7
            normalized.contains("shoulder press") -> 0.62
            normalized.contains("row") -> 0.78
            normalized.contains("lat pulldown") -> 0.72
            else -> 0.68
        }
        val totalLoad = (benchmarkWeight * factor).toInt().coerceAtLeast(10)
        val perSide = if (normalized.contains("plate") || normalized.contains("per side")) {
            (totalLoad / 2).coerceAtLeast(5)
        } else {
            null
        }
        return RelativeLoadEstimate(
            totalLoad = totalLoad,
            perSideLoad = perSide,
        )
    }
}

data class RelativeLoadEstimate(
    val totalLoad: Int,
    val perSideLoad: Int?,
)
