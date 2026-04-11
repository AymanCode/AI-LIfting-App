package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.PendingReview

data class CycleSlotUi(
    val type: Int,
    val occurrence: Int,
    val label: String,
    val shortLabel: String,
    val isExpected: Boolean = false,
)

data class LogSetUi(
    val id: Long,
    val setNumber: Int,
    val weightLbs: Int,
    val reps: Int,
    val isBodyweight: Boolean,
    val completed: Boolean,
)

data class LogExerciseUi(
    val exerciseId: Long,
    val name: String,
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
    val restTimerSeconds: Int? = null,
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
)

data class ProgressExerciseUi(
    val exerciseId: Long,
    val name: String,
    val sessions: Int,
    val lastSessionDate: String,
    val trend: List<Int>, // Last few volumes for sparkline
)

enum class TimeframeFilter {
    THREE_MONTHS, YTD, ALL_TIME
}

data class ProgressUiState(
    val exercises: List<ProgressExerciseUi> = emptyList(),
    val selectedExerciseId: Long? = null,
    val selectedExerciseName: String = "",
    val isBodyweight: Boolean = false,
    val chartPoints: List<ProgressPointUi> = emptyList(),
    val timeframe: TimeframeFilter = TimeframeFilter.THREE_MONTHS,
)

data class SplitUiState(
    val isActive: Boolean = false,
    val numTypes: Int = 3,
    val previewSlots: List<String> = emptyList(),
)

fun cycleTypeLabel(type: Int): String = "Session ${type + 1}"

fun cycleTypeShortLabel(type: Int, occurrence: Int): String = "S${type + 1}.$occurrence"
