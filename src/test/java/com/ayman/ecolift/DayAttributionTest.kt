package com.ayman.ecolift

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.buildDayInfoMap
import com.ayman.ecolift.data.splits.volumeBrightThreshold
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayAttributionTest {
    private val slots = listOf(
        CycleSlot(id = 20L, name = "Pull", orderIndex = 0, colorIndex = 0),
        CycleSlot(id = 10L, name = "Push", orderIndex = 1, colorIndex = 1),
    )

    @Test
    fun a_retired_slot_keeps_its_colour_and_label() {
        val retired = CycleSlot(id = 30L, name = "Old Legs", orderIndex = 0, archivedAt = 1L, colorIndex = 5)
        val info = buildDayInfoMap(
            completedSetsByDate = mapOf("2026-07-16" to listOf(set(id = 1L, exerciseId = 1L, completed = true))),
            setVolume = { 0.0 },
            workoutDays = listOf(WorkoutDay(date = "2026-07-16", cycleSlotId = 30L)),
            slots = slots + retired,
        ).getValue(LocalDate.parse("2026-07-16"))
        assertEquals(5, info.visualIndex)
        assertEquals("Old Legs", info.label)
    }

    @Test
    fun slot_match_uses_the_slots_colour_index() {
        val result = build(
            date = "2026-07-16",
            workoutDay = WorkoutDay(date = "2026-07-16", cycleSlotType = 0, cycleSlotId = 10L),
        )

        assertEquals(10L, result.splitId)
        assertEquals(1, result.visualIndex)
    }

    @Test
    fun deleted_slot_is_unattributed() {
        val result = build(
            date = "2026-07-16",
            workoutDay = WorkoutDay(date = "2026-07-16", cycleSlotType = 1, cycleSlotId = 99L),
        )

        assertNull(result.splitId)
        assertNull(result.visualIndex)
    }

    @Test
    fun null_slot_id_is_unattributed_even_with_legacy_type() {
        val result = build(
            date = "2026-07-16",
            workoutDay = WorkoutDay(date = "2026-07-16", cycleSlotType = 1, cycleSlotId = null),
        )

        assertNull(result.splitId)
        assertNull(result.visualIndex)
    }

    @Test
    fun volume_and_exercise_count_use_completed_sets_only() {
        val date = "2026-07-16"
        val result = buildDayInfoMap(
            completedSetsByDate = mapOf(
                date to listOf(
                    set(id = 1L, exerciseId = 1L, completed = true),
                    set(id = 2L, exerciseId = 1L, completed = true),
                    set(id = 3L, exerciseId = 2L, completed = true),
                    set(id = 4L, exerciseId = 3L, completed = false),
                )
            ),
            setVolume = { it.id * 100.0 },
            workoutDays = listOf(WorkoutDay(date = date, cycleSlotId = 20L)),
            slots = slots,
        ).getValue(LocalDate.parse(date))

        assertEquals(600L, result.volumeLbs)
        assertEquals(2, result.exerciseCount)
    }

    @Test
    fun p75_uses_nearest_rank() {
        assertEquals(300L, volumeBrightThreshold(listOf(100L, 200L, 300L, 400L)))
    }

    @Test
    fun empty_volumes_have_zero_threshold() {
        assertEquals(0L, volumeBrightThreshold(emptyList()))
    }

    @Test
    fun unparseable_dates_are_skipped() {
        val result = buildDayInfoMap(
            completedSetsByDate = mapOf("not-a-date" to listOf(set(completed = true))),
            setVolume = { 100.0 },
            workoutDays = listOf(WorkoutDay(date = "not-a-date", cycleSlotId = 20L)),
            slots = slots,
        )

        assertTrue(result.isEmpty())
    }

    private fun build(date: String, workoutDay: WorkoutDay) = buildDayInfoMap(
        completedSetsByDate = mapOf(date to listOf(set(completed = true))),
        setVolume = { 100.0 },
        workoutDays = listOf(workoutDay),
        slots = slots,
    ).getValue(LocalDate.parse(date))

    private fun set(
        id: Long = 1L,
        exerciseId: Long = 1L,
        completed: Boolean,
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        date = "2026-07-16",
        setNumber = 1,
        completed = completed,
    )
}
