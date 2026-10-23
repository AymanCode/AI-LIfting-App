package com.ayman.ecolift.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.UNCLASSIFIED_MUSCLE_GROUPS
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import kotlinx.datetime.LocalDate

@Immutable
data class CycleSlotUi(
    val slotId: Long,
    val occurrence: Int,
    val label: String,
    val shortLabel: String,
    val isExpected: Boolean = false,
    val isSelected: Boolean = false,
)

@Immutable
enum class RecommendationState {
    Idle,
    Working,
    Done,
}

@Immutable
data class LogSetUi(
    val id: Long,
    val syncId: String = "",
    val exerciseId: Long,
    val setNumber: Int,
    val weightLbs: Int?,
    val reps: Int?,
    val suggestedWeightLbs: Int? = null,
    val suggestedReps: Int? = null,
    val hasAssistSuggestion: Boolean = false,
    val inputWarning: String? = null,
    val isBodyweight: Boolean,
    val completed: Boolean,
    val restAfterSeconds: Int? = null,
    val isWarmup: Boolean = false,
)

/** One set from the last session this exercise was trained — the number to beat. */
@Immutable
data class GhostSetUi(
    val weightLbs: Int?,
    val reps: Int?,
    val isBodyweight: Boolean,
    val isWarmup: Boolean = false,
)

/** One past session's best estimated 1RM (Epley), for the log card's trend line. ISO date. */
@Immutable
data class E1rmPointUi(
    val date: String,
    val e1rm: Float,
)

@Immutable
data class LogExerciseUi(
    val cardId: String,
    val cardPosition: Int,
    val displayMode: WorkoutCardDisplayMode,
    val exerciseId: Long,
    val name: String,
    val muscleGroups: String = UNCLASSIFIED_MUSCLE_GROUPS,
    val lastTopSet: String?,
    val bestTopSet: String?,
    val showBaselineHint: Boolean = false,
    val suggestedMuscleGroup: String? = null,
    val sets: List<LogSetUi>,
    val estimated1RM: Int = 0,
    val isNewPB: Boolean = false,
    val recommendationState: RecommendationState = RecommendationState.Idle,
    // Last session's sets, in order, so the log can aim set N at last session's set N.
    val ghostSets: List<GhostSetUi> = emptyList(),
    val lastSessionDate: String? = null,
    // Best estimated 1RM per past session, oldest first, so the card can draw its trend.
    val e1rmHistory: List<E1rmPointUi> = emptyList(),
    // The user's bodyweight setting (whole lb), for bodyweight exercises' e1RM caption.
    val userBodyweightLbs: Int? = null,
    val showTakenChip: Boolean = false,
    val takenGroupKey: String? = null,
    val takenChipLabel: String = "Machine taken?",
)

@Immutable
data class LogUiState(
    val currentDate: String,
    val currentDateLabel: String,
    val cycleEnabled: Boolean,
    val cycleSlot: CycleSlotUi? = null,
    val alternativeForDate: String? = null, // For Dynamic Swap alert
    val cycleOptions: List<CycleSlotUi> = emptyList(),
    val exercises: List<LogExerciseUi> = emptyList(),
    val exerciseInput: String = "",
    val predictiveExercises: List<Exercise> = emptyList(),
    val pendingExerciseResolution: PendingExerciseResolutionUi? = null,
    val pendingReviews: List<PendingReview> = emptyList(),
    val reviewsExpanded: Boolean = false,
    val uiMessage: String? = null,
)

@Immutable
data class PendingExerciseResolutionUi(
    val requestId: Long,
    val selectedExerciseId: Long?,
    val selectedName: String?,
    val typedName: String,
    val candidateExerciseId: Long,
    val candidateName: String,
    val isBusy: Boolean = false,
)

@Immutable
data class ExerciseChipUi(
    val id: Long,
    val name: String,
)

@Immutable
data class ProgressPointUi(
    val date: String,
    val label: String,
    val volume: Int,
    val estimated1RM: Float,
    val maxWeight: Int,
    val maxReps: Int,
    val reps: Int = 0, // Added for tooltip
    val repsAtTopWeight: Int = 0,
    val estimateEvidence: String = "",
    val repEvidence: String = "",
    val workingSetCount: Int = 0,
    val totalWorkingReps: Int = 0,
)

