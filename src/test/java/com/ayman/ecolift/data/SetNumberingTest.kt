package com.ayman.ecolift.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the set_number allocation contract: unique within (date, exercise).
 * The regression below is the live bug `current.size + 1` produced — after a
 * middle set is deleted, size+1 collides with a surviving higher set_number.
 */
class SetNumberingTest {
    private fun set(setNumber: Int) =
        WorkoutSet(id = setNumber.toLong(), exerciseId = 1, date = "2026-06-24", setNumber = setNumber)

    @Test
    fun empty_starts_at_one() {
        assertEquals(1, SetQueries.nextSetNumber(emptyList()))
    }

    @Test
    fun sequential_increments_from_max() {
        assertEquals(4, SetQueries.nextSetNumber(listOf(set(1), set(2), set(3))))
    }

    @Test
    fun after_middle_delete_uses_max_not_size() {
        // sets were 1,2,3; set 2 deleted -> survivors 1,3. Next must be 4 (max+1),
        // NOT 3 (size+1), which would collide with the surviving set_number 3.
        val survivors = listOf(set(1), set(3))
        assertEquals(4, SetQueries.nextSetNumber(survivors))
    }
}
