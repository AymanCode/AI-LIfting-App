package com.ayman.ecolift.agent.tools

import com.ayman.ecolift.data.Exercise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseEmbeddingIndexTest {
    @Test
    fun similar_exercise_keeps_transfer_ratio_separate_from_similarity_score() {
        val similar = ExerciseEmbeddingIndex().findSimilar(
            queryExercise = Exercise(id = 1L, name = "Overhead Press"),
            catalog = listOf(
                Exercise(id = 1L, name = "Overhead Press"),
                Exercise(id = 2L, name = "Bench Press"),
            ),
            k = 1,
        ).single()

        assertEquals(2L, similar.exerciseId)
        assertEquals(0.65, similar.transferRatioToQuery ?: 0.0, absoluteTolerance = 0.0001)
        assertTrue(similar.similarityScore < (similar.transferRatioToQuery ?: 0.0))
    }
}