@Immutable
data class ProgressExerciseUi(
    val exerciseId: Long,
    val name: String,
    val muscleGroups: String = UNCLASSIFIED_MUSCLE_GROUPS,
    val suggestedMuscleGroup: String? = null,
    val sessions: Int,
    val lastSessionDate: String,
    val lastSessionIsoDate: String,
    val lastSessionSummary: String, // e.g. "165 x 7"
    val changePercentage: Float, // % e1RM change across the selected period
    val rangeStatLabel: String? = null,
    val rangeStatSign: Int = 0,
    val rangeStatContext: String? = null,
    val firstSessionIsoDate: String? = null,
    val monthDeltaLabel: String? = null,
    val monthDeltaSign: Int = 0,
    val monthDeltaSubLabel: String? = null,
    val trend: List<Int>, // Last few e1RMs for sparkline
    val overview: QuietProgressOverview = QuietProgressOverview(),
    val report: ExerciseReport = ExerciseReport(),
    val isBodyweight: Boolean = false,
)

@Immutable
data class ProgressHeroUi(
    val lifetimeVolumeLabel: String,
    val equivalenceLabel: String,
    val milestoneProgress: Float,
    val milestoneLabel: String,
    val sessionCount: Int,
    val completedSetCount: Int,
    val totalRepCount: Int,
    val cyclesDone: Int,
    val prsThisMonth: Int,
)

enum class ProgressOrganizationMode {
    HIGHLIGHTS, PROGRESS
}

enum class ProgressSortMode {
    TREND, RECENT, NAME, SESSIONS, BODY_PART
}

enum class TimeframeFilter {
    TWO_WEEKS, ONE_MONTH, THREE_MONTHS, SIX_MONTHS, ONE_YEAR, ALL_TIME
}

enum class ProgressMetric {
    ESTIMATED_1RM, WEIGHT, VOLUME, REPS
}

sealed interface ProgressScope {
    data object Overview : ProgressScope
    data class Report(val exerciseId: Long) : ProgressScope
}

sealed interface ProgressProjection {
    data object Pending : ProgressProjection
    data class Ready(val state: ProgressUiState) : ProgressProjection
}

@Immutable
data class ProgressUiState(
    val windowEndIsoDate: String? = null,
    val exercises: List<ProgressExerciseUi> = emptyList(),
    val hero: ProgressHeroUi? = null,
    val heroSessionsThisMonthLabel: String? = null,
    val organizationMode: ProgressOrganizationMode = ProgressOrganizationMode.PROGRESS,
    val sortMode: ProgressSortMode = ProgressSortMode.TREND,
    val searchQuery: String = "",
    val visibleExercises: List<ProgressExerciseUi> = emptyList(),
    val selectedExerciseId: Long? = null,
    val selectedExerciseName: String = "",
    val selectedExerciseMuscleGroups: String = "",
    val selectedExerciseSuggestedMuscleGroup: String? = null,
    val isBodyweight: Boolean = false,
    val userBodyweightLbs: Int? = null,
    val chartPoints: List<ProgressPointUi> = emptyList(),
    val timeframe: TimeframeFilter = TimeframeFilter.ONE_MONTH,
    val selectedMetric: ProgressMetric = ProgressMetric.ESTIMATED_1RM,
    val stats: ProgressStatsUi? = null,
    val report: ExerciseReport? = null,
    val groupWall: GroupWallUi = GroupWallUi(),
    val heldAvailable: Boolean = false,
    val highlights: com.ayman.ecolift.data.progress.highlights.HighlightReelSnapshot =
        com.ayman.ecolift.data.progress.highlights.HighlightReelSnapshot(emptyList()),
)

@Immutable
data class ProgressStatsUi(
    val currentPr: String,
    val currentPrLbs: Float = 0f,
    val bestSetLabel: String = "",
    val est1Rm: String,
    val totalVolume: String,
    val totalVolumeLbs: Int = 0,
    val workoutCount: Int,
    val currentPrDate: LocalDate? = null,
)

// Split tab models

sealed interface CycleEntry {
    val label: String
    data class SplitDay(val splitId: Long, override val label: String) : CycleEntry
    data class RestDay(override val label: String = "Rest") : CycleEntry
}

@Immutable
data class SplitCycle(
    val enabled: Boolean,
    val order: List<CycleEntry>,
    val currentIndex: Int,
)

@Immutable
data class SplitUiState(
    val cycle: SplitCycle = SplitCycle(false, emptyList(), 0),
    /** Learned groups and hand-made splits in due order (spec 2026-09-28). */
    val cards: List<SplitGroupCardUi> = emptyList(),
    /** False until the saved boards are read: nothing can be edited or started from them yet. */
    val boardsReady: Boolean = false,
    /** Splits whose board is still being saved; Start waits for them. */
    val pendingSlots: Set<Long> = emptySet(),
) {
    val isRestDay: Boolean
        get() = cycle.enabled &&
            cycle.order.getOrNull(cycle.currentIndex) is CycleEntry.RestDay
}

fun cycleTypeLabel(type: Int): String = "Session ${type + 1}"

fun cycleTypeShortLabel(type: Int, occurrence: Int): String = "S${type + 1}.$occurrence"

// ---- AI / IronMind UI models ----

