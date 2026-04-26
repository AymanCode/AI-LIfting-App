package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.PendingReview

data class CycleSlotUi(
    val type: Int,
    val occurrence: Int,
    val label: String,
    val shortLabel: String,
    val isExpected: Boolean = false,
    val isSelected: Boolean = false,
)

data class LogSetUi(
    val id: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val weightLbs: Int?,
    val reps: Int?,
    val isBodyweight: Boolean,
    val completed: Boolean,
    val restAfterSeconds: Int? = null,
)

data class LogExerciseUi(
    val exerciseId: Long,
    val name: String,
    val muscleGroups: String = "CHEST · TRICEPS",
    val lastSessionHint: String?,
    val sets: List<LogSetUi>,
    val estimated1RM: Int = 0,
    val isNewPB: Boolean = false,
)

data class LogUiState(
    val currentDate: String,
    val currentDateLabel: String,
    val cycleEnabled: Boolean,
    val cycleSlot: CycleSlotUi? = null,
    val alternativeForDate: String? = null, // For Dynamic Swap alert
    val cycleOptions: List<CycleSlotUi> = emptyList(),
    val exercises: List<LogExerciseUi> = emptyList(),
    val exerciseInput: String = "",
    val inlineSuggestions: List<String> = emptyList(),
    val quickAddExercises: List<ExerciseChipUi> = emptyList(),
    val pendingReviews: List<PendingReview> = emptyList(),
    val reviewsExpanded: Boolean = false,
    val restStopwatchSeconds: Int? = null,
)

data class ExerciseChipUi(
    val id: Long,
    val name: String,
)


data class ProgressPointUi(
    val date: String,
    val label: String,
    val volume: Int,
    val estimated1RM: Float,
    val maxWeight: Int,
    val maxReps: Int,
    val reps: Int = 0, // Added for tooltip
)

data class ProgressExerciseUi(
    val exerciseId: Long,
    val name: String,
    val sessions: Int,
    val lastSessionDate: String,
    val lastSessionSummary: String, // e.g. "165 x 7"
    val changePercentage: Float, // % change last 30 days
    val trend: List<Int>, // Last few volumes for sparkline
)

enum class TimeframeFilter {
    ONE_MONTH, THREE_MONTHS, SIX_MONTHS, ONE_YEAR, ALL_TIME
}

enum class ProgressMetric {
    ESTIMATED_1RM, WEIGHT, VOLUME
}

data class ProgressUiState(
    val exercises: List<ProgressExerciseUi> = emptyList(),
    val selectedExerciseId: Long? = null,
    val selectedExerciseName: String = "",
    val isBodyweight: Boolean = false,
    val chartPoints: List<ProgressPointUi> = emptyList(),
    val timeframe: TimeframeFilter = TimeframeFilter.THREE_MONTHS,
    val selectedMetric: ProgressMetric = ProgressMetric.ESTIMATED_1RM,
    val stats: ProgressStatsUi? = null
)

data class ProgressStatsUi(
    val currentPr: String,
    val currentPrDelta: Float,
    val est1Rm: String,
    val est1RmDelta: Float,
    val totalVolume: String,
    val volumeDelta: Float,
    val workoutCount: Int,
    val workoutCountDelta: Int
)

// Split tab models

data class SplitExerciseRef(
    val exerciseId: Long,
    val displayName: String,
    val recentMaxVolume: List<Float> = emptyList(),
)

data class Split(
    val id: Long,
    val name: String,
    val exercises: List<SplitExerciseRef>,
    val lastPerformedEpochDay: Long?,   // null = never
    val estimatedDurationMin: Int,
    val recentVolume: List<Float>,
    val isSaved: Boolean = false,       // true = user explicitly saved exercise list
)

sealed interface CycleEntry {
    val label: String
    data class SplitDay(val splitId: Long, override val label: String) : CycleEntry
    data class RestDay(override val label: String = "Rest") : CycleEntry
}

data class SplitCycle(
    val enabled: Boolean,
    val order: List<CycleEntry>,
    val currentIndex: Int,
)

data class SplitUiState(
    val cycle: SplitCycle = SplitCycle(false, emptyList(), 0),
    val splits: List<Split> = emptyList(),
) {
    val today: Split?
        get() {
            if (!cycle.enabled) return null
            val entry = cycle.order.getOrNull(cycle.currentIndex) ?: return null
            val splitId = (entry as? CycleEntry.SplitDay)?.splitId ?: return null
            return splits.firstOrNull { it.id == splitId }
        }

    val isRestDay: Boolean
        get() = cycle.enabled &&
            cycle.order.getOrNull(cycle.currentIndex) is CycleEntry.RestDay
}

fun cycleTypeLabel(type: Int): String = "Session ${type + 1}"

fun cycleTypeShortLabel(type: Int, occurrence: Int): String = "S${type + 1}.$occurrence"
