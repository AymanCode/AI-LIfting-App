package com.ayman.ecolift.data.progress

import com.ayman.ecolift.data.CycleSlot
import com.ayman.ecolift.data.CycleSnapshotBuilder
import com.ayman.ecolift.data.ExerciseMeta
import com.ayman.ecolift.data.SplitExercise
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CycleProgressSmokeTest {
    @Test
    fun snapshot_and_core_build_from_a_tiny_cycle() {
        val sets = listOf(
            WorkoutSet(id = 1, exerciseId = 1, date = "2026-06-01", setNumber = 1, weightLbs = 1850, reps = 8, completed = true),
            WorkoutSet(id = 2, exerciseId = 1, date = "2026-06-08", setNumber = 1, weightLbs = 1900, reps = 8, completed = true),
        )
        val snapshot = CycleSnapshotBuilder.build(
            startDate = "2026-06-01",
            endDate = "2026-06-08",
            slots = listOf(CycleSlot(id = 1, name = "Push", orderIndex = 0)),
            splitExercises = listOf(SplitExercise(id = 1, splitId = 1, exerciseId = 1, orderIndex = 0)),
            workoutDays = listOf(
                WorkoutDay(date = "2026-06-01", cycleSlotId = 1),
                WorkoutDay(date = "2026-06-08", cycleSlotId = 1),
            ),
            sets = sets,
            exerciseNames = mapOf(1L to ExerciseMeta(name = "Bench Press", isBodyweight = false)),
            userBodyweightLbs = null,
        )
        assertEquals(2, snapshot.totals.sessions)
        assertTrue(snapshot.totals.totalVolumeLbs > 0)

        val core = CycleProgressCalculator.buildCore(
            snapshot = snapshot,
            sets = sets.map {
                CycleProgressCalculator.SetInput(it.id, it.exerciseId, it.date, it.setNumber, it.weightLbs, it.reps, it.isBodyweight, it.completed)
            },
            userBodyweightLbs = null,
            realSlotCount = 1,
        )
        assertEquals(1, core.core.lifts.size)
        assertEquals(1, core.core.liftsBySplit.size)
    }
}
