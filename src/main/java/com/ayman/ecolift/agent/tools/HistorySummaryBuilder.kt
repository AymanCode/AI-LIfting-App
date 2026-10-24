package com.ayman.ecolift.agent.tools

import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.epleyEstimate

internal fun buildHistorySummaryForSuggestion(
    exerciseId: Long,
    exerciseName: String,
    allSets: List<WorkoutSet>,
    beforeDate: String,
    windowDays: Int,
): HistorySummary {
    val eligible = allSets
        .filter { it.exerciseId == exerciseId && it.completed && it.date < beforeDate }
        .sortedWith(compareBy<WorkoutSet> { it.date }.thenBy { it.setNumber })

    val sessionsNewestFirst = eligible
        .groupBy { it.date }
        .entries
        .sortedByDescending { it.key }

    val sessionTopSets = sessionsNewestFirst.mapNotNull { (date, sets) ->
        val top = sets.maxWithOrNull(
            compareBy<WorkoutSet> { estimatedOneRepMaxScore(it.weightLbs, it.reps) }
                .thenBy { it.reps ?: 0 }
                .thenBy { it.weightLbs ?: 0 }
        ) ?: return@mapNotNull null
        SessionTopSet(
            date = date,
            weightLbs = top.weightLbs,
            reps = top.reps,
        )
    }

    val maxWeightSet = eligible
        .filter { (it.weightLbs ?: 0) > 0 }
        .maxWithOrNull(
            compareBy<WorkoutSet> { it.weightLbs ?: 0 }
                .thenBy { it.reps ?: 0 }
        )

    val latestTopWeight = sessionTopSets.firstOrNull()?.weightLbs
    var previousTopRepsAtLatestWeight: Int? = null
    if (latestTopWeight != null) {
        for ((_, sets) in sessionsNewestFirst.drop(1)) {
            val repsAtWeight = sets.filter { it.weightLbs == latestTopWeight }
                .mapNotNull { it.reps }
                .filter { it > 0 }
                .maxOrNull()
            if (repsAtWeight != null) {
                previousTopRepsAtLatestWeight = repsAtWeight
                break
            }
        }
    }

    return HistorySummary(
        exerciseId = exerciseId,
        windowDays = windowDays,
        sessionCount = sessionsNewestFirst.size,
        topSetWeightLbs = maxWeightSet?.weightLbs,
        topSetReps = maxWeightSet?.reps,
        recentSets = eligible
            .map { set ->
                SetSummary(
                    setId = set.id,
                    date = set.date,
                    setNumber = set.setNumber,
                    weightLbs = set.weightLbs,
                    reps = set.reps,
                    isBodyweight = set.isBodyweight,
                )
            }
            .sortedWith(compareByDescending<SetSummary> { it.date }.thenBy { it.setNumber }),
        exerciseName = exerciseName,
        topSetsBySession = sessionTopSets,
        distinctCompletedWeights = eligible
            .mapNotNull { it.weightLbs }
            .filter { it > 0 }
            .distinct()
            .sorted(),
        previousTopRepsAtLatestWeight = previousTopRepsAtLatestWeight,
    )
}

private fun estimatedOneRepMaxScore(weightStored: Int?, reps: Int?): Float {
    val repCount = reps ?: return 0f
    if (repCount <= 0) return 0f
    return epleyEstimate(WeightLbs.toLbs(weightStored).toFloat(), repCount)
}
