package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.test.*

class QuietProgressMathTest {
    private val start = LocalDate.parse("2026-08-09")
    private val end = LocalDate.parse("2026-09-09")
    private fun set(date: String, load: Int, reps: Int, completed: Boolean = true, warmup: Boolean = false, bodyweight: Boolean = false) =
        WorkoutSet(exerciseId = 1, date = date, setNumber = 1, weightLbs = load, reps = reps, completed = completed, isWarmup = warmup, isBodyweight = bodyweight)

    @Test fun estimatesAndProofUseTheSameBestWorkingSet() {
        val result = buildQuietProgressOverview(listOf(set("2026-09-02", 2150, 10), set("2026-09-09", 2200, 11)), false, start, end)
        assertEquals("220 lb × 11", result.latest?.evidence)
        assertEquals("215 lb × 10", result.previous?.evidence)
        assertEquals(300.66666f, result.latest!!.value, .001f)
        assertEquals("+14 lb", result.sessionChangeLabel)
        assertTrue(result.isRecord)
        assertEquals("Est. 1RM", result.metricLabel)
    }

    @Test fun draftsWarmupsInvalidAndFutureSetsNeverProduceWins() {
        val sets = listOf(set("2026-08-20", 1000, 10), set("2026-09-09", 1100, 10),
            set("2026-09-09", 9990, 10, completed = false), set("2026-09-09", 9990, 10, warmup = true),
            set("2026-09-09", 9990, 0), set("2026-09-10", 9990, 10))
        val result = buildQuietProgressOverview(sets, false, start, end)
        assertEquals(2, result.points.size)
        assertEquals("110 lb × 10", result.latest?.evidence)
        assertEquals("+10.0%", result.deltaLabel)
    }

    @Test fun equalLoadShowsRealComparableReps() {
        val result = buildQuietProgressOverview(listOf(set("2026-08-10", 1720, 5), set("2026-09-09", 1720, 7)), false, start, end)
        assertEquals("+2 reps", result.deltaLabel)
        assertEquals("at 172 lb", result.metricLabel)
        assertEquals(listOf(5f, 7f), result.points.map { it.value })
        assertEquals("+2 reps", result.sessionChangeLabel)
    }

    @Test fun changingLoadDoesNotBecomeARepImprovement() {
        val result = buildQuietProgressOverview(listOf(set("2026-08-10", 2000, 7), set("2026-09-09", 1000, 10)), false, start, end)
        assertEquals("Est. 1RM", result.metricLabel)
        assertTrue(result.deltaSign < 0)
        assertFalse(result.isRecord)
    }

    @Test fun bodyweightComparesRepsOnlyAtTheSameExternalLoad() {
        val result = buildQuietProgressOverview(listOf(set("2026-08-10", 0, 20, bodyweight = true),
            set("2026-08-20", 150, 5, bodyweight = true), set("2026-09-09", 150, 7, bodyweight = true)), true, start, end)
        assertEquals("at BW +15 lb", result.metricLabel)
        assertEquals(listOf(5f, 7f), result.points.map { it.value })
        assertEquals("+2 reps", result.deltaLabel)
        assertEquals("BW +15 lb × 5", result.previous?.evidence)
    }

    @Test fun firstSessionAndTiesNeverClaimNewRecords() {
        val first = buildQuietProgressOverview(listOf(set("2026-09-09", 1000, 10)), false, start, end)
        assertFalse(first.isRecord)
        assertEquals("1 session", first.deltaLabel)
        assertNull(first.previous)
        val tie = buildQuietProgressOverview(listOf(set("2026-08-10", 1000, 10), set("2026-09-09", 1000, 10)), false, start, end)
        assertFalse(tie.isRecord)
        assertEquals("Steady", tie.deltaLabel)
    }

    @Test fun oldHistorySupportsProofButNeverInventsAWindowTrend() {
        val result = buildQuietProgressOverview(listOf(set("2026-07-01", 1200, 10), set("2026-09-09", 1100, 10)), false, start, end)
        assertEquals(1, result.points.size)
        assertEquals("1 session", result.deltaLabel)
        assertEquals("2026-07-01", result.previous?.date)
        assertFalse(result.isRecord)
        val empty = buildQuietProgressOverview(listOf(set("2026-07-01", 1200, 10)), false, start, end)
        assertTrue(empty.points.isEmpty())
        assertNull(empty.latest)
    }

    @Test fun bestEstimateCanDifferFromTheHeaviestSet() {
        val result = buildQuietProgressOverview(listOf(set("2026-08-20", 1000, 10),
            set("2026-09-09", 1600, 3), set("2026-09-09", 1300, 20)), false, start, end)
        assertEquals("130 lb × 20", result.latest?.evidence)
        assertEquals(216.66667f, result.latest!!.value, .001f)
    }
}
