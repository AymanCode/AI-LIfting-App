package com.ayman.ecolift.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// WorkoutDay and DayQueries are in the same package — no import needed.

class LogicTest {

    private fun set(
        id: Long,
        exerciseId: Long = 1L,
        date: String,
        setNumber: Int,
        weightLbs: Int? = 1850,
        reps: Int? = 8,
        isBodyweight: Boolean = false,
        completed: Boolean = false,
    ) = WorkoutSet(id, exerciseId, date, setNumber, weightLbs, reps, isBodyweight, completed, null)

    // --- FuzzyMatcher ---

    @Test
    fun levenshtein_basics() {
        assertEquals(0, FuzzyMatcher.levenshteinDistance("squat", "squat"))
        assertEquals(1, FuzzyMatcher.levenshteinDistance("squat", "squats"))
        assertEquals(2, FuzzyMatcher.levenshteinDistance("bench", "brunch"))
        assertEquals(5, FuzzyMatcher.levenshteinDistance("", "squat"))
    }

    // --- ExerciseNames ---

    @Test
    fun normalize_title_cases_and_collapses_spaces() {
        assertEquals("Bench Press", ExerciseNames.normalize("  bench   PRESS "))
        assertEquals("Lat Pulldown", ExerciseNames.normalize("lat pulldown"))
    }

    // --- Bodyweight ---

    @Test
    fun bodyweight_name_detection() {
        assertTrue(looksLikeBodyweightExerciseName("Weighted Pull Up"))
        assertTrue(looksLikeBodyweightExerciseName("dips"))
        assertFalse(looksLikeBodyweightExerciseName("Dip Machine"))
        assertFalse(looksLikeBodyweightExerciseName("Lat Pulldown"))
        assertFalse(looksLikeBodyweightExerciseName("Bench Press"))
    }

    @Test
    fun bodyweight_load_normalization() {
        assertEquals(250, normalizedBodyweightLoad(250))
        assertNull(normalizedBodyweightLoad(0))
        assertNull(normalizedBodyweightLoad(-10))
        assertNull(normalizedBodyweightLoad(null))
    }

    // --- SetQueries ---

    @Test
    fun lastCompletedSession_picks_latest_completed_date_only() {
        val sets = listOf(
            set(1, date = "2026-06-01", setNumber = 1, completed = true),
            set(2, date = "2026-06-01", setNumber = 2, completed = true),
            set(3, date = "2026-06-05", setNumber = 1, completed = false), // parked, must not win
            set(4, date = "2026-06-03", setNumber = 1, completed = true),
        )
        val session = SetQueries.lastCompletedSession(sets, exerciseId = 1L, beforeDate = "2026-06-09")
        assertEquals(listOf(4L), session.map { it.id })
    }

    @Test
    fun lastCompletedSession_excludes_target_date_and_other_exercises() {
        val sets = listOf(
            set(1, date = "2026-06-09", setNumber = 1, completed = true),       // same day: excluded
            set(2, exerciseId = 2L, date = "2026-06-01", setNumber = 1, completed = true), // other exercise
        )
        assertTrue(SetQueries.lastCompletedSession(sets, 1L, "2026-06-09").isEmpty())
    }

    @Test
    fun buildLastSessionSetCopies_renumbers_and_resets_completion() {
        val history = listOf(
            set(10, date = "2026-06-01", setNumber = 2, weightLbs = 1900, reps = 6, completed = true),
            set(11, date = "2026-06-01", setNumber = 1, weightLbs = 1850, reps = 8, completed = true),
            set(12, date = "2026-06-03", setNumber = 1, weightLbs = 0, reps = 12, isBodyweight = true, completed = true),
        )
        val copies = SetQueries.buildLastSessionSetCopies("2026-06-09", 1L, history)
        assertEquals(1, copies.size)
        assertEquals(1, copies[0].setNumber)
        assertNull(copies[0].weightLbs) // bodyweight load 0 normalizes to null
        assertEquals(12, copies[0].reps)
        assertFalse(copies[0].completed)
        assertEquals(0L, copies[0].id)
    }

    @Test
    fun maxCompletedWeightBefore_ignores_bodyweight_and_incomplete() {
        val sets = listOf(
            set(1, date = "2026-06-01", setNumber = 1, weightLbs = 2000, completed = true),
            set(2, date = "2026-06-02", setNumber = 1, weightLbs = 9999, completed = false),
            set(3, date = "2026-06-03", setNumber = 1, weightLbs = 500, isBodyweight = true, completed = true),
        )
        assertEquals(2000, SetQueries.maxCompletedWeightBefore(sets, 1L, "2026-06-09"))
    }

