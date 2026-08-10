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

    @Test
    fun `buildLastSessionSetCopies seeds from the latest completed session, skipping later uncompleted ones`() {
        val copies = buildLastSessionSetCopies(
            date = "2026-05-20",
            exerciseId = 7L,
            historyBeforeDate = listOf(
                // Older session, fully checked off -> this is the real reference.
                workoutSet(id = 1L, date = "2026-05-10", setNumber = 1, weight = 135, reps = 5, completed = true),
                workoutSet(id = 2L, date = "2026-05-10", setNumber = 2, weight = 145, reps = 3, completed = true),
                // Newer session that was logged but never checked (parked / abandoned) -> must be ignored.
                workoutSet(id = 3L, date = "2026-05-18", setNumber = 1, weight = 95, reps = 12, completed = false),
            ),
        )

        assertEquals(2, copies.size)
        assertEquals(listOf(1, 2), copies.map { it.setNumber })
        assertEquals(listOf(135, 145).map(WeightLbs::fromWholePounds), copies.map { it.weightLbs })
        assertEquals(listOf(5, 3), copies.map { it.reps })
    }

    @Test
    fun `buildLastSessionSetCopies returns empty when no previous session was completed`() {
        val copies = buildLastSessionSetCopies(
            date = "2026-05-20",
            exerciseId = 7L,
            historyBeforeDate = listOf(
                workoutSet(id = 1L, date = "2026-05-10", setNumber = 1, weight = 135, reps = 5, completed = false),
                workoutSet(id = 2L, date = "2026-05-18", setNumber = 1, weight = 95, reps = 12, completed = false),
            ),
        )

        assertEquals(emptyList<WorkoutSet>(), copies)
    }

    // ── Fix A: nextSetNumber ──────────────────────────────────────────────────

    @Test
    fun `nextSetNumber returns max plus 1 for a gapped list`() {
        val sets = listOf(
            workoutSet(id = 1L, date = "2026-06-01", setNumber = 1, weight = 100, reps = 5),
            workoutSet(id = 2L, date = "2026-06-01", setNumber = 3, weight = 110, reps = 5),
        )
        assertEquals(4, nextSetNumber(sets))
    }

    @Test
    fun `nextSetNumber returns 1 for empty list`() {
        assertEquals(1, nextSetNumber(emptyList()))
    }

    // ── Fix A: renumberSequentially ──────────────────────────────────────────

    @Test
    fun `renumberSequentially compacts a gapped list`() {
        val sets = listOf(
            workoutSet(id = 1L, date = "2026-06-01", setNumber = 1, weight = 100, reps = 5),
            workoutSet(id = 2L, date = "2026-06-01", setNumber = 3, weight = 110, reps = 5),
        )
        val result = renumberSequentially(sets)
        assertEquals(listOf(1, 2), result.map { it.setNumber })
    }

    @Test
    fun `renumberSequentially deduplicates tied setNumbers using id as tiebreak`() {
        val sets = listOf(
            workoutSet(id = 1L, date = "2026-06-01", setNumber = 1, weight = 100, reps = 5),
            workoutSet(id = 2L, date = "2026-06-01", setNumber = 3, weight = 110, reps = 5),
            workoutSet(id = 3L, date = "2026-06-01", setNumber = 3, weight = 120, reps = 5),
        )
        val result = renumberSequentially(sets)
        assertEquals(listOf(1, 2, 3), result.map { it.setNumber })
    }

    @Test
    fun `renumberSequentially is a no-op for already contiguous lists`() {
        val sets = listOf(
            workoutSet(id = 1L, date = "2026-06-01", setNumber = 1, weight = 100, reps = 5),
            workoutSet(id = 2L, date = "2026-06-01", setNumber = 2, weight = 110, reps = 5),
            workoutSet(id = 3L, date = "2026-06-01", setNumber = 3, weight = 120, reps = 5),
        )
        val result = renumberSequentially(sets)
        assertEquals(listOf(1, 2, 3), result.map { it.setNumber })
        // ids also preserved in original order
        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
    }

    @Test
    fun `renumberSequentially preserves order by setNumber then id`() {
        // id=5 has lower setNumber than id=4, so must come first after renumber
        val sets = listOf(
            workoutSet(id = 4L, date = "2026-06-01", setNumber = 3, weight = 120, reps = 5),
            workoutSet(id = 5L, date = "2026-06-01", setNumber = 2, weight = 110, reps = 5),
        )
        val result = renumberSequentially(sets)
        assertEquals(listOf(5L, 4L), result.map { it.id })
        assertEquals(listOf(1, 2), result.map { it.setNumber })
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
