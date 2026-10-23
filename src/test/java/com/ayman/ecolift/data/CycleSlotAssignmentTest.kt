package com.ayman.ecolift.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CycleSlotAssignmentTest {
    private val slots = listOf(
        CycleSlot(id = 20, name = "Pull", orderIndex = 1),
        CycleSlot(id = 10, name = "Push", orderIndex = 0),
    )

    @Test
    fun valid_id_resolves() {
        val day = WorkoutDay(date = "2026-08-01", cycleSlotId = 20, cycleSlotType = 0)

        assertEquals(20L, day.resolvedCycleSlotId(slots))
    }

    @Test
    fun absent_id_returns_null_without_falling_back_to_type() {
        val day = WorkoutDay(date = "2026-08-01", cycleSlotId = 99, cycleSlotType = 0)

        assertNull(day.resolvedCycleSlotId(slots))
    }

    @Test
    fun null_id_with_valid_type_resolves_from_ordered_slots() {
        val day = WorkoutDay(date = "2026-08-01", cycleSlotType = 1)

        assertEquals(20L, day.resolvedCycleSlotId(slots))
    }

    @Test
    fun null_id_with_out_of_range_type_returns_null() {
        val day = WorkoutDay(date = "2026-08-01", cycleSlotType = 2)

        assertNull(day.resolvedCycleSlotId(slots))
    }

    @Test
    fun null_id_and_null_type_return_null() {
        val day = WorkoutDay(date = "2026-08-01")

        assertNull(day.resolvedCycleSlotId(slots))
    }
}