    @Test
    fun completedHistoryBeforeDate_ignores_unchecked_prior_sets() {
        val sets = listOf(
            set(1, date = "2026-06-01", setNumber = 1, completed = true),
            set(2, date = "2026-06-07", setNumber = 1, completed = false),
            set(3, exerciseId = 2L, date = "2026-06-08", setNumber = 1, completed = true),
            set(4, date = "2026-06-03", setNumber = 1, completed = true),
        )

        val history = SetQueries.completedHistoryBeforeDate(sets, 1L, "2026-06-09")

        assertEquals(listOf(4L, 1L), history.map { it.id })
    }

    @Test
    fun forDateAndExercise_sorted_by_set_number() {
        val sets = listOf(
            set(1, date = "2026-06-09", setNumber = 2),
            set(2, date = "2026-06-09", setNumber = 1),
            set(3, date = "2026-06-08", setNumber = 1),
        )
        assertEquals(listOf(2L, 1L), SetQueries.forDateAndExercise(sets, "2026-06-09", 1L).map { it.id })
    }

    // --- ExerciseSearch ---

    @Test
    fun search_ranks_by_usage_then_name() {
        val exercises = listOf(
            Exercise(id = 1, name = "Bench Press"),
            Exercise(id = 2, name = "Incline Bench Press"),
            Exercise(id = 3, name = "Squat"),
        )
        val usage = mapOf(2L to 5, 1L to 1)
        val results = ExerciseSearch.results(exercises, usage, "bench")
        assertEquals(listOf(2L, 1L), results.map { it.id })
    }

    @Test
    fun search_fuzzy_catches_typos() {
        val exercises = listOf(Exercise(id = 1, name = "Squat"))
        val results = ExerciseSearch.results(exercises, emptyMap(), "sqaut")
        assertEquals(listOf(1L), results.map { it.id })
    }

    @Test
    fun search_blank_query_is_empty() {
        assertTrue(ExerciseSearch.results(listOf(Exercise(id = 1, name = "Squat")), emptyMap(), "  ").isEmpty())
    }

    @Test
    fun completedSetsInRange_is_inclusive_and_completed_only() {
        val sets = listOf(
            set(1, date = "2026-06-01", setNumber = 1, completed = true),
            set(2, date = "2026-06-05", setNumber = 1, completed = false),
            set(3, date = "2026-06-08", setNumber = 1, completed = true),
            set(4, date = "2026-06-09", setNumber = 1, completed = true),
        )
        assertEquals(listOf(1L, 3L), SetQueries.completedSetsInRange(sets, "2026-06-01", "2026-06-08").map { it.id })
    }

    @Test
    fun completed_date_bounds() {
        val sets = listOf(
            set(1, date = "2026-06-05", setNumber = 1, completed = true),
            set(2, date = "2026-06-01", setNumber = 1, completed = false),
            set(3, date = "2026-06-09", setNumber = 1, completed = true),
        )
        assertEquals("2026-06-05", SetQueries.earliestCompletedDate(sets))
        assertEquals("2026-06-09", SetQueries.latestCompletedDate(sets))
        assertEquals(null, SetQueries.earliestCompletedDate(emptyList()))
    }

    @Test
    fun day_queries_for_slot_occurrences() {
        val days = listOf(
            WorkoutDay(date = "2026-06-01", cycleSlotId = 5, cycleSlotOccurrence = 1),
            WorkoutDay(date = "2026-06-08", cycleSlotId = 5, cycleSlotOccurrence = 2),
            WorkoutDay(date = "2026-06-04", cycleSlotOccurrence = 1, cycleSlotType = 1),
        )
        assertEquals(2, DayQueries.maxOccurrenceForSlotBefore(days, "2026-06-10", 5))
        assertEquals("2026-06-01", DayQueries.previousOccurrenceForSlot(days, 5, 1, "2026-06-10")?.date)
        assertEquals("2026-06-08", DayQueries.latestForSlotIdBefore(days, "2026-06-10", 5)?.date)
        assertEquals("2026-06-04", DayQueries.latestLegacyForSlotTypeBefore(days, "2026-06-10", 1)?.date)
    }

    @Test
    fun occurrence_queries_do_not_cross_the_active_cycle_start() {
        val days = listOf(
            WorkoutDay(date = "2026-06-01", cycleSlotId = 5, cycleSlotOccurrence = 7),
            WorkoutDay(date = "2026-07-01", cycleSlotId = 5, cycleSlotOccurrence = 1),
        )

        assertEquals(
            1,
            DayQueries.maxOccurrenceForSlotBefore(
                days = days,
                date = "2026-07-08",
                slotId = 5,
                notBeforeDate = "2026-07-01",
            ),
        )
        assertNull(
            DayQueries.previousOccurrenceForSlot(
                days = days,
                slotId = 5,
                occurrence = 7,
                beforeDate = "2026-07-08",
                notBeforeDate = "2026-07-01",
            ),
        )
    }
}
