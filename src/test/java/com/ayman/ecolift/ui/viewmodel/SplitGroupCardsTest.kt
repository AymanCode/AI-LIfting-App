package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.LearnedFixtures
import com.ayman.ecolift.data.splits.LearnedSplitsEngine
import com.ayman.ecolift.data.splits.SplitMemberChoice
import com.ayman.ecolift.data.splits.WorkedExample
import com.ayman.ecolift.data.splits.WorkedExample.BENCH_PRESS
import com.ayman.ecolift.data.splits.WorkedExample.DIP
import com.ayman.ecolift.data.splits.WorkedExample.INCLINE_BENCH
import com.ayman.ecolift.data.splits.WorkedExample.LAT_PULLDOWN
import com.ayman.ecolift.data.splits.WorkedExample.LEANING_DUMBELL_CURL
import com.ayman.ecolift.data.splits.WorkedExample.UPPER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SplitGroupCardsTest {
    private val model = LearnedSplitsEngine.compute(WorkedExample.inputs())

    private fun choice(slotId: Long, inSplit: List<Long>, options: List<Long> = emptyList()) =
        SplitMemberChoice(slotId, 1, EffectiveSplitMembers.of(inSplit, options))

    private fun cards(
        slots: List<CycleSlot> = listOf(WorkedExample.upper1),
        choices: Map<Long, SplitMemberChoice> = emptyMap(),
        sets: List<WorkoutSet> = WorkedExample.sets,
        canonicalId: (Long) -> Long = { it },
    ) = buildSplitGroupCards(model, WorkedExample.AS_OF, WorkedExample.exercises, sets, canonicalId, slots, choices = choices)

    private fun List<SplitCardMemberUi>.names() = map { it.name }

    @Test
    fun cardsRunInDueOrderWithFadedLiftsAsOptions() {
        val all = cards()
        assertEquals(listOf("Chest · Arms", "Legs", "Upper 1", "Legs"), all.map { it.name })
        assertEquals(true, all.first().isDue)
        val upper = all.single { it.name == "Upper 1" }
        assertEquals(4, upper.sessions)
        assertEquals("2026-09-26", upper.lastSessionDate)
        assertEquals(listOf("Lat Pulldown", "Rear Shoulder Single Pec", "Cbum Tricep Extension", "Leaning Hammer Curl"), upper.members.names())
        assertEquals(listOf("Incline Bench Press"), upper.otherOptions.names())
        assertEquals("140×10", upper.members.first().metrics.topSetLabel)
    }

    @Test
    fun aBoardDecidesTheCardIncludingALiftTheGroupNeverHad() {
        val upper = cards(choices = mapOf(7L to choice(7, listOf(BENCH_PRESS, INCLINE_BENCH), listOf(LAT_PULLDOWN))))
            .single { it.name == "Upper 1" }
        assertEquals(listOf("Bench Press", "Incline Bench Press"), upper.members.names())
        assertEquals(listOf("Lat Pulldown", "Rear Shoulder Single Pec", "Cbum Tricep Extension", "Leaning Hammer Curl"), upper.otherOptions.names())
    }

    @Test
    fun creationNumbersRankEverySlotRetiredOnesIncluded() {
        val archived = CycleSlot(id = 3, name = "Old", archivedAt = 1L)
        val retired = CycleSlot(id = 5, name = "Gone", suppressedAt = 1L)
        val orphan = CycleSlot(id = 40, name = "Arms day", colorIndex = 9)
        val all = cards(slots = listOf(WorkedExample.upper1, archived, retired, orphan))
        assertEquals(3, all.single { it.name == "Upper 1" }.creationNumber)
        assertEquals(4, all.single { it.name == "Arms day" }.creationNumber)
        assertNull(all.first { it.name == "Chest · Arms" }.creationNumber)
        assertEquals(listOf("Chest · Arms", "Legs", "Upper 1", "Legs", "Arms day"), all.map { it.name })
    }

    @Test
    fun anUnnamedSlotWithABoardShowsEvenWithNoGroup() {
        val unnamed = CycleSlot(id = 41, name = "", colorIndex = 8)
        assertEquals(4, cards(slots = listOf(WorkedExample.upper1, unnamed)).size)
        val card = cards(slots = listOf(WorkedExample.upper1, unnamed), choices = mapOf(41L to choice(41, listOf(DIP, LEANING_DUMBELL_CURL))))
            .single { it.slotId == 41L }
        assertEquals("Arms · Chest", card.name)
        assertEquals(true, card.isUnnamed)
        assertEquals(listOf("Dip", "Leaning Dumbell Curl"), card.members.names())
        assertEquals(0, card.sessions)
    }

    @Test
    fun aDuplicateLeftWithABoardStaysOnTheList() {
        val loser = LearnedFixtures.slot(31, learned = true)
        val slots = listOf(WorkedExample.upper1.copy(learned = true), loser)
        val inputs = WorkedExample.inputs().copy(
            slots = slots,
            slotMembers = WorkedExample.upper1Members + LearnedFixtures.members(31, *UPPER.toLongArray()),
            memberChoices = mapOf(31L to choice(31, listOf(INCLINE_BENCH))),
        )
        val duplicateModel = LearnedSplitsEngine.compute(inputs)
        assertEquals(null, duplicateModel.groupForSlot(31))
        val all = buildSplitGroupCards(duplicateModel, WorkedExample.AS_OF, WorkedExample.exercises, WorkedExample.sets, { it }, slots,
            savedRows = inputs.slotMembers, choices = inputs.memberChoices)
        val card = all.single { it.slotId == 31L }
        assertEquals(listOf("Incline Bench Press"), card.members.names())
        assertEquals(listOf("Lat Pulldown", "Rear Shoulder Single Pec", "Cbum Tricep Extension", "Leaning Hammer Curl"), card.otherOptions.names())
    }

    @Test
    fun theTicketStartsWithExactlyTheCardsInLifts() {
        val unnamed = CycleSlot(id = 41, name = "", colorIndex = 8)
        val slots = listOf(WorkedExample.upper1, unnamed)
        for (choices in listOf(emptyMap(), mapOf(7L to choice(7, listOf(BENCH_PRESS, INCLINE_BENCH), listOf(LAT_PULLDOWN)), 41L to choice(41, listOf(DIP))))) {
            val programs = buildLearnedPrograms(model, WorkedExample.AS_OF, WorkedExample.exercises, WorkedExample.sets, slots, choices = choices) { it }
            val cardLists = cards(slots, choices).map { it.slotId to it.members.map { member -> member.exerciseId } }
            assertEquals(cardLists, programs.map { it.slotId to it.lifts.map { lift -> lift.exerciseId } })
        }
        val chosen = buildLearnedPrograms(model, WorkedExample.AS_OF, WorkedExample.exercises, WorkedExample.sets,
            choices = mapOf(7L to choice(7, listOf(BENCH_PRESS, INCLINE_BENCH), listOf(LAT_PULLDOWN)))) { it }.single { it.slotId == 7L }
        assertEquals(TicketRowKind.MEMBER, chosen.rows.single { it.exerciseId == INCLINE_BENCH }.kind)
        assertEquals(TicketRowKind.OPTION, chosen.rows.single { it.exerciseId == LAT_PULLDOWN }.kind)
        val automatic = buildLearnedPrograms(model, WorkedExample.AS_OF, WorkedExample.exercises, WorkedExample.sets) { it }.single { it.slotId == 7L }
        assertEquals(TicketRowKind.FADED, automatic.rows.single { it.exerciseId == INCLINE_BENCH }.kind)
    }

    @Test
    fun aMergedDonorsSetsCountForItsKeeper() {
        val donor = 9_001L
        val sets = WorkedExample.sets.map { if (it.exerciseId == LAT_PULLDOWN && it.date == "2026-09-26") it.copy(exerciseId = donor, reps = 12) else it }
        val upper = cards(sets = sets, canonicalId = { if (it == donor) LAT_PULLDOWN else it }).single { it.name == "Upper 1" }
        assertEquals("140×12", upper.members.single { it.exerciseId == LAT_PULLDOWN }.metrics.topSetLabel)
    }
}
