package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.splits.LearnedFixtures.members
import com.ayman.ecolift.data.splits.LearnedFixtures.millis
import com.ayman.ecolift.data.splits.LearnedFixtures.slot
import com.ayman.ecolift.data.splits.WorkedExample.CHEST_ARMS
import com.ayman.ecolift.data.splits.WorkedExample.LEGS_HACK
import com.ayman.ecolift.data.splits.WorkedExample.UPPER
import com.ayman.ecolift.data.splits.WorkedExample.UPPER_1_SLOT
import com.ayman.ecolift.data.splits.WorkedExample.key
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitChoicesEngineTest {
    private fun choice(slotId: Long, inSplit: List<Long>, options: List<Long> = emptyList()) =
        SplitMemberChoice(slotId, revision = 1, board = EffectiveSplitMembers.of(inSplit, options))

    @Test
    fun aSeedBuiltOnlyInTheEditorLearnsItsClusterWithoutASecondSlot() {
        val editorOnly = WorkedExample.inputs().copy(slotMembers = emptyList())
        val withoutBoard = LearnedSplitsEngine.compute(editorOnly)
        assertEquals(listOf(-1L, -2L, -3L, -4L), withoutBoard.actions.slotUpserts.map { it.modelSlotId })

        val model = LearnedSplitsEngine.compute(editorOnly.copy(memberChoices = mapOf(UPPER_1_SLOT to choice(UPPER_1_SLOT, UPPER))))
        val upper = model.group(key(UPPER))!!
        assertEquals(UPPER_1_SLOT, upper.slotId)
        assertEquals(true, upper.learned)
        assertEquals(listOf(-1L, -2L, -3L, UPPER_1_SLOT), model.actions.slotUpserts.map { it.modelSlotId })
        assertEquals(
            SlotUpsert(UPPER_1_SLOT, UPPER_1_SLOT, "Upper 1", learned = true, memberIds = upper.ticketOrder),
            model.actions.slotUpserts.single { it.slotId == UPPER_1_SLOT },
        )
    }

    @Test
    fun extraChosenLiftsOnALearnedSlotLeaveItsClusterAndAttributionAlone() {
        val learned = WorkedExample.inputs().copy(slots = listOf(WorkedExample.upper1.copy(learned = true)))
        val board = choice(UPPER_1_SLOT, CHEST_ARMS + LEGS_HACK, UPPER)
        val without = LearnedSplitsEngine.compute(learned)
        val with = LearnedSplitsEngine.compute(learned.copy(memberChoices = mapOf(UPPER_1_SLOT to board)))
        assertEquals(UPPER_1_SLOT, with.group(key(UPPER))!!.slotId)
        assertEquals(without.groups, with.groups)
        assertEquals(without.attribution, with.attribution)
        assertEquals(without.actions, with.actions)
    }

    @Test
    fun aSlotWithABoardIsNeverArchivedAutomatically() {
        val idle = slot(30, learned = true, anchorUpdatedAt = millis("2026-08-20"))
        val loser = slot(31, learned = true)
        val base = WorkedExample.inputs().let {
            it.copy(
                slots = listOf(WorkedExample.upper1.copy(learned = true), idle, loser),
                slotMembers = it.slotMembers + members(30, 900, 901) + members(31, *UPPER.toLongArray()),
            )
        }
        assertEquals(listOf(30L, 31L), LearnedSplitsEngine.compute(base).actions.slotArchives)
        val chosen = base.copy(memberChoices = mapOf(30L to choice(30, listOf(900)), 31L to choice(31, emptyList())))
        assertEquals(emptyList<Long>(), LearnedSplitsEngine.compute(chosen).actions.slotArchives)
    }
}
