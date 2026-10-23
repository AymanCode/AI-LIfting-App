package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.BodyPartRegion
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.test.*

class EmphasisTest {
    private val today = LocalDate(2026, 10, 5)
    private fun rows(id: Long, date: String, count: Int) = (1..count).map { n ->
        WorkoutSet(exerciseId = id, date = date, setNumber = n, weightLbs = 500, reps = 10, completed = true)
    }

    @Test fun weeklyBucketsAttributeEachLiftToItsFirstRegionAndExcludeWarmupsUncheckedAndCurrentWeek() {
        val exercises = listOf(Exercise(1, "Multi", "QUADS · TRICEPS"), Exercise(2, "Curl", "BICEPS"),
            Exercise(3, "Hamstrings", "HAMSTRINGS"), Exercise(4, "Calf", "CALVES"), Exercise(5, "Glutes", "GLUTES"),
            Exercise(6, "Forearms", "FOREARMS"), Exercise(7, "Extension", "TRICEPS"))
        val sets = rows(1, "2026-09-28", 3) + rows(2, "2026-10-04", 2) + rows(3, "2026-10-01", 1) +
            rows(4, "2026-10-01", 1) + rows(5, "2026-10-01", 1) + rows(6, "2026-10-01", 1) + rows(7, "2026-10-01", 1) +
            rows(1, "2026-09-28", 2).map { it.copy(isWarmup = true) } +
            rows(2, "2026-10-04", 2).map { it.copy(completed = false) } + rows(1, "2026-10-05", 50)
        val state = buildEmphasis(sets, exercises, today)
        assertEquals(8, state.weeks.size)
        assertEquals("Sep 28", state.weeks.last().label)
        assertEquals("Oct 4", state.weeks.last().endLabel)
        assertEquals(10, state.weeks.last().total)
        assertEquals(6, state.weeks.last().sets[BodyPartRegion.LEGS])
        assertEquals(4, state.weeks.last().sets[BodyPartRegion.ARMS])
        assertEquals(2, state.weeks.last().sessions[BodyPartRegion.LEGS])
        assertEquals(listOf(3, 1, 1, 1), state.weeks.last().lifts[BodyPartRegion.LEGS]!!.map { it.sets })
        assertEquals(1, state.history.size) // strip begins where the actual history begins
        assertEquals(BodyPartRegion.LEGS, state.headline?.region)
        assertEquals(6, state.headline?.count)
    }

    @Test fun gapWeeksAreZeroAndTheEightWeekWindowStopsAtTheLastFullWeek() {
        val state = buildEmphasis(rows(1, "2026-08-10", 4) + rows(1, "2026-09-21", 7), listOf(Exercise(1, "Leg", "QUADS")), today)
        assertEquals(listOf("Aug 10", "Aug 17", "Aug 24", "Aug 31", "Sep 7", "Sep 14", "Sep 21", "Sep 28"),
            state.weeks.map { it.label })
        assertEquals(listOf(4, 0, 0, 0, 0, 0, 7, 0), state.weeks.map { it.total })
        assertEquals(7, state.headline?.week?.sets?.get(BodyPartRegion.LEGS))
    }

    @Test fun recordHeadlineCarriesTheOlderRunnerUp() {
        val state = buildEmphasis(rows(1, "2026-04-06", 10) + rows(1, "2026-05-11", 21) + rows(1, "2026-09-21", 29),
            listOf(Exercise(1, "Squat", "QUADS")), today)
        assertEquals(29, state.headline?.count)
        assertEquals("May 11", state.headline?.runnerUp?.label)
        assertEquals(21, state.headline?.runnerUpCount)
        assertTrue(state.headline!!.record)
        assertEquals("Sep 21", state.headline!!.week.label)
        assertEquals("Squat", state.headline!!.week.lifts[BodyPartRegion.LEGS]!!.single().name)
    }

