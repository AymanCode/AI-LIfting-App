package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.splits.LearnedInputs
import com.ayman.ecolift.data.splits.LearnedSplitsEngine
import com.ayman.ecolift.data.splits.SplitAlternative
import com.ayman.ecolift.web.demo.DemoData
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardTakenSwapTest {
    @Test fun chipNeedsSavedGroupAlternativesAndNoCheckedSet() {
        assertTrue(canUseTakenChip(1, 2, false))
        assertFalse(canUseTakenChip(1, 1, false))
        assertFalse(canUseTakenChip(1, 2, true))
        assertFalse(canUseTakenChip(-1, 2, false))
        assertFalse(canUseTakenChip(0, 2, false))
        assertFalse(canUseTakenChip(null, 2, false))
    }

    @Test fun demoPracticeDayHasAChestAlternativeAndAFixedArmsLift() {
        val anchor = LocalDate(2026, 10, 4)
        val seed = DemoData.generate(anchor)
        val model = LearnedSplitsEngine.compute(LearnedInputs(anchor, seed.sets, emptyList(), { it },
            slots = seed.slots, slotMembers = seed.splitExercises, workoutDays = seed.workoutDays,
            archivedCycleEndDates = seed.archives.map { it.endDate }))
        val practice = "2026-10-05"
        val group = model.visibleGroups.first { it.slotId == seed.slots.first().id }
        val programs = buildLearnedPrograms(model, practice, seed.exercises, seed.sets) { it }
        val snapshot = buildEmptyDayLedgerSnapshot(practice, programs, model, seed.exercises, seed.sets) { it }
        val bench = seed.exercises.first { it.name == "Barbell Bench Press" }.id
        val fixed = seed.exercises.first { it.name == "Cable Triceps Pressdown" }.id
        assertTrue(canUseTakenChip(group.slotId, slotAlternatives(snapshot, group.key, bench).size, false))
        assertEquals(1, slotAlternatives(snapshot, group.key, fixed).size)
        assertTrue(seed.sets.filter { it.date == practice }.all { !it.completed })
    }

    @Test fun startRetainsEverySourceTargetRelationshipAndSkipsUnsavedGroups() {
        val swaps = mapOf(10L to 40L, 20L to 50L, 30L to 40L)
        assertEquals(listOf(SplitAlternative(1, 10, 40), SplitAlternative(1, 20, 50), SplitAlternative(1, 30, 40)), proposalAppends(1, swaps))
        assertTrue(proposalAppends(-1, swaps).isEmpty())
        assertTrue(proposalAppends(null, swaps).isEmpty())
        assertTrue(proposalAppends(1, emptyMap()).isEmpty())
    }
}
