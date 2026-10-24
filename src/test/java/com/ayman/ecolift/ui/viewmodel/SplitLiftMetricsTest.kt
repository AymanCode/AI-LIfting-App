package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.splits.LearnedFixtures.set
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitLiftMetricsTest {
    private val press = Exercise(1, "Overhead Press", "SHOULDERS")
    private val today = LocalDate.parse("2026-10-15")

    @Test
    fun workingVolumeBeforeTodayGivesTheBarsAndTheChange() {
        val history = listOf(
            set("2026-10-01", 1, weightLbs = 500, reps = 10, warmup = true),
            set("2026-10-05", 1, weightLbs = 1000, reps = 10),
            set("2026-10-05", 1, setNumber = 2, weightLbs = 500, reps = 10, warmup = true),
            set("2026-10-10", 1, weightLbs = 1000, reps = 15),
            set("2026-10-10", 1, setNumber = 2, weightLbs = 2000, reps = 10, completed = false),
            set("2026-10-15", 1, weightLbs = 3000, reps = 10),
        )
        assertEquals(SplitLiftMetrics("100×15", listOf(1000f, 1500f), 50), splitLiftMetrics(press, history, today))
    }

    @Test
    fun onlyTheLastSixSessionsCount() {
        val history = (1..8).map { day -> set("2026-10-0$day", 1, weightLbs = 1000, reps = day) }
        assertEquals(listOf(300f, 400f, 500f, 600f, 700f, 800f), splitLiftMetrics(press, history, today).workingVolume)
        assertEquals(167, splitLiftMetrics(press, history, today).deltaPercent)
    }

    @Test
    fun aZeroFirstSessionOrASingleSessionHasNoChange() {
        val dips = Exercise(2, "Dip", "CHEST", isBodyweight = true)
        val bodyweight = listOf(set("2026-10-05", 2, weightLbs = 0, reps = 10, bodyweight = true), set("2026-10-10", 2, weightLbs = 250, reps = 8, bodyweight = true))
        assertEquals(SplitLiftMetrics("BW+25×8", listOf(0f, 200f), null), splitLiftMetrics(dips, bodyweight, today))
        assertEquals(SplitLiftMetrics("100×8", listOf(800f), null), splitLiftMetrics(press, listOf(set("2026-10-05", 1)), today))
    }
}
