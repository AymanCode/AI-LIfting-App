package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.LearnedSplitsEngine
import com.ayman.ecolift.data.splits.WorkedExample
import com.ayman.ecolift.data.splits.WorkedExample.BENCH_PRESS
import com.ayman.ecolift.data.splits.WorkedExample.CBUM_TRICEP_EXTENSION
import com.ayman.ecolift.data.splits.WorkedExample.INCLINE_BENCH
import com.ayman.ecolift.data.splits.WorkedExample.LAT_PULLDOWN
import com.ayman.ecolift.data.splits.WorkedExample.LEANING_HAMMER_CURL
import com.ayman.ecolift.data.splits.WorkedExample.REAR_SHOULDER_SINGLE_PEC
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnedTicketTest {
    private val model = LearnedSplitsEngine.compute(WorkedExample.inputs())
    private val programs = buildLearnedPrograms(model, WorkedExample.AS_OF, WorkedExample.exercises, WorkedExample.sets) { it }

    private fun set(date: String, weight: Int?, reps: Int, bodyweight: Boolean = false) = WorkoutSet(
        exerciseId = 1, date = date, setNumber = 1, weightLbs = weight, reps = reps, completed = true, isBodyweight = bodyweight,
    )

    @Test
    fun topSetsUseStoredTenthsAndTheCompactBodyweightForm() {
        assertEquals("22.5×8", formatTicketTopSet(TopSet(225, 8), isBodyweight = false))
        assertEquals("185×5", formatTicketTopSet(TopSet(1850, 5), isBodyweight = false))
        assertEquals("BW×8", formatTicketTopSet(TopSet(0, 8), isBodyweight = true))
        assertEquals("BW+25×5", formatTicketTopSet(TopSet(250, 5), isBodyweight = true))
    }

    @Test
    fun aTopSetThatReachedTheTriggerRepsStepsUp() {
        val history = listOf(
            set("2026-08-01", 1000, 8), set("2026-08-04", 1000, 10),
            set("2026-08-08", 1050, 8), set("2026-08-11", 1050, 10),
            set("2026-08-15", 1100, 8), set("2026-08-18", 1100, 10),
        )
        val stake = stakeFor(history, isBodyweight = false, before = LocalDate.parse("2026-09-28"))
        assertEquals(StakeUi.StepUp(1150, isBodyweight = false), stake)
        assertEquals("step up → 115", stakeText(stake!!))
    }

    @Test
    fun oneOrTwoBelowTheBestAtThisWeightIsRepsToARecord() {
        val one = stakeFor(listOf(set("2026-09-01", 1000, 10), set("2026-09-08", 1000, 9)), false, LocalDate.parse("2026-09-28"))
        assertEquals("1 rep to a record", stakeText(one!!))
        val two = stakeFor(listOf(set("2026-09-01", 0, 10, true), set("2026-09-08", 0, 8, true)), true, LocalDate.parse("2026-09-28"))
        assertEquals("2 reps to a record", stakeText(two!!))
    }

    @Test
    fun otherwiseBeatTheBestRepsAtThisWeight() {
        assertEquals("beat 10", stakeText(stakeFor(listOf(set("2026-09-08", 1000, 10)), false, LocalDate.parse("2026-09-28"))!!))
        assertEquals("beat 12", stakeText(stakeFor(listOf(set("2026-09-01", 1000, 12), set("2026-09-08", 1000, 8)), false, LocalDate.parse("2026-09-28"))!!))
    }

    @Test
    fun aLiftNeverTrainedHasNoStakeAndTodayNeverCounts() {
        assertNull(stakeFor(emptyList(), false, LocalDate.parse("2026-09-28")))
        assertNull(stakeFor(listOf(set("2026-09-28", 1000, 10)), false, LocalDate.parse("2026-09-28")))
    }

    @Test
    fun long_history_optimisation_keeps_literal_ticket_and_split_results() {
        val history = listOf(
            set("2026-08-01", 1000, 8), set("2026-08-04", 1000, 10),
            set("2026-08-08", 1050, 8), set("2026-08-11", 1050, 10),
            set("2026-08-15", 1100, 8), set("2026-08-18", 1100, 10),
            set("2026-09-28", 9990, 99),
            set("2026-09-01", 9990, 99).copy(isWarmup = true),
            set("2026-09-02", 9990, 99).copy(completed = false),
        )
        val before = LocalDate(2026, 9, 28)
        val report = reportBefore(history, false, before)
        assertEquals(TopSet(1100, 10), report.topSet)
        assertEquals("2026-08-18", report.lastSessionDate)
        assertEquals(NextStep(1150, 10, "10", 10, 1100, 2, 2), report.nextStep)
        assertEquals(11, report.suggestedReps)
        assertEquals(6, report.events.size)
        assertEquals(StakeUi.StepUp(1150, false), stakeFor(history, false, before))
        assertEquals(
            SplitLiftMetrics("110\u00d710", listOf(800f, 1000f, 840f, 1050f, 880f, 1100f), 38),
            splitLiftMetrics(com.ayman.ecolift.data.Exercise(1, "Bench", "CHEST"), history, before),
        )
    }

    @Test
    fun programsRunInDueOrderWithTheDueGroupMarked() {
        assertEquals(listOf("Chest · Arms", "Legs", "Upper 1", "Legs"), programs.map { it.name })
        assertTrue(programs.first().isExpected)
        assertEquals(listOf(0, 1, 2, 3), programs.map { it.position })
        assertEquals(model.groupsInDueOrder.map { it.colorIndex }, programs.map { it.colorIndex })
    }

    @Test
    fun upper1RowsAreLastSessionOrderThenFadedThenTail() {
        val upper = programs.single { it.name == "Upper 1" }
        assertEquals(
            listOf(LAT_PULLDOWN, REAR_SHOULDER_SINGLE_PEC, CBUM_TRICEP_EXTENSION, LEANING_HAMMER_CURL, INCLINE_BENCH, BENCH_PRESS),
            upper.rows.map { it.exerciseId },
        )
        assertEquals(
            listOf(TicketRowKind.MEMBER, TicketRowKind.MEMBER, TicketRowKind.MEMBER, TicketRowKind.MEMBER, TicketRowKind.FADED, TicketRowKind.TAIL),
            upper.rows.map { it.kind },
        )
        assertEquals("2026-09-15", upper.rows.single { it.kind == TicketRowKind.FADED }.lastDate)
        assertEquals(listOf(LAT_PULLDOWN, REAR_SHOULDER_SINGLE_PEC, CBUM_TRICEP_EXTENSION, LEANING_HAMMER_CURL), upper.lifts.map { it.exerciseId })
        assertEquals("140×10", upper.rows.first().topSetLabel)
    }

    @Test
    fun startIncludesMembersByDefaultAndFollowsToggles() {
        val upper = programs.single { it.name == "Upper 1" }
        assertEquals(listOf(LAT_PULLDOWN, REAR_SHOULDER_SINGLE_PEC, CBUM_TRICEP_EXTENSION, LEANING_HAMMER_CURL), includedTicketLifts(upper, null))
        val toggled = defaultTicketInclusion(upper) - REAR_SHOULDER_SINGLE_PEC + INCLINE_BENCH
        assertEquals(listOf(LAT_PULLDOWN, CBUM_TRICEP_EXTENSION, LEANING_HAMMER_CURL, INCLINE_BENCH), includedTicketLifts(upper, toggled))
        assertEquals("2026-09-28|c:1", ticketInclusionKey("2026-09-28", "c:1"))
    }
}
