package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClipboardModelsTest {
    @Test
    fun last_done_comes_from_checked_sets_only_and_warm_ups_count() {
        val lifts = buildClipboardLifts(
            exercises = listOf(Exercise(1, "Bench Press", "CHEST"), Exercise(2, "Dip", "CHEST")),
            sets = listOf(
                set(1, "2026-09-20", completed = true),
                set(1, "2026-09-22", completed = false),
                set(2, "2026-09-17", completed = true, warmup = true),
            ),
            canonicalId = { it },
        )

        assertEquals("2026-09-20", lifts.single { it.name == "Bench Press" }.lastCheckedDate)
        assertEquals("2026-09-17", lifts.single { it.name == "Dip" }.lastCheckedDate)
    }

    @Test
    fun merged_exercises_count_under_their_keeper() {
        val lifts = buildClipboardLifts(
            exercises = listOf(Exercise(1, "Lat Pulldown", "BACK · BICEPS")),
            sets = listOf(set(9, "2026-09-15", completed = true)),
            canonicalId = { id -> if (id == 9L) 1L else id },
        )

        val lift = lifts.single()
        assertEquals("2026-09-15", lift.lastCheckedDate)
        assertEquals("BACK", lift.primaryGroup)
    }

    @Test
    fun newest_first_then_a_to_z_with_never_logged_last() {
        val lifts = buildClipboardLifts(
            exercises = listOf(
                Exercise(1, "Squat", "QUADS"),
                Exercise(2, "Leg Extension", "QUADS"),
                Exercise(3, "Hack Squat", "QUADS"),
                Exercise(4, "Deadlift", "HAMSTRINGS"),
            ),
            sets = listOf(
                set(1, "2026-08-10", completed = true),
                set(2, "2026-09-18", completed = true),
                set(3, "2026-09-18", completed = true),
            ),
            canonicalId = { it },
        )

        assertEquals(listOf("Hack Squat", "Leg Extension", "Squat", "Deadlift"), lifts.map { it.name })
        assertNull(lifts.last().lastCheckedDate)
    }

    private fun set(exerciseId: Long, date: String, completed: Boolean, warmup: Boolean = false) = WorkoutSet(
        id = exerciseId * 1000 + date.takeLast(2).toLong(),
        exerciseId = exerciseId,
        date = date,
        setNumber = 1,
        weightLbs = 1000,
        reps = 8,
        isBodyweight = false,
        completed = completed,
        isWarmup = warmup,
    )
}
