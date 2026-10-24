package com.ayman.ecolift.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CycleSlotRetirementTest {
    private val retiredAt = 1_786_800_000_000L

    @Test
    fun retiring_slots_keeps_rows_and_children_but_removes_them_from_active_views() {
        val active = CycleSlot(id = 1, name = "Push", orderIndex = 0)
        val alreadyRetired = CycleSlot(id = 2, name = "Old Pull", orderIndex = 1, archivedAt = 10L)
        val children = listOf(
            SplitExercise(id = 11, splitId = 1, exerciseId = 101),
            SplitExercise(id = 12, splitId = 2, exerciseId = 102),
        )

        val allSlots = listOf(active, alreadyRetired).retireCycleSlots(setOf(active.id, alreadyRetired.id), retiredAt)

        assertEquals(2, allSlots.size)
        assertEquals(retiredAt, allSlots.single { it.id == active.id }.archivedAt)
        assertEquals(10L, allSlots.single { it.id == alreadyRetired.id }.archivedAt)
        assertTrue(allSlots.activeCycleSlots().isEmpty())
        assertEquals(2, children.size)
        assertTrue(children.forActiveCycleSlots(allSlots).isEmpty())
    }

    @Test
    fun next_order_restarts_at_zero_and_cycle_count_ignores_retired_slots() {
        val retired = listOf(
            CycleSlot(id = 1, name = "Push", orderIndex = 0, archivedAt = retiredAt),
            CycleSlot(id = 2, name = "Pull", orderIndex = 1, archivedAt = retiredAt),
        )

        assertEquals(0, retired.nextActiveCycleSlotOrder())
        assertEquals(
            Cycle(numTypes = 1, nextSessionType = 0),
            Cycle(numTypes = 9, nextSessionType = 8).withActiveCycleSlotCount(retired),
        )

        val withNewSlot = retired + CycleSlot(id = 3, name = "New cycle", orderIndex = 0)
        assertEquals(1, withNewSlot.nextActiveCycleSlotOrder())
        assertEquals(1, Cycle(numTypes = 9).withActiveCycleSlotCount(withNewSlot).numTypes)
    }

    @Test
    fun historical_day_resolves_against_its_retired_slot_instead_of_deleted_bucket() {
        val retiredSlot = CycleSlot(
            id = 44,
            name = "Retired Push",
            orderIndex = 0,
            archivedAt = retiredAt,
        )
        val snapshot = CycleSnapshotBuilder.build(
            startDate = "2026-07-01",
            endDate = "2026-07-01",
            slots = listOf(retiredSlot),
            splitExercises = listOf(SplitExercise(id = 1, splitId = 44, exerciseId = 7)),
            workoutDays = listOf(WorkoutDay(date = "2026-07-01", cycleSlotId = 44)),
            sets = listOf(
                WorkoutSet(
                    id = 1,
                    exerciseId = 7,
                    date = "2026-07-01",
                    setNumber = 1,
                    weightLbs = 1000,
                    reps = 10,
                    completed = true,
                ),
            ),
            exerciseNames = mapOf(7L to ExerciseMeta("Bench", false)),
        )

        assertEquals(SplitBucketKind.Real, snapshot.splits.single().bucketKind)
        assertEquals("Retired Push", snapshot.splits.single().name)
        assertNull(snapshot.splits.firstOrNull { it.bucketKind == SplitBucketKind.Deleted })
    }
}
