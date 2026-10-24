package com.ayman.ecolift.data

// In-memory equivalents of the Android WorkoutSetDao queries. The whole user
// dataset lives in the SetRepository cache, so these run on plain lists.
object SetQueries {

    /**
     * Creation order for a day's rows.
     *
     * `sortedBy { it.id }` was standing in for this, and it is right only while every row has a
     * server id. Optimistic rows carry temp ids that count *downwards* (-1, -2, -3), so an
     * ascending sort hands them back newest-first: add four sets offline and they render 4, 3, 2,
     * 1, and a day holding both synced and unsynced rows scatters the new ones in front of the old
     * ones. Sets have to read in the order they were made no matter what, so synced rows come
     * first in ascending id and pending rows follow in the order they were minted.
     */
    private val creationOrder: Comparator<WorkoutSet> =
        compareBy({ if (it.id >= 0L) 0 else 1 }, { if (it.id >= 0L) it.id else -it.id })

    fun forDate(sets: List<WorkoutSet>, date: String): List<WorkoutSet> =
        sets.filter { it.date == date }.sortedWith(creationOrder)

    fun forDateAndExercise(sets: List<WorkoutSet>, date: String, exerciseId: Long): List<WorkoutSet> =
        sets.filter { it.date == date && it.exerciseId == exerciseId }.sortedBy { it.setNumber }

    /** Next set_number for an exercise on a date: max existing + 1 (NOT size + 1,
     *  which collides with a surviving higher number after a middle set is deleted —
     *  e.g. survivors [1,3] must yield 4, not 3). [currentSets] is that exercise+date slice. */
    fun nextSetNumber(currentSets: List<WorkoutSet>): Int =
        (currentSets.maxOfOrNull { it.setNumber } ?: 0) + 1

    /**
     * Reps a set should open with when it is appended at [position] (1-based, counting the rows
     * already on the card).
     *
     * The rule, in Ayman's words: the new set keeps the previous set's reps, "UNLESS the previous
     * time a user has done the exercise and sets have recorded differences. If that is the case
     * you apply the same differences."
     *
     * Jan 1 was 8, 5, 2. On Jan 8 set 1 gets pushed to 7 reps, so set 2 opens at 7 + (5 - 8) = 4,
     * not at 7 and not at last week's 5. What carries over is the *shape* of the drop-off, applied
     * to what you actually did today.
     *
     * [lastSessionReps] is the previous session's reps in set order. Both it and [position] are
     * read by ordinal position rather than by stored set number, so a set deleted from the middle
     * of either day shifts the mapping the same way the card's own numbering shifts. With no
     * usable pair of history slots there is no difference to apply, and the previous set's reps
     * stand.
     */
    fun predictedRepsForNewSet(
        previousReps: Int?,
        lastSessionReps: List<Int?>,
        position: Int,
    ): Int? {
        val previous = previousReps ?: return null
        if (position < 2) return previous
        val here = lastSessionReps.getOrNull(position - 1)?.takeIf { it > 0 } ?: return previous
        val before = lastSessionReps.getOrNull(position - 2)?.takeIf { it > 0 } ?: return previous
        // A delta steep enough to push the prediction to zero or below is not usable: history
        // 8 -> 2 applied to a first set of 5 would ask for -1. Clamping that to 1 would quietly
        // turn an impossible number into a confident prescription for a max-effort single, so
        // treat the delta as unusable and let the previous set's reps stand.
        val predicted = previous + (here - before)
        return if (predicted < 1) previous else predicted
    }

    fun historyBeforeDate(sets: List<WorkoutSet>, exerciseId: Long, beforeDate: String): List<WorkoutSet> =
        sets.filter { it.exerciseId == exerciseId && it.date < beforeDate }
            .sortedWith(compareByDescending<WorkoutSet> { it.date }.thenBy { it.setNumber })

    fun completedHistoryBeforeDate(sets: List<WorkoutSet>, exerciseId: Long, beforeDate: String): List<WorkoutSet> =
        historyBeforeDate(sets, exerciseId, beforeDate).filter { it.completed }

    fun mostRecentBeforeDate(sets: List<WorkoutSet>, exerciseId: Long, beforeDate: String): WorkoutSet? =
        sets.filter { it.exerciseId == exerciseId && it.date < beforeDate }
            .maxWithOrNull(compareBy({ it.date }, { it.setNumber }))

    fun lastCompletedSession(sets: List<WorkoutSet>, exerciseId: Long, beforeDate: String): List<WorkoutSet> {
        val completed = sets.filter { it.exerciseId == exerciseId && it.date < beforeDate && it.completed }
        val lastDate = completed.maxOfOrNull { it.date } ?: return emptyList()
        return completed.filter { it.date == lastDate }.sortedBy { it.setNumber }
    }

