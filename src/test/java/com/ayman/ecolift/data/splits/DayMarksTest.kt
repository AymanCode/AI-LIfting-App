package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.progress.MonthRollup
import com.ayman.ecolift.data.progress.MuscleSlice
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DayMarksTest {
    private val bench = Exercise(id = 1, name = "Bench Press", muscleGroups = "CHEST")
    private val curl = Exercise(id = 2, name = "Leaning Dumbell Curl", muscleGroups = "BICEPS")
    private val calf = Exercise(id = 3, name = "Standing Calf Raise Machine", muscleGroups = "CALVES")
    private val dip = Exercise(id = 4, name = "Dip", muscleGroups = "CHEST", isBodyweight = true)
    private val row = Exercise(id = 5, name = "Row Machine", muscleGroups = "BACK · BICEPS")
    private val byId = listOf(bench, curl, calf, dip, row).associateBy { it.id }

    private var nextId = 1L
    private fun set(
        exercise: Exercise,
        date: String,
        lbs: Double,
        reps: Int = 8,
        completed: Boolean = true,
        warmup: Boolean = false,
    ) = WorkoutSet(
        id = nextId++,
        exerciseId = exercise.id,
        date = date,
        setNumber = 1,
        weightLbs = (lbs * 10).toInt(),
        reps = reps,
        isBodyweight = exercise.isBodyweight,
        completed = completed,
        isWarmup = warmup,
    )

    @Test
    fun lightThresholdIsTheBottomQuarterAndHeavyWinsATie() {
        val volumes = listOf(1_000L, 2_000L, 3_000L, 4_000L, 5_000L, 6_000L, 7_000L, 8_000L)
        assertEquals(2_000L, volumeLightThreshold(volumes))
        assertEquals(6_000L, volumeBrightThreshold(volumes))
        assertEquals(DayTier.LIGHT, dayTier(1_500L, 2_000L, 6_000L))
        assertEquals(DayTier.REGULAR, dayTier(4_000L, 2_000L, 6_000L))
        assertEquals(DayTier.HEAVY, dayTier(6_000L, 2_000L, 6_000L))
        // One day only: both thresholds are that day, and it stays heavy as it was bright before.
        assertEquals(DayTier.HEAVY, dayTier(900L, volumeLightThreshold(listOf(900L)), volumeBrightThreshold(listOf(900L))))
        assertEquals(0L, volumeLightThreshold(emptyList()))
    }

    @Test
    fun prsNeedThreeEarlierDatesAndMatchTheMonthRecap() {
        val sets = listOf(
            set(bench, "2026-08-01", 165.0),
            set(bench, "2026-08-04", 175.0),
            set(bench, "2026-08-08", 170.0),
            set(bench, "2026-08-11", 185.0, reps = 3), // 4th date, beats 175
            set(bench, "2026-08-11", 185.0, reps = 5), // most reps at the top weight is the one shown
            set(bench, "2026-08-11", 135.0, reps = 12),
            set(bench, "2026-08-15", 185.0), // ties the best: not a PR
            set(bench, "2026-09-01", 195.0, reps = 2),
            set(bench, "2026-09-02", 205.0, reps = 1, completed = false), // not checked: ignored
        )
        val marks = buildDayMarks(sets, byId)
        val prDates = marks.filterValues { it.prs.isNotEmpty() }.keys.map { it.toString() }.sorted()
        assertEquals(listOf("2026-08-11", "2026-09-01"), prDates)
        val aug11 = marks.getValue(LocalDate(2026, 8, 11)).prs.single()
        assertEquals(1850, aug11.weightStored)
        assertEquals(5, aug11.reps)
        assertEquals(1750, aug11.previousBestStored)

        val recap = listOf(8, 9).flatMap { month ->
            MonthRollup.build(2026, month, sets, byId.values.toList(), emptyList(), emptyList(), 180)
                .days.filter { it.hasPr }.map { it.date.toString() }
        }.sorted()
        assertEquals(recap, prDates)
    }

    @Test
    fun onlyPrsOnTheHeaviestLiftsEarnTheStar() {
        val dates = listOf("2026-08-01", "2026-08-04", "2026-08-08", "2026-08-11", "2026-08-15")
        val sets = dates.mapIndexed { i, d -> set(bench, d, 165.0 + i * 10) } +
            dates.mapIndexed { i, d -> set(curl, d, 25.0 + i * 2.5) } +
            dates.mapIndexed { i, d -> set(calf, d, 230.0 + i * 5) } +
            dates.mapIndexed { i, d -> set(row, d, 120.0 + i * 5) } +
            dates.mapIndexed { i, d -> set(dip, d, 10.0 + i * 5) }
        // Bests: calf 250, bench 205, row 140, curl 35; the dip is bodyweight and left out.
        assertEquals(2500, heavyLiftMinimumStored(sets, byId))
        val prs = buildDayMarks(sets, byId).getValue(LocalDate(2026, 8, 15)).prs
        assertEquals(5, prs.size)
        assertEquals(listOf("Standing Calf Raise Machine"), prs.filter { it.heavyLift }.map { it.exerciseName })
        assertTrue(prs.first().heavyLift, "star-earning PRs sort first")
        val dipPr = prs.single { it.exerciseName == "Dip" }
        assertTrue(dipPr.isBodyweight)
        assertFalse(dipPr.heavyLift)
    }

    @Test
    fun heavyLiftMinimumIsNullWithoutWeightedLifts() {
        assertNull(heavyLiftMinimumStored(listOf(set(dip, "2026-08-01", 0.0)), byId))
    }

    @Test
    fun musclesAreTheTwoMostTrainedWithTiesByName() {
        val day = "2026-09-20"
        val sets = listOf(
            set(bench, day, 205.0),
            set(row, day, 150.0), set(row, day, 150.0), set(row, day, 150.0),
            set(curl, day, 30.0), set(curl, day, 30.0), set(curl, day, 30.0),
            set(calf, day, 250.0, completed = false),
        )
        val marks = buildDayMarks(sets, byId).getValue(LocalDate(2026, 9, 20))
        assertEquals(listOf(MuscleSlice.BACK, MuscleSlice.BICEPS), marks.muscles)
        assertEquals(7, marks.setCount)
    }
}
