package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.tools.AgentToolsImpl
import com.ayman.ecolift.agent.tools.WeightSuggestion
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolsImplTest {

    private val benchPress = Exercise(id = 1L, name = "Bench Press", isBodyweight = false)
    private val pullUp = Exercise(id = 2L, name = "Pull Up", isBodyweight = true)
    private val inclineBench = Exercise(id = 3L, name = "Incline Bench Press", isBodyweight = false)
    private val calfRaise = Exercise(id = 4L, name = "Standing Calf Raise Machine", isBodyweight = false)

    private fun lbs(value: Int): Int = WeightLbs.fromWholePounds(value)!!

    private fun ago(days: Int): String = todayLocalDate().minusDays(days.toLong()).toString()

    private fun logged(
        id: Long,
        exerciseId: Long,
        daysAgo: Int,
        setNumber: Int,
        weightLbs: Int?,
        reps: Int,
        isBodyweight: Boolean = false,
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        date = ago(daysAgo),
        setNumber = setNumber,
        weightLbs = weightLbs,
        reps = reps,
        isBodyweight = isBodyweight,
        completed = true,
    )

    private fun tools(exercises: List<Exercise>, sets: List<WorkoutSet> = emptyList()) =
        AgentToolsImpl(AgentMutationRepos(exercises, sets))

    @Test
    fun `findExercise exact match returns exercise`() = runTest {
        val result = tools(listOf(benchPress, pullUp)).findExercise("Bench Press")
        assertNotNull(result)
        assertEquals(1L, result!!.exerciseId)
        assertEquals(0.0, result.score, 0.001)
    }

    @Test
    fun `findExercise fuzzy match returns closest`() = runTest {
        val result = tools(listOf(benchPress, pullUp)).findExercise("bench pres")
        assertNotNull(result)
        assertEquals(1L, result!!.exerciseId)
    }

    @Test
    fun `findExercise maps common body-part alias to concrete exercise`() = runTest {
        val result = tools(listOf(benchPress, pullUp, calfRaise)).findExercise("calves")
        assertNotNull(result)
        assertEquals(calfRaise.id, result!!.exerciseId)
        assertEquals(0.0, result.score, 0.001)
    }

    @Test
    fun `findExercise blank query returns null`() = runTest {
        assertNull(tools(listOf(benchPress)).findExercise("   "))
    }

    @Test
    fun `findExercise no match returns null`() = runTest {
        assertNull(tools(listOf(benchPress)).findExercise("xyz"))
    }

    @Test
    fun `findExercise empty catalog returns null`() = runTest {
        assertNull(tools(emptyList()).findExercise("Bench Press"))
    }

    @Test
    fun `getRecentSets returns mapped summaries`() = runTest {
        val sets = listOf(
            logged(1L, 1L, daysAgo = 1, setNumber = 1, weightLbs = lbs(135), reps = 8),
            logged(2L, 1L, daysAgo = 1, setNumber = 2, weightLbs = lbs(135), reps = 7),
        )
        val result = tools(listOf(benchPress), sets).getRecentSets(1L, limit = 10)
        assertEquals(2, result.size)
        assertEquals(lbs(135), result[0].weightLbs)
        assertEquals(8, result[0].reps)
    }

    @Test
    fun `getRecentSets respects limit`() = runTest {
        val sets = (1..10).map { i ->
            logged(i.toLong(), 1L, daysAgo = 1, setNumber = i, weightLbs = lbs(135), reps = 8)
        }
        val result = tools(listOf(benchPress), sets).getRecentSets(1L, limit = 3)
        assertEquals(3, result.size)
    }

    @Test
    fun `getExerciseHistory computes session count and top set`() = runTest {
        val sets = listOf(
            logged(1L, 1L, daysAgo = 3, setNumber = 1, weightLbs = lbs(135), reps = 8),
            logged(2L, 1L, daysAgo = 3, setNumber = 2, weightLbs = lbs(145), reps = 6),
            logged(3L, 1L, daysAgo = 1, setNumber = 1, weightLbs = lbs(150), reps = 5),
        )
        val result = tools(listOf(benchPress), sets).getExerciseHistory(1L, windowDays = 30)
        assertEquals(2, result.sessionCount)
        assertEquals(lbs(150), result.topSetWeightLbs)
        assertEquals(5, result.topSetReps)
    }

    @Test
    fun `getExerciseHistory empty returns zero sessions`() = runTest {
        val result = tools(listOf(benchPress)).getExerciseHistory(1L, windowDays = 30)
        assertEquals(0, result.sessionCount)
        assertNull(result.topSetWeightLbs)
    }

    @Test
    fun `suggestWeight with history returns suggestion`() = runTest {
        // Eight reps stays under the step-up rule. Three sessions make the confidence high.
        val sets = listOf(
            logged(1L, 1L, daysAgo = 1, setNumber = 1, weightLbs = lbs(135), reps = 8),
            logged(2L, 1L, daysAgo = 2, setNumber = 1, weightLbs = lbs(135), reps = 8),
            logged(3L, 1L, daysAgo = 3, setNumber = 1, weightLbs = lbs(135), reps = 8),
        )
        val result = tools(listOf(benchPress), sets).suggestWeight(1L, targetReps = 8)
        assertEquals(lbs(135), result.suggestedWeightLbs)
        assertEquals(9, result.suggestedReps ?: -1)
        assertEquals(WeightSuggestion.Confidence.HIGH, result.confidence)
    }

    @Test
    fun `suggestWeight no history returns NO_DATA`() = runTest {
        val result = tools(listOf(benchPress)).suggestWeight(1L, targetReps = 8)
        assertEquals(WeightSuggestion.Confidence.NO_DATA, result.confidence)
        assertNull(result.suggestedWeightLbs)
    }

    @Test
    fun `suggestWeight bodyweight exercise returns null weight`() = runTest {
        val result = tools(listOf(pullUp)).suggestWeight(2L, targetReps = 10)
        assertNull(result.suggestedWeightLbs)
        assertEquals(WeightSuggestion.Confidence.NO_DATA, result.confidence)
    }

    @Test
    fun `suggestTransferWeight with similar exercise returns LOW confidence suggestion`() = runTest {
        val benchSets = listOf(
            logged(1L, 1L, daysAgo = 1, setNumber = 1, weightLbs = lbs(200), reps = 5),
            logged(2L, 1L, daysAgo = 2, setNumber = 1, weightLbs = lbs(200), reps = 5),
            logged(3L, 1L, daysAgo = 3, setNumber = 1, weightLbs = lbs(200), reps = 5),
        )
        val result = tools(listOf(benchPress, pullUp, inclineBench), benchSets)
            .suggestTransferWeight(3L, targetReps = 8)
        assertEquals(WeightSuggestion.Confidence.LOW, result.confidence)
        assertNotNull(result.suggestedWeightLbs)
        assertTrue(result.reasoning.contains("Transfer"))
    }

    @Test
    fun `suggestTransferWeight no similar exercises returns NO_DATA`() = runTest {
        val lone = Exercise(id = 99L, name = "Unknown Machine", isBodyweight = false)
        val result = tools(listOf(lone)).suggestTransferWeight(99L, targetReps = 8)
        assertEquals(WeightSuggestion.Confidence.NO_DATA, result.confidence)
        assertNull(result.suggestedWeightLbs)
    }

    @Test
    fun `suggestTransferWeight target not found returns NO_DATA`() = runTest {
        val result = tools(listOf(benchPress)).suggestTransferWeight(999L, targetReps = 8)
        assertEquals(WeightSuggestion.Confidence.NO_DATA, result.confidence)
    }
}
