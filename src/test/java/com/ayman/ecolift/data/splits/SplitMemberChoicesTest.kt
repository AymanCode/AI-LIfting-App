package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.splits.LearnedFixtures.cards
import com.ayman.ecolift.data.splits.LearnedFixtures.members
import com.ayman.ecolift.data.splits.LearnedFixtures.set
import com.ayman.ecolift.data.splits.LearnedFixtures.slot
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitMemberChoicesTest {
    private fun group(
        ticketOrder: List<Long>,
        faded: Set<Long> = emptySet(),
        optional: Set<Long> = emptySet(),
        slotId: Long = 7,
    ) = LearnedGroup(
        key = "c:x", slotId = slotId, name = "Push", colorIndex = 0, members = ticketOrder.sorted(),
        ticketOrder = ticketOrder, coOccurrenceDays = emptyList(), sessions = emptyList(), fadedMembers = faded,
        isCluster = true, learned = true, createdAt = 0, isFresh = false, isDormant = false, suppressed = false,
        optionalMembers = optional,
    )

    private fun board(inSplit: List<Long>, options: List<Long> = emptyList()) = EffectiveSplitMembers.of(inSplit, options)

    private fun choice(inSplit: List<Long>, options: List<Long> = emptyList(), slotId: Long = 7) =
        SplitMemberChoice(slotId, revision = 1, board = board(inSplit, options))

    private fun EffectiveSplitMembers.lists() = inSplit to otherOptions

    @Test
    fun aBoardKeepsEachLiftOnceAndAnIdInBothListsStaysIn() {
        assertEquals(listOf(3L, 1L) to listOf(2L), board(listOf(3, 1, 3), listOf(1, 2, 2)).lists())
    }

    @Test
    fun withoutABoardFadedAndOptionalLiftsAreOptionsInEngineOrder() {
        val members = effectiveSplitMembers(7, group(listOf(1, 2, 3), faded = setOf(3)), emptyList(), null) { it }
        assertEquals(listOf(1L, 2L) to listOf(3L), members.lists())
        val withOptional = effectiveSplitMembers(7, group(listOf(4, 1, 5, 2), faded = setOf(2), optional = setOf(5)), emptyList(), null) { it }
        assertEquals(listOf(4L, 1L) to listOf(2L, 5L), withOptional.lists())
    }

    @Test
    fun withoutABoardAUserAddedRowOutsideTheGroupIsAnOption() {
        val rows = members(7, 1, 2) + SplitExercise(id = 9, splitId = 7, exerciseId = 8, orderIndex = 2, userAdded = true)
        assertEquals(listOf(1L, 2L) to listOf(8L), effectiveSplitMembers(7, group(listOf(1, 2)), rows, null) { it }.lists())
    }

    @Test
    fun aSlotWithNoGroupUsesItsSavedRowsInSavedOrder() {
        val rows = members(7, 5, 2, 9) + members(8, 1)
        assertEquals(listOf(5L, 2L, 9L) to emptyList<Long>(), effectiveSplitMembers(7, null, rows, null) { it }.lists())
    }

    @Test
    fun aBoardWinsOverTheEngineAndNewDiscoveriesBecomeOptions() {
        val members = effectiveSplitMembers(7, group(listOf(2, 4)), emptyList(), choice(listOf(3, 1, 9), listOf(2))) { it }
        assertEquals(listOf(3L, 1L, 9L) to listOf(2L, 4L), members.lists())
    }

    @Test
    fun aBoardKeepsAFadedLiftInAndNeverFallsBackWhenEmpty() {
        val faded = effectiveSplitMembers(7, group(listOf(1, 2, 3), faded = setOf(3)), emptyList(), choice(listOf(3, 1))) { it }
        assertEquals(listOf(3L, 1L) to listOf(2L), faded.lists())
        val empty = effectiveSplitMembers(7, null, members(7, 1, 2), choice(emptyList(), listOf(1))) { it }
        assertEquals(emptyList<Long>() to listOf(1L, 2L), empty.lists())
    }

    @Test
    fun aMergedDonorCollapsesOntoItsKeeperAndInWinsAcrossLists() {
        val keeper = mapOf(8L to 3L)
        val members = effectiveSplitMembers(7, null, emptyList(), choice(listOf(8, 3, 1), listOf(5))) { keeper[it] ?: it }
        assertEquals(listOf(3L, 1L) to listOf(5L), members.lists())
        val across = effectiveSplitMembers(7, null, emptyList(), choice(listOf(3), listOf(8, 5))) { keeper[it] ?: it }
        assertEquals(listOf(3L) to listOf(5L), across.lists())
    }

    @Test
    fun includeAppendsOnceAndExcludeMovesToTheEndOfOptions() {
        val start = board(listOf(1, 3), listOf(2, 4))
        val included = applyEdit(start, SplitMemberEdit.Include(2))
        assertEquals(listOf(1L, 3L, 2L) to listOf(4L), included.lists())
        assertEquals(included, applyEdit(included, SplitMemberEdit.Include(2)))
        val typed = applyEdit(start, SplitMemberEdit.Include(9))
        assertEquals(listOf(1L, 3L, 9L) to listOf(2L, 4L), typed.lists())
        val excluded = applyEdit(start, SplitMemberEdit.Exclude(1))
        assertEquals(listOf(3L) to listOf(2L, 4L, 1L), excluded.lists())
        assertEquals(excluded, applyEdit(excluded, SplitMemberEdit.Exclude(1)))
    }

    @Test
    fun moveBeforeReordersInOnlyAndIgnoresLiftsThatAreNotIn() {
        val start = board(listOf(3, 1, 9), listOf(2))
        val moved = applyEdit(start, SplitMemberEdit.MoveBefore(9, 3))
        assertEquals(listOf(9L, 3L, 1L) to listOf(2L), moved.lists())
        assertEquals(moved, applyEdit(moved, SplitMemberEdit.MoveBefore(9, 3)))
        assertEquals(listOf(1L, 9L, 3L) to listOf(2L), applyEdit(start, SplitMemberEdit.MoveBefore(3, null)).lists())
        assertEquals(start, applyEdit(start, SplitMemberEdit.MoveBefore(2, 3)))
        assertEquals(start, applyEdit(start, SplitMemberEdit.MoveBefore(3, 2)))
    }

    @Test
    fun replacePutsDroppedLiftsAtTheFrontOfOptionsInPriorOrder() {
        val start = board(listOf(1, 3, 2), listOf(5, 4))
        val replaced = applyEdit(start, SplitMemberEdit.Replace(listOf(4, 2, 4)))
        assertEquals(listOf(4L, 2L) to listOf(1L, 3L, 5L), replaced.lists())
        assertEquals(replaced, applyEdit(replaced, SplitMemberEdit.Replace(listOf(4, 2))))
    }

    @Test
    fun onlyASeedLearnsFromItsBoard() {
        val slots = listOf(slot(1, learned = false), slot(2, learned = true), slot(3, learned = false))
        val rows = members(2, 1, 2, 3, 4) + members(3, 6)
        val choices = mapOf(
            1L to choice(listOf(5, 6), slotId = 1),
            2L to choice(listOf(9, 8, 7, 6, 5), slotId = 2),
        )
        assertEquals(
            mapOf(1L to listOf(5L, 6L), 2L to listOf(1L, 2L, 3L, 4L), 3L to listOf(6L)),
            learningAnchors(slots, rows, choices) { it },
        )
    }

    @Test
    fun aWorkoutsLiftsAreItsCheckedLiftsInCardOrder() {
        val date = "2026-10-10"
        val sets = listOf(
            set(date, 4, setNumber = 1),
            set(date, 2, setNumber = 2, warmup = true),
            set(date, 6, setNumber = 3, completed = false),
            set(date, 9, setNumber = 5),
            set(date, 8, setNumber = 4),
            set("2026-10-09", 1, setNumber = 1),
            set(date, 10, setNumber = 6),
        )
        val keeper = mapOf(10L to 4L)
        assertEquals(listOf(2L, 4L, 8L, 9L), workoutLiftsFor(date, sets, cards(date, 2, 4, 6)) { keeper[it] ?: it })
    }
}
