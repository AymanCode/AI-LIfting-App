package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutMetrics
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.progress.MonthRollup
import com.ayman.ecolift.data.progress.MuscleSlice
import kotlinx.datetime.LocalDate
import kotlin.math.ceil

/** How much a trained day moved against every other trained day: sticker size on the calendar. */
enum class DayTier { LIGHT, REGULAR, HEAVY }

/**
 * One weight PR logged on a day. Weights are stored units (tenths of a pound), like [WorkoutSet].
 * [heavyLift] is the calendar's star: Ayman's rule that a PR on one of his heaviest lifts
 * (a bench) earns it and a PR on a light lift (a curl) does not.
 */
data class DayPr(
    val exerciseName: String,
    val weightStored: Int,
    val reps: Int,
    val previousBestStored: Int,
    val isBodyweight: Boolean,
    val heavyLift: Boolean,
)

/** What the sticker calendar's note needs beyond [DayInfo]. */
data class DayMarks(
    val setCount: Int,
    val muscles: List<MuscleSlice>,
    val prs: List<DayPr>,
) {
    val hasHeavyLiftPr: Boolean get() = prs.any { it.heavyLift }
}

/** The mirror of [volumeBrightThreshold]: days at or under the bottom quarter are light. */
internal fun volumeLightThreshold(dayVolumes: Collection<Long>): Long {
    if (dayVolumes.isEmpty()) return 0L
    val sorted = dayVolumes.sorted()
    val index = (ceil(sorted.size * 0.25).toInt() - 1).coerceIn(sorted.indices)
    return sorted[index]
}

/** Heavy wins a tie, so the calendar keeps drawing every day it drew bright before. */
internal fun dayTier(volumeLbs: Long, lightThreshold: Long, heavyThreshold: Long): DayTier = when {
    volumeLbs >= heavyThreshold -> DayTier.HEAVY
    volumeLbs <= lightThreshold -> DayTier.LIGHT
    else -> DayTier.REGULAR
}

/**
 * The lightest weight that still counts as one of the heaviest lifts: the top tenth of every
 * non-bodyweight exercise's best weight. Bodyweight lifts log added load, which can't be compared.
 */
internal fun heavyLiftMinimumStored(
    sets: List<WorkoutSet>,
    exercisesById: Map<Long, Exercise>,
): Int? {
    val bests = sets
        .filter { WorkoutMetrics.isStrengthEligible(it) && !isBodyweightSet(it, exercisesById) }
        .groupBy { it.exerciseId }
        .values
        .map { exerciseSets -> exerciseSets.maxOf { it.weightLbs ?: 0 } }
        .filter { it > 0 }
        .sorted()
    if (bests.isEmpty()) return null
    return bests[(ceil(bests.size * 0.9).toInt() - 1).coerceIn(bests.indices)]
}

/**
 * Per trained date: checked set count, the two most-trained muscles and every weight PR.
 *
 * PRs follow `MonthRollup.prDatesOf` exactly: an exercise's best weight on a date beats every
 * earlier date's best, and the exercise already has at least three earlier dates.
 */
internal fun buildDayMarks(
    sets: List<WorkoutSet>,
    exercisesById: Map<Long, Exercise>,
): Map<LocalDate, DayMarks> {
    val completed = sets.filter { it.completed }
    if (completed.isEmpty()) return emptyMap()
    val heavyMinimum = heavyLiftMinimumStored(sets, exercisesById)

    val prsByDate = mutableMapOf<String, MutableList<DayPr>>()
    sets.filter { WorkoutMetrics.isStrengthEligible(it) }
        .groupBy { it.exerciseId }
        .forEach { (exerciseId, exerciseSets) ->
            val exercise = exercisesById[exerciseId]
            val bodyweight = exercise?.isBodyweight == true || exerciseSets.any { it.isBodyweight }
            var bestSoFar = Int.MIN_VALUE
            var priorSessions = 0
            exerciseSets.groupBy { it.date }.entries.sortedBy { it.key }.forEach { (date, daySets) ->
                val dayBest = daySets.maxOf { it.weightLbs ?: 0 }
                if (priorSessions >= 3 && dayBest > bestSoFar) {
                    val topSet = daySets.filter { (it.weightLbs ?: 0) == dayBest }.maxBy { it.reps ?: 0 }
                    prsByDate.getOrPut(date) { mutableListOf() } += DayPr(
                        exerciseName = exercise?.name ?: "Exercise",
                        weightStored = dayBest,
                        reps = topSet.reps ?: 0,
                        previousBestStored = bestSoFar,
                        isBodyweight = bodyweight,
                        heavyLift = !bodyweight && heavyMinimum != null && dayBest >= heavyMinimum,
                    )
                }
                if (dayBest > bestSoFar) bestSoFar = dayBest
                priorSessions++
            }
        }

    return completed.groupBy { it.date }.mapNotNull { (dateIso, daySets) ->
        val date = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return@mapNotNull null
        val muscles = daySets
            .groupingBy { MonthRollup.sliceOf(exercisesById[it.exerciseId]) }
            .eachCount()
            .filterKeys { it != MuscleSlice.UNKNOWN }
            .entries
            .sortedWith(compareByDescending<Map.Entry<MuscleSlice, Int>> { it.value }.thenBy { it.key.name })
            .take(2)
            .map { it.key }
        date to DayMarks(
            setCount = daySets.size,
            muscles = muscles,
            prs = prsByDate[dateIso].orEmpty().sortedWith(
                compareByDescending<DayPr> { it.heavyLift }.thenByDescending { it.weightStored },
            ),
        )
    }.toMap()
}

private fun isBodyweightSet(set: WorkoutSet, exercisesById: Map<Long, Exercise>): Boolean =
    set.isBodyweight || exercisesById[set.exerciseId]?.isBodyweight == true
