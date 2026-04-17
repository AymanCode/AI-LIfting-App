package com.ayman.ecolift.agent.tools

/**
 * Pure Kotlin weight recommendation logic. No model, no DB — takes history as input.
 *
 * Rules (all weights in lbs):
 *   - No history → NO_DATA
 *   - Bodyweight exercise → null weight, MEDIUM confidence
 *   - Last top set reps ≥ targetReps + 2 → increase by STEP_LBS
 *   - Last top set reps < targetReps     → decrease by STEP_LBS
 *   - Otherwise                          → hold current weight
 *
 * "Top set" = set with the highest weight on the most recent session.
 */
object WeightRecommender {

    private const val STEP_LBS = 5  // standard small plate increment

    fun suggest(
        history: HistorySummary,
        targetReps: Int,
        isBodyweight: Boolean
    ): WeightSuggestion {
        if (isBodyweight) {
            return WeightSuggestion(
                exerciseId = history.exerciseId,
                targetReps = targetReps,
                suggestedWeightLbs = null,
                confidence = WeightSuggestion.Confidence.MEDIUM,
                reasoning = "Bodyweight exercise — no load to suggest."
            )
        }

        val topWeight = history.topSetWeightLbs
        val topReps = history.topSetReps

        if (topWeight == null || topReps == null || history.sessionCount == 0) {
            return WeightSuggestion(
                exerciseId = history.exerciseId,
                targetReps = targetReps,
                suggestedWeightLbs = null,
                confidence = WeightSuggestion.Confidence.NO_DATA,
                reasoning = "No logged history for this exercise."
            )
        }

        val (suggested, reasoning) = when {
            topReps >= targetReps + 2 ->
                topWeight + STEP_LBS to
                    "Last top set: ${topWeight}lbs × ${topReps} reps. " +
                    "Reps ≥ target + 2 → increase by ${STEP_LBS}lbs."

            topReps < targetReps ->
                maxOf(topWeight - STEP_LBS, STEP_LBS) to
                    "Last top set: ${topWeight}lbs × ${topReps} reps. " +
                    "Reps < target → decrease by ${STEP_LBS}lbs."

            else ->
                topWeight to
                    "Last top set: ${topWeight}lbs × ${topReps} reps. " +
                    "On target — hold weight."
        }

        val confidence = when {
            history.sessionCount >= 3 -> WeightSuggestion.Confidence.HIGH
            history.sessionCount >= 1 -> WeightSuggestion.Confidence.MEDIUM
            else -> WeightSuggestion.Confidence.LOW
        }

        return WeightSuggestion(
            exerciseId = history.exerciseId,
            targetReps = targetReps,
            suggestedWeightLbs = suggested,
            confidence = confidence,
            reasoning = reasoning
        )
    }
}
