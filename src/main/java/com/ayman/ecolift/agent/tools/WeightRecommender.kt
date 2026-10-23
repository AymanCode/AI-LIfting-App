package com.ayman.ecolift.agent.tools

import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.progress.inferWeightStepStored
import com.ayman.ecolift.web.util.countLabel
import kotlin.math.max

/**
 * Pure Kotlin weight recommendation logic. No model, no DB - takes history as input.
 *
 * Rules (all weights in lbs):
 *   - No history -> NO_DATA
 *   - Bodyweight exercise -> one more rep, null weight
 *   - Weighted exercise -> infer a realistic step, then progress from the
 *     latest session's estimated-1RM top set.
 */
object WeightRecommender {

    fun suggest(
        history: HistorySummary,
        targetReps: Int,
        isBodyweight: Boolean
    ): WeightSuggestion {
        val latestTopSet = history.topSetsBySession.firstOrNull()
            ?: history.topSetWeightLbs?.let { weight ->
                SessionTopSet(date = "", weightLbs = weight, reps = history.topSetReps)
            }
        val topWeight = latestTopSet?.weightLbs
        val topReps = latestTopSet?.reps

        if (topReps == null || topReps <= 0 || history.sessionCount == 0) {
            return noData(history.exerciseId, targetReps)
        }

        if (isBodyweight) {
            val suggestedReps = topReps + 1
            return WeightSuggestion(
                exerciseId = history.exerciseId,
                targetReps = targetReps,
                suggestedWeightLbs = null,
                confidence = WeightSuggestion.Confidence.MEDIUM,
                reasoning = "You did $topReps last time — try $suggestedReps.",
                suggestedReps = suggestedReps,
            )
        }

        if (topWeight == null || topWeight <= 0) return noData(history.exerciseId, targetReps)

        val step = inferWeightStepStored(
            distinctStoredWeights = history.distinctCompletedWeights,
            exerciseName = history.exerciseName,
            topStoredWeight = topWeight,
        )

        val stepUp = topReps >= 12 ||
            (topReps >= 10 && (history.previousTopRepsAtLatestWeight ?: 0) >= 10)

        val (suggestedWeight, suggestedReps, reasoning) = when {
            stepUp -> {
                val nextWeight = topWeight + step
                Triple(
                    nextWeight,
                    max(6, topReps - 4),
                    "You've hit ${fmt(topWeight)}×$topReps — move up to ${fmt(nextWeight)}.",
                )
            }

            targetReps > 0 && topReps < targetReps -> {
                val nextWeight = maxOf(topWeight - step, step)
                Triple(
                    nextWeight,
                    targetReps,
                    "Last top set: ${fmt(topWeight)} lb × ${countLabel(topReps, "rep")}. Reps < target -> decrease by ${fmt(step)} lb.",
                )
            }

            else -> {
                val nextReps = topReps + 1
                Triple(
                    topWeight,
                    nextReps,
                    "Last time: ${fmt(topWeight)}×$topReps. Try one more rep.",
                )
            }
        }

        val confidence = when {
            history.sessionCount >= 3 -> WeightSuggestion.Confidence.HIGH
            history.sessionCount >= 1 -> WeightSuggestion.Confidence.MEDIUM
            else -> WeightSuggestion.Confidence.LOW
        }

        return WeightSuggestion(
            exerciseId = history.exerciseId,
            targetReps = targetReps,
            suggestedWeightLbs = suggestedWeight,
            confidence = confidence,
            reasoning = reasoning,
            suggestedReps = suggestedReps,
        )
    }

    private fun noData(exerciseId: Long, targetReps: Int): WeightSuggestion =
        WeightSuggestion(
            exerciseId = exerciseId,
            targetReps = targetReps,
            suggestedWeightLbs = null,
            confidence = WeightSuggestion.Confidence.NO_DATA,
            reasoning = "No logged history for this exercise.",
        )

    private fun fmt(storedWeight: Int): String = WeightLbs.formatStored(storedWeight)
}
