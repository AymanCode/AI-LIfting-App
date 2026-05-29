package com.ayman.ecolift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SetRepositoryTest {

    @Test
    fun `buildLastSessionSetCopies clones every set from the latest previous session`() {
        val copies = buildLastSessionSetCopies(
            date = "2026-05-20",
            exerciseId = 7L,
            historyBeforeDate = listOf(
                workoutSet(id = 1L, date = "2026-05-01", setNumber = 1, weight = 100, reps = 10),
                workoutSet(id = 2L, date = "2026-05-15", setNumber = 2, weight = 125, reps = 8, completed = true, restSeconds = 120),
                workoutSet(id = 3L, date = "2026-05-15", setNumber = 1, weight = 135, reps = 6, completed = true, restSeconds = 90),
                workoutSet(id = 4L, exerciseId = 9L, date = "2026-05-16", setNumber = 1, weight = 200, reps = 5),
            ),
        )

        assertEquals(2, copies.size)
        assertEquals(listOf(1, 2), copies.map { it.setNumber })
        assertEquals(listOf(135, 125).map(WeightLbs::fromWholePounds), copies.map { it.weightLbs })
        assertEquals(listOf(6, 8), copies.map { it.reps })
        copies.forEach { copy ->
            assertEquals(0L, copy.id)
            assertEquals(7L, copy.exerciseId)
            assertEquals("2026-05-20", copy.date)
            assertFalse(copy.completed)
            assertNull(copy.restTimeSeconds)
        }
    }

    @Test
    fun `buildLastSessionSetCopies returns empty when exercise has no previous session`() {
        val copies = buildLastSessionSetCopies(
            date = "2026-05-20",
            exerciseId = 7L,
            historyBeforeDate = listOf(
                workoutSet(id = 4L, exerciseId = 9L, date = "2026-05-16", setNumber = 1, weight = 200, reps = 5),
            ),
        )

        assertEquals(emptyList<WorkoutSet>(), copies)
    }

    private fun workoutSet(
        id: Long,
        exerciseId: Long = 7L,
        date: String,
        setNumber: Int,
        weight: Int,
        reps: Int,
        completed: Boolean = false,
        restSeconds: Int? = null,
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        date = date,
        setNumber = setNumber,
        weightLbs = WeightLbs.fromWholePounds(weight),
        reps = reps,
        isBodyweight = false,
        completed = completed,
        restTimeSeconds = restSeconds,
    )
}