    fun maxCompletedWeightBefore(sets: List<WorkoutSet>, exerciseId: Long, beforeDate: String): Int? =
        sets.filter { it.exerciseId == exerciseId && it.date < beforeDate && it.isCheckedWorking && !it.isBodyweight }
            .mapNotNull { it.weightLbs }
            .maxOrNull()

    fun usageCounts(sets: List<WorkoutSet>): Map<Long, Int> =
        sets.filter { it.completed }.groupingBy { it.exerciseId }.eachCount()

    fun workedDates(sets: List<WorkoutSet>): Set<String> =
        sets.filter { it.completed }.map { it.date }.toSet()

    fun completedSetsInRange(sets: List<WorkoutSet>, start: String, end: String): List<WorkoutSet> =
        sets.filter { it.completed && it.date >= start && it.date <= end }.sortedWith(creationOrder)

    fun earliestCompletedDate(sets: List<WorkoutSet>): String? =
        sets.filter { it.completed }.minOfOrNull { it.date }

    fun latestCompletedDate(sets: List<WorkoutSet>): String? =
        sets.filter { it.completed }.maxOfOrNull { it.date }

    // Verbatim port of the Android SetRepository.buildLastSessionSetCopies:
    // the "reference" for a re-added exercise is the last session the user
    // actually checked off. Unchecked sessions must not seed the new sets.
    fun buildLastSessionSetCopies(
        date: String,
        exerciseId: Long,
        historyBeforeDate: List<WorkoutSet>,
    ): List<WorkoutSet> {
        val completedHistory = historyBeforeDate
            .filter { it.exerciseId == exerciseId && it.date < date && it.completed }
        val lastDate = completedHistory
            .maxOfOrNull(WorkoutSet::date)
            ?: return emptyList()

        return completedHistory
            .filter { it.date == lastDate }
            .sortedBy(WorkoutSet::setNumber)
            .mapIndexed { index, template ->
                WorkoutSet(
                    exerciseId = exerciseId,
                    date = date,
                    setNumber = index + 1,
                    weightLbs = if (template.isBodyweight) normalizedBodyweightLoad(template.weightLbs) else template.weightLbs,
                    reps = template.reps,
                    isBodyweight = template.isBodyweight,
                    isWarmup = template.isWarmup,
                    completed = false,
                    restTimeSeconds = null,
                )
            }
    }
}

data class ExerciseVolume(val exerciseId: Long, val volume: Long)
data class ExerciseMaxWeight(val exerciseId: Long, val maxWeight: Int)

data class ExerciseProgressSummary(
    val exerciseId: Long,
    val exerciseName: String,
    val isBodyweight: Boolean,
    val sessionCount: Int,
    val lastSessionDate: String,
)

data class DateVolume(
    val date: String,
    val volume: Long,
)

object DayQueries {
    fun maxOccurrenceForSlotBefore(
        days: List<WorkoutDay>,
        date: String,
        slotId: Long,
        notBeforeDate: String? = null,
    ): Int? =
        days.filter {
            it.cycleSlotId == slotId &&
                it.date < date &&
                (notBeforeDate == null || it.date >= notBeforeDate)
        }
            .mapNotNull { it.cycleSlotOccurrence }
            .maxOrNull()

    fun previousOccurrenceForSlot(
        days: List<WorkoutDay>,
        slotId: Long,
        occurrence: Int,
        beforeDate: String,
        notBeforeDate: String? = null,
    ): WorkoutDay? =
        days.filter {
            it.cycleSlotId == slotId &&
                it.cycleSlotOccurrence == occurrence &&
                it.date < beforeDate &&
                (notBeforeDate == null || it.date >= notBeforeDate)
        }
            .maxByOrNull { it.date }

    fun latestForSlotIdBefore(
        days: List<WorkoutDay>,
        date: String,
        slotId: Long,
        notBeforeDate: String? = null,
    ): WorkoutDay? =
        days.filter {
            it.cycleSlotId == slotId &&
                it.date < date &&
                (notBeforeDate == null || it.date >= notBeforeDate)
        }.maxByOrNull { it.date }

    fun latestLegacyForSlotTypeBefore(
        days: List<WorkoutDay>,
        date: String,
        slotType: Int,
        notBeforeDate: String? = null,
    ): WorkoutDay? =
        days.filter {
            it.cycleSlotId == null &&
                it.cycleSlotType == slotType &&
                it.date < date &&
                (notBeforeDate == null || it.date >= notBeforeDate)
        }.maxByOrNull { it.date }
}
