package com.ayman.ecolift.data.progress.highlights

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProgressHighlightBuilderTest {
    private val today = LocalDate(2026, 8, 23) // Sunday
    private val bench = Exercise(id = 1, name = "Close-Grip Bench", muscleGroups = "CHEST")

    @Test
    fun empty_history_has_no_reel_pages() {
        assertTrue(build(emptyList(), emptyList()).frames.isEmpty())
    }

    @Test
    fun reel_page_count_is_driven_by_eligible_families_from_zero_through_three() {
        val fourWorkoutDates = listOf("2026-07-29", "2026-08-05", "2026-08-12", "2026-08-19")
        val eightWorkoutDates = canonicalEightWorkoutDates()

        val zero = build(emptyList(), emptyList())
        val one = build(listOf(bench), fourWorkoutDates.mapIndexed { index, date -> set(date, id = index.toLong()) })
        val two = build(listOf(bench), eightWorkoutDates.mapIndexed { index, date -> set(date, id = index.toLong()) })
        val threeSets = eightWorkoutDates.mapIndexed { index, date ->
            set(
                date = date,
                id = index.toLong(),
                weight = if (index == eightWorkoutDates.lastIndex) 1_040 else 1_000,
                reps = 7,
            )
        }
        val three = build(listOf(bench), threeSets)

        assertEquals(0, zero.frames.size)
        assertEquals(1, one.frames.size)
        assertIs<ProgressHighlightFrame.CadenceBands>(one.frames.single())
        assertEquals(2, two.frames.size)
        assertIs<ProgressHighlightFrame.CadenceBands>(two.frames[0])
        assertIs<ProgressHighlightFrame.RunningTotal>(two.frames[1])
        assertEquals(3, three.frames.size)
        assertIs<ProgressHighlightFrame.MatchCut>(three.frames[0])
        assertIs<ProgressHighlightFrame.CadenceBands>(three.frames[1])
        assertIs<ProgressHighlightFrame.RunningTotal>(three.frames[2])
    }

    @Test
    fun match_cut_rejects_a_heavier_set_when_the_rep_count_changed() {
        val snapshot = build(
            listOf(bench),
            listOf(
                set("2026-08-01", weight = 1_720, reps = 7),
                set("2026-08-15", weight = 1_760, reps = 6),
            ),
        )

        assertTrue(snapshot.frames.none { it is ProgressHighlightFrame.MatchCut })
    }

    @Test
    fun match_cut_compares_against_the_previous_all_time_best_not_the_previous_session() {
        val snapshot = buildHistorical(
            LocalDate.parse("2026-06-29"),
            listOf(bench),
            listOf(
                set("2026-06-01", weight = 1_000, reps = 7),
                set("2026-06-08", weight = 1_200, reps = 7),
                set("2026-06-15", weight = 1_100, reps = 7),
                set("2026-06-22", weight = 1_150, reps = 7),
                set("2026-06-29", weight = 1_250, reps = 7),
            ),
        )

        val match = snapshot.frames.filterIsInstance<ProgressHighlightFrame.MatchCut>().single()
        assertEquals(LocalDate(2026, 6, 8), match.beforeDate)
        assertEquals(LocalDate(2026, 6, 29), match.nowDate)
        assertEquals(1_200, match.beforeWeightLbsStored)
        assertEquals(1_250, match.afterWeightLbsStored)
        assertEquals(50, match.deltaWeightLbsStored)
        assertEquals("120 LB", match.beforeLoadLabel)
        assertEquals("125 LB", match.afterLoadLabel)
        assertEquals("+5 LB", match.deltaLoadLabel)
        assertEquals(7, match.reps)
    }

    @Test
    fun an_out_of_layout_historical_best_cannot_be_discarded_to_create_a_false_pr() {
        val snapshot = build(
            listOf(bench),
            listOf(
                set("2026-05-01", weight = 10_000, reps = 7),
                set("2026-05-08", weight = 9_000, reps = 7),
                set("2026-05-15", weight = 9_500, reps = 7),
            ),
        )

        assertTrue(snapshot.frames.none { it is ProgressHighlightFrame.MatchCut })
    }

    @Test
    fun match_cut_omits_values_that_need_an_unapproved_type_or_width_variant() {
        val wideName = Exercise(id = 2, name = "Single-Arm Lat Pulldown")
        val decimalLoads = build(
            listOf(bench),
            listOf(
                set("2026-04-01", weight = 1_725, reps = 7),
                set("2026-04-15", weight = 1_765, reps = 7),
            ),
        )
        val doubleDigitDelta = build(
            listOf(bench),
            listOf(
                set("2026-04-01", weight = 1_000, reps = 7),
                set("2026-04-15", weight = 1_100, reps = 7),
            ),
        )
        val doubleDigitReps = build(
            listOf(bench),
            listOf(
                set("2026-04-01", weight = 1_000, reps = 10),
                set("2026-04-15", weight = 1_040, reps = 10),
            ),
        )
        val wideExerciseName = build(
            listOf(wideName),
            listOf(
                set("2026-04-01", exerciseId = 2, weight = 1_000, reps = 7),
                set("2026-04-15", exerciseId = 2, weight = 1_040, reps = 7),
            ),
        )

        listOf(decimalLoads, doubleDigitDelta, doubleDigitReps, wideExerciseName).forEach { snapshot ->
            assertTrue(snapshot.frames.none { it is ProgressHighlightFrame.MatchCut })
        }
    }

    @Test
    fun match_cut_uses_the_canonical_exercise_across_a_merge_chain() {
        val retired = Exercise(id = 1, name = "Old Bench", mergedIntoId = 2)
        val canonical = Exercise(id = 2, name = "Close-Grip Bench")
        val snapshot = buildHistorical(
            LocalDate.parse("2026-04-15"),
            listOf(retired, canonical),
            listOf(
                set("2026-04-01", exerciseId = 1, weight = 1_720, reps = 7),
                set("2026-04-15", exerciseId = 2, weight = 1_760, reps = 7),
            ),
        )

        val match = assertIs<ProgressHighlightFrame.MatchCut>(snapshot.frames.single())
        assertEquals(2L, match.canonicalExerciseId)
        assertEquals("Close-Grip Bench", match.exerciseName)
        assertEquals("APR 15, 2026", match.dateLabel)
    }

    @Test
    fun cadence_uses_monday_sunday_weeks_across_a_year_boundary() {
        val yearEndToday = LocalDate(2027, 1, 10) // Sunday
        val workoutDates = listOf(
            "2026-12-14", // Mon, week 1
            "2026-12-22", // Tue, week 2
            "2026-12-31", // Thu, week 3
            "2027-01-10", // Sun, week 4
        )
        val snapshot = buildHighlightReelSnapshot(
            exercises = listOf(bench),
            sets = workoutDates.mapIndexed { index, date -> set(date, id = index.toLong()) },
            today = yearEndToday,
        )

        val cadence = assertIs<ProgressHighlightFrame.CadenceBands>(snapshot.frames.single())
        assertEquals(LocalDate(2026, 12, 14), cadence.windowStart)
        assertEquals(LocalDate(2027, 1, 10), cadence.windowEnd)
        assertEquals(
            listOf("DEC 14–20", "DEC 21–27", "DEC 28–JAN 03", "JAN 04–10"),
            cadence.weeks.map(CadenceWeek::dateRangeLabel),
        )
        assertEquals(listOf(setOf(0), setOf(1), setOf(3), setOf(6)), cadence.weeks.map(CadenceWeek::workoutDays))
        assertEquals(4, cadence.workoutCount)
    }

    @Test
    fun cadence_selects_the_latest_four_week_chapter_and_keeps_only_real_workout_dots() {
        val workoutDates = listOf(
            "2026-07-20", // an earlier fifth active week
            "2026-07-29", "2026-08-01",
            "2026-08-05", "2026-08-08",
            "2026-08-12", "2026-08-15",
            "2026-08-19", "2026-08-22",
        )
        val snapshot = build(
            listOf(bench),
            workoutDates.mapIndexed { index, date -> set(date, id = index.toLong()) },
        )

        val cadence = snapshot.frames.filterIsInstance<ProgressHighlightFrame.CadenceBands>().single()
        assertEquals(LocalDate(2026, 7, 27), cadence.windowStart)
        assertEquals(LocalDate(2026, 8, 23), cadence.windowEnd)
        assertEquals(8, cadence.workoutCount)
        assertEquals(
            listOf(setOf(2, 5), setOf(2, 5), setOf(2, 5), setOf(2, 5)),
            cadence.weeks.map(CadenceWeek::workoutDays),
        )
    }

    @Test
    fun running_total_rows_set_count_and_headline_reconcile_exactly() {
        val expectedSessionReps = listOf(84, 92, 88, 96, 78, 90, 86, 98)
        val sets = canonicalEightWorkoutDates().flatMapIndexed { sessionIndex, date ->
            setsForSession(date, expectedSessionReps[sessionIndex], count = 8, idBase = sessionIndex * 100L)
        }
        val snapshot = build(listOf(bench), sets)

        val total = snapshot.frames.filterIsInstance<ProgressHighlightFrame.RunningTotal>().single()
        assertEquals(expectedSessionReps, total.sessionReps)
        assertEquals(712, total.totalReps)
        assertEquals(total.sessionReps.sum(), total.totalReps)
        assertEquals(8, total.workoutCount)
        assertEquals(64, total.setCount)
        assertEquals((1..8).toList(), total.sessions.map(RunningTotalSession::ordinal))
        assertEquals(canonicalEightWorkoutDates(), total.sessions.map { it.date.toString() })
        assertEquals(List(8) { 8 }, total.sessions.map(RunningTotalSession::setCount))
        assertEquals(LocalDate(2026, 7, 27), total.windowStart)
        assertEquals(LocalDate(2026, 8, 23), total.windowEnd)
        assertEquals("LAST 4 WEEKS", total.periodLabel)
    }

    @Test
    fun running_total_window_keeps_sunday_and_monday_on_opposite_iso_weeks_at_year_end() {
        val monday = LocalDate(2027, 1, 4)
        val dates = listOf(
            "2026-12-14", "2026-12-17",
            "2026-12-21", "2026-12-24",
            "2026-12-28", "2026-12-31", "2027-01-03",
            "2027-01-04",
        )
        val snapshot = buildHighlightReelSnapshot(
            exercises = listOf(bench),
            sets = dates.mapIndexed { index, date -> set(date, id = index.toLong()) } +
                set("2027-01-05", id = 100), // future relative to the supplied local today
            today = monday,
        )

        val total = snapshot.frames.filterIsInstance<ProgressHighlightFrame.RunningTotal>().single()
        assertEquals(LocalDate(2026, 12, 14), total.windowStart)
        assertEquals(LocalDate(2027, 1, 10), total.windowEnd)
        assertEquals(dates, total.sessions.map { it.date.toString() })
        assertEquals(8, total.workoutCount)
    }

    @Test
    fun running_total_is_omitted_unless_all_eight_real_session_rows_fit() {
        val onlySeven = canonicalEightWorkoutDates().dropLast(1).mapIndexed { index, date ->
            set(date, id = index.toLong())
        }
        val invalidEighth = set("not-a-date", id = 99)
        val futureEighth = set("2026-08-24", id = 100)

        val invalidSnapshot = build(listOf(bench), onlySeven + invalidEighth)
        val futureSnapshot = build(listOf(bench), onlySeven + futureEighth)

        assertTrue(invalidSnapshot.frames.none { it is ProgressHighlightFrame.RunningTotal })
        assertTrue(futureSnapshot.frames.none { it is ProgressHighlightFrame.RunningTotal })
    }

    @Test
    fun running_total_is_omitted_when_a_receipt_row_or_set_count_exceeds_the_poster() {
        val threeDigitSession = canonicalEightWorkoutDates().flatMapIndexed { sessionIndex, date ->
            setsForSession(
                date = date,
                totalReps = if (sessionIndex == 0) 100 else 80,
                count = 10,
                idBase = sessionIndex * 100L,
            )
        }
        val threeDigitSetCount = canonicalEightWorkoutDates().flatMapIndexed { sessionIndex, date ->
            setsForSession(
                date = date,
                totalReps = 84,
                count = 13,
                idBase = sessionIndex * 100L,
            )
        }

        assertTrue(
            build(listOf(bench), threeDigitSession).frames.none {
                it is ProgressHighlightFrame.RunningTotal
            },
        )
        assertTrue(
            build(listOf(bench), threeDigitSetCount).frames.none {
                it is ProgressHighlightFrame.RunningTotal
            },
        )
    }

    @Test
    fun unchecked_sets_do_not_create_workouts_dots_or_receipts() {
        val completed = canonicalEightWorkoutDates().dropLast(1).mapIndexed { index, date ->
            set(date, id = index.toLong())
        }
        val unchecked = set(canonicalEightWorkoutDates().last(), id = 100, completed = false)
        val snapshot = build(listOf(bench), completed + unchecked)

        assertTrue(snapshot.frames.none { it is ProgressHighlightFrame.RunningTotal })
        val cadence = snapshot.frames.filterIsInstance<ProgressHighlightFrame.CadenceBands>().single()
        assertEquals(7, cadence.workoutCount)
        assertEquals(setOf(2), cadence.weeks.last().workoutDays)
    }

    private fun buildHistorical(cutoff: LocalDate, exercises: List<Exercise>, sets: List<WorkoutSet>) =
        buildHighlightReelSnapshot(exercises, sets, cutoff)

    private fun build(
        exercises: List<Exercise>,
        sets: List<WorkoutSet>,
    ): HighlightReelSnapshot = buildHighlightReelSnapshot(exercises, sets, today)

    private fun set(
        date: String,
        id: Long = 0,
        exerciseId: Long = bench.id,
        weight: Int = 1_000,
        reps: Int = 10,
        completed: Boolean = true,
        bodyweight: Boolean = false,
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        date = date,
        setNumber = (id % 100).toInt() + 1,
        weightLbs = weight,
        reps = reps,
        isBodyweight = bodyweight,
        completed = completed,
    )

    private fun setsForSession(
        date: String,
        totalReps: Int,
        count: Int,
        idBase: Long,
    ): List<WorkoutSet> {
        val base = totalReps / count
        val remainder = totalReps % count
        return List(count) { index ->
            set(
                date = date,
                id = idBase + index,
                reps = base + if (index < remainder) 1 else 0,
            )
        }
    }

    private fun canonicalEightWorkoutDates(): List<String> = listOf(
        "2026-07-29", "2026-08-01",
        "2026-08-05", "2026-08-08",
        "2026-08-12", "2026-08-15",
        "2026-08-19", "2026-08-22",
    )
}
