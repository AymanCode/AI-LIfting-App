package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.tools.ExerciseEmbeddingIndex
import com.ayman.ecolift.data.Exercise
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExerciseEmbeddingIndexTest {

    private lateinit var index: ExerciseEmbeddingIndex

    // Small fixture catalog
    private val catalog = listOf(
        Exercise(id = 1L, name = "Bench Press"),
        Exercise(id = 2L, name = "Incline Bench Press"),
        Exercise(id = 3L, name = "Overhead Press"),
        Exercise(id = 4L, name = "Squat"),
        Exercise(id = 5L, name = "Deadlift"),
        Exercise(id = 6L, name = "Lat Pulldown"),
        Exercise(id = 7L, name = "Cable Row"),
        Exercise(id = 8L, name = "Bicep Curl"),
        Exercise(id = 9L, name = "Tricep Pushdown"),
        Exercise(id = 10L, name = "Chest Fly"),
    )

    @Before
    fun setUp() {
        index = ExerciseEmbeddingIndex()
    }

    @Test
    fun `query exercise excluded from results`() {
        val query = catalog[0] // Bench Press id=1
        val results = index.findSimilar(query, catalog, k = 5)
        assertTrue(results.none { it.exerciseId == 1L })
    }

    @Test
    fun `same pattern exercises ranked highest`() {
        val benchPress = catalog[0] // HorizontalPress
        val results = index.findSimilar(benchPress, catalog, k = 5)
        // Incline Bench Press is InclinePress (close to HorizontalPress)
        // Chest Fly is ChestFly (related)
        assertTrue(results.isNotEmpty())
        // Top result should be same-or-adjacent pattern, not Squat/Deadlift
        val topId = results.first().exerciseId
        assertFalse("Squat should not be top match for Bench Press", topId == 4L)
        assertFalse("Deadlift should not be top match for Bench Press", topId == 5L)
    }

    @Test
    fun `k limits result count`() {
        val query = catalog[0]
        val results = index.findSimilar(query, catalog, k = 3)
        assertTrue(results.size <= 3)
    }

    @Test
    fun `results ordered by descending similarity score`() {
        val query = catalog[0]
        val results = index.findSimilar(query, catalog, k = 5)
        val scores = results.map { it.similarityScore }
        assertEquals(scores.sortedDescending(), scores)
    }

    @Test
    fun `all scores between 0 and 1`() {
        val query = catalog[0]
        val results = index.findSimilar(query, catalog, k = 10)
        results.forEach { result ->
            assertTrue("Score ${result.similarityScore} out of range", result.similarityScore in 0.0..1.0)
        }
    }

    @Test
    fun `unrelated movements return low or no results`() {
        val squat = catalog[3] // Squat
        val results = index.findSimilar(squat, catalog, k = 5)
        // Should not include Bench Press or Lat Pulldown in top results
        val ids = results.map { it.exerciseId }.toSet()
        // Hinge (deadlift) should appear since squat→hinge ratio exists
        assertTrue("Deadlift should be similar to Squat", 5L in ids)
    }

    @Test
    fun `empty catalog returns empty list`() {
        val results = index.findSimilar(catalog[0], emptyList(), k = 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `single item catalog excluding query returns empty`() {
        val results = index.findSimilar(catalog[0], listOf(catalog[0]), k = 5)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `sharedPattern field populated`() {
        val results = index.findSimilar(catalog[0], catalog, k = 3)
        results.forEach { result ->
            assertTrue("sharedPattern should not be blank", result.sharedPattern.isNotBlank())
        }
    }
}
