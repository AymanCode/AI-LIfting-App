package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.SplitMemberChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomSplitMemberChoiceTest {
    @Test
    fun stored_board_keeps_order_options_and_long_ids() {
        val choice = RoomSplitMemberChoice(9, 7, "[5000000000,3,1,3]", "[2,1,4,2]").toChoice()

        assertEquals(9L, choice.splitId)
        assertEquals(7L, choice.revision)
        assertEquals(listOf(5_000_000_000L, 3L, 1L), choice.board.inSplit)
        assertEquals(listOf(2L, 4L), choice.board.otherOptions)
    }

    @Test
    fun empty_in_list_is_persisted_as_an_explicit_board() {
        val row = SplitMemberChoice(9, 8, EffectiveSplitMembers.of(emptyList(), listOf(3, 1))).toRoomChoice()

        assertEquals(RoomSplitMemberChoice(9, 8, "[]", "[3,1]"), row)
        assertEquals(listOf(3L, 1L), row.toChoice().board.otherOptions)
        assertEquals(emptyList<Long>(), row.toChoice().board.inSplit)
    }
}