    @Test fun aRecentLeaderBelowItsHistoricalBestIsNotARecordAndCarriesTheBest() {
        val state = buildEmphasis(rows(1, "2026-04-06", 35) + rows(1, "2026-09-21", 29) + rows(2, "2026-09-28", 27),
            listOf(Exercise(1, "Squat", "QUADS"), Exercise(2, "Curl", "BICEPS")), today)
        assertEquals(29, state.headline?.count)
        assertFalse(state.headline!!.record)
        assertEquals("Apr 6", state.headline?.runnerUp?.label)
        assertEquals(35, state.headline?.runnerUpCount)
    }

    @Test fun tiesAndFirstWeeksAreNotRecordsAndTiedRecentWeeksPreferTheNewestThenLegendOrder() {
        val state = buildEmphasis(rows(1, "2026-09-21", 10) + rows(1, "2026-09-28", 10) + rows(2, "2026-09-28", 10),
            listOf(Exercise(1, "Squat", "QUADS"), Exercise(2, "Curl", "BICEPS")), today)
        assertEquals(BodyPartRegion.ARMS, state.headline?.region)
        assertEquals("Sep 28", state.headline?.week?.label)
        assertNull(state.headline!!.runnerUp) // Arms' first week beats nothing
        assertFalse(state.headline!!.record)
        val legsOnly = buildEmphasis(rows(1, "2026-09-21", 10) + rows(1, "2026-09-28", 10), listOf(Exercise(1, "Squat", "QUADS")), today)
        assertEquals("Sep 28", legsOnly.headline?.week?.label)
        assertEquals("Sep 21", legsOnly.headline?.runnerUp?.label)
        assertFalse(legsOnly.headline!!.record) // equalling the best is not a record
    }

    @Test fun coreAndUnknownAreShownWhenPresentSoTheStackTotalsRemainHonest() {
        val state = buildEmphasis(rows(1, "2026-09-28", 3) + rows(2, "2026-09-28", 2),
            listOf(Exercise(1, "Core", "CORE"), Exercise(2, "Unknown")), today)
        assertEquals(5, state.weeks.last().total)
        assertTrue(BodyPartRegion.CORE in state.regions)
        assertTrue(BodyPartRegion.UNKNOWN in state.regions)
        assertEquals(state.weeks.last().total, state.regions.sumOf { state.weeks.last().sets[it] ?: 0 })
    }

    @Test fun emptyAndOnlyCurrentWeekHaveNoHeadline() {
        assertNull(buildEmphasis(emptyList(), emptyList(), today).headline)
        assertNull(buildEmphasis(rows(1, "2026-10-05", 50), listOf(Exercise(1, "Curl", "BICEPS")), today).headline)
    }

    @Test fun weekSpanDropsTheRepeatedMonthAndUnknownReadsAsOther() {
        val state = buildEmphasis(rows(1, "2026-09-21", 3) + rows(1, "2026-09-28", 2), listOf(Exercise(1, "Squat", "QUADS")), today)
        assertEquals("SEP 21 – 27", emphasisWeekSpan(state.weeks.first { it.label == "Sep 21" }))
        assertEquals("SEP 28 – OCT 4", emphasisWeekSpan(state.weeks.last()))
        assertEquals("Other", emphasisRegionName(BodyPartRegion.UNKNOWN))
        assertEquals("Legs", emphasisRegionName(BodyPartRegion.LEGS))
    }

    @Test fun weightAndRepEditsDoNotRebuildRegionalSetCountsButNewCheckedRowsDo() {
        val source = rows(1, "2026-09-28", 3)
        assertEquals(emphasisCountRows(source), emphasisCountRows(source.map { it.copy(weightLbs = 700, reps = 12, rowRevision = 5) }))
        assertNotEquals(emphasisCountRows(source), emphasisCountRows(source + rows(1, "2026-09-29", 1)))
        assertNotEquals(emphasisCountRows(source), emphasisCountRows(source.map { it.copy(isWarmup = true) }))
    }
}
