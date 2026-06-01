package com.ayman.ecolift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleSnapshotBuilderTest {
    private val push = CycleSlot(id = 10L, name = "Push", orderIndex = 0)
    private val pull = CycleSlot(id = 20L, name = "Pull", orderIndex = 1)

    @Test
    fun `builder filters by date window and completed sets and converts tenths once`() {
        val snapshot = CycleSnapshotBuilder.build(
            startDate = "2026-01-01",
            endDate = "2026-01-31",
            slots = listOf(push),
            splitExercises = emptyList(),
            workoutDays = listOf(day("2026-01-10", slotId = 10L)),
            sets = listOf(
                set(1L, "2026-01-10", 1, weightLbs = 1850, reps = 5, completed = true),
                set(1L, "2026-01-10", 2, weightLbs = 1850, reps = 5, completed = false),
                set(1L, "2026-02-01", 1, weightLbs = 3000, reps = 5, completed = true),
            ),
            exerciseNames = mapOf(1L to ExerciseMeta("Bench Press", isBodyweight = false)),
        )

        val point = snapshot.splits.single().exercises.single().sessions.single()
        assertEquals(1, snapshot.totals.totalSets)
        assertEquals(925L, snapshot.totals.totalVolumeLbs)
        assertEquals(185f, point.topWeight!!, 0.001f)
        assertEquals(185f * (1f + 5f / 30f), point.bestE1rm!!, 0.01f)
    }

    @Test
    fun `orphaned slots become distinct deleted buckets and null slots become unassigned`() {
        val snapshot = CycleSnapshotBuilder.build(
            startDate = "2026-01-01",
            endDate = "2026-01-31",
            slots = listOf(push),
            splitExercises = emptyList(),
            workoutDays = listOf(
                day("2026-01-05", slotId = 99L),
                day("2026-01-06", slotId = 98L),
                day("2026-01-07", slotId = null),
            ),
            sets = listOf(
                set(1L, "2026-01-05", 1, weightLbs = 1850, reps = 5),
                set(1L, "2026-01-06", 1, weightLbs = 1850, reps = 5),
                set(1L, "2026-01-07", 1, weightLbs = 1850, reps = 5),
            ),
            exerciseNames = mapOf(1L to ExerciseMeta("Bench Press", isBodyweight = false)),
        )

        val deleted = snapshot.splits.filter { it.bucketKind == SplitBucketKind.Deleted }
        assertEquals(listOf(98L, 99L), deleted.map { it.slotId })
        assertEquals(1, snapshot.splits.count { it.bucketKind == SplitBucketKind.Unassigned })
    }

    @Test
    fun `split count excludes untrained real splits and synthetic buckets`() {
        val snapshot = CycleSnapshotBuilder.build(
            startDate = "2026-01-01",
            endDate = "2026-01-31",
            slots = listOf(push, pull),
            splitExercises = emptyList(),
            workoutDays = listOf(
                day("2026-01-05", slotId = 10L),
                day("2026-01-06", slotId = null),
            ),
            sets = listOf(
                set(1L, "2026-01-05", 1, weightLbs = 1850, reps = 5),
                set(1L, "2026-01-06", 1, weightLbs = 1850, reps = 5),
            ),
            exerciseNames = mapOf(1L to ExerciseMeta("Bench Press", isBodyweight = false)),
        )

        assertEquals(1, CycleSnapshotBuilder.splitCount(snapshot))
    }

    @Test
    fun `bodyweight sets contribute reps and zero weighted volume`() {
        val snapshot = CycleSnapshotBuilder.build(
            startDate = "2026-01-01",
            endDate = "2026-01-31",
            slots = listOf(push),
            splitExercises = emptyList(),
            workoutDays = listOf(day("2026-01-10", slotId = 10L)),
            sets = listOf(
                set(2L, "2026-01-10", 1, weightLbs = null, reps = 12, isBodyweight = true),
            ),
            exerciseNames = mapOf(2L to ExerciseMeta("Pull-up", isBodyweight = true)),
        )

        val exercise = snapshot.splits.single().exercises.single()
        assertTrue(exercise.isBodyweight)
        assertNull(exercise.endTopWeight)
        assertEquals(12, exercise.sessions.single().totalReps)
        assertEquals(0L, exercise.sessions.single().volumeLbs)
    }

    private fun set(
        exerciseId: Long,
        date: String,
        setNumber: Int,
        weightLbs: Int?,
        reps: Int?,
        completed: Boolean = true,
        isBodyweight: Boolean = false,
    ): WorkoutSet = WorkoutSet(
        exerciseId = exerciseId,
        date = date,
        setNumber = setNumber,
        weightLbs = weightLbs,
        reps = reps,
        completed = completed,
        isBodyweight = isBodyweight,
    )

    private fun day(date: String, slotId: Long? = null, slotType: Int? = null): WorkoutDay =
        WorkoutDay(date = date, cycleSlotId = slotId, cycleSlotType = slotType)
}
