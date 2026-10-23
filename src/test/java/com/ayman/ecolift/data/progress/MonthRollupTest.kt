package com.ayman.ecolift.data.progress

import com.ayman.ecolift.data.BodyPartRegion
import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthRollupTest {

    private fun ex(id: Long, name: String, groups: String, bodyweight: Boolean = false) =
        Exercise(id = id, name = name, muscleGroups = groups, isBodyweight = bodyweight)

    /** [weightLbs] is in TENTHS of a pound, matching storage. 1850 == 185 lb. */
    private fun set(
        exerciseId: Long,
        date: String,
        setNumber: Int = 1,
        weightLbs: Int? = 1_000,
        reps: Int? = 10,
        completed: Boolean = true,
        isBodyweight: Boolean = false,
    ) = WorkoutSet(
        id = 0,
        exerciseId = exerciseId,
        date = date,
        setNumber = setNumber,
        weightLbs = weightLbs,
        reps = reps,
        completed = completed,
        isBodyweight = isBodyweight,
    )

    @Test
    fun counts_only_checked_sets() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(
            set(1, "2026-05-04", setNumber = 1),
            set(1, "2026-05-04", setNumber = 2, completed = false),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(1, rollup.sets)
        assertEquals(10, rollup.reps)
    }

    @Test
    fun ignores_sets_outside_the_requested_month() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-04-30"), set(1, "2026-05-01"), set(1, "2026-06-01"))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(1, rollup.sets)
        assertEquals(1, rollup.sessions)
    }

    @Test
    fun sessions_are_distinct_dates_with_checked_sets() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(
            set(1, "2026-05-04", setNumber = 1),
            set(1, "2026-05-04", setNumber = 2),
            set(1, "2026-05-07", setNumber = 1),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(2, rollup.sessions)
    }

    @Test
    fun load_uses_tenths_of_a_pound_storage_scale() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-05-04", weightLbs = 1_850, reps = 10))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(1_850.0, rollup.totalLoadLbs)
    }

    @Test
    fun bodyweight_reps_count_as_activity_without_inventing_logged_load() {
        val exercises = listOf(ex(1, "Pull Up", "BACK · BICEPS", bodyweight = false))
        val sets = listOf(set(1, "2026-05-10", weightLbs = 0, reps = 4, isBodyweight = true))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(0.0, rollup.totalLoadLbs)
        assertEquals(4, rollup.reps)
        assertEquals(1, rollup.sessions)
    }

    @Test
    fun exercise_bodyweight_flag_does_not_add_profile_weight_to_logged_volume() {
        val exercises = listOf(ex(1, "Dip", "CHEST", bodyweight = true))
        val sets = listOf(set(1, "2026-05-10", weightLbs = 0, reps = 5, isBodyweight = false))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(0.0, rollup.totalLoadLbs)
        assertEquals(5, rollup.reps)
    }

    @Test
    fun load_per_session_divides_total_by_session_count() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(
            set(1, "2026-05-04", weightLbs = 1_000, reps = 10),
            set(1, "2026-05-07", weightLbs = 1_000, reps = 10),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(1_000.0, rollup.loadPerSessionLbs)
    }

    @Test
    fun empty_month_produces_zeroes_and_no_division_by_zero() {
        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = emptyList(), exercises = emptyList(),
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(0, rollup.sessions)
        assertEquals(0, rollup.sets)
        assertEquals(0.0, rollup.totalLoadLbs)
        assertEquals(0.0, rollup.loadPerSessionLbs)
    }

    @Test
    fun regions_use_the_first_muscle_group_and_report_both_units() {
        val exercises = listOf(
            ex(1, "Bench Press", "CHEST · TRICEPS"),
            ex(2, "Lat Pulldown", "BACK"),
        )
        val sets = listOf(
            set(1, "2026-05-04", setNumber = 1, weightLbs = 1_000, reps = 10),
            set(1, "2026-05-04", setNumber = 2, weightLbs = 1_000, reps = 10),
            set(2, "2026-05-04", setNumber = 3, weightLbs = 1_000, reps = 10),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        val chest = rollup.regions.single { it.region == BodyPartRegion.CHEST }
        val back = rollup.regions.single { it.region == BodyPartRegion.BACK }
        assertEquals(2, chest.sets)
        assertEquals(1, back.sets)
        assertEquals(2f / 3f, chest.setShare)
        assertEquals(1f / 3f, back.loadShare)
    }

    @Test
    fun regions_are_sorted_by_set_count_descending() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"), ex(2, "Squat", "QUADS"))
        val sets = listOf(
            set(1, "2026-05-04", setNumber = 1),
            set(2, "2026-05-04", setNumber = 2),
            set(2, "2026-05-04", setNumber = 3),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(BodyPartRegion.LEGS, rollup.regions.first().region)
    }

    @Test
    fun untagged_exercises_land_in_UNKNOWN_and_stay_in_the_denominator() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"), ex(2, "Mystery Machine", ""))
        val sets = listOf(set(1, "2026-05-04", setNumber = 1), set(2, "2026-05-04", setNumber = 2))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        val unknown = rollup.regions.single { it.region == BodyPartRegion.UNKNOWN }
        assertEquals(1, unknown.sets)
        assertEquals(0.5f, unknown.setShare)
        assertEquals(1f, rollup.regions.sumOf { it.setShare.toDouble() }.toFloat())
    }

    @Test
    fun regions_with_no_sets_are_omitted_entirely() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-05-04"))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(1, rollup.regions.size)
    }

    @Test
    fun an_empty_month_has_no_regions() {
        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = emptyList(), exercises = emptyList(),
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(emptyList(), rollup.regions)
    }

    @Test
    fun muscles_split_legs_into_quads_hamstrings_glutes_and_calves() {
        val exercises = listOf(
            ex(1, "Squat", "QUADS"),
            ex(2, "Leg Curl", "HAMSTRINGS"),
            ex(3, "Hip Thrust", "GLUTES"),
            ex(4, "Calf Raise", "CALVES"),
        )
        val sets = exercises.mapIndexed { index, exercise ->
            set(exercise.id, "2026-05-04", setNumber = index + 1)
        }

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(
            setOf(MuscleSlice.QUADS, MuscleSlice.HAMSTRINGS, MuscleSlice.GLUTES, MuscleSlice.CALVES),
            rollup.muscles.map { it.slice }.toSet(),
        )
        assertEquals(4, rollup.muscles.size)
        assertEquals(BodyPartRegion.LEGS, rollup.regions.single().region)
        assertEquals(4, rollup.regions.single().sets)
    }

    @Test
    fun muscles_split_arms_into_biceps_triceps_and_forearms() {
        val exercises = listOf(
            ex(1, "Curl", "BICEPS"),
            ex(2, "Pressdown", "TRICEPS"),
            ex(3, "Wrist Curl", "FOREARMS"),
        )
        val sets = exercises.mapIndexed { index, exercise ->
            set(exercise.id, "2026-05-04", setNumber = index + 1)
        }

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(
            setOf(MuscleSlice.BICEPS, MuscleSlice.TRICEPS, MuscleSlice.FOREARMS),
            rollup.muscles.map { it.slice }.toSet(),
        )
        assertEquals(3, rollup.muscles.size)
        assertEquals(BodyPartRegion.ARMS, rollup.regions.single().region)
        assertEquals(3, rollup.regions.single().sets)
    }

    @Test
    fun muscles_are_sorted_by_set_count_descending() {
        val exercises = listOf(
            ex(1, "Bench Press", "CHEST"),
            ex(2, "Row", "BACK"),
            ex(3, "Squat", "QUADS"),
        )
        val sets = listOf(
            set(1, "2026-05-04", setNumber = 1),
            set(2, "2026-05-04", setNumber = 2),
            set(2, "2026-05-04", setNumber = 3),
            set(2, "2026-05-04", setNumber = 4),
            set(3, "2026-05-04", setNumber = 5),
            set(3, "2026-05-04", setNumber = 6),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(
            listOf(MuscleSlice.BACK, MuscleSlice.QUADS, MuscleSlice.CHEST),
            rollup.muscles.map { it.slice },
        )
    }

    @Test
    fun an_untagged_exercise_lands_in_UNKNOWN_and_stays_in_the_denominator() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"), ex(2, "Mystery Machine", ""))
        val sets = listOf(set(1, "2026-05-04", setNumber = 1), set(2, "2026-05-04", setNumber = 2))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(setOf(MuscleSlice.CHEST, MuscleSlice.UNKNOWN), rollup.muscles.map { it.slice }.toSet())
        assertEquals(1f, rollup.muscles.sumOf { it.setShare.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun muscle_shares_use_the_first_group_only() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST · TRICEPS"))
        val sets = listOf(set(1, "2026-05-04"))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(listOf(MuscleSlice.CHEST), rollup.muscles.map { it.slice })
    }

    @Test
    fun an_empty_month_has_no_muscles() {
        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = emptyList(), exercises = emptyList(),
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(emptyList(), rollup.muscles)
    }

    @Test
    fun muscle_load_uses_the_tenths_of_a_pound_storage_scale() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-05-04", weightLbs = 1_850, reps = 10))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(1_850.0, rollup.muscles.single().loadLbs)
    }

    @Test
    fun days_cover_the_whole_month_and_mark_trained_dates() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-05-04"))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(31, rollup.days.size)
        assertEquals(1, rollup.days.count { it.trained })
        assertEquals(4, rollup.days.single { it.trained }.date.dayOfMonth)
    }

    @Test
    fun days_length_follows_the_calendar_including_leap_february() {
        fun daysFor(year: Int, month: Int): Int = MonthRollup.build(
            year = year, month = month,
            sets = emptyList(), exercises = emptyList(),
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = null,
        ).days.size

        assertEquals(30, daysFor(2026, 4))
        assertEquals(28, daysFor(2026, 2))
        assertEquals(29, daysFor(2028, 2))
        assertEquals(28, daysFor(2100, 2))
        assertEquals(29, daysFor(2000, 2))
    }

    @Test
    fun days_carry_the_split_name_when_the_workout_day_resolves_a_slot() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-05-04"))
        val days = listOf(WorkoutDay(date = "2026-05-04", cycleSlotId = 7L))
        val slots = listOf(CycleSlot(id = 7L, name = "Push", orderIndex = 0))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = days, slots = slots,
            userBodyweightLbs = 162,
        )

        assertEquals("Push", rollup.days.single { it.trained }.slotName)
    }

    @Test
    fun a_pr_needs_three_prior_sessions_of_history() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val history = listOf(
            set(1, "2026-04-01", weightLbs = 1_000),
            set(1, "2026-04-08", weightLbs = 1_000),
            set(1, "2026-04-15", weightLbs = 1_000),
            set(1, "2026-05-04", weightLbs = 2_000),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = history, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(1, rollup.days.count { it.hasPr })
        assertEquals(4, rollup.days.single { it.hasPr }.date.day)
    }

    @Test
    fun a_young_log_flags_no_prs() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val history = listOf(
            set(1, "2026-04-01", weightLbs = 1_000),
            set(1, "2026-04-08", weightLbs = 1_000),
            set(1, "2026-05-04", weightLbs = 2_000),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = history, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(0, rollup.days.count { it.hasPr })
    }

    @Test
    fun a_heavier_set_later_the_same_day_is_not_a_pr_against_its_own_morning() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val history = listOf(
            set(1, "2026-04-01", weightLbs = 1_000),
            set(1, "2026-04-08", weightLbs = 1_000),
            set(1, "2026-04-15", weightLbs = 1_000),
            set(1, "2026-05-04", setNumber = 1, weightLbs = 900),
            set(1, "2026-05-11", setNumber = 1, weightLbs = 900),
            set(1, "2026-05-11", setNumber = 2, weightLbs = 950),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = history, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        // Neither May day beats the 1,000 set from April, so neither is a PR.
        assertEquals(0, rollup.days.count { it.hasPr })
    }

    @Test
    fun equalling_the_old_best_is_not_a_pr() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val history = listOf(
            set(1, "2026-04-01", weightLbs = 1_000),
            set(1, "2026-04-08", weightLbs = 1_000),
            set(1, "2026-04-15", weightLbs = 1_000),
            set(1, "2026-05-04", weightLbs = 1_000),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = history, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(0, rollup.days.count { it.hasPr })
    }

    @Test
    fun unchecked_sets_never_count_as_prs_or_as_history() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val history = listOf(
            set(1, "2026-04-01", weightLbs = 1_000),
            set(1, "2026-04-08", weightLbs = 1_000),
            set(1, "2026-04-15", weightLbs = 1_000),
            set(1, "2026-05-04", weightLbs = 5_000, completed = false),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = history, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(0, rollup.days.count { it.hasPr })
    }

    @Test
    fun each_exercise_tracks_its_own_history_independently() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"), ex(2, "Row", "BACK"))
        val history = listOf(
            // Bench has three prior sessions, then a PR in May.
            set(1, "2026-04-01", setNumber = 1, weightLbs = 1_000),
            set(1, "2026-04-08", setNumber = 1, weightLbs = 1_000),
            set(1, "2026-04-15", setNumber = 1, weightLbs = 1_000),
            set(1, "2026-05-04", setNumber = 1, weightLbs = 2_000),
            // Row has only one prior session, so its May jump is not a PR.
            set(2, "2026-04-01", setNumber = 2, weightLbs = 1_000),
            set(2, "2026-05-07", setNumber = 2, weightLbs = 9_000),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = history, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(1, rollup.days.count { it.hasPr })
        assertEquals(4, rollup.days.single { it.hasPr }.date.day)
    }

    @Test
    fun a_session_with_no_workout_day_row_counts_as_unassigned() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-05-04"), set(1, "2026-05-07"))
        val days = listOf(WorkoutDay(date = "2026-05-04", cycleSlotId = 7L))
        val slots = listOf(CycleSlot(id = 7L, name = "Push", orderIndex = 0))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = days, slots = slots,
            userBodyweightLbs = 162,
        )

        assertEquals(1, rollup.unassignedSessions)
    }

    @Test
    fun a_null_slot_id_counts_as_unassigned() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-05-04"))
        val days = listOf(WorkoutDay(date = "2026-05-04", cycleSlotId = null))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = days, slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(1, rollup.unassignedSessions)
    }

    @Test
    fun a_slot_id_that_resolves_to_nothing_counts_as_unassigned() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-05-04"))
        val days = listOf(WorkoutDay(date = "2026-05-04", cycleSlotId = 999L))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = days, slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(1, rollup.unassignedSessions)
        assertEquals(null, rollup.days.single { it.trained }.slotName)
    }

    @Test
    fun slot_runs_count_how_often_each_split_ran() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-05-04"), set(1, "2026-05-07"), set(1, "2026-05-11"))
        val days = listOf(
            WorkoutDay(date = "2026-05-04", cycleSlotId = 7L),
            WorkoutDay(date = "2026-05-11", cycleSlotId = 7L),
            WorkoutDay(date = "2026-05-07", cycleSlotId = 8L),
        )
        val slots = listOf(
            CycleSlot(id = 7L, name = "Push", orderIndex = 0),
            CycleSlot(id = 8L, name = "Pull", orderIndex = 1),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = days, slots = slots,
            userBodyweightLbs = 162,
        )

        assertEquals(listOf(2, 1), rollup.slotRuns.map { it.runs })
        assertEquals("Push", rollup.slotRuns.first().name)
    }

    @Test
    fun heaviest_day_reports_its_load_and_set_count() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(
            set(1, "2026-05-04", setNumber = 1, weightLbs = 1_000, reps = 10),
            set(1, "2026-05-07", setNumber = 1, weightLbs = 2_000, reps = 10),
            set(1, "2026-05-07", setNumber = 2, weightLbs = 1_000, reps = 10),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(7, rollup.heaviestDay?.date?.dayOfMonth)
        assertEquals(3_000.0, rollup.heaviestDay?.loadLbs)
        assertEquals(2, rollup.heaviestDay?.sets)
    }

    @Test
    fun heaviest_day_ties_break_on_the_earlier_date() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(
            set(1, "2026-05-07", setNumber = 1, weightLbs = 1_000, reps = 10),
            set(1, "2026-05-04", setNumber = 1, weightLbs = 1_000, reps = 10),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(4, rollup.heaviestDay?.date?.dayOfMonth)
    }

    @Test
    fun recap_excludes_lifts_with_only_one_session() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-05-04", weightLbs = 1_000, reps = 10))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(0, rollup.recap.size)
    }

    @Test
    fun recap_includes_a_two_session_lift_and_marks_it_thin() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(
            set(1, "2026-05-04", weightLbs = 1_000, reps = 10),
            set(1, "2026-05-11", weightLbs = 2_000, reps = 10),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        val entry = rollup.recap.single()
        assertEquals(2, entry.sessions)
        assertEquals(true, entry.thin)
        assertEquals(100f, entry.changePct)
    }

    @Test
    fun a_three_session_lift_is_not_marked_thin() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(
            set(1, "2026-05-04", weightLbs = 1_000, reps = 10),
            set(1, "2026-05-07", weightLbs = 1_500, reps = 10),
            set(1, "2026-05-11", weightLbs = 2_000, reps = 10),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(false, rollup.recap.single().thin)
    }

    @Test
    fun recap_is_sorted_by_change_descending() {
        val exercises = listOf(ex(1, "Up Lift", "CHEST"), ex(2, "Down Lift", "BACK"))
        val sets = listOf(
            set(1, "2026-05-04", setNumber = 1, weightLbs = 1_000, reps = 10),
            set(1, "2026-05-11", setNumber = 1, weightLbs = 2_000, reps = 10),
            set(2, "2026-05-04", setNumber = 2, weightLbs = 2_000, reps = 10),
            set(2, "2026-05-11", setNumber = 2, weightLbs = 1_000, reps = 10),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(listOf("Up Lift", "Down Lift"), rollup.recap.map { it.name })
    }

    @Test
    fun recap_uses_the_best_set_of_each_session_by_estimated_one_rep_max() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        // Session 1 best e1RM: 100 lb x 10 -> 133.33. Session 2 best: 200 lb x 1 -> 206.67
        // (the 100x10 set in session 2 must not win over the heavier single).
        val sets = listOf(
            set(1, "2026-05-04", setNumber = 1, weightLbs = 1_000, reps = 10),
            set(1, "2026-05-11", setNumber = 1, weightLbs = 1_000, reps = 10),
            set(1, "2026-05-11", setNumber = 2, weightLbs = 2_000, reps = 1),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        // (206.667 - 133.333) / 133.333 * 100 = 55.0
        assertEquals(55f, rollup.recap.single().changePct)
    }

    @Test
    fun a_trained_day_carries_its_own_set_count_and_load() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(
            set(1, "2026-05-04", setNumber = 1, weightLbs = 1_000, reps = 10),
            set(1, "2026-05-04", setNumber = 2, weightLbs = 1_000, reps = 10),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        val day = rollup.days.single { it.trained }
        assertEquals(2, day.setCount)
        assertEquals(2_000.0, day.loadLbs)
    }

    @Test
    fun an_untrained_day_has_zero_set_count_and_load() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-05-04"))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        val day = rollup.days.first { !it.trained }
        assertEquals(0, day.setCount)
        assertEquals(0.0, day.loadLbs)
    }

    @Test
    fun an_archived_split_still_resolves_on_a_past_month() {
        val exercises = listOf(ex(1, "Bench Press", "CHEST"))
        val sets = listOf(set(1, "2026-05-04"))
        val days = listOf(WorkoutDay(date = "2026-05-04", cycleSlotId = 7L))
        // archivedAt set: this split has since been retired, but the month it ran in
        // must still show it.
        val slots = listOf(CycleSlot(id = 7L, name = "Push", orderIndex = 0, archivedAt = 1_780_000_000_000L))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = days, slots = slots,
            userBodyweightLbs = 162,
        )

        assertEquals("Push", rollup.days.single { it.trained }.slotName)
        assertEquals(0, rollup.unassignedSessions)
        assertEquals(1, rollup.slotRuns.single().runs)
    }

    @Test
    fun a_day_carries_its_two_busiest_muscles_most_sets_first() {
        val exercises = listOf(
            ex(1, "Squat", "QUADS"),
            ex(2, "Leg Curl", "HAMSTRINGS"),
            ex(3, "Hip Thrust", "GLUTES"),
        )
        val sets = listOf(
            set(1, "2026-05-04", setNumber = 1),
            set(1, "2026-05-04", setNumber = 2),
            set(1, "2026-05-04", setNumber = 3),
            set(2, "2026-05-04", setNumber = 4),
            set(2, "2026-05-04", setNumber = 5),
            set(3, "2026-05-04", setNumber = 6),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(
            listOf(MuscleSlice.QUADS, MuscleSlice.HAMSTRINGS),
            rollup.days.single { it.trained }.topMuscles,
        )
    }

    @Test
    fun an_untrained_day_has_no_muscles() {
        val exercises = listOf(ex(1, "Squat", "QUADS"))
        val sets = listOf(set(1, "2026-05-04"))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(emptyList(), rollup.days.first { !it.trained }.topMuscles)
    }

    @Test
    fun most_run_lifts_count_days_not_sets() {
        val exercises = listOf(ex(1, "Squat", "QUADS"), ex(2, "Row", "BACK"))
        val sets = listOf(
            // Squat: five sets, but only two days.
            set(1, "2026-05-04", setNumber = 1),
            set(1, "2026-05-04", setNumber = 2),
            set(1, "2026-05-04", setNumber = 3),
            set(1, "2026-05-06", setNumber = 1),
            set(1, "2026-05-06", setNumber = 2),
            // Row: three sets across three days.
            set(2, "2026-05-04", setNumber = 4),
            set(2, "2026-05-06", setNumber = 3),
            set(2, "2026-05-08", setNumber = 1),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(listOf("Row" to 3, "Squat" to 2), rollup.mostRunLifts.map { it.name to it.runs })
    }

    @Test
    fun rhythm_counts_sessions_per_weekday_with_sunday_first() {
        val exercises = listOf(ex(1, "Squat", "QUADS"))
        // 2026-05-03 is a Sunday, 2026-05-04 a Monday, 2026-05-11 a Monday.
        val sets = listOf(set(1, "2026-05-03"), set(1, "2026-05-04"), set(1, "2026-05-11"))

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 162,
        )

        assertEquals(listOf(1, 2, 0, 0, 0, 0, 0), rollup.rhythm)
        assertEquals(3, rollup.rhythm.sum())
    }

    @Test
    fun made_of_session_naming_rules() {
        val ex1 = ex(1, "Bench", "CHEST")
        val ex2 = ex(2, "Row", "BACK")
        val ex3 = ex(3, "Bicep Curl", "BICEPS")
        val exercises = listOf(ex1, ex2, ex3)

        val days = listOf(
            WorkoutDay(date = "2026-05-01", cycleSlotId = 1L),
            WorkoutDay(date = "2026-05-02", cycleSlotId = 2L),
        )
        val slots = listOf(
            CycleSlot(id = 1L, name = "Upper 1"),
            CycleSlot(id = 2L, name = ""),
        )

        val sets = listOf(
            set(1, "2026-05-01", 1),
            set(1, "2026-05-02", 1),
            set(2, "2026-05-02", 2),
            set(1, "2026-05-03", 1),
            set(3, "2026-05-03", 2),
            set(1, "2026-05-04", 1),
        )

        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = days, slots = slots,
            userBodyweightLbs = 162,
        )

        val day1 = rollup.days.first { it.date.day == 1 }
        val day2 = rollup.days.first { it.date.day == 2 }
        val day3 = rollup.days.first { it.date.day == 3 }
        val day4 = rollup.days.first { it.date.day == 4 }

        assertEquals("Upper 1", day1.sessionLabel)
        assertEquals("CHEST · BACK", day2.sessionLabel)
        assertEquals("CHEST · ARMS", day3.sessionLabel)
        assertEquals("CHEST", day4.sessionLabel)
        assertTrue(rollup.days.filter { it.trained }.all { it.sessionLabel.isNotBlank() })
    }

    @Test
    fun muscle_shares_pounds_sum_to_month_total() {
        val exercises = listOf(
            ex(1, "Bench", "CHEST"),
            ex(2, "Squat", "QUADS"),
            ex(3, "Dip", "CHEST", bodyweight = true),
        )
        val sets = listOf(
            set(1, "2026-05-01", 1, weightLbs = 1_500, reps = 10),
            set(2, "2026-05-02", 1, weightLbs = 2_000, reps = 10),
            set(3, "2026-05-03", 1, weightLbs = 0, reps = 10, isBodyweight = true),
        )
        val rollup = MonthRollup.build(
            year = 2026, month = 5,
            sets = sets, exercises = exercises,
            workoutDays = emptyList(), slots = emptyList(),
            userBodyweightLbs = 160,
        )
        val sumMuscles = rollup.muscles.sumOf { it.loadLbs }
        assertEquals(rollup.totalLoadLbs, sumMuscles)
    }
}
