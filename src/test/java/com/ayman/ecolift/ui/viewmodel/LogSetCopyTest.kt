package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LogSetCopyTest {

    @Test
    fun `copyValuesForAppendedSet copies weight reps and bodyweight state`() {
        val appended = WorkoutSet(
            id = 20L,
            exerciseId = 1L,
            date = "2026-06-02",
            setNumber = 4,
            weightLbs = 135,
            reps = 8,
            completed = true,
            restTimeSeconds = 90,
        )
        val source = WorkoutSet(
            id = 10L,
            exerciseId = 1L,
            date = "2026-06-02",
            setNumber = 2,
            weightLbs = 185,
            reps = 10,
            isBodyweight = false,
            completed = true,
            restTimeSeconds = 45,
        )

        val copied = copyValuesForAppendedSet(appended, source)

        assertEquals(20L, copied.id)
        assertEquals(4, copied.setNumber)
        assertEquals(185, copied.weightLbs)
        assertEquals(10, copied.reps)
        assertFalse(copied.isBodyweight)
        assertFalse(copied.completed)
        assertNull(copied.restTimeSeconds)
    }

    @Test
    fun `copyValuesForAppendedSet preserves bodyweight copied load`() {
        val appended = WorkoutSet(
            id = 21L,
            exerciseId = 2L,
            date = "2026-06-02",
            setNumber = 3,
            weightLbs = null,
            reps = null,
        )
        val source = WorkoutSet(
            id = 11L,
            exerciseId = 2L,
            date = "2026-06-02",
            setNumber = 1,
            weightLbs = 25,
            reps = 12,
            isBodyweight = true,
            completed = true,
        )

        val copied = copyValuesForAppendedSet(appended, source)

        assertEquals(25, copied.weightLbs)
        assertEquals(12, copied.reps)
        assertEquals(true, copied.isBodyweight)
        assertFalse(copied.completed)
    }
}
