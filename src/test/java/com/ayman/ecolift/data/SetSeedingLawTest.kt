package com.ayman.ecolift.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The rule Ayman called "the end all be all" of set logging: a new set keeps the previous set's
 * reps unless last session's reps moved between those same two slots, in which case it moves by
 * the same amount.
 */
class SetSeedingLawTest {

    @Test
    fun the_worked_example_from_the_brief() {
        // Jan 1: bench 135 for 8, 5, 2.
        val janFirst = listOf(8, 5, 2)

        // Jan 8: set 1 opens on last session's first set, so 8 reps.
        assertEquals(
            8,
            SetQueries.predictedRepsForNewSet(
                previousReps = 8,
                lastSessionReps = janFirst,
                position = 1,
            ),
        )

        // He pushes set 1 to 140 for 7, then adds a set. Last time reps went 8 -> 5, three lower,
        // so set 2 opens at 4 rather than at 7.
        assertEquals(
            4,
            SetQueries.predictedRepsForNewSet(
                previousReps = 7,
                lastSessionReps = janFirst,
                position = 2,
            ),
        )

        // Set 3 follows the same way: last time 5 -> 2, three lower again.
        assertEquals(
            1,
            SetQueries.predictedRepsForNewSet(
                previousReps = 4,
                lastSessionReps = janFirst,
                position = 3,
            ),
        )
    }

    @Test
    fun without_a_previous_session_the_new_set_just_repeats_the_last_one() {
        assertEquals(
            7,
            SetQueries.predictedRepsForNewSet(
                previousReps = 7,
                lastSessionReps = emptyList(),
                position = 2,
            ),
        )
    }

    @Test
    fun a_set_beyond_the_end_of_last_session_repeats_the_last_one() {
        // Last time was three sets; this is the fourth. Nothing to take a difference from.
        assertEquals(
            5,
            SetQueries.predictedRepsForNewSet(
                previousReps = 5,
                lastSessionReps = listOf(8, 5, 2),
                position = 4,
            ),
        )
    }

    @Test
    fun rising_reps_carry_upward_not_just_downward() {
        // Last time 5 then 8. Today's first set is 7, so set 2 owes 10, not 7 and not 8.
        assertEquals(
            10,
            SetQueries.predictedRepsForNewSet(
                previousReps = 7,
                lastSessionReps = listOf(5, 8),
                position = 2,
            ),
        )
    }

    @Test
    fun an_impossible_delta_leaves_the_previous_reps_alone() {
        // Last time 8 then 2. Applied to a first set of 5 that is -1, which is not a set anyone
        // does. Fall back rather than prescribing a single.
        assertEquals(
            5,
            SetQueries.predictedRepsForNewSet(
                previousReps = 5,
                lastSessionReps = listOf(8, 2),
                position = 2,
            ),
        )
    }

    @Test
    fun a_blank_previous_set_stays_blank() {
        assertNull(
            SetQueries.predictedRepsForNewSet(
                previousReps = null,
                lastSessionReps = listOf(8, 5),
                position = 2,
            ),
        )
    }

    @Test
    fun a_gap_in_last_session_is_read_by_position_not_by_stored_set_number() {
        // Last session stored set numbers 1 and 3 after its middle set was deleted, so its reps
        // arrive as the two surviving rows 8 and 5. Position 2 means the second surviving row,
        // giving a difference of 8 -> 5 rather than looking for a stored set number 2 that no
        // longer exists.
        assertEquals(
            3,
            SetQueries.predictedRepsForNewSet(
                previousReps = 6,
                lastSessionReps = listOf(8, 5),
                position = 2,
            ),
        )
    }

    @Test
    fun a_zero_rep_history_slot_is_not_treated_as_a_difference() {
        assertEquals(
            7,
            SetQueries.predictedRepsForNewSet(
                previousReps = 7,
                lastSessionReps = listOf(8, 0, 5),
                position = 2,
            ),
        )
    }
}
