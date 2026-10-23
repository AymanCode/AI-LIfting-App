package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitSeedBuilderTest {

    private fun set(
        id: Long, exerciseId: Long, date: String, setNumber: Int,
        weightLbs: Int? = null, reps: Int? = null,
        isBodyweight: Boolean = false, completed: Boolean = false,
        restTimeSeconds: Int? = null,
    ) = WorkoutSet(id, exerciseId, date, setNumber, weightLbs, reps, isBodyweight, completed, restTimeSeconds)

    @Test
    fun explicit_template_copies_latest_completed_prior_date_per_exercise() {
        val history = listOf(
            set(1, 10, "2026-08-01", 1, 135, 8, completed = true),
            set(2, 10, "2026-08-01", 2, 135, 7, completed = true),
            set(3, 10, "2026-07-25", 1, 125, 8, completed = true),
        )
        val seed = SplitSeedBuilder.forExplicitExercises(
            date = "2026-08-10",
            orderedExerciseIds = listOf(10L),
            historyBeforeDate = history,
        )
        assertEquals(listOf(1, 2), seed.map { it.setNumber })
        assertEquals(listOf(135, 135), seed.map { it.weightLbs })
        assertEquals(listOf(8, 7), seed.map { it.reps })
        assertTrue(seed.none { it.completed })
        assertTrue(seed.all { it.restTimeSeconds == null })
        assertTrue(seed.all { it.date == "2026-08-10" })
    }

    @Test
    fun explicit_template_ignores_incomplete_history() {
        val history = listOf(set(1, 10, "2026-08-01", 1, 135, 8, completed = false))
        val seed = SplitSeedBuilder.forExplicitExercises("2026-08-10", listOf(10L), history)
        assertEquals(1, seed.size)
        assertEquals(1, seed.single().setNumber)
        assertEquals(null, seed.single().weightLbs)
        assertEquals(null, seed.single().reps)
    }

    @Test
    fun explicit_template_blank_fallback_is_not_bodyweight() {
        val seed = SplitSeedBuilder.forExplicitExercises("2026-08-10", listOf(10L), emptyList())
        assertEquals(false, seed.single().isBodyweight)
    }

    @Test
    fun explicit_template_renumbers_from_one_over_gapped_source() {
        val history = listOf(
            set(1, 10, "2026-08-01", 3, 135, 8, completed = true),
            set(2, 10, "2026-08-01", 7, 135, 7, completed = true),
        )
        val seed = SplitSeedBuilder.forExplicitExercises("2026-08-10", listOf(10L), history)
        assertEquals(listOf(1, 2), seed.map { it.setNumber })
    }

    @Test
    fun explicit_template_normalizes_non_positive_bodyweight_load_to_null() {
        val history = listOf(set(1, 10, "2026-08-01", 1, 0, 8, isBodyweight = true, completed = true))
        val seed = SplitSeedBuilder.forExplicitExercises("2026-08-10", listOf(10L), history)
        assertEquals(null, seed.single().weightLbs)
        assertEquals(true, seed.single().isBodyweight)
    }

    @Test
    fun prior_occurrence_clone_preserves_source_set_numbers_and_rest() {
        val source = listOf(
            set(1, 10, "2026-08-01", 2, 135, 8, completed = true, restTimeSeconds = 90),
        )
        val seed = SplitSeedBuilder.forPriorOccurrence(date = "2026-08-10", sourceRows = source)
        assertEquals(2, seed.single().setNumber)
        assertEquals(90, seed.single().restTimeSeconds)
        assertEquals(false, seed.single().completed)
        assertEquals("2026-08-10", seed.single().date)
    }

    @Test
    fun prior_occurrence_clone_does_not_skip_exercises_already_on_target() {
        val source = listOf(
            set(1, 10, "2026-08-01", 1, 135, 8, completed = true),
            set(2, 11, "2026-08-01", 1, 95, 10, completed = true),
        )
        val seed = SplitSeedBuilder.forPriorOccurrence("2026-08-10", source)
        assertEquals(setOf(10L, 11L), seed.map { it.exerciseId }.toSet())
    }

    @Test
    fun unknown_baseline_is_empty_so_any_existing_row_is_dirty() {
        assertEquals(emptyList(), SplitSeedBuilder.unknownBaseline())
    }
}
