package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.SetQueries
import com.ayman.ecolift.data.WorkoutSet

object SplitSeedBuilder {
    /** Each lift's whole last session; a lift never trained gets one empty set (a bodyweight set
     *  when it is in [bodyweightIds]). */
    fun forExplicitExercises(
        date: String,
        orderedExerciseIds: List<Long>,
        historyBeforeDate: List<WorkoutSet>,
        bodyweightIds: Set<Long> = emptySet(),
    ): List<WorkoutSet> = orderedExerciseIds.distinct().flatMap { exerciseId ->
        SetQueries.buildLastSessionSetCopies(
            date = date,
            exerciseId = exerciseId,
            historyBeforeDate = historyBeforeDate,
        ).ifEmpty {
            listOf(
                WorkoutSet(
                    exerciseId = exerciseId,
                    date = date,
                    setNumber = 1,
                    isBodyweight = exerciseId in bodyweightIds,
                    completed = false,
                ),
            )
        }
    }

    fun forPriorOccurrence(
        date: String,
        sourceRows: List<WorkoutSet>,
    ): List<WorkoutSet> = sourceRows.map { source ->
        source.copy(
            id = 0,
            date = date,
            completed = false,
        )
    }

    fun unknownBaseline(): List<WorkoutSet> = emptyList()
}
