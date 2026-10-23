package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.minusMonths
import com.ayman.ecolift.web.util.minusYears
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
            timeframe = TimeframeFilter.ONE_MONTH,
            isBodyweight = false,
            userBodyWeight = 180,
            now = LocalDate(2026, 4, 17)
        )

        assertEquals("215", stats.currentPr)
        assertEquals(215f, stats.currentPrLbs, 0.01f)
        assertEquals(2, stats.workoutCount)
        assertEquals("1.7k", stats.totalVolume)
        assertEquals(1670, stats.totalVolumeLbs)
        assertTrue(stats.est1Rm.toFloat() > 236f)
    }

    @Test
    fun `buildProgressStats computes current-period values from workout history`() {
        val allTimeSets = listOf(
            workoutSet(date = "2026-02-10", weight = 100, reps = 10),
            workoutSet(date = "2026-03-10", weight = 200, reps = 5),
            workoutSet(date = "2026-04-10", weight = 220, reps = 5),
            workoutSet(date = "2026-04-20", weight = 220, reps = 5),
        )

        val stats = buildProgressStats(
            allTimeSets = allTimeSets,
            timeframe = TimeframeFilter.ONE_MONTH,
            isBodyweight = false,
            userBodyWeight = 180,
            now = LocalDate(2026, 4, 25)
        )

        assertEquals(220f, stats.currentPrLbs, 0.01f)
        assertEquals(2200, stats.totalVolumeLbs)
        assertEquals(2, stats.workoutCount)
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
    fun `progress change percentage uses e1rm instead of session volume`() {
        val points = buildProgressChartPoints(
            filteredSets = listOf(
                workoutSet(id = 1L, date = "2026-04-10", weight = 100, reps = 10, setNumber = 1),
                workoutSet(id = 2L, date = "2026-04-15", weight = 100, reps = 10, setNumber = 1),
                workoutSet(id = 3L, date = "2026-04-15", weight = 100, reps = 10, setNumber = 2),
            ),
            isBodyweight = false,
            userBodyWeight = 180,
        )

        assertEquals(1000, points[0].volume)
        assertEquals(2000, points[1].volume)
        assertEquals(0f, calculateE1rmChangePercentage(points), 0.01f)
    }

    @Test
    fun `buildProgressChartPoints uses best e1rm set for estimated one rep max`() {
        val points = buildProgressChartPoints(
            filteredSets = listOf(
                workoutSet(id = 1L, date = "2026-04-10", weight = 225, reps = 1, setNumber = 1),
                workoutSet(id = 2L, date = "2026-04-10", weight = 185, reps = 10, setNumber = 2),
            ),
            isBodyweight = false,
            userBodyWeight = 180,
        )

        assertEquals(185f * (1f + 10f / 30f), points.single().estimated1RM, 0.01f)
        assertEquals(WeightLbs.fromWholePounds(225), points.single().maxWeight)
        assertEquals(10, points.single().reps)
    }

    @Test
    fun `bodyweight progress counts added load and skips a profile-weight estimate`() {
        val sets = listOf(
            workoutSet(date = "2026-04-10", weight = null, reps = 10, setNumber = 1, isBodyweight = true),
            workoutSet(date = "2026-04-10", weight = 25, reps = 8, setNumber = 2, isBodyweight = true),
        )

        val points = buildProgressChartPoints(
            filteredSets = sets,
            isBodyweight = true,
            userBodyWeight = 180,
        )
        val stats = buildProgressStats(
            allTimeSets = sets,
            timeframe = TimeframeFilter.ONE_MONTH,
            isBodyweight = true,
            userBodyWeight = 180,
            now = LocalDate(2026, 4, 17),
        )

        assertEquals(1, points.size)
        assertEquals(WeightLbs.fromWholePounds(25), points.single().maxWeight)
        assertEquals(200, points.single().volume)
        assertEquals("25", stats.currentPr)
        assertEquals(25f, stats.currentPrLbs, 0.01f)
        assertEquals(200, stats.totalVolumeLbs)
        assertEquals(0f, points.single().estimated1RM, 0.01f)
    }

    @Test
    fun `bodyweight progress without saved bodyweight uses added load only`() {
        val sets = listOf(
            workoutSet(date = "2026-04-10", weight = null, reps = 10, setNumber = 1, isBodyweight = true),
            workoutSet(date = "2026-04-10", weight = 25, reps = 8, setNumber = 2, isBodyweight = true),
        )

        val points = buildProgressChartPoints(
            filteredSets = sets,
            isBodyweight = true,
            userBodyWeight = null,
        )
        val stats = buildProgressStats(
            allTimeSets = sets,
            timeframe = TimeframeFilter.ONE_MONTH,
            isBodyweight = true,
            userBodyWeight = null,
            now = LocalDate(2026, 4, 17),
        )

        assertEquals(WeightLbs.fromWholePounds(25), points.single().maxWeight)
        assertEquals(200, points.single().volume)
        assertEquals("25", stats.currentPr)
        assertEquals(25f, stats.currentPrLbs, 0.01f)
        assertEquals(200, stats.totalVolumeLbs)
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

    private fun progressExercise(
        id: Long,
        name: String,
        change: Float,
    ) = ProgressExerciseUi(
        exerciseId = id,
        name = name,
        muscleGroups = "CHEST",
        sessions = 1,
        lastSessionDate = "May 14",
        lastSessionIsoDate = "2026-05-14",
        lastSessionSummary = "100 x 10",
        changePercentage = change,
        trend = listOf(100),
    )

    private fun workoutSet(
        id: Long = 0L,
        exerciseId: Long = 1L,
        date: String,
        weight: Int?,
        reps: Int,
        setNumber: Int = 1,
        isBodyweight: Boolean = false,
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        date = date,
        setNumber = setNumber,
        weightLbs = WeightLbs.fromWholePounds(weight),
        reps = reps,
        isBodyweight = isBodyweight,
        completed = true,
        restTimeSeconds = null
    )

    @Test
    fun `bestTimeframeFor picks ONE_MONTH when two sessions fall in the last month`() {
        val now = LocalDate(2026, 6, 8)
        val dates = listOf(LocalDate(2026, 6, 6), LocalDate(2026, 5, 20))
        assertEquals(TimeframeFilter.ONE_MONTH, bestTimeframeFor(dates, now))
    }

    @Test
    fun `bestTimeframeFor escalates to THREE_MONTHS for the Lateral Raise case`() {
        // Real emulator data: sessions on Apr 4 and Jun 6, viewed Jun 8.
        // 1M window (since May 8) has only Jun 6; 3M window (since Mar 8) has both.
        val now = LocalDate(2026, 6, 8)
        val dates = listOf(LocalDate(2026, 4, 4), LocalDate(2026, 6, 6))
        assertEquals(TimeframeFilter.THREE_MONTHS, bestTimeframeFor(dates, now))
    }

    @Test
    fun `bestTimeframeFor escalates to SIX_MONTHS when sessions are older than three months`() {
        val now = LocalDate(2026, 6, 8)
        val dates = listOf(now.minusMonths(4), now.minusMonths(5))
        assertEquals(TimeframeFilter.SIX_MONTHS, bestTimeframeFor(dates, now))
    }

    @Test
    fun `bestTimeframeFor escalates to ONE_YEAR when sessions are older than six months`() {
        val now = LocalDate(2026, 6, 8)
        val dates = listOf(now.minusMonths(8), now.minusMonths(10))
        assertEquals(TimeframeFilter.ONE_YEAR, bestTimeframeFor(dates, now))
    }

    @Test
    fun `bestTimeframeFor falls back to ALL_TIME when no window has two sessions`() {
        val now = LocalDate(2026, 6, 8)
        val dates = listOf(now.minusYears(2), now.minusYears(3))
        assertEquals(TimeframeFilter.ALL_TIME, bestTimeframeFor(dates, now))
    }

    @Test
    fun `bestTimeframeFor falls back to ALL_TIME with a single session`() {
        val now = LocalDate(2026, 6, 8)
        assertEquals(TimeframeFilter.ALL_TIME, bestTimeframeFor(listOf(now.minusDays(2)), now))
    }

    @Test
    fun `bestTimeframeFor does not count multiple sets on the same day as two sessions`() {
        val now = LocalDate(2026, 6, 8)
        val sameDay = LocalDate(2026, 6, 6)
        assertEquals(TimeframeFilter.ALL_TIME, bestTimeframeFor(listOf(sameDay, sameDay), now))
    }
}