data class AiMessageUi(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val timestampMillis: Long = 0L,
    val isError: Boolean = false,
    val card: AiMessageCardUi? = null,
    val recovery: AiRecoveryActionUi? = null,
)

sealed interface AiMessageCardUi

data class AiHistoryCardUi(
    val exerciseName: String,
    val prLine: String?,
    val sessions: List<AiHistoryRowUi>,
) : AiMessageCardUi

data class AiHistoryRowUi(
    val date: String,
    val setLabel: String,
)

data class AiChangesCardUi(
    val changes: List<String>,
) : AiMessageCardUi

data class AiShortcutUi(
    val title: String,
    val subtitle: String,
    val prompt: String,
)

data class AiPendingActionUi(
    val title: String,
    val detail: String,
    val confirmLabel: String,
)

data class AiRecoveryActionUi(
    val title: String,
    val detail: String,
    val originalText: String,
    val suggestedTemplate: String,
    val saveDate: String,
    val canTryModel: Boolean,
)

data class AiUiState(
    val statusHeadline: String = "",
    val statusDetail: String = "",
    val modelPath: String? = null,
    val isModelReady: Boolean = false,
    val messages: List<AiMessageUi> = emptyList(),
    val shortcuts: List<AiShortcutUi> = emptyList(),
    val input: String = "",
    val availableExerciseNames: List<String> = emptyList(),
    val attachedImageLabel: String? = null,
    val pendingAction: AiPendingActionUi? = null,
    val isWorking: Boolean = false,
)

/** All wall text and geometry are projected once, before composition. Coordinates are normalized. */
@Immutable
data class GroupWallUi(
    val panels: List<GroupWallPanelUi> = emptyList(),
    val windowLabel: String = "",
    val windowParts: List<DataPart> = emptyList(),
)

@Immutable
data class GroupWallPanelUi(
    val key: String,
    val title: String,
    val colorIndex: Int?,
    val description: String,
    val sessionParts: List<DataPart>,
    val rows: List<List<GroupWallTileUi>>,
    val tail: List<GroupWallTailUi>,
    val sessions: Int,
    val latestWindowDate: String?,
    val lastAttributedDate: String?,
    val foldedParts: List<DataPart>,
    val foldedDescription: String,
    /** Short date of the last attributed session ever, for the folded line's `last` chip. */
    val lastLabel: String? = null,
    /** Kicker over the tail chips: `ONE SESSION` when every tail lift has one session, else `+N LIFTS`. */
    val tailLabel: String = "",
)

/** One y gridline: where it sits in the plot (0 = bottom of the range, 1 = top) and its printed value. */
@Immutable
data class GroupWallYTick(val fraction: Float, val label: String)

/** One mark under the baseline: calendar fraction of the window, an optional label, and whether it is the taller mark. */
@Immutable
data class GroupWallXTick(val fraction: Float, val label: String?, val major: Boolean)

/**
 * A tile's chart, projected once: calendar x fractions, y fractions inside the axis range
 * (0 = bottom, 1 = top), the gridlines, the unit printed under the gutter (`1RM`, `+LB` or `REPS`)
 * and the x marks for the window's length. Nothing here is parsed or computed while drawing.
 */
@Immutable
data class GroupWallChartUi(
    val xs: List<Float> = emptyList(),
    val ys: List<Float> = emptyList(),
    val yTicks: List<GroupWallYTick> = emptyList(),
    val unit: String = "1RM",
    val xTicks: List<GroupWallXTick> = emptyList(),
) {
    val pointCount: Int get() = xs.size
}

/** A top set split for display: `280` or `BW+25` or `BW`, the reps, and whether the weight carries the `LB` superscript. */
@Immutable
data class GroupWallTopSetUi(val weight: String, val reps: String, val showsLb: Boolean)

@Immutable
data class GroupWallTileUi(
    val exerciseId: Long,
    val name: String,
    val chart: GroupWallChartUi,
    /** Accessibility and QA text for the top set (`280×13`); the tile prints [topSet] instead. */
    val topSetParts: List<DataPart>,
    /** Null when the lift has no session in the window. */
    val topSet: GroupWallTopSetUi?,
    val sessions: Int,
    val win: ChangelogWin?,
    /** The pill's number (`+25`) and unit (`LB`, `REP`, `REPS`); empty without a win. */
    val winNumber: String,
    val winUnit: String,
    val description: String,
)

@Immutable
data class GroupWallTailUi(
    val exerciseId: Long,
    val parts: List<DataPart>,
    val description: String,
    val name: String = "",
    val topSetLabel: String = "",
    val sessions: Int = 0,
    /** The chip's set split like a tile's: weight part (`145`, `BW+25`) and reps. */
    val weight: String = "",
    val reps: String = "",
)
