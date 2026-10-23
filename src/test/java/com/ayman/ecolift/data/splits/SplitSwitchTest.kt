package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplitSwitchTest {
    private fun set(
        id: Long = 0,
        exerciseId: Long = 10,
        setNumber: Int = 1,
        weightLbs: Int? = 135,
        reps: Int? = 8,
        isBodyweight: Boolean = false,
        completed: Boolean = false,
        restTimeSeconds: Int? = null,
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        date = DATE,
        setNumber = setNumber,
        weightLbs = weightLbs,
        reps = reps,
        isBodyweight = isBodyweight,
        completed = completed,
        restTimeSeconds = restTimeSeconds,
    )

    private fun request(
        target: SplitSwitchTarget,
        intent: SplitSwitchIntent = SplitSwitchIntent.UserSelection,
    ) = SplitSwitchRequest(
        requestId = 1,
        date = DATE,
        target = target,
        intent = intent,
    )

    @Test
    fun untouched_weighted_set_is_not_dirty() {
        val seed = set(id = 1)
        val day = seed.copy(id = -10)

        assertFalse(SplitSwitch.isDirty(listOf(day), listOf(seed)))
    }

    @Test
    fun completed_set_is_dirty() {
        val seed = set()

        assertTrue(SplitSwitch.isDirty(listOf(seed.copy(completed = true)), listOf(seed)))
    }

    @Test
    fun edited_weight_is_dirty() {
        val seed = set(weightLbs = 135)

        assertTrue(SplitSwitch.isDirty(listOf(seed.copy(weightLbs = 145)), listOf(seed)))
    }

    @Test
    fun edited_reps_is_dirty() {
        val seed = set(reps = 8)

        assertTrue(SplitSwitch.isDirty(listOf(seed.copy(reps = 9)), listOf(seed)))
    }

    @Test
    fun bodyweight_added_load_edit_is_dirty() {
        val seed = set(weightLbs = 25, isBodyweight = true)

        assertTrue(SplitSwitch.isDirty(listOf(seed.copy(weightLbs = 45)), listOf(seed)))
    }

    @Test
    fun is_bodyweight_toggle_is_dirty() {
        val seed = set(isBodyweight = false)

        assertTrue(SplitSwitch.isDirty(listOf(seed.copy(isBodyweight = true)), listOf(seed)))
    }

    @Test
    fun recorded_rest_on_incomplete_row_is_dirty() {
        val seed = set(restTimeSeconds = null)

        assertTrue(SplitSwitch.isDirty(listOf(seed.copy(restTimeSeconds = 90)), listOf(seed)))
    }

    @Test
    fun extra_exercise_is_dirty() {
        val seed = set()
        val day = listOf(seed, set(exerciseId = 11))

        assertTrue(SplitSwitch.isDirty(day, listOf(seed)))
    }

    @Test
    fun extra_set_is_dirty() {
        val seed = set()
        val day = listOf(seed, set(setNumber = 2))

        assertTrue(SplitSwitch.isDirty(day, listOf(seed)))
    }

    @Test
    fun same_total_count_but_different_natural_keys_is_dirty() {
        val seed = listOf(set(exerciseId = 10), set(exerciseId = 11))
        val day = listOf(set(exerciseId = 10), set(exerciseId = 12))

        assertTrue(SplitSwitch.isDirty(day, seed))
    }

    @Test
    fun database_ids_are_not_part_of_identity() {
        val seed = set(id = 25)
        val day = seed.copy(id = -99)

        assertFalse(SplitSwitch.isDirty(listOf(day), listOf(seed)))
    }

    @Test
    fun user_selection_of_assigned_slot_converts_to_clear() {
        val original = request(SplitSwitchTarget.Saved(slotId = 7))

        val plan = SplitSwitch.plan(
            request = original,
            currentAssignedSlotId = 7,
            daySets = emptyList(),
            seedSets = emptyList(),
        )

        assertEquals(
            SplitSwitchPlan.Apply(original.copy(target = SplitSwitchTarget.Clear)),
            plan,
        )
    }

    @Test
    fun deep_link_to_assigned_slot_does_not_convert_to_clear() {
        val original = request(
            target = SplitSwitchTarget.Saved(slotId = 7),
            intent = SplitSwitchIntent.DeepLinkLoad,
        )

        val plan = SplitSwitch.plan(
            request = original,
            currentAssignedSlotId = 7,
            daySets = emptyList(),
            seedSets = emptyList(),
        )

        assertEquals(SplitSwitchPlan.Apply(original), plan)
    }

    @Test
    fun clear_on_blank_unassigned_date_is_noop() {
        val plan = SplitSwitch.plan(
            request = request(SplitSwitchTarget.Clear),
            currentAssignedSlotId = null,
            daySets = emptyList(),
            seedSets = emptyList(),
        )

        assertEquals(SplitSwitchPlan.NoOp, plan)
    }

    @Test
    fun dirty_plan_counts_every_current_set() {
        val seed = listOf(set(exerciseId = 10), set(exerciseId = 11))
        val day = listOf(seed[0].copy(reps = 9), seed[1])
        val switchRequest = request(SplitSwitchTarget.Saved(slotId = 8))

        val plan = SplitSwitch.plan(
            request = switchRequest,
            currentAssignedSlotId = 7,
            daySets = day,
            seedSets = seed,
        )

        assertEquals(SplitSwitchPlan.NeedsConfirm(switchRequest, affectedSetCount = 2), plan)
    }

    @Test
    fun precondition_ignores_database_ids_and_input_order() {
        val first = set(id = 1, exerciseId = 10)
        val second = set(id = 2, exerciseId = 11)
        val workoutDay = WorkoutDay(date = DATE, cycleSlotId = 7)

        val left = SplitSwitchPrecondition.capture(
            workoutDay = workoutDay,
            daySets = listOf(first, second),
            baselineSeed = listOf(first, second),
            desiredSeed = listOf(first, second),
        )
        val right = SplitSwitchPrecondition.capture(
            workoutDay = workoutDay.copy(),
            daySets = listOf(second.copy(id = -2), first.copy(id = -1)),
            baselineSeed = listOf(second.copy(id = 20), first.copy(id = 10)),
            desiredSeed = listOf(second.copy(id = 200), first.copy(id = 100)),
        )

        assertEquals(left, right)
    }

    @Test
    fun duplicate_natural_key_rows_are_not_collapsed_when_comparing() {
        // A day holding two rows for the same (exercise, set number) differs from a seed that
        // generated one. Keying by natural key instead of comparing multisets would drop the
        // duplicate, call the day clean, and delete it without confirming.
        val seed = listOf(set(id = 10))
        val day = listOf(set(id = -1), set(id = -2))

        assertTrue(SplitSwitch.isDirty(daySets = day, seedSets = seed))
    }

    @Test
    fun identical_multisets_with_duplicate_keys_are_clean() {
        val seed = listOf(set(id = 10), set(id = 11))
        val day = listOf(set(id = -1), set(id = -2))

        assertFalse(SplitSwitch.isDirty(daySets = day, seedSets = seed))
    }

    private companion object {
        const val DATE = "2026-08-10"
    }
}
