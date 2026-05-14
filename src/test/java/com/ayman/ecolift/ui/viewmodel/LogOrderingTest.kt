package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

class LogOrderingTest {

    @Test
    fun `adding a set to an existing exercise keeps loaded split order stable`() {
        val sets = listOf(
            workoutSet(id = 1L, exerciseId = 10L, setNumber = 1),
            workoutSet(id = 2L, exerciseId = 20L, setNumber = 1),
            workoutSet(id = 9L, exerciseId = 20L, setNumber = 2),
        )

        val orderedIds = orderLogExerciseGroups(sets.groupBy(WorkoutSet::exerciseId).entries)
            .map { it.key }

        assertEquals(listOf(10L, 20L), orderedIds)
    }

    @Test
    fun `newly added exercises appear after existing logged exercises`() {
        val sets = listOf(
            workoutSet(id = 1L, exerciseId = 10L, setNumber = 1),
            workoutSet(id = 2L, exerciseId = 20L, setNumber = 1),
            workoutSet(id = 9L, exerciseId = 30L, setNumber = 1),
        )

        val orderedIds = orderLogExerciseGroups(sets.groupBy(WorkoutSet::exerciseId).entries)
            .map { it.key }

        assertEquals(listOf(10L, 20L, 30L), orderedIds)
    }

    private fun workoutSet(
        id: Long,
        exerciseId: Long,
        setNumber: Int,
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        date = "2026-05-12",
        setNumber = setNumber,
        weightLbs = 1000,
        reps = 5,
        isBodyweight = false,
        completed = false,
        restTimeSeconds = null,
    )
}
