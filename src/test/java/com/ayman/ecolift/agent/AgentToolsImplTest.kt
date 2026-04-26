package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.tools.AgentToolsImpl
import com.ayman.ecolift.agent.tools.ExerciseEmbeddingIndex
import com.ayman.ecolift.agent.tools.WeightSuggestion
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseDao
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.WorkoutSetDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class AgentToolsImplTest {

    private lateinit var db: AppDatabase
    private lateinit var setDao: WorkoutSetDao
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var tools: AgentToolsImpl

    private val benchPress = Exercise(id = 1L, name = "Bench Press", isBodyweight = false)
    private val pullUp = Exercise(id = 2L, name = "Pull Up", isBodyweight = true)
    private val inclineBench = Exercise(id = 3L, name = "Incline Bench Press", isBodyweight = false)
    private fun lbs(value: Int): Int = WeightLbs.fromWholePounds(value)!!

    @Before
    fun setUp() {
        db = mock()
        setDao = mock()
        exerciseDao = mock()
        whenever(db.workoutSetDao()).thenReturn(setDao)
        whenever(db.exerciseDao()).thenReturn(exerciseDao)
        tools = AgentToolsImpl(db, ExerciseEmbeddingIndex())
    }

    // ── findExercise ──────────────────────────────────────────────────

    @Test
    fun `findExercise exact match returns exercise`() = runTest {
        whenever(exerciseDao.getAll()).thenReturn(listOf(benchPress, pullUp))
        val result = tools.findExercise("Bench Press")
        assertNotNull(result)
        assertEquals(1L, result!!.exerciseId)
        assertEquals(0.0, result.score, 0.001)
    }

    @Test
    fun `findExercise fuzzy match returns closest`() = runTest {
        whenever(exerciseDao.getAll()).thenReturn(listOf(benchPress, pullUp))
        val result = tools.findExercise("bench pres") // typo
        assertNotNull(result)
        assertEquals(1L, result!!.exerciseId)
    }

    @Test
    fun `findExercise blank query returns null`() = runTest {
        val result = tools.findExercise("   ")
        assertNull(result)
        verifyNoInteractions(exerciseDao)
    }

    @Test
    fun `findExercise no match returns null`() = runTest {
        whenever(exerciseDao.getAll()).thenReturn(listOf(benchPress))
        // "xyz" has distance > half query length from "bench press"
        val result = tools.findExercise("xyz")
        assertNull(result)
    }

    @Test
    fun `findExercise empty catalog returns null`() = runTest {
        whenever(exerciseDao.getAll()).thenReturn(emptyList())
        val result = tools.findExercise("Bench Press")
        assertNull(result)
    }

    // ── getRecentSets ─────────────────────────────────────────────────

    @Test
    fun `getRecentSets returns mapped summaries`() = runTest {
        val sets = listOf(
            WorkoutSet(id = 1L, exerciseId = 1L, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 8),
            WorkoutSet(id = 2L, exerciseId = 1L, date = "2026-04-16", setNumber = 2, weightLbs = 135, reps = 7),
        )
        whenever(setDao.getRecentHistoryForExercise(eq(1L), any())).thenReturn(sets)
        val result = tools.getRecentSets(1L, limit = 10)
        assertEquals(2, result.size)
        assertEquals(135, result[0].weightLbs)
        assertEquals(8, result[0].reps)
    }

    @Test
    fun `getRecentSets respects limit`() = runTest {
        val sets = (1..10).map { i ->
            WorkoutSet(id = i.toLong(), exerciseId = 1L, date = "2026-04-16", setNumber = i, weightLbs = 135, reps = 8)
        }
        whenever(setDao.getRecentHistoryForExercise(eq(1L), any())).thenReturn(sets)
        val result = tools.getRecentSets(1L, limit = 3)
        assertEquals(3, result.size)
    }

    // ── getExerciseHistory ────────────────────────────────────────────

    @Test
    fun `getExerciseHistory computes session count and top set`() = runTest {
        val sets = listOf(
            WorkoutSet(id = 1L, exerciseId = 1L, date = "2026-04-14", setNumber = 1, weightLbs = 135, reps = 8),
            WorkoutSet(id = 2L, exerciseId = 1L, date = "2026-04-14", setNumber = 2, weightLbs = 145, reps = 6),
            WorkoutSet(id = 3L, exerciseId = 1L, date = "2026-04-16", setNumber = 1, weightLbs = 150, reps = 5),
        )
        whenever(setDao.getSetsSince(eq(1L), any())).thenReturn(sets)
        val result = tools.getExerciseHistory(1L, windowDays = 30)
        assertEquals(2, result.sessionCount)         // 2 distinct dates
        assertEquals(150, result.topSetWeightLbs)    // max weight
        assertEquals(5, result.topSetReps)
    }

    @Test
    fun `getExerciseHistory empty returns zero sessions`() = runTest {
        whenever(setDao.getSetsSince(eq(1L), any())).thenReturn(emptyList())
        val result = tools.getExerciseHistory(1L, windowDays = 30)
        assertEquals(0, result.sessionCount)
        assertNull(result.topSetWeightLbs)
    }

    // ── suggestWeight ─────────────────────────────────────────────────

    @Test
    fun `suggestWeight with history returns suggestion`() = runTest {
        whenever(exerciseDao.getById(1L)).thenReturn(benchPress)
        val sets = listOf(
            WorkoutSet(id = 1L, exerciseId = 1L, date = "2026-04-16", setNumber = 1, weightLbs = lbs(135), reps = 10),
            WorkoutSet(id = 2L, exerciseId = 1L, date = "2026-04-15", setNumber = 1, weightLbs = lbs(135), reps = 10),
            WorkoutSet(id = 3L, exerciseId = 1L, date = "2026-04-14", setNumber = 1, weightLbs = lbs(135), reps = 10),
        )
        whenever(setDao.getSetsSince(eq(1L), any())).thenReturn(sets)

        // Target 8 reps, last top set 135×10 → 10 >= 8+2 → increase to 140
        val result = tools.suggestWeight(1L, targetReps = 8)
        assertEquals(lbs(140), result.suggestedWeightLbs)
        assertEquals(WeightSuggestion.Confidence.HIGH, result.confidence)
    }

    @Test
    fun `suggestWeight no history returns NO_DATA`() = runTest {
        whenever(exerciseDao.getById(1L)).thenReturn(benchPress)
        whenever(setDao.getSetsSince(eq(1L), any())).thenReturn(emptyList())
        val result = tools.suggestWeight(1L, targetReps = 8)
        assertEquals(WeightSuggestion.Confidence.NO_DATA, result.confidence)
        assertNull(result.suggestedWeightLbs)
    }

    @Test
    fun `suggestWeight bodyweight exercise returns null weight`() = runTest {
        whenever(exerciseDao.getById(2L)).thenReturn(pullUp)
        whenever(setDao.getSetsSince(eq(2L), any())).thenReturn(emptyList())
        val result = tools.suggestWeight(2L, targetReps = 10)
        assertNull(result.suggestedWeightLbs)
        assertEquals(WeightSuggestion.Confidence.MEDIUM, result.confidence)
    }

    // ── suggestTransferWeight ─────────────────────────────────────────

    @Test
    fun `suggestTransferWeight with similar exercise returns LOW confidence suggestion`() = runTest {
        // Target: Incline Bench Press (no history)
        whenever(exerciseDao.getById(3L)).thenReturn(inclineBench)
        whenever(exerciseDao.getAll()).thenReturn(listOf(benchPress, pullUp, inclineBench))
        whenever(exerciseDao.getById(1L)).thenReturn(benchPress)

        // Source: Bench Press has history
        val benchSets = listOf(
            WorkoutSet(id = 1L, exerciseId = 1L, date = "2026-04-16", setNumber = 1, weightLbs = 200, reps = 5),
            WorkoutSet(id = 2L, exerciseId = 1L, date = "2026-04-15", setNumber = 1, weightLbs = 200, reps = 5),
            WorkoutSet(id = 3L, exerciseId = 1L, date = "2026-04-14", setNumber = 1, weightLbs = 200, reps = 5),
        )
        whenever(setDao.getSetsSince(eq(1L), any())).thenReturn(benchSets)
        whenever(setDao.getSetsSince(eq(3L), any())).thenReturn(emptyList()) // no history for incline

        val result = tools.suggestTransferWeight(3L, targetReps = 8)
        assertEquals(WeightSuggestion.Confidence.LOW, result.confidence)
        assertNotNull(result.suggestedWeightLbs)
        assertTrue(result.reasoning.contains("Transfer"))
    }

    @Test
    fun `suggestTransferWeight no similar exercises returns NO_DATA`() = runTest {
        val loneExercise = Exercise(id = 99L, name = "Unknown Machine", isBodyweight = false)
        whenever(exerciseDao.getById(99L)).thenReturn(loneExercise)
        whenever(exerciseDao.getAll()).thenReturn(listOf(loneExercise))

        val result = tools.suggestTransferWeight(99L, targetReps = 8)
        assertEquals(WeightSuggestion.Confidence.NO_DATA, result.confidence)
        assertNull(result.suggestedWeightLbs)
    }

    @Test
    fun `suggestTransferWeight target not found returns NO_DATA`() = runTest {
        whenever(exerciseDao.getById(999L)).thenReturn(null)
        val result = tools.suggestTransferWeight(999L, targetReps = 8)
        assertEquals(WeightSuggestion.Confidence.NO_DATA, result.confidence)
    }
}
