package com.ayman.ecolift.data.progress.highlights

import kotlinx.datetime.LocalDate

/**
 * A frozen, truth-preserving set of poster frames for one opening of the Highlights reel.
 *
 * The builder deliberately omits ineligible poster families instead of filling their approved
 * geometry with weaker claims or fabricated records. The UI must therefore treat [frames] as a
 * zero-to-five element list rather than assuming a fixed page count.
 */
data class HighlightReelSnapshot(
    val frames: List<ProgressHighlightFrame>,
    val cards: List<HighlightCard> = emptyList(),
    val monthLabel: String = "",
    val kicker: String = "",
)

enum class HighlightKind { MATCH_CUT, CADENCE_BANDS, RUNNING_TOTAL, BEAT, REPEATABLE_BEST }

/** All labels, chart coordinates and selection evidence are projected before composition. */
data class HighlightCard(
    val key: String,
    val kind: HighlightKind,
    val frame: ProgressHighlightFrame,
    val evidenceDate: LocalDate,
    val reportExerciseId: Long,
    val reportExerciseName: String,
    val title: String,
    val tag: String,
    val why: String,
    val dateLabel: String,
    val accessibilitySummary: String = "",
)

data class HighlightSetEvidence(
    val date: LocalDate,
    val dateLabel: String,
    val weightLbsStored: Int,
    val loadLabel: String,
    val reps: Int,
)

data class HighlightChartPoint(val x: Float, val y: Float, val atOrAbove: Boolean)

sealed interface ProgressHighlightFrame {
    /** A record load at identical reps; both endpoints and every intervening session survive. */
    data class Beat(
        val exerciseName: String,
        val canonicalExerciseId: Long,
        val then: HighlightSetEvidence,
        val now: HighlightSetEvidence,
        val deltaWeightLbsStored: Int,
        val deltaLoadLabel: String,
        val sessions: List<HighlightSetEvidence>,
        val sessionTickPositions: List<Float>,
    ) : ProgressHighlightFrame

    /** The old best is a ceiling. Only sessions with a set at its load and reps qualify. */
    data class RepeatableBest(
        val exerciseName: String,
        val canonicalExerciseId: Long,
        val best: HighlightSetEvidence,
        val heldDays: Int,
        val sessions: List<HighlightSetEvidence>,
        val sessionsAtOrAbove: Int,
        val chartPoints: List<HighlightChartPoint>,
        val ceilingY: Float,
    ) : ProgressHighlightFrame

    /** Same exercise and exact rep count, with a later all-time load best. */
    data class MatchCut(
        val exerciseName: String,
        val dateLabel: String,
        val beforeLoadLabel: String,
        val afterLoadLabel: String,
        val deltaLoadLabel: String,
        val reps: Int,
        val canonicalExerciseId: Long,
        val beforeDate: LocalDate,
        val nowDate: LocalDate,
        val beforeWeightLbsStored: Int,
        val afterWeightLbsStored: Int,
        val deltaWeightLbsStored: Int,
    ) : ProgressHighlightFrame

    /** Four consecutive Monday-Sunday calendar weeks with at least one workout in every week. */
    data class CadenceBands(
        val straightWeekCount: Int,
        val weeks: List<CadenceWeek>,
        val workoutCount: Int,
        val windowStart: LocalDate,
        val windowEnd: LocalDate,
    ) : ProgressHighlightFrame

    /** Eight visible workout receipts whose rep totals reconcile exactly to the headline. */
    data class RunningTotal(
        val sessionReps: List<Int>,
        val totalReps: Int,
        val workoutCount: Int,
        val setCount: Int,
        val periodLabel: String,
        val sessions: List<RunningTotalSession>,
        val windowStart: LocalDate,
        val windowEnd: LocalDate,
    ) : ProgressHighlightFrame
}

/** A single fixed Monday-Sunday rail in the approved cadence poster. */
data class CadenceWeek(
    val dateRangeLabel: String,
    /** ISO weekday offsets used by the poster: Monday = 0, Sunday = 6. */
    val workoutDays: Set<Int>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val workoutDates: List<LocalDate>,
)

/** One chronological source row in the running-total equation. */
data class RunningTotalSession(
    val ordinal: Int,
    val date: LocalDate,
    val reps: Int,
    val setCount: Int,
)
