package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DayContentSignatureTest {
    private val d = "2026-10-30"
    private val set = WorkoutSet(id = -1, exerciseId = 10, date = d, setNumber = 1, weightLbs = 100, reps = 8, syncId = "a", rowRevision = 1)
    private val card = WorkoutExerciseCard(id = "c1", date = d, exerciseId = 10, position = 0, displayMode = WorkoutCardDisplayMode.EXPANDED, origin = WorkoutCardOrigin.SPLIT, rowRevision = 1)

    @Test fun ignores_ids_revisions_and_raw_positions() {
        val before = dayContentSignature(d, listOf(set), listOf(card))
        val synced = dayContentSignature(d, listOf(set.copy(id = 5512, rowRevision = 2)), listOf(card.copy(id = "c9", position = 4, rowRevision = 3)))
        assertEquals(before, synced)
    }

    @Test fun catches_checked_weight_warmup_and_park() {
        val before = dayContentSignature(d, listOf(set), listOf(card))
        assertNotEquals(before, dayContentSignature(d, listOf(set.copy(completed = true)), listOf(card)))
        assertNotEquals(before, dayContentSignature(d, listOf(set.copy(weightLbs = 105)), listOf(card)))
        assertNotEquals(before, dayContentSignature(d, listOf(set.copy(isWarmup = true)), listOf(card)))
        assertNotEquals(before, dayContentSignature(d, listOf(set), listOf(card.copy(displayMode = WorkoutCardDisplayMode.COMPACT))))
        assertNotEquals(before, dayContentSignature(d, listOf(set, set.copy(setNumber = 2, syncId = "b")), listOf(card)))
    }

    @Test fun ignores_other_days() {
        val other = set.copy(date = "2026-10-29", syncId = "z")
        assertEquals(dayContentSignature(d, listOf(set), listOf(card)), dayContentSignature(d, listOf(set, other), listOf(card)))
    }
}
