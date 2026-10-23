package com.ayman.ecolift.data.progress

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthFiguresTest {

    private fun ex(id: Long, name: String, groups: String) =
        Exercise(id = id, name = name, muscleGroups = groups)

    private fun set(exerciseId: Long, date: String, setNumber: Int = 1, weightLbs: Int = 1_000, reps: Int = 10) =
        WorkoutSet(
            id = 0, exerciseId = exerciseId, date = date, setNumber = setNumber,
            weightLbs = weightLbs, reps = reps, completed = true,
        )

    private fun rollupOf(
        sets: List<WorkoutSet>,
        exercises: List<Exercise>,
        days: List<WorkoutDay> = emptyList(),
        slots: List<CycleSlot> = emptyList(),
    ) = MonthRollup.build(
        year = 2026, month = 5,
        sets = sets, exercises = exercises,
        workoutDays = days, slots = slots,
        userBodyweightLbs = 162,
    )

    @Test
    fun empty_month_produces_no_figures() {
        assertEquals(emptyList(), MonthFigures.build(rollupOf(emptyList(), emptyList())))
    }

    @Test
    fun heaviest_day_figure_appears_when_there_is_a_session() {
        val rollup = rollupOf(listOf(set(1, "2026-05-04")), listOf(ex(1, "Bench Press", "CHEST")))
        val figure = MonthFigures.build(rollup).single { it.caption.contains("HEAVIEST DAY") }
        assertEquals("1,000", figure.value)
        assertTrue(figure.caption.contains("MAY 4"))
        assertTrue(figure.caption.contains("1 SETS") || figure.caption.contains("1 SET"))
    }

    @Test
    fun heaviest_day_value_is_comma_grouped() {
        val rollup = rollupOf(
            listOf(set(1, "2026-05-04", weightLbs = 2_000, reps = 10)),
            listOf(ex(1, "Bench Press", "CHEST")),
        )
        assertEquals("2,000", MonthFigures.build(rollup).single { it.caption.contains("HEAVIEST DAY") }.value)
    }

    @Test
    fun rotation_figure_is_absent_when_every_slot_ran_the_same_number_of_times() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val slots = listOf(
            CycleSlot(id = 7L, name = "Push", orderIndex = 0),
            CycleSlot(id = 8L, name = "Pull", orderIndex = 1),
        )
        val even = rollupOf(
            sets = listOf(set(1, "2026-05-04"), set(1, "2026-05-07")),
            exercises = exercises,
            days = listOf(
                WorkoutDay(date = "2026-05-04", cycleSlotId = 7L),
                WorkoutDay(date = "2026-05-07", cycleSlotId = 8L),
            ),
            slots = slots,
        )
        assertTrue(MonthFigures.build(even).none { it.caption.contains("RUNS") })
    }

    @Test
    fun rotation_figure_names_the_least_run_slot() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val slots = listOf(
            CycleSlot(id = 7L, name = "Push", orderIndex = 0),
            CycleSlot(id = 8L, name = "Pull", orderIndex = 1),
        )
        val uneven = rollupOf(
            sets = listOf(set(1, "2026-05-04"), set(1, "2026-05-07"), set(1, "2026-05-11")),
            exercises = exercises,
            days = listOf(
                WorkoutDay(date = "2026-05-04", cycleSlotId = 7L),
                WorkoutDay(date = "2026-05-11", cycleSlotId = 7L),
                WorkoutDay(date = "2026-05-07", cycleSlotId = 8L),
            ),
            slots = slots,
        )
        val rotation = MonthFigures.build(uneven).single { it.caption.contains("RUNS") }
        assertEquals("×1", rotation.value)
        assertTrue(rotation.caption.contains("PULL"))
        assertTrue(rotation.caption.contains("PUSH"))
    }

    @Test
    fun rotation_figure_needs_at_least_two_slots() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val single = rollupOf(
            sets = listOf(set(1, "2026-05-04")),
            exercises = exercises,
            days = listOf(WorkoutDay(date = "2026-05-04", cycleSlotId = 7L)),
            slots = listOf(CycleSlot(id = 7L, name = "Push", orderIndex = 0)),
        )
        assertTrue(MonthFigures.build(single).none { it.caption.contains("RUNS") })
    }

    @Test
    fun figures_keep_a_stable_order() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val slots = listOf(
            CycleSlot(id = 7L, name = "Push", orderIndex = 0),
            CycleSlot(id = 8L, name = "Pull", orderIndex = 1),
        )
        val rollup = rollupOf(
            sets = listOf(set(1, "2026-05-04"), set(1, "2026-05-07"), set(1, "2026-05-11"), set(1, "2026-05-14")),
            exercises = exercises,
            days = listOf(
                WorkoutDay(date = "2026-05-04", cycleSlotId = 7L),
                WorkoutDay(date = "2026-05-11", cycleSlotId = 7L),
                WorkoutDay(date = "2026-05-07", cycleSlotId = 8L),
            ),
            slots = slots,
        )
        val captions = MonthFigures.build(rollup).map { it.caption }
        assertEquals(2, captions.size)
        assertTrue(captions[0].contains("HEAVIEST DAY"))
        assertTrue(captions[1].contains("RUNS"))
    }

    @Test
    fun a_session_without_a_split_produces_no_figure_about_it() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        // No workout_day rows at all: none of these sessions belong to a split.
        val rollup = rollupOf(listOf(set(1, "2026-05-04"), set(1, "2026-05-07")), exercises)

        assertTrue(MonthFigures.build(rollup).none { it.caption.contains("SPLIT") })
    }

    @Test
    fun no_figure_text_contains_advice() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val slots = listOf(
            CycleSlot(id = 7L, name = "Push", orderIndex = 0),
            CycleSlot(id = 8L, name = "Pull", orderIndex = 1),
        )
        val rollup = rollupOf(
            sets = listOf(set(1, "2026-05-04"), set(1, "2026-05-07"), set(1, "2026-05-11")),
            exercises = exercises,
            days = listOf(
                WorkoutDay(date = "2026-05-04", cycleSlotId = 7L),
                WorkoutDay(date = "2026-05-11", cycleSlotId = 7L),
                WorkoutDay(date = "2026-05-07", cycleSlotId = 8L),
            ),
            slots = slots,
        )
        val banned = listOf("should", "consider", "try", "needs", "neglect", "lagging")
        MonthFigures.build(rollup).forEach { figure ->
            val text = (figure.value + " " + figure.caption).lowercase()
            banned.forEach { word ->
                assertTrue(!text.contains(word), "figure text must not advise, found '$word' in: $text")
            }
        }
    }
}
