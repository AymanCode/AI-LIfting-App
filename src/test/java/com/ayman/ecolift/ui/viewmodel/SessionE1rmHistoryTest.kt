package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `sessionE1rmHistory` normally drops bodyweight sets (there's no external load to Epley), but
 * once a bodyweight is on file it counts bodyweight + added weight as the total load, same rule
 * `rotationTodayE1rm` uses for today's live point on the card.
 */
class SessionE1rmHistoryTest {
    @Test
    fun bodyweight_set_counts_bodyweight_plus_added_weight_when_bodyweight_is_known() {
        val set = pullUp(reps = 11, addedWeightStored = 50) // "BW + 5" x 11

        val history = sessionE1rmHistory(listOf(set), userBodyweightLbs = 162)

        assertEquals(1, history.size)
        assertEquals("2026-09-10", history.single().date)
        val expected = 167f * (1f + 11f / 30f) // (162 bodyweight + 5 added) Epley'd
        assertEquals(expected, history.single().e1rm)
    }

    @Test
    fun bodyweight_set_is_skipped_without_a_bodyweight_on_file() {
        val set = pullUp(reps = 11, addedWeightStored = 50)

        assertTrue(sessionE1rmHistory(listOf(set), userBodyweightLbs = null).isEmpty())
    }

    @Test
    fun non_bodyweight_result_is_unchanged_by_the_bodyweight_param() {
        val set = WorkoutSet(
            id = 1L,
            exerciseId = 1L,
            date = "2026-09-10",
            setNumber = 1,
            weightLbs = 1_850, // 185.0 lb, stored in tenths
            reps = 8,
            isBodyweight = false,
            completed = true,
        )

        val withoutBodyweight = sessionE1rmHistory(listOf(set), userBodyweightLbs = null)
        val withBodyweight = sessionE1rmHistory(listOf(set), userBodyweightLbs = 162)

        assertEquals(withoutBodyweight, withBodyweight)
        val expected = 185f * (1f + 8f / 30f)
        assertEquals(expected, withoutBodyweight.single().e1rm)
    }

    private fun pullUp(reps: Int, addedWeightStored: Int) = WorkoutSet(
        id = 1L,
        exerciseId = 1L,
        date = "2026-09-10",
        setNumber = 1,
        weightLbs = addedWeightStored,
        reps = reps,
        isBodyweight = true,
        completed = true,
    )
}
