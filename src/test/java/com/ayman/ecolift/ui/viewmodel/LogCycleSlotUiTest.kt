package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.WorkoutDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogCycleSlotUiTest {

    @Test
    fun `current cycle slot uses saved split name when slot still exists`() {
        val slotUi = buildCurrentCycleSlotUi(
            currentDay = WorkoutDay(
                date = "2026-06-07",
                cycleSlotId = 42L,
                cycleSlotOccurrence = 3,
            ),
            slots = listOf(CycleSlot(id = 42L, name = "Push", orderIndex = 0)),
        )

        assertEquals("Push", slotUi?.label)
        assertEquals("Pu", slotUi?.shortLabel)
        assertEquals(42, slotUi?.type)
        assertEquals(3, slotUi?.occurrence)
    }

    @Test
    fun `current cycle slot is empty when workout day points at deleted split`() {
        val slotUi = buildCurrentCycleSlotUi(
            currentDay = WorkoutDay(
                date = "2026-06-07",
                cycleSlotId = 42L,
                cycleSlotOccurrence = 3,
            ),
            slots = emptyList(),
        )

        assertNull(slotUi)
    }
}
