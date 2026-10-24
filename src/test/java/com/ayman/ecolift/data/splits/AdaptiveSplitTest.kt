package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.*

class AdaptiveSplitTest {
    @Test fun sourceWorkoutPreservesEverySetAndResetsCompletion() {
        val rows = listOf(WorkoutSet(id = 1, exerciseId = 1, date = "2026-08-15", setNumber = 1, weightLbs = 1350, reps = 8, completed = true), WorkoutSet(id = 2, exerciseId = 1, date = "2026-08-15", setNumber = 2, weightLbs = 1300, reps = 7, completed = true))
        val seed = SplitSeedBuilder.forExplicitExercises("2026-09-04", listOf(1), rows)
        assertEquals(listOf(1350, 1300), seed.map { it.weightLbs })
        assertEquals(listOf(8, 7), seed.map { it.reps })
        assertTrue(seed.all { !it.completed && it.date == "2026-09-04" })
    }
}
