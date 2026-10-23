package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.ui.navigation.progressDateFraction
import kotlinx.datetime.LocalDate
import kotlin.test.*

class ProgressWindowTest {
    private fun point(date: String, weight: Int = 1350, reps: Int = 8, estimate: Float = 171f) =
        ProgressPointUi(date, date, 0, estimate, weight, reps, reps, reps)

    @Test fun windowExcludesOldAndFutureSessionsAndIncludesBothBoundaries() {
        val end = LocalDate.parse("2026-09-04")
        val start = progressWindowStart(TimeframeFilter.ONE_MONTH, end)
        val points = listOf("2026-08-03", "2026-08-04", "2026-09-04", "2026-09-05").map { point(it) }
        assertEquals(listOf("2026-08-04", "2026-09-04"), progressPointsInWindow(points, start, end).map { it.date })
    }

    @Test fun sortingUsesWholeWindowRatherThanLastTwoSessions() {
        val points = listOf(point("2026-08-04", estimate = 100f), point("2026-08-20", estimate = 130f), point("2026-09-04", estimate = 120f))
        assertEquals(20f, calculateWindowChangePercentage(points))
    }

    @Test fun onlySameRepComparisonsShowWeightImprovement() {
        val first = point("2026-08-04")
        val higher = point("2026-09-04", weight = 1400, estimate = 177f)
        assertEquals(PastMonthDelta("+5 lb", 1, "at 8 reps"), calculateProgressWindowStat(listOf(first, higher), false))
        val changedReps = higher.copy(repsAtTopWeight = 5, estimated1RM = 163f)
        assertEquals("Est. 1RM", calculateProgressWindowStat(listOf(first, changedReps), false)?.subLabel)
        assertEquals(-1, calculateProgressWindowStat(listOf(first, changedReps), false)?.sign)
    }

    @Test fun repImprovementIncludesTheUnchangedLoad() {
        assertEquals(PastMonthDelta("+2 reps", 1, "at 135 lb"), calculateProgressWindowStat(
            listOf(point("2026-08-04"), point("2026-09-04", reps = 10)), false))
        assertEquals("at BW +15 lb", calculateProgressWindowStat(
            listOf(point("2026-08-04", weight = 150), point("2026-09-04", weight = 150, reps = 10)), true, 185)?.subLabel)
    }

    @Test fun sparseWindowDoesNotBorrowHistory() {
        assertNull(calculateProgressWindowStat(listOf(point("2026-09-04")), false))
        assertEquals(0f, calculateWindowChangePercentage(listOf(point("2026-09-04"))))
    }

    @Test fun chartPositionsUseCalendarTimeIncludingGaps() {
        val start = LocalDate.parse("2026-08-01")
        val end = LocalDate.parse("2026-08-31")
        assertEquals(0f, progressDateFraction(start, start, end))
        assertEquals(0.1f, progressDateFraction(LocalDate.parse("2026-08-04"), start, end))
        assertEquals(1f, progressDateFraction(end, start, end))
    }

    @Test fun draftsNeverCreateAProgressPoint() {
        val sets = listOf(WorkoutSet(exerciseId = 1, date = "2026-09-04", setNumber = 1, weightLbs = 1350, reps = 8, completed = false))
        assertTrue(buildProgressChartPoints(sets, false, null).isEmpty())
    }
}
