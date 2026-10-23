package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.web.util.YearMonthW
import kotlinx.datetime.LocalDate
import kotlin.test.*

class MonthInsightsTest {
    private val september = YearMonthW(2026, 9)
    private val today = LocalDate(2026, 10, 4)
    private fun set(id: Long, date: String, load: Int, reps: Int = 10, number: Int = 1) =
        WorkoutSet(exerciseId = id, date = date, setNumber = number, weightLbs = load, reps = reps, completed = true)

    @Test fun entranceCountsDistinctDaysAndWorkingRows() {
        val rows = listOf(
            set(1, "2026-09-01", 500), set(1, "2026-09-01", 450, number = 2),
            set(1, "2026-09-10", 650), set(1, "2026-09-20", 550, 6),
            set(2, "2026-09-01", 800), set(2, "2026-09-20", 800, 12),
            set(3, "2026-09-01", 1000), set(3, "2026-09-10", 1200), set(3, "2026-09-20", 900),
            set(4, "2026-09-10", 700),
            set(1, "2026-09-30", 9999).copy(isWarmup = true),
            set(2, "2026-09-29", 9999).copy(completed = false),
            set(1, "2026-08-10", 400), set(1, "2026-10-01", 600),
        )
        val entrance = MonthInsightIndex(rows, (1L..4L).map { Exercise(it, "Lift $it", "TRICEPS") }).entrance(september, today)
        assertEquals(3, entrance.sessions)
        assertEquals(10, entrance.workingSets)
        assertEquals("September", entrance.titleMonth)
    }

    @Test fun topSetBeatsRules() {
        assertTrue(topSetBeats(TopSet(600, 1), TopSet(500, 20)))
        assertTrue(topSetBeats(TopSet(500, 11), TopSet(500, 10)))
        assertFalse(topSetBeats(TopSet(400, 20), TopSet(500, 10)))
        assertFalse(topSetBeats(TopSet(500, 10), TopSet(500, 10)))
    }

    @Test fun storyAndRowsShareOneYardstickAndStoryLiftsAreExcludedFromCompare() {
        val exercises = listOf(Exercise(1, "Bench", "CHEST"), Exercise(2, "Squat", "QUADS"))
        val rows = listOf(
            set(1, "2026-08-01", 1850, 4), set(1, "2026-09-01", 2050, 1),
            set(2, "2026-08-01", 2250, 5), set(2, "2026-09-01", 2450, 5),
        )
        val index = MonthInsightIndex(rows, exercises)
        val story = index.storyLifts(september)
        val comparison = index.comparison(september)!!
        assertEquals(2, comparison.allCount)
        assertEquals(setOf(1L, 2L), story.map { it.exerciseId }.toSet())
        assertEquals(0, comparison.rows.size)
        val augBest = index.lifts(september.minusMonths(1)).associate { it.exercise.id to it.sessions.maxWith(bestTopSetComparator).top }
        val sepBest = index.lifts(september).associate { it.exercise.id to it.sessions.maxWith(bestTopSetComparator).top }
        for (s in story) {
            val augTop = augBest.getValue(s.exerciseId)
            val sepTop = sepBest.getValue(s.exerciseId)
            assertEquals(compareTopSetWin(augTop, sepTop), s.win)
        }
    }

    @Test fun liftsWithoutLastMonthSessionsNeverGetChipOrStory() {
        val exercises = listOf(Exercise(1, "Bench", "CHEST"), Exercise(2, "NewLift", "BACK"))
        val rows = listOf(
            set(1, "2026-08-01", 1850, 4), set(1, "2026-09-01", 2050, 1),
            set(2, "2026-09-01", 1000, 10),
        )
        val index = MonthInsightIndex(rows, exercises)
        val comparison = index.comparison(september)!!
        assertEquals(listOf("NewLift"), comparison.onlyAfter.names)
        assertTrue(index.storyLifts(september).none { it.name == "NewLift" })
    }

    @Test fun noPreviousMonthWithSessionsYieldsNullStoryAndCompare() {
        val exercises = listOf(Exercise(1, "Bench", "CHEST"))
        val rows = listOf(set(1, "2026-09-01", 2050, 1))
        val index = MonthInsightIndex(rows, exercises)
        assertTrue(index.storyLifts(september).isEmpty())
        assertNull(index.comparison(september))
    }

    @Test fun leadSelectionPicksLongestRisingRunAndRejectsDippingLine() {
        val exercises = listOf(Exercise(1, "Leaning Dumbell Curl", "BICEPS"), Exercise(2, "Dip", "CHEST", isBodyweight = true))
        val rows = listOf(
            // Leaning Dumbell Curl: Aug 25 lb x 10, then 6 straight rising sessions in Sep
            set(1, "2026-08-20", 250, 10),
            set(1, "2026-09-03", 275, 8),
            set(1, "2026-09-07", 275, 10),
            set(1, "2026-09-12", 275, 11),
            set(1, "2026-09-17", 325, 5),
            set(1, "2026-09-23", 325, 6),
            set(1, "2026-09-29", 325, 8),
            // Dip: Aug BW x 6, Sep 1 BW+50 x 10, Sep 2 BW+45 x 8 (dips!)
            set(2, "2026-08-20", 0, 6).copy(isBodyweight = true),
            set(2, "2026-09-05", 500, 10).copy(isBodyweight = true),
            set(2, "2026-09-15", 450, 8).copy(isBodyweight = true),
        )
        val index = MonthInsightIndex(rows, exercises)
        val stories = index.storyLifts(september)
        assertEquals(1, stories.size)
        assertEquals("Leaning Dumbell Curl", stories.first().name)
        val lead = stories.first()
        val anchor = lead.points.first()
        assertTrue(anchor.isAnchor)
        assertEquals("AUG", anchor.dateLabel)
        assertEquals("25×10", anchor.setLabel)
    }

