package com.ayman.ecolift.data.progress

import com.ayman.ecolift.data.CycleSnapshot
import com.ayman.ecolift.data.CycleTotals
import com.ayman.ecolift.data.ExerciseSnapshot
import com.ayman.ecolift.data.SessionPoint
import com.ayman.ecolift.data.SplitBucketKind
import com.ayman.ecolift.data.SplitSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class CycleProgressRollupTest {
    @Test
    fun one_exercise_in_three_buckets_has_one_rolled_union_and_three_split_trends() {
        val dates = listOf("2026-06-01", "2026-06-02", "2026-06-03")
        val snapshot = snapshot(
            dates.indices.map { index ->
                split(
                    slotId = (index + 1).toLong(),
                    orderIndex = index,
                    exercises = listOf(exercise(7, "Bench Press", dates = listOf(dates[index]))),
                )
            },
        )
        val sets = dates.flatMapIndexed { dateIndex, date ->
            List(dateIndex + 1) { setIndex ->
                set(
                    id = (dateIndex * 10 + setIndex + 1).toLong(),
                    exerciseId = 7,
                    date = date,
                    setNumber = setIndex + 1,
                    weightLbs = 1_000 + dateIndex * 100,
                    reps = 8,
                )
            }
        }

        val result = CycleProgressCalculator.buildCore(snapshot, sets, userBodyweightLbs = null)

        assertEquals(1, result.core.lifts.size)
        assertEquals(dates, result.core.lifts.single().points.map { it.date })
        assertEquals(3, result.core.liftsBySplit.size)
        assertEquals(6, result.totalSetsByExerciseId[7L])
    }

    @Test
    fun known_bodyweight_keeps_unweighted_and_weighted_sessions_in_one_e1rm_series() {
        val snapshot = snapshot(
            listOf(
                split(
                    slotId = 1,
                    orderIndex = 0,
                    exercises = listOf(
                        exercise(
                            id = 9,
                            name = "Dip",
                            isBodyweight = true,
                            dates = listOf("2026-06-01", "2026-06-08"),
                        ),
                    ),
                ),
            ),
            endDate = "2026-06-08",
        )
        val sets = listOf(
            set(1, 9, "2026-06-01", 1, weightLbs = null, reps = 10, isBodyweight = true),
            set(2, 9, "2026-06-08", 1, weightLbs = 1_000, reps = 8, isBodyweight = true),
        )

        val lift = CycleProgressCalculator.buildCore(snapshot, sets, userBodyweightLbs = 162)
            .core.lifts.single()

        assertEquals(LiftMetric.ADDED_WEIGHT, lift.metric)
        assertEquals(listOf("2026-06-08"), lift.points.map { it.date })
        assertEquals(100f, lift.points[0].value, absoluteTolerance = 0.001f)
    }

    @Test
    fun unknown_bodyweight_still_selects_a_metric_for_mixed_sessions() {
        val snapshot = snapshot(
            listOf(
                split(
                    slotId = 1,
                    orderIndex = 0,
                    exercises = listOf(
                        exercise(
                            id = 9,
                            name = "Dip",
                            isBodyweight = true,
                            dates = listOf("2026-06-01", "2026-06-08"),
                        ),
                    ),
                ),
            ),
            endDate = "2026-06-08",
        )
        val sets = listOf(
            set(1, 9, "2026-06-01", 1, weightLbs = null, reps = 10, isBodyweight = true),
            set(2, 9, "2026-06-08", 1, weightLbs = 1_000, reps = 8, isBodyweight = true),
        )

        val lift = CycleProgressCalculator.buildCore(snapshot, sets, userBodyweightLbs = null)
            .core.lifts.single()

        assertEquals(LiftMetric.ADDED_WEIGHT, lift.metric)
        assertEquals(listOf("2026-06-08"), lift.points.map { it.date })
    }

    @Test
    fun archive_shape_with_46_exercises_across_9_buckets_has_46_comparison_rows() {
        val splits = List(9) { bucketIndex ->
            val date = "2026-06-${(bucketIndex + 1).toString().padStart(2, '0')}"
            split(
                slotId = (bucketIndex + 1).toLong(),
                orderIndex = bucketIndex,
                exercises = List(9) { withinBucket ->
                    val occurrence = bucketIndex * 9 + withinBucket
                    val exerciseId = (occurrence % 46 + 1).toLong()
                    exercise(exerciseId, "Exercise $exerciseId", dates = listOf(date))
                },
            )
        }
        val coreResult = CycleProgressCalculator.buildCore(
            snapshot = snapshot(splits, endDate = "2026-06-09"),
            sets = emptyList(),
            userBodyweightLbs = null,
        )

        val comparison = CycleProgressCalculator.compare(
            coreResult = coreResult,
            priorSetsByExerciseId = emptyMap(),
            window = ComparisonWindow.M1,
            userBodyweightLbs = null,
        )

        assertEquals(81, coreResult.core.liftsBySplit.size)
        assertEquals(46, coreResult.core.lifts.size)
        assertEquals(46, comparison.lifts.size)
        assertEquals((1L..46L).toList(), comparison.lifts.map { it.exerciseId })
    }

    @Test
    fun cycle_wide_aggregates_are_independent_of_exercise_rollup() {
        val snapshot = snapshot(
            splits = listOf(
                split(
                    slotId = 1,
                    orderIndex = 0,
                    exercises = listOf(
                        exercise(1, "Squat", dates = listOf("2026-06-01", "2026-06-02")),
                    ),
                ),
                split(
                    slotId = 2,
                    orderIndex = 1,
                    exercises = listOf(
                        exercise(1, "Squat", dates = listOf("2026-06-03")),
                        exercise(2, "Row", dates = listOf("2026-06-03")),
                    ),
                ),
            ),
            endDate = "2026-06-07",
        )
        val sets = listOf(
            set(1, 1, "2026-06-01", 1, weightLbs = 1_000, reps = 5),
            set(2, 1, "2026-06-02", 1, weightLbs = 1_000, reps = 8),
            set(3, 1, "2026-06-03", 1, weightLbs = 1_000, reps = 15),
            set(4, 2, "2026-06-03", 1, weightLbs = 1_000, reps = 20),
        )

        val core = CycleProgressCalculator.buildCore(snapshot, sets, userBodyweightLbs = null).core

        assertEquals(3, core.sessions)
        assertEquals(3f, core.sessionsPerWeek)
        assertEquals(listOf("2026-06-01", "2026-06-02", "2026-06-03"), core.sessionDates)
        assertEquals(4, core.totalSets)
        assertEquals(listOf(1, 1, 1, 1), core.repBuckets.map { it.sets })
        assertEquals(2, core.lifts.size)
        assertEquals(3, core.liftsBySplit.size)
    }

    private fun snapshot(
        splits: List<SplitSnapshot>,
        startDate: String = "2026-06-01",
        endDate: String = "2026-06-30",
    ): CycleSnapshot = CycleSnapshot(
        startDate = startDate,
        endDate = endDate,
        totals = CycleTotals(sessions = 0, totalVolumeLbs = 0, totalSets = 0, spanDays = 0),
        splits = splits,
    )

    private fun split(
        slotId: Long,
        orderIndex: Int,
        exercises: List<ExerciseSnapshot>,
    ): SplitSnapshot = SplitSnapshot(
        slotId = slotId,
        bucketKind = SplitBucketKind.Real,
        name = "Split $slotId",
        orderIndex = orderIndex,
        firstUsedDate = exercises.flatMap { it.sessions }.minOfOrNull { it.date },
        lastUsedDate = exercises.flatMap { it.sessions }.maxOfOrNull { it.date },
        usageCount = exercises.flatMap { it.sessions }.map { it.date }.distinct().size,
        exercises = exercises,
    )

    private fun exercise(
        id: Long,
        name: String,
        isBodyweight: Boolean = false,
        dates: List<String>,
    ): ExerciseSnapshot = ExerciseSnapshot(
        exerciseId = id,
        name = name,
        isBodyweight = isBodyweight,
        sessions = dates.map { date ->
            SessionPoint(
                date = date,
                topWeight = if (isBodyweight) null else 100f,
                bestE1rm = if (isBodyweight) null else 120f,
                volumeLbs = 1_000,
                totalReps = 10,
                setCount = 1,
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
        id: Long,
        exerciseId: Long,
        date: String,
        setNumber: Int,
        weightLbs: Int?,
        reps: Int?,
        isBodyweight: Boolean = false,
    ): CycleProgressCalculator.SetInput = CycleProgressCalculator.SetInput(
        id = id,
        exerciseId = exerciseId,
        date = date,
        setNumber = setNumber,
        weightLbs = weightLbs,
        reps = reps,
        isBodyweight = isBodyweight,
        completed = true,
    )
}
