package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressLifetimeTotalsTest {
    @Test
    fun counts_only_completed_work() {
        val totals = calculateProgressLifetimeTotals(
            listOf(
                WorkoutSet(
                    id = 1,
                    exerciseId = 10,
                    date = "2026-07-15",
                    setNumber = 1,
                    reps = 8,
                    completed = true,
                ),
                WorkoutSet(
                    id = 2,
                    exerciseId = 10,
                    date = "2026-07-15",
                    setNumber = 2,
                    reps = 6,
                    completed = true,
                ),
                WorkoutSet(
                    id = 3,
                    exerciseId = 10,
                    date = "2026-07-15",
                    setNumber = 3,
                    reps = 12,
                    completed = false,
                ),
            )
        )

        assertEquals(2, totals.completedSetCount)
        assertEquals(14, totals.totalRepCount)
    }
}
