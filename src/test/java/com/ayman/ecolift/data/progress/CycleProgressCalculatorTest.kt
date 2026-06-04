package com.ayman.ecolift.data.progress

import com.ayman.ecolift.data.CycleSnapshot
import com.ayman.ecolift.data.CycleTotals
import com.ayman.ecolift.data.ExerciseSnapshot
import com.ayman.ecolift.data.SessionPoint
import com.ayman.ecolift.data.SplitBucketKind
import com.ayman.ecolift.data.SplitSnapshot
import com.ayman.ecolift.data.WeightLbs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleProgressCalculatorTest {
    @Test
    fun `epley e1rm session reduction uses max value across sets`() {
        val result = CycleProgressCalculator.buildCore(
            snapshot = snapshot(exercises = listOf(exercise(1L, "Bench Press", false, listOf("2026-04-01")))),
            sets = listOf(
                set(1L, "2026-04-01", 1, weight = 100, reps = 5),
                set(1L, "2026-04-01", 2, weight = 110, reps = 3),
            ),
            userBodyweightLbs = null,
            realSlotCount = 1,
        )

        val lift = result.core.lifts.single()
        assertEquals(LiftMetric.E1RM, lift.metric)
        assertEquals(121f, lift.points.single().value, 0.001f)
    }

    @Test
    fun `regression returns known slope and r2 and null for undefined series`() {
        val regression = CycleProgressCalculator.linreg(
            listOf(
                TrendPoint("2026-01-01", 10f),
                TrendPoint("2026-01-08", 17f),
                TrendPoint("2026-01-15", 24f),
            )
        )

        assertEquals(1f, regression!!.slopePerDay, 0.001f)
        assertEquals(1f, regression.r2, 0.001f)
        assertNull(CycleProgressCalculator.linreg(listOf(TrendPoint("2026-01-01", 10f))))
        assertNull(
            CycleProgressCalculator.linreg(
                listOf(
                    TrendPoint("2026-01-01", 10f),
                    TrendPoint("2026-01-08", 10f),
                )
            )
        )
    }

    @Test
    fun `baseline uses point fallback for two prior sessions and projection for three`() {
        val coreResult = repsCore(endReps = 130)
        val twoPrior = CycleProgressCalculator.compare(
            coreResult = coreResult,
            priorSetsByExerciseId = mapOf(
                1L to listOf(
                    set(1L, "2026-01-11", 1, reps = 20, isBodyweight = true),
                    set(1L, "2026-01-21", 1, reps = 30, isBodyweight = true),
                )
            ),
            window = ComparisonWindow.M6,
            userBodyweightLbs = null,
        )
        val threePrior = CycleProgressCalculator.compare(
            coreResult = coreResult,
            priorSetsByExerciseId = mapOf(
                1L to listOf(
                    set(1L, "2026-01-01", 1, reps = 10, isBodyweight = true),
                    set(1L, "2026-01-11", 1, reps = 20, isBodyweight = true),
                    set(1L, "2026-01-21", 1, reps = 30, isBodyweight = true),
                )
            ),
            window = ComparisonWindow.M6,
            userBodyweightLbs = null,
        )

        assertEquals(30f, twoPrior.lifts.single().baselineValue!!, 0.001f)
        assertNull(twoPrior.lifts.single().priorRatePctPerMonth)
        assertEquals(129f, threePrior.lifts.single().baselineValue!!, 0.001f)
        assertTrue(threePrior.lifts.single().priorRatePctPerMonth!! > 0f)
    }

    @Test
    fun `new lifts are excluded from aggregate progression`() {
        val coreResult = CycleProgressCalculator.buildCore(
            snapshot = snapshot(
                exercises = listOf(
                    exercise(1L, "Pull-up", true, listOf("2026-04-01")),
                    exercise(2L, "Push-up", true, listOf("2026-04-01")),
                )
            ),
            sets = listOf(
                set(1L, "2026-04-01", 1, reps = 12, isBodyweight = true),
                set(2L, "2026-04-01", 1, reps = 12, isBodyweight = true),
            ),
            userBodyweightLbs = null,
            realSlotCount = 1,
        )

        val comparison = CycleProgressCalculator.compare(
            coreResult = coreResult,
            priorSetsByExerciseId = mapOf(1L to listOf(set(1L, "2026-03-01", 1, reps = 10, isBodyweight = true))),
            window = ComparisonWindow.M3,
            userBodyweightLbs = null,
        )

        assertEquals(1, comparison.comparedCount)
        assertEquals(100f, comparison.progression, 0.001f)
        assertTrue(comparison.lifts.single { it.exerciseId == 2L }.isNew)
    }

    @Test
    fun `movement buckets respect one percent boundaries`() {
        val coreResult = CycleProgressCalculator.buildCore(
            snapshot = snapshot(
                exercises = listOf(
                    exercise(1L, "Held positive", true, listOf("2026-04-01")),
                    exercise(2L, "Improved", true, listOf("2026-04-01")),
                    exercise(3L, "Held negative", true, listOf("2026-04-01")),
                    exercise(4L, "Regressed", true, listOf("2026-04-01")),
                )
            ),
            sets = listOf(
                set(1L, "2026-04-01", 1, reps = 101, isBodyweight = true),
                set(2L, "2026-04-01", 1, reps = 102, isBodyweight = true),
                set(3L, "2026-04-01", 1, reps = 99, isBodyweight = true),
                set(4L, "2026-04-01", 1, reps = 98, isBodyweight = true),
            ),
            userBodyweightLbs = null,
            realSlotCount = 1,
        )

        val comparison = CycleProgressCalculator.compare(
            coreResult = coreResult,
            priorSetsByExerciseId = (1L..4L).associateWith { id ->
                listOf(set(id, "2026-03-01", 1, reps = 100, isBodyweight = true))
            },
            window = ComparisonWindow.M3,
            userBodyweightLbs = null,
        )

        assertEquals(Movement.HELD, comparison.lifts.single { it.exerciseId == 1L }.movement)
        assertEquals(Movement.IMPROVED, comparison.lifts.single { it.exerciseId == 2L }.movement)
        assertEquals(Movement.HELD, comparison.lifts.single { it.exerciseId == 3L }.movement)
        assertEquals(Movement.REGRESSED, comparison.lifts.single { it.exerciseId == 4L }.movement)
    }

    @Test
    fun `rep buckets include boundaries and open metabolic bucket`() {
        val result = CycleProgressCalculator.buildCore(
            snapshot = snapshot(exercises = listOf(exercise(1L, "Bench Press", false, listOf("2026-04-01")))),
            sets = listOf(
                set(1L, "2026-04-01", 1, weight = 100, reps = 5),
                set(1L, "2026-04-01", 2, weight = 100, reps = 6),
                set(1L, "2026-04-01", 3, weight = 100, reps = 12),
                set(1L, "2026-04-01", 4, weight = 100, reps = 13),
                set(1L, "2026-04-01", 5, weight = 100, reps = 19),
                set(1L, "2026-04-01", 6, weight = 100, reps = 20),
            ),
            userBodyweightLbs = null,
            realSlotCount = 1,
        )

        assertEquals(listOf(1, 2, 2, 1), result.core.repBuckets.map { it.sets })
        assertEquals(Int.MAX_VALUE, result.core.repBuckets.last().maxReps)
    }

    @Test
    fun `bodyweight metrics support pure reps and weighted added load modes`() {
        val pure = CycleProgressCalculator.buildCore(
            snapshot = snapshot(exercises = listOf(exercise(1L, "Pull-up", true, listOf("2026-04-01")))),
            sets = listOf(set(1L, "2026-04-01", 1, reps = 12, isBodyweight = true)),
            userBodyweightLbs = null,
            realSlotCount = 1,
        ).core.lifts.single()
        val addedWithoutBodyweight = CycleProgressCalculator.buildCore(
            snapshot = snapshot(exercises = listOf(exercise(1L, "Weighted Pull-up", true, listOf("2026-04-01")))),
            sets = listOf(set(1L, "2026-04-01", 1, weight = 25, reps = 8, isBodyweight = true)),
            userBodyweightLbs = null,
            realSlotCount = 1,
        ).core.lifts.single()
        val addedWithBodyweight = CycleProgressCalculator.buildCore(
            snapshot = snapshot(exercises = listOf(exercise(1L, "Weighted Pull-up", true, listOf("2026-04-01")))),
            sets = listOf(set(1L, "2026-04-01", 1, weight = 25, reps = 8, isBodyweight = true)),
            userBodyweightLbs = 180,
            realSlotCount = 1,
        ).core.lifts.single()
        val addedWithInvalidBodyweight = CycleProgressCalculator.buildCore(
            snapshot = snapshot(exercises = listOf(exercise(1L, "Weighted Pull-up", true, listOf("2026-04-01")))),
            sets = listOf(set(1L, "2026-04-01", 1, weight = 25, reps = 8, isBodyweight = true)),
            userBodyweightLbs = 0,
            realSlotCount = 1,
        ).core.lifts.single()

        assertEquals(LiftMetric.REPS, pure.metric)
        assertEquals(12f, pure.points.single().value, 0.001f)
        assertEquals(LiftMetric.ADDED_WEIGHT, addedWithoutBodyweight.metric)
        assertEquals(25f, addedWithoutBodyweight.points.single().value, 0.001f)
        assertEquals(LiftMetric.ADDED_WEIGHT, addedWithInvalidBodyweight.metric)
        assertEquals(25f, addedWithInvalidBodyweight.points.single().value, 0.001f)
        assertEquals(LiftMetric.E1RM, addedWithBodyweight.metric)
        assertEquals(205f * (1f + 8f / 30f), addedWithBodyweight.points.single().value, 0.001f)
    }

    @Test
    fun `score normalizes weights and guards zero total weight`() {
        val comparison = CycleComparison(
            window = ComparisonWindow.M3,
            lifts = emptyList(),
            progression = 100f,
            momentum = 50f,
            consistency = 80f,
            improvedCount = 0,
            heldCount = 0,
            regressedCount = 0,
            comparedCount = 0,
        )

        assertEquals(78, CycleProgressCalculator.score(comparison, ScoreWeights.PRESET).composite)
        assertEquals(0, CycleProgressCalculator.score(comparison, ScoreWeights(0, 0, 0)).composite)
    }

    @Test
    fun `consistency clamps at one hundred when over attended`() {
        val coreResult = CycleProgressCalculator.buildCore(
            snapshot = snapshot(
                startDate = "2026-04-01",
                endDate = "2026-04-07",
                exercises = listOf(exercise(1L, "Pull-up", true, listOf("2026-04-01", "2026-04-02"))),
            ),
            sets = listOf(
                set(1L, "2026-04-01", 1, reps = 10, isBodyweight = true),
                set(1L, "2026-04-02", 1, reps = 10, isBodyweight = true),
            ),
            userBodyweightLbs = null,
            realSlotCount = 1,
        )

        val comparison = CycleProgressCalculator.compare(
            coreResult = coreResult,
            priorSetsByExerciseId = emptyMap(),
            window = ComparisonWindow.M1,
            userBodyweightLbs = null,
        )

        assertEquals(100f, comparison.consistency, 0.001f)
    }

    private fun repsCore(endReps: Int): CycleProgressCalculator.CoreResult =
        CycleProgressCalculator.buildCore(
            snapshot = snapshot(exercises = listOf(exercise(1L, "Pull-up", true, listOf("2026-04-30")))),
            sets = listOf(set(1L, "2026-04-30", 1, reps = endReps, isBodyweight = true)),
            userBodyweightLbs = null,
            realSlotCount = 1,
        )

    private fun snapshot(
        startDate: String = "2026-04-01",
        endDate: String = "2026-04-30",
        exercises: List<ExerciseSnapshot>,
    ): CycleSnapshot =
        CycleSnapshot(
            startDate = startDate,
            endDate = endDate,
            totals = CycleTotals(
                sessions = exercises.flatMap { exercise -> exercise.sessions.map { it.date } }.distinct().size,
                totalVolumeLbs = 0L,
                totalSets = 0,
                spanDays = 0,
            ),
            splits = listOf(
                SplitSnapshot(
                    slotId = 10L,
                    bucketKind = SplitBucketKind.Real,
                    name = "Push",
                    orderIndex = 0,
                    firstUsedDate = exercises.flatMap { it.sessions }.minOfOrNull { it.date },
                    lastUsedDate = exercises.flatMap { it.sessions }.maxOfOrNull { it.date },
                    usageCount = exercises.flatMap { it.sessions.map { session -> session.date } }.distinct().size,
                    exercises = exercises,
                )
            ),
        )

    private fun exercise(
        id: Long,
        name: String,
        isBodyweight: Boolean,
        dates: List<String>,
    ): ExerciseSnapshot =
        ExerciseSnapshot(
            exerciseId = id,
            name = name,
            isBodyweight = isBodyweight,
            sessions = dates.map { date ->
                SessionPoint(
                    date = date,
                    topWeight = null,
                    bestE1rm = null,
                    volumeLbs = 0L,
                    totalReps = 0,
                    setCount = 0,
                )
            },
            startE1rm = null,
            endE1rm = null,
            startTopWeight = null,
            endTopWeight = null,
            startVolumeLbs = null,
            endVolumeLbs = null,
        )

    private fun set(
        exerciseId: Long,
        date: String,
        setNumber: Int,
        weight: Int? = null,
        reps: Int,
        isBodyweight: Boolean = false,
        completed: Boolean = true,
    ): CycleProgressCalculator.SetInput =
        CycleProgressCalculator.SetInput(
            id = setNumber.toLong(),
            exerciseId = exerciseId,
            date = date,
            setNumber = setNumber,
            weightLbs = WeightLbs.fromWholePounds(weight),
            reps = reps,
            isBodyweight = isBodyweight,
            completed = completed,
        )
}
