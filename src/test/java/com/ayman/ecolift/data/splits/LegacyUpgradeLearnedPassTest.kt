package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.LearnedFixtures.cards
import com.ayman.ecolift.data.splits.LearnedFixtures.day
import com.ayman.ecolift.data.splits.LearnedFixtures.inputs
import com.ayman.ecolift.data.splits.LearnedFixtures.members
import com.ayman.ecolift.data.splits.LearnedFixtures.millis
import com.ayman.ecolift.data.splits.LearnedFixtures.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyUpgradeLearnedPassTest {
    @Test
    fun sessions_before_the_upgrade_stamp_do_not_replace_the_hand_made_list() {
        val slotId = 7L
        val sets = day("2026-09-21", 1, 2) + day("2026-10-05", 1, 2)
        val sessionCards = cards("2026-09-21", 1, 2) + cards("2026-10-05", 1, 2)
        val saved = members(slotId, 1, 2, 3, 4)
        val withoutStamp = LearnedSplitsEngine.compute(
            pass(slotId, sets, sessionCards, saved, anchorUpdatedAt = null),
        )
        val stamped = LearnedSplitsEngine.compute(
            pass(slotId, sets, sessionCards, saved, anchorUpdatedAt = millis("2026-10-20")),
        )

        assertEquals(
            listOf(1L, 2L),
            withoutStamp.actions.slotUpserts.single { it.slotId == slotId }.memberIds,
            "two sessions of lifts 1 and 2 must be enough to replace an unstamped four-lift list",
        )
        assertTrue(
            stamped.actions.slotUpserts.none { it.slotId == slotId },
            "stamp should keep the hand-made list, upserts were ${stamped.actions.slotUpserts}",
        )
        assertTrue(slotId !in stamped.actions.slotArchives)
    }

    private fun pass(
        slotId: Long,
        sets: List<WorkoutSet>,
        sessionCards: List<WorkoutExerciseCard>,
        saved: List<SplitExercise>,
        anchorUpdatedAt: Long?,
    ) = inputs(
        asOf = "2026-10-23",
        sets = sets,
        cards = sessionCards,
        slots = listOf(slot(slotId, name = "Push", createdAt = 0, anchorUpdatedAt = anchorUpdatedAt)),
        slotMembers = saved,
    )
}
