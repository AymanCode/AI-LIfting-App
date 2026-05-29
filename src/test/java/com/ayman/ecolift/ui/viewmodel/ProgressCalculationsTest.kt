package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutDay
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

        val chartPoints = buildProgressChartPoints(allTimeSets, false, 180)
        val stats = buildProgressStats(
            allTimeSets = allTimeSets,
            timeframe = TimeframeFilter.ONE_MONTH,
            chartPoints = chartPoints,
            selectedMetric = ProgressMetric.VOLUME,
            isBodyweight = false,
            userBodyWeight = 180,
            now = LocalDate.of(2026, 4, 17)
        )

        assertEquals("215", stats.currentPr)
        assertEquals(215f, stats.currentPrLbs, 0.01f)
        assertEquals(2, stats.workoutCount)
        assertEquals("1.7k", stats.totalVolume)
        assertEquals(1670, stats.totalVolumeLbs)
        assertTrue(stats.est1Rm.toFloat() > 236f)
    }

    @Test
    fun `buildProgressStats calculates real period deltas from workout history`() {
        val allTimeSets = listOf(
            workoutSet(date = "2026-02-10", weight = 100, reps = 10),
            workoutSet(date = "2026-03-10", weight = 200, reps = 5),
            workoutSet(date = "2026-04-10", weight = 220, reps = 5),
            workoutSet(date = "2026-04-20", weight = 220, reps = 5),
        )

        val chartPoints = buildProgressChartPoints(
            filteredSets = allTimeSets.filter { it.date >= "2026-04-01" },
            isBodyweight = false,
            userBodyWeight = 180
        )
        val stats = buildProgressStats(
            allTimeSets = allTimeSets,
            timeframe = TimeframeFilter.ONE_MONTH,
            chartPoints = chartPoints,
            selectedMetric = ProgressMetric.VOLUME,
            isBodyweight = false,
            userBodyWeight = 180,
            now = LocalDate.of(2026, 4, 25)
        )

        assertEquals(220f, stats.currentPrLbs, 0.01f)
        assertEquals(10f, stats.currentPrDelta, 0.01f)
        assertEquals(2200, stats.totalVolumeLbs)
        assertEquals(120f, stats.volumeDelta, 0.01f)
        assertEquals(2, stats.workoutCount)
        assertEquals(100f, stats.workoutCountDelta, 0.01f)
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
        assertEquals(WeightLbs.fromWholePounds(205), points.first().maxWeight)
    }

    @Test
    fun `organizeProgressExercises sorts positives first then neutral then negative`() {
        val exercises = listOf(
            progressExercise(1, "Flat", 0f),
            progressExercise(2, "Falling", -4f),
            progressExercise(3, "Climbing", 12f),
            progressExercise(4, "Small positive", 1f),
        )

        val result = organizeProgressExercises(exercises, "")

        assertEquals(listOf("Climbing", "Small positive", "Flat", "Falling"), result.map { it.name })
    }

    @Test
    fun `organizeProgressExercises filters by search query`() {
        val exercises = listOf(
            progressExercise(1, "Bench Press", 10f),
            progressExercise(2, "Back Squat", 5f),
        )

        val result = organizeProgressExercises(exercises, "bench")

        assertEquals(listOf("Bench Press"), result.map { it.name })
    }

    @Test
    fun `buildProgressSplitPages keeps each split isolated`() {
        val exercises = listOf(
            progressExercise(1, "Bench Press", 10f),
            progressExercise(2, "Back Squat", 5f),
            progressExercise(3, "Unassigned Curl", 20f),
        )
        val splits = listOf(
            ProgressSplitSource(10, "Push", listOf(1)),
            ProgressSplitSource(20, "Legs", listOf(2)),
        )

        val pages = buildProgressSplitPages(exercises, splits, "")

        assertEquals(2, pages.size)
        assertEquals("Push", pages[0].name)
        assertEquals(listOf("Bench Press"), pages[0].exercises.map { it.name })
        assertEquals("Legs", pages[1].name)
        assertEquals(listOf("Back Squat"), pages[1].exercises.map { it.name })
    }

    @Test
    fun `buildProgressSplitPages keeps split pages even when filtered empty`() {
        val pages = buildProgressSplitPages(
            exercises = listOf(progressExercise(1, "Bench Press", 10f)),
            splits = listOf(
                ProgressSplitSource(10, "Push", listOf(1)),
                ProgressSplitSource(20, "Pull", listOf(2)),
            ),
            searchQuery = "",
        )

        assertEquals(2, pages.size)
        assertEquals("Push", pages[0].name)
        assertEquals(listOf("Bench Press"), pages[0].exercises.map { it.name })
        assertEquals("Pull", pages[1].name)
        assertTrue(pages[1].exercises.isEmpty())
    }

    @Test
    fun `buildProgressSplitSources falls back to latest logged split session`() {
        val sources = buildProgressSplitSources(
            slots = listOf(
                CycleSlot(id = 10L, name = "Push", orderIndex = 0),
                CycleSlot(id = 20L, name = "Pull", orderIndex = 1),
            ),
            savedRows = emptyList(),
            workoutDays = listOf(
                WorkoutDay(date = "2026-05-01", cycleSlotId = 10L),
                WorkoutDay(date = "2026-05-15", cycleSlotId = 10L),
                WorkoutDay(date = "2026-05-10", cycleSlotId = 20L),
            ),
            setsByDate = mapOf(
                "2026-05-01" to listOf(workoutSet(date = "2026-05-01", exerciseId = 99L, weight = 95, reps = 10)),
                "2026-05-15" to listOf(
                    workoutSet(date = "2026-05-15", exerciseId = 1L, weight = 185, reps = 5, setNumber = 1),
                    workoutSet(date = "2026-05-15", exerciseId = 1L, weight = 205, reps = 3, setNumber = 2),
                    workoutSet(date = "2026-05-15", exerciseId = 2L, weight = 95, reps = 12, setNumber = 1),
                ),
                "2026-05-10" to listOf(workoutSet(date = "2026-05-10", exerciseId = 3L, weight = 135, reps = 8)),
            ),
        )

        assertEquals(listOf(1L, 2L), sources.first { it.splitId == 10L }.exerciseIds)
        assertEquals(listOf(3L), sources.first { it.splitId == 20L }.exerciseIds)
    }

    @Test
    fun `buildProgressSplitSources prefers saved split exercises over history`() {
        val sources = buildProgressSplitSources(
            slots = listOf(CycleSlot(id = 10L, name = "Push", orderIndex = 0)),
            savedRows = listOf(
                SplitExercise(splitId = 10L, exerciseId = 3L, orderIndex = 1),
                SplitExercise(splitId = 10L, exerciseId = 2L, orderIndex = 0),
            ),
            workoutDays = listOf(WorkoutDay(date = "2026-05-15", cycleSlotId = 10L)),
            setsByDate = mapOf(
                "2026-05-15" to listOf(workoutSet(date = "2026-05-15", exerciseId = 1L, weight = 185, reps = 5)),
            ),
        )

        assertEquals(listOf(2L, 3L), sources.single().exerciseIds)
    }

    @Test
    fun `normalizeSplitIndex clamps index to available pages`() {
        assertEquals(0, normalizeProgressSplitIndex(-1, 3))
        assertEquals(1, normalizeProgressSplitIndex(1, 3))
        assertEquals(2, normalizeProgressSplitIndex(7, 3))
        assertEquals(0, normalizeProgressSplitIndex(7, 0))
    }

    private fun progressExercise(
        id: Long,
        name: String,
        change: Float,
    ) = ProgressExerciseUi(
        exerciseId = id,
        name = name,
        sessions = 1,
        lastSessionDate = "May 14",
        lastSessionSummary = "100 x 10",
        changePercentage = change,
        trend = listOf(100),
    )

    private fun workoutSet(
        id: Long = 0L,
        exerciseId: Long = 1L,
        date: String,
        weight: Int,
        reps: Int,
        setNumber: Int = 1,
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        date = date,
        setNumber = setNumber,
        weightLbs = WeightLbs.fromWholePounds(weight),
        reps = reps,
        isBodyweight = false,
        completed = true,
        restTimeSeconds = null
    )
}
