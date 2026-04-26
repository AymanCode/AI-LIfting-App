package com.ayman.ecolift.agent.tools

import com.ayman.ecolift.data.WeightLbs

/**
 * Pure Kotlin weight recommendation logic. No model, no DB - takes history as input.
 *
 * Rules (all weights in lbs):
 *   - No history -> NO_DATA
 *   - Bodyweight exercise -> null weight, MEDIUM confidence
 *   - Last top set reps >= targetReps + 2 -> increase by 5 lbs
 *   - Last top set reps < targetReps      -> decrease by 5 lbs
 *   - Otherwise                           -> hold current weight
 */
object WeightRecommender {

    private val stepStorage = WeightLbs.fromWholePounds(5) ?: 50

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
                reasoning = "Bodyweight exercise - no load to suggest."
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
                topWeight + stepStorage to
                    "Last top set: ${WeightLbs.formatStored(topWeight)}lbs x $topReps reps. Reps >= target + 2 -> increase by 5lbs."

            topReps < targetReps ->
                maxOf(topWeight - stepStorage, stepStorage) to
                    "Last top set: ${WeightLbs.formatStored(topWeight)}lbs x $topReps reps. Reps < target -> decrease by 5lbs."

            else ->
                topWeight to
                    "Last top set: ${WeightLbs.formatStored(topWeight)}lbs x $topReps reps. On target - hold weight."
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
