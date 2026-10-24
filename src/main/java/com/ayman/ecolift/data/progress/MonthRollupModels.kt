package com.ayman.ecolift.data.progress

import com.ayman.ecolift.data.BodyPartRegion
import kotlinx.datetime.LocalDate

/**
 * A muscle at the resolution the log actually records, rather than the six broad regions.
 *
 * [BodyPartRegion] folds quads, hamstrings, glutes and calves into LEGS, and biceps,
 * triceps and forearms into ARMS. The month sheet reports the finer name, because "Legs,
 * 72 sets" hides whether that was all squats or all calf raises.
 */
enum class MuscleSlice(val displayName: String) {
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    CALVES("Calves"),
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARMS("Forearms"),
    CORE("Core"),
    UNKNOWN("Unknown"),
}

/** One muscle's slice of the month, in both units. Shares are 0f..1f. */
data class MuscleShare(
    val slice: MuscleSlice,
    val sets: Int,
    val loadLbs: Double,
    val setShare: Float,
    val loadShare: Float,
)

/** One region's slice of the month, in both units. Shares are 0f..1f. */
data class RegionShare(
    val region: BodyPartRegion,
    val sets: Int,
    val loadLbs: Double,
    val setShare: Float,
    val loadShare: Float,
)

/** One calendar cell. [slotId] is null when the session has no split assigned. */
data class MonthDay(
    val date: LocalDate,
    val trained: Boolean,
    val slotId: Long?,
    val slotName: String?,
    val hasPr: Boolean,
    /** Checked sets logged this day. 0 on untrained days. */
    val setCount: Int,
    /** Load moved this day, in pounds. 0.0 on untrained days. */
    val loadLbs: Double,
    /**
     * The day's two busiest muscles by checked sets, most sets first. Empty on untrained
     * days. This is what a day without a split name is labelled with: reporting what was
     * logged, never a session type the user did not write.
     */
    val topMuscles: List<MuscleSlice> = emptyList(),
    val sessionLabel: String = "",
    val leadRegion: BodyPartRegion = BodyPartRegion.UNKNOWN,
)

/** One row of Month Recap. [thin] marks a lift with exactly two sessions. */
data class RecapEntry(
    val exerciseId: Long,
    val name: String,
    val sessions: Int,
    val changePct: Float,
    val thin: Boolean,
)

data class HeaviestDay(
    val date: LocalDate,
    val loadLbs: Double,
    val sets: Int,
)

/** One lift and the number of days it was run. The mix falls back to these when no session carries a split. */
data class LiftRun(
    val exerciseId: Long,
    val name: String,
    val runs: Int,
)

data class SlotRun(
    val slotId: Long,
    val name: String,
    val runs: Int,
)

data class MonthRollupResult(
    val year: Int,
    val month: Int,
    val sessions: Int,
    val sets: Int,
    val reps: Int,
    val totalLoadLbs: Double,
    val loadPerSessionLbs: Double,
    val days: List<MonthDay>,
    val regions: List<RegionShare>,
    val recap: List<RecapEntry>,
    val heaviestDay: HeaviestDay?,
    val slotRuns: List<SlotRun>,
    val unassignedSessions: Int,
    /** Every lift run this month, most-run first. The mix reads the top few when [slotRuns] is empty. */
    val mostRunLifts: List<LiftRun> = emptyList(),
    /** Sessions per weekday, seven entries, index 0 is Sunday. */
    val rhythm: List<Int> = List(7) { 0 },
    /** The month by muscle, at logging resolution. Most sets first. */
    val muscles: List<MuscleShare> = emptyList(),
)
