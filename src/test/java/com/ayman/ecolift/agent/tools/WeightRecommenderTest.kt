package com.ayman.ecolift.agent.tools

import com.ayman.ecolift.data.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeightRecommenderTest {
    @Test
    fun highRepSetStepsUpWithInferredTenPoundStep() {
        val suggestion = WeightRecommender.suggest(
            history = history(
                exerciseName = "Incline Barbell Bench",
                sets = listOf(set(date = "2026-07-01", weightLbs = 2400, reps = 13)),
            ),
            targetReps = 10,
            isBodyweight = false,
        )

        assertEquals(2500, suggestion.suggestedWeightLbs)
        assertEquals(9, suggestion.suggestedReps)
    }

    @Test
    fun normalSetHoldsWeightAndAddsOneRep() {
        val suggestion = WeightRecommender.suggest(
            history = history(
                exerciseName = "Bench Press",
                sets = listOf(set(date = "2026-07-01", weightLbs = 2400, reps = 8)),
            ),
            targetReps = 8,
            isBodyweight = false,
        )

        assertEquals(2400, suggestion.suggestedWeightLbs)
        assertEquals(9, suggestion.suggestedReps)
    }

    @Test
    fun sessionTopSetUsesEstimatedOneRepMaxNotHighestWeight() {
        val suggestion = WeightRecommender.suggest(
            history = history(
                exerciseName = "Bench Press",
                sets = listOf(
                    set(date = "2026-07-01", setNumber = 1, weightLbs = 2500, reps = 1),
                    set(date = "2026-07-01", setNumber = 2, weightLbs = 2400, reps = 10),
                ),
            ),
            targetReps = 10,
            isBodyweight = false,
        )

        assertEquals(2400, suggestion.suggestedWeightLbs)
        assertEquals(11, suggestion.suggestedReps)
    }

    @Test
    fun bodyweightAddsOneRepWithoutChangingWeight() {
        val suggestion = WeightRecommender.suggest(
            history = history(
                exerciseName = "Pull Up",
                sets = listOf(set(date = "2026-07-01", weightLbs = null, reps = 10, isBodyweight = true)),
            ),
            targetReps = 10,
            isBodyweight = true,
        )

        assertNull(suggestion.suggestedWeightLbs)
        assertEquals(11, suggestion.suggestedReps)
    }

    @Test
    fun noHistoryReturnsNoData() {
        val suggestion = WeightRecommender.suggest(
            history = history(exerciseName = "Bench Press", sets = emptyList()),
            targetReps = 8,
            isBodyweight = false,
        )

        assertEquals(WeightSuggestion.Confidence.NO_DATA, suggestion.confidence)
        assertNull(suggestion.suggestedWeightLbs)
    }

    @Test
    fun dateBoundHistoryExcludesSelectedDateAndFutureSets() {
        val summary = history(
            exerciseName = "Bench Press",
            beforeDate = "2026-07-03",
            sets = listOf(
                set(date = "2026-07-01", weightLbs = 2300, reps = 8),
                set(date = "2026-07-03", weightLbs = 2400, reps = 12),
                set(date = "2026-07-04", weightLbs = 2500, reps = 12),
            ),
        )
        val suggestion = WeightRecommender.suggest(
            history = summary,
            targetReps = 8,
            isBodyweight = false,
        )

        assertEquals(1, summary.sessionCount)
        assertEquals(listOf(2300), summary.distinctCompletedWeights)
        assertEquals(2300, suggestion.suggestedWeightLbs)
        assertEquals(9, suggestion.suggestedReps)
    }

    private fun history(
        exerciseName: String,
        sets: List<WorkoutSet>,
        beforeDate: String = "2026-07-03",
    ): HistorySummary =
        buildHistorySummaryForSuggestion(
            exerciseId = 1L,
            exerciseName = exerciseName,
            allSets = sets,
            beforeDate = beforeDate,
            windowDays = 3650,
        )

    private fun set(
        date: String,
        setNumber: Int = 1,
        weightLbs: Int?,
        reps: Int,
        isBodyweight: Boolean = false,
    ): WorkoutSet =
        WorkoutSet(
            id = setNumber.toLong(),
            exerciseId = 1L,
            date = date,
            setNumber = setNumber,
            weightLbs = weightLbs,
            reps = reps,
            isBodyweight = isBodyweight,
            completed = true,
        )
}
