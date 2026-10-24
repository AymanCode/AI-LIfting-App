package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.hasCycleSlotAssignment
import com.ayman.ecolift.web.util.minusDays
import kotlinx.datetime.LocalDate

internal data class DayExercises(
    val date: String,
    val exerciseIds: Set<Long>,
)

internal const val SMART_SPLIT_LOOKBACK_DAYS = 56
internal const val SMART_SPLIT_MIN_DAY_EXERCISES = 3

/**
 * The one rule for which days may suggest a split, shared by IronMind and the Splits tab: checked
 * sets only, the last eight weeks, never today (a workout in progress is not evidence yet), and
 * never a day that already belongs to a saved split.
 */
internal fun smartSplitDays(
    sets: List<WorkoutSet>,
    workoutDays: List<WorkoutDay>,
    today: String,
    lookbackDays: Int = SMART_SPLIT_LOOKBACK_DAYS,
): List<DayExercises> {
    val parsedToday = runCatching { LocalDate.parse(today) }.getOrNull() ?: return emptyList()
    val startDate = parsedToday.minusDays(lookbackDays.toLong()).toString()
    val assignedDates = workoutDays.asSequence()
        .filter { it.hasCycleSlotAssignment() }
        .mapTo(hashSetOf()) { it.date }
    return sets.asSequence()
        .filter { it.completed }
        .filter { it.date >= startDate && it.date < today }
        .filterNot { it.date in assignedDates }
        .groupBy { it.date }
        .mapNotNull { (date, daySets) ->
            val exerciseIds = daySets.map { it.exerciseId }.toSet()
            if (exerciseIds.size >= SMART_SPLIT_MIN_DAY_EXERCISES) DayExercises(date, exerciseIds) else null
        }
        .sortedBy { it.date }
}
