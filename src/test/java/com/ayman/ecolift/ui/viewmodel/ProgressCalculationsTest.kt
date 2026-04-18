package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressCalculationsTest {

    @Test
    fun `buildProgressStats uses recent database sets for card values`() {
        val allTimeSets = listOf(
            workoutSet(date = "2026-03-10", weight = 185, reps = 5),
            workoutSet(date = "2026-04-10", weight = 205, reps = 5),
            workoutSet(date = "2026-04-15", weight = 215, reps = 3),
        )

        val stats = buildProgressStats(
            allTimeSets = allTimeSets,
            recentSets = allTimeSets,
            isBodyweight = false,
            userBodyWeight = 180,
            now = LocalDate.of(2026, 4, 17)
        )

        assertEquals("215", stats.currentPr)
        assertEquals(2, stats.workoutCount)
        assertEquals("1.7k", stats.totalVolume)
        assertTrue(stats.est1Rm.toFloat() > 236f)
    }

    @Test
    fun `buildProgressChartPoints groups sets by session date`() {
        val points = buildProgressChartPoints(
            filteredSets = listOf(
                workoutSet(id = 1L, date = "2026-04-10", weight = 185, reps = 5, setNumber = 1),
                workoutSet(id = 2L, date = "2026-04-10", weight = 205, reps = 3, setNumber = 2),
                workoutSet(id = 3L, date = "2026-04-15", weight = 215, reps = 2, setNumber = 1),
            ),
            isBodyweight = false,
            userBodyWeight = 180
        )

        assertEquals(2, points.size)
        assertEquals("Apr 10", points.first().label)
        assertEquals(1540, points.first().volume)
        assertEquals(205, points.first().maxWeight)
    }

    private fun workoutSet(
        id: Long = 0L,
        date: String,
        weight: Int,
        reps: Int,
        setNumber: Int = 1,
    ) = WorkoutSet(
        id = id,
        exerciseId = 1L,
        date = date,
        setNumber = setNumber,
        weightLbs = weight,
        reps = reps,
        isBodyweight = false,
        completed = true,
        restTimeSeconds = null
    )
}