    @Test fun leadTiesBreakByLargerPercentageGainOverLastMonthBestLoad() {
        val exercises = listOf(Exercise(1, "Alpha", "CHEST"), Exercise(2, "Beta", "BACK"))
        val rows = listOf(
            // Alpha: Aug 100 lb x 10 -> Sep 120 lb x 10 (+20% gain, run 1)
            set(1, "2026-08-01", 1000, 10), set(1, "2026-09-01", 1200, 10),
            // Beta: Aug 100 lb x 10 -> Sep 110 lb x 10 (+10% gain, run 1)
            set(2, "2026-08-01", 1000, 10), set(2, "2026-09-01", 1100, 10),
        )
        val index = MonthInsightIndex(rows, exercises)
        val stories = index.storyLifts(september)
        assertEquals("Alpha", stories.first().name)
        assertEquals("Beta", stories[1].name)
    }

    @Test fun compareRowsOrderedByLbWinsThenRepWinsThenNoChipByName() {
        val exercises = listOf(
            Exercise(1, "LbWinSmall", "CHEST"),
            Exercise(2, "LbWinBig", "CHEST"),
            Exercise(3, "RepWinSmall", "ARMS"),
            Exercise(4, "RepWinBig", "ARMS"),
            Exercise(5, "ZuluNoChip", "BACK"),
            Exercise(6, "AlphaNoChip", "BACK"),
        )
        val rows = listOf(
            // LbWinSmall: +10 lb
            set(1, "2026-08-01", 1000, 10), set(1, "2026-09-01", 1100, 10),
            // LbWinBig: +20 lb
            set(2, "2026-08-01", 1000, 10), set(2, "2026-09-01", 1200, 10),
            // RepWinSmall: +1 rep
            set(3, "2026-08-01", 500, 10), set(3, "2026-09-01", 500, 11),
            // RepWinBig: +3 reps
            set(4, "2026-08-01", 500, 10), set(4, "2026-09-01", 500, 13),
            // ZuluNoChip: 0 gain
            set(5, "2026-08-01", 800, 10), set(5, "2026-09-01", 800, 10),
            // AlphaNoChip: 0 gain
            set(6, "2026-08-01", 800, 10), set(6, "2026-09-01", 800, 10),
        )
        val index = MonthInsightIndex(rows, exercises)
        val comp = compareMonthLifts(index.lifts(september.minusMonths(1)), index.lifts(september), september, emptySet())!!
        val names = comp.rows.map { it.name }
        assertEquals(listOf("LbWinBig", "LbWinSmall", "RepWinBig", "RepWinSmall", "AlphaNoChip", "ZuluNoChip"), names)
    }

    @Test fun bodyweightFlagOnEitherSourceKeepsLabelsHonestAndSessionCountsUseDistinctDates() {
        val rows = listOf(
            set(1, "2026-08-01", 0, 6).copy(isBodyweight = true),
            set(1, "2026-08-01", 0, 8, 2).copy(isBodyweight = true),
            set(1, "2026-09-01", 450, 8).copy(isBodyweight = true),
        )
        val index = MonthInsightIndex(rows, listOf(Exercise(1, "Dip", isBodyweight = true)))
        val comparison = compareMonthLifts(index.lifts(september.minusMonths(1)), index.lifts(september), september, emptySet())!!
        val row = comparison.rows.single()
        assertEquals("BW×8", row.fromLabel)
        assertEquals("BW+45×8", row.toLabel)
        assertEquals("1 → 1", row.sessionsLabel)
        assertTrue(row.dashed)
    }

    @Test fun fallbackUsesWorkingTrainingDaysAtZeroOneAndTwoAndCarriesJanuaryBack() {
        assertEquals(YearMonthW(2026, 9), initialMonthFor(today, 0))
        assertEquals(YearMonthW(2026, 9), initialMonthFor(today, 1))
        assertEquals(YearMonthW(2026, 10), initialMonthFor(today, 2))
        assertEquals(YearMonthW(2025, 12), initialMonthFor(LocalDate(2026, 1, 1), 0))
        val index = MonthInsightIndex(listOf(set(1, "2026-10-01", 500), set(1, "2026-10-01", 600, number = 2),
            set(1, "2026-10-02", 500).copy(isWarmup = true), set(1, "2026-10-03", 500).copy(completed = false)), emptyList())
        assertEquals(1, index.trainingDays(YearMonthW(2026, 10)))
    }

    @Test fun compactTopSetFormattingRules() {
        assertEquals("27.5×8", formatCompactTopSet(TopSet(275, 8), isBodyweight = false))
        assertEquals("BW×6", formatCompactTopSet(TopSet(0, 6), isBodyweight = true))
        assertEquals("BW+45×8", formatCompactTopSet(TopSet(450, 8), isBodyweight = true))
    }
}
