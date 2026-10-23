package com.ayman.ecolift.agent.tools

import com.ayman.ecolift.data.FuzzyMatcher
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.epleyEstimate
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.plusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.LocalDate

class AgentToolsImpl(
    private val repos: AppRepos,
    private val embeddingIndex: ExerciseEmbeddingIndex = ExerciseEmbeddingIndex()
) : AgentTools {

    // findExercise

    override suspend fun findExercise(fuzzyName: String): ExerciseMatch? {
        val query = normalizeExerciseQuery(fuzzyName.trim().lowercase())
        if (query.isEmpty()) return null

        val all = repos.exercises.getAll()
        if (all.isEmpty()) return null

        fun ExerciseMatch(ex: com.ayman.ecolift.data.Exercise, score: Double) =
            ExerciseMatch(ex.id, ex.name, ex.isBodyweight, score)

        // 0. Exact name match (case-insensitive) - highest priority for @mentions
        all.firstOrNull { it.name.equals(query, ignoreCase = true) }
            ?.let { return ExerciseMatch(it, 0.0) }

        // 1. Exact substring
        all.firstOrNull { it.name.lowercase().contains(query) || query.contains(it.name.lowercase()) }
            ?.let { return ExerciseMatch(it, 0.0) }

        // 2. Word-level - any significant query word (>2 chars) appears in exercise name
        val queryWords = query.split(Regex("\\s+")).filter { it.length > 2 }
        all.firstOrNull { ex ->
            val exLower = ex.name.lowercase()
            queryWords.any { qw -> exLower.contains(qw) }
        }?.let { return ExerciseMatch(it, 1.0) }

        // 3. Levenshtein fallback on full query string
        val best = all.minByOrNull { FuzzyMatcher.levenshteinDistance(query, it.name.lowercase()) }
            ?: return null
        val score = FuzzyMatcher.levenshteinDistance(query, best.name.lowercase()).toDouble()
        if (score > (query.length / 2.0).coerceAtLeast(3.0)) return null

        return ExerciseMatch(best, score)
    }

    private fun normalizeExerciseQuery(query: String): String =
        when (query) {
            "calves", "calfs", "calf raises", "standing calves" -> "calf raise"
            "pullups", "pull ups" -> "pull up"
            "ohp" -> "overhead press"
            else -> query
        }

    // getRecentSets

    override suspend fun getRecentSets(exerciseId: Long, limit: Int): List<SetSummary> {
        val canonicalExerciseId = repos.exercises.canonicalId(exerciseId)
        val futureDate = "2030-01-01" // Allow seeing future test data
        return repos.sets.getRecentHistoryForExercise(canonicalExerciseId, futureDate)
            .take(limit)
            .map { set ->
                SetSummary(
                    setId = set.id,
                    date = set.date,
                    setNumber = set.setNumber,
                    weightLbs = set.weightLbs,
                    reps = set.reps,
                    isBodyweight = set.isBodyweight
                )
            }
    }

    // getExerciseHistory

    override suspend fun getExerciseHistory(exerciseId: Long, windowDays: Int): HistorySummary {
        val canonicalExerciseId = repos.exercises.canonicalId(exerciseId)
        val today = todayLocalDate().plusDays(1)
        val since = today.minusDays(windowDays.toLong()).toString()
        val exercise = repos.exercises.getById(canonicalExerciseId)
        val sets = repos.sets.getSetsSince(canonicalExerciseId, since)
        return buildHistorySummaryForSuggestion(
            exerciseId = canonicalExerciseId,
            windowDays = windowDays,
            exerciseName = exercise?.name.orEmpty(),
            allSets = sets,
            beforeDate = today.toString(),
        )
    }

    // getSimilarExercises

    override suspend fun getSimilarExercises(exerciseId: Long, k: Int): List<SimilarExercise> {
        val target = repos.exercises.getById(repos.exercises.canonicalId(exerciseId)) ?: return emptyList()
        val catalog = repos.exercises.getAll()
        return embeddingIndex.findSimilar(target, catalog, k)
    }

    // suggestWeight

    override suspend fun suggestWeight(exerciseId: Long, targetReps: Int): WeightSuggestion {
        return suggestWeightBeforeDate(
            exerciseId = exerciseId,
            targetReps = targetReps,
            beforeDate = todayLocalDate().plusDays(1).toString(),
        )
    }

    override suspend fun suggestWeightBeforeDate(
        exerciseId: Long,
        targetReps: Int,
        beforeDate: String
    ): WeightSuggestion {
        val canonicalExerciseId = repos.exercises.canonicalId(exerciseId)
        val exercise = repos.exercises.getById(canonicalExerciseId)
        val history = buildHistorySummaryForSuggestion(
            exerciseId = canonicalExerciseId,
            exerciseName = exercise?.name.orEmpty(),
            allSets = repos.sets.sets.value,
            beforeDate = beforeDate,
            windowDays = SUGGESTION_WINDOW_DAYS,
        )
        return WeightRecommender.suggest(history, targetReps, exercise?.isBodyweight ?: false)
    }

    // suggestTransferWeight

    override suspend fun suggestTransferWeight(
        targetExerciseId: Long,
        targetReps: Int
    ): WeightSuggestion {
        val canonicalTargetExerciseId = repos.exercises.canonicalId(targetExerciseId)
        val target = repos.exercises.getById(canonicalTargetExerciseId)
            ?: return noDataSuggestion(targetExerciseId, targetReps, "Target exercise not found.")

        // Find similar exercises that the user has actually logged
        val similar = getSimilarExercises(canonicalTargetExerciseId, k = 10)
        val sourced = similar.firstOrNull { candidate ->
            // Must have recent history
            val h = getExerciseHistory(candidate.exerciseId, windowDays = 60)
            h.sessionCount > 0 && h.topSetWeightLbs != null
        } ?: return noDataSuggestion(
            targetExerciseId, targetReps,
            "No similar exercises with history found. Log ${target.name} once to get a recommendation."
        )

        val sourceHistory = getExerciseHistory(sourced.exerciseId, windowDays = 60)
        val sourceWeight = sourceHistory.topSetWeightLbs ?: return noDataSuggestion(
            targetExerciseId, targetReps, "Source exercise has no weight data."
        )

        val ratio = sourced.transferRatioToQuery ?: return noDataSuggestion(
            targetExerciseId,
            targetReps,
            "Similar exercise is missing a transfer ratio. Log ${target.name} once to get a recommendation."
        )
        val estimatedLbs = (WeightLbs.toLbs(sourceWeight) * ratio).toInt().coerceAtLeast(5)
        val roundedLbs = ((estimatedLbs + 2) / 5) * 5
        val rounded = WeightLbs.fromWholePounds(roundedLbs)

        return WeightSuggestion(
            exerciseId = targetExerciseId,
            targetReps = targetReps,
            suggestedWeightLbs = rounded,
            confidence = WeightSuggestion.Confidence.LOW,
            reasoning = "Transfer from ${sourced.name} (${WeightLbs.formatStored(sourceWeight)} lb, ratio ${
                formatTwoDecimals(ratio)
            }). Estimated: ${roundedLbs} lb for ${countLabel(targetReps, "rep")}. Log ${target.name} to calibrate."
        )
    }

    // getSessionByDate

    override suspend fun getSessionByDate(date: String): SessionSnapshot {
        val sets = repos.sets.getSetsForDate(date)
        val exerciseIds = sets.map { it.exerciseId }.distinct()
        val exercises = exerciseIds.associateWith { repos.exercises.getById(it) }

        val snapshots = exerciseIds.mapNotNull { exId ->
            val ex = exercises[exId] ?: return@mapNotNull null
            ExerciseSnapshot(
                exerciseId = exId,
                name = ex.name,
                sets = sets.filter { it.exerciseId == exId }.map { s ->
                    SetSummary(s.id, s.date, s.setNumber, s.weightLbs, s.reps, s.isBodyweight)
                }
            )
        }
        return SessionSnapshot(date = date, exercises = snapshots)
    }

    // getProgressTrend

    override suspend fun getProgressTrend(exerciseId: Long): ProgressTrend {
        val canonicalExerciseId = repos.exercises.canonicalId(exerciseId)
        val futureDate = "2030-01-01" // Allow seeing future test data
        val exercise = repos.exercises.getById(canonicalExerciseId)
        val sets = repos.sets.getRecentHistoryForExercise(canonicalExerciseId, futureDate)

        // For trend analysis, use the latest workout date as "today" if it's in the future
        val latestDate = sets.maxOfOrNull { it.date } ?: todayLocalDate().toString()
        val referenceDate = if (latestDate > todayLocalDate().toString()) LocalDate.parse(latestDate) else todayLocalDate()

        val cutoff30 = referenceDate.minusDays(30).toString()
        val cutoff60 = referenceDate.minusDays(60).toString()

        val recent30Max = sets.filter { it.date >= cutoff30 }
            .mapNotNull { s -> s.weightLbs?.let { w -> s.reps?.let { r -> if (r > 0) epleyEstimate(WeightLbs.toLbs(w).toFloat(), r) else null } } }
            .maxOrNull()

        val prev30Max = sets.filter { it.date in cutoff60..<cutoff30 }
            .mapNotNull { s -> s.weightLbs?.let { w -> s.reps?.let { r -> if (r > 0) epleyEstimate(WeightLbs.toLbs(w).toFloat(), r) else null } } }
            .maxOrNull()

        val delta = if (recent30Max != null && prev30Max != null && prev30Max > 0f)
            ((recent30Max - prev30Max) / prev30Max) * 100f else null

        val allEst1Rms = sets.mapNotNull { s ->
            s.weightLbs?.let { w -> s.reps?.let { r -> if (r > 0) epleyEstimate(WeightLbs.toLbs(w).toFloat(), r) else null } }
        }
        val prSet = sets.maxByOrNull { it.weightLbs ?: 0 }

        val recentSessions = sets.groupBy { it.date }
            .entries.sortedByDescending { it.key }
            .take(5)
            .map { (date, setsForDate) ->
                val top = setsForDate.maxByOrNull { it.weightLbs ?: 0 }
                "$date: ${top?.weightLbs?.let(WeightLbs::formatStored) ?: "bw"}x${top?.reps ?: 0}"
            }

        return ProgressTrend(
            exerciseId = exerciseId,
            name = exercise?.name ?: "Unknown",
            sessionCount = sets.map { it.date }.distinct().size,
            prWeightLbs = prSet?.weightLbs,
            prDate = prSet?.date,
            est1Rm = allEst1Rms.maxOrNull()?.toInt(),
            deltaPercent = delta,
            recentSessions = recentSessions
        )
    }

    private fun noDataSuggestion(exerciseId: Long, targetReps: Int, reason: String) =
        WeightSuggestion(
            exerciseId = exerciseId,
            targetReps = targetReps,
            suggestedWeightLbs = null,
            confidence = WeightSuggestion.Confidence.NO_DATA,
            reasoning = reason
        )

    /** Replacement for "%.2f".format(value) — not available in wasm. */
    private fun formatTwoDecimals(value: Double): String {
        val scaled = (value * 100).toLong()
        val whole = scaled / 100
        val frac = (scaled % 100).toInt()
        return "$whole.${frac.toString().padStart(2, '0')}"
    }

    private companion object {
        const val SUGGESTION_WINDOW_DAYS = 3650
    }
}
