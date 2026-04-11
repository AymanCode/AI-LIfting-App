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
)

data class LogUiState(
    val currentDate: String,
    val currentDateLabel: String,
    val cycleEnabled: Boolean,
    val cycleSlot: CycleSlotUi? = null,
    val cycleOptions: List<CycleSlotUi> = emptyList(),
    val swapNotices: List<SwapNoticeUi> = emptyList(),
    val exercises: List<LogExerciseUi> = emptyList(),
    val exerciseInput: String = "",
    val inlineSuggestions: List<String> = emptyList(),
    val pendingReviews: List<PendingReview> = emptyList(),
    val reviewsExpanded: Boolean = false,
)

data class ProgressPointUi(
    val date: String,
    val label: String,
    val weightLbs: Int,
)

data class ProgressExerciseUi(
    val exerciseId: Long,
    val name: String,
    val sessions: Int,
)

data class ProgressUiState(
    val exercises: List<ProgressExerciseUi> = emptyList(),
    val selectedExerciseId: Long? = null,
    val selectedExerciseName: String = "",
    val chartPoints: List<ProgressPointUi> = emptyList(),
)

data class AiMessageUi(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val isError: Boolean = false,
)

data class AiShortcutUi(
    val title: String,
    val subtitle: String,
    val prompt: String,
)

data class AiPendingActionUi(
    val title: String,
    val detail: String,
    val confirmLabel: String = "Confirm",
)

data class AiUiState(
    val statusHeadline: String = "Gemma unavailable",
    val statusDetail: String = "",
    val modelPath: String? = null,
    val isModelReady: Boolean = false,
    val messages: List<AiMessageUi> = emptyList(),
    val shortcuts: List<AiShortcutUi> = emptyList(),
    val input: String = "",
    val attachedImageLabel: String? = null,
    val pendingAction: AiPendingActionUi? = null,
    val isWorking: Boolean = false,
)

data class SplitUiState(
    val isActive: Boolean = false,
    val numTypes: Int = 3,
    val nextSessionLabel: String? = null,
    val previewSlots: List<String> = emptyList(),
)

data class SwapNoticeUi(
    val title: String,
    val detail: String,
)

fun cycleTypeLabel(type: Int): String = "Day ${('A' + type)}"

fun cycleTypeShortLabel(type: Int, occurrence: Int): String = "${('A' + type)}$occurrence"
