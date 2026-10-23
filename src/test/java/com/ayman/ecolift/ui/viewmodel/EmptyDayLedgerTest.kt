package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.DayAttribution
import com.ayman.ecolift.data.splits.LearnedGroup
import com.ayman.ecolift.data.splits.LearnedModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmptyDayLedgerTest {
    private val date = "2026-10-04"
    private val group = LearnedGroup("upper", 1, "Upper 1", 0, listOf(1, 3, 5), listOf(1, 5, 3), emptyList(),
        listOf("2026-09-09", "2026-09-26"), setOf(3), true, true, 0, false, false, false)
    private val model = LearnedModel.EMPTY.copy(
        asOf = date, memoryStart = "2026-09-01", groups = listOf(group),
        attribution = mapOf(
            "2026-09-09" to DayAttribution("2026-09-09", group.key, listOf(2)),
            "2026-09-26" to DayAttribution("2026-09-26", group.key, listOf(4)),
            "2026-09-28" to DayAttribution("2026-09-28", "other", listOf(6)),
            "2026-08-30" to DayAttribution("2026-08-30", group.key, listOf(6)),
        ),
    )
    private val exercises = listOf(
        Exercise(1, "Pec Deck", "CHEST"), Exercise(2, "Incline Bench Press", "CHEST"),
        Exercise(3, "Row", "BACK · CHEST"), Exercise(4, "Bench Press", "CHEST"),
        Exercise(5, "New Lift", "CHEST"), Exercise(6, "Other Group Lift", "CHEST"),
        Exercise(7, "Pull Up", "BACK", isBodyweight = true),
    )
    private fun set(id: Long, lift: Long, day: String, load: Int, reps: Int, number: Int = 1, warmup: Boolean = false, checked: Boolean = true) =
        WorkoutSet(id = id, exerciseId = lift, date = day, setNumber = number, weightLbs = load, reps = reps, completed = checked, isWarmup = warmup)
    private val sets = listOf(
        set(1, 1, "2026-09-26", 1450, 10), set(2, 1, "2026-09-26", 1450, 6, 2),
        set(3, 1, "2026-09-26", 2000, 20, warmup = true), set(4, 1, "2026-09-26", 2500, 20, checked = false),
        set(5, 1, "2026-09-30", 1500, 8), // latest eligible top can differ from the ledger's group day
        set(6, 2, "2026-09-09", 1350, 9), set(7, 4, "2026-09-26", 2050, 1),
        set(8, 3, "2026-09-09", 1000, 8), set(9, 6, "2026-09-28", 500, 7),
        set(10, 6, "2026-08-30", 600, 7), set(11, 5, date, 1000, 10),
        set(12, 7, "2026-09-26", 250, 5).copy(isBodyweight = true),
        set(13, 2, "2026-09-26", 2000, 0), // invalid reps do not replace its eligible top
    )
    private val rows = listOf(
        TicketRowUi(1, "Pec Deck", "CHEST", TicketRowKind.MEMBER, null, null, null),
        TicketRowUi(5, "New Lift", "CHEST", TicketRowKind.MEMBER, null, null, null),
        TicketRowUi(3, "Row", "BACK", TicketRowKind.FADED, null, null, null),
        TicketRowUi(4, "Bench Press", "CHEST", TicketRowKind.TAIL, null, null, null),
    )
    private val program = ClipboardProgramUi(1, "Upper 1", 0, true, emptyList(), groupKey = group.key, rows = rows)
    private fun snapshot() = buildEmptyDayLedgerSnapshot(date, listOf(program), model, exercises, sets) { it }

    @Test fun numberToBeatAddsOnlyOneRepAndPreservesStoredFractionalLoad() {
        assertEquals(TopSet(225, 9), numberToBeat(TopSet(225, 8)))
        assertEquals("BW×9", formatTicketTopSet(numberToBeat(TopSet(0, 8))!!, true))
        assertEquals("BW+25×6", formatTicketTopSet(numberToBeat(TopSet(250, 5))!!, true))
        assertNull(numberToBeat(null))
    }

    @Test fun ledgerUsesGroupDayWorkingSetsButLatestEligibleTopForTarget() {
        val ledger = snapshot().programs.single()
        assertEquals("SEP 26", ledger.ledgerLastDateLabel)
        assertEquals(8, ledger.daysSinceLastSession)
        assertEquals(1, ledger.activeGroupCount)
        val pec = ledger.rows.first()
        assertEquals(listOf(LedgerSetUi("145×10", true), LedgerSetUi("145×6", false)), pec.lastSets)
        assertEquals("150×9", pec.toBeatLabel)
        assertNull(ledger.rows[1].toBeatLabel) // selected day is excluded
        assertTrue(ledger.rows[1].lastSets.isEmpty())
        assertEquals("BW+25×6", snapshot().lifts[7]?.toBeatLabel)
    }

    @Test fun alternativesUseFirstRegionAttributedMemoryDaysAndMostRecentUse() {
        val alternatives = slotAlternatives(snapshot(), group.key, 1)
        assertEquals(listOf(1L, 2L, 4L), alternatives.map { it.exerciseId })
        assertEquals(listOf(false, true), alternatives.first().sessionDots)
        assertEquals(listOf(true, true), alternatives[1].sessionDots) // checked zero-rep row still counts as use
        assertEquals("135×9", alternatives[1].topSetLabel)
        assertEquals("Sep 9", alternatives[1].lastDateLabel)
        assertEquals(listOf(7L, 3L), slotAlternatives(snapshot(), group.key, 3).map { it.exerciseId })
        assertEquals(5L, slotAlternatives(snapshot(), group.key, 5).last().exerciseId) // never-trained current row is included
        assertTrue(alternatives.none { it.exerciseId == 6L })
    }

    @Test fun swapKeepsOrderingKindAndUsualIdentityWithChosenLiftsOwnHistory() {
        val state = applyLedgerSwaps(snapshot(), mapOf(ticketInclusionKey(date, group.key) to mapOf(1L to 2L)))
        val ledger = state.programs.single()
        assertEquals(listOf(2L, 5L, 3L, 4L), ledger.rows.map { it.exerciseId })
        assertEquals(rows.map { it.kind }, ledger.rows.map { it.kind })
        val swapped = ledger.rows.first()
        assertEquals(1L, swapped.usualExerciseId)
        assertEquals("Pec Deck", swapped.usualName)
        assertEquals(listOf(LedgerSetUi("135×9", true)), swapped.lastSets)
        assertEquals("Sep 9", swapped.lastSetsDateLabel)
        assertEquals("135×10", swapped.toBeatLabel)
        assertEquals(listOf(2L, 5L), includedTicketLifts(program, null, mapOf(1L to 2L)))
        assertTrue(state.sheets[group.key to 1L]!!.canUndo)
        assertEquals(2L, state.sheets[group.key to 1L]!!.currentExerciseId)
    }

    @Test fun canonicalHistoryAndDotsFoldMergedDonorsOntoKeeper() {
        val donor = set(20, 99, "2026-09-26", 1400, 10)
        val snapshot = buildEmptyDayLedgerSnapshot(date, listOf(program), model, exercises, listOf(donor)) { if (it == 99L) 2L else it }
        assertEquals(listOf(false, true), slotAlternatives(snapshot, group.key, 1).first().sessionDots)
        assertEquals(2L, slotAlternatives(snapshot, group.key, 1).first().exerciseId)
        assertEquals("140×10", snapshot.lifts[2]?.topSetLabel)
    }
}
