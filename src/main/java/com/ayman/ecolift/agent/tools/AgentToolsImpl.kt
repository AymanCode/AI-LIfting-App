package com.ayman.ecolift.agent.tools

import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.FuzzyMatcher
import java.time.LocalDate

class AgentToolsImpl(
    private val db: AppDatabase,
    private val embeddingIndex: ExerciseEmbeddingIndex = ExerciseEmbeddingIndex()
) : AgentTools {

    // ── findExercise ─────────────────────────────────────────────────

    override suspend fun findExercise(fuzzyName: String): ExerciseMatch? {
        val query = fuzzyName.trim().lowercase()
        if (query.isEmpty()) return null

        val all = db.exerciseDao().getAll()
        if (all.isEmpty()) return null

        // Score each exercise by Levenshtein distance; lower = better
        val best = all.minByOrNull { exercise ->
            FuzzyMatcher.levenshteinDistance(query, exercise.name.lowercase())
        } ?: return null

        val score = FuzzyMatcher.levenshteinDistance(query, best.name.lowercase()).toDouble()
        // Reject if distance > half the query length (too dissimilar)
        if (score > (query.length / 2.0).coerceAtLeast(3.0)) return null

        return ExerciseMatch(
            exerciseId = best.id,
            name = best.name,
            isBodyweight = best.isBodyweight,
            score = score
        )
    }

    // ── getRecentSets ────────────────────────────────────────────────

    override suspend fun getRecentSets(exerciseId: Long, limit: Int): List<SetSummary> {
        val today = LocalDate.now().toString()
        return db.workoutSetDao()
            .getRecentHistoryForExercise(exerciseId, today)
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

    // ── getExerciseHistory ───────────────────────────────────────────

    override suspend fun getExerciseHistory(exerciseId: Long, windowDays: Int): HistorySummary {
        val today = LocalDate.now()
        val since = today.minusDays(windowDays.toLong()).toString()
        val beforeDate = today.toString()

        val sets = db.workoutSetDao().getSetsSince(exerciseId, since)
        val summaries = sets.map { set ->
            SetSummary(
                setId = set.id,
                date = set.date,
                setNumber = set.setNumber,
                weightLbs = set.weightLbs,
                reps = set.reps,
                isBodyweight = set.isBodyweight
            )
        }

        val sessionCount = sets.map { it.date }.distinct().size
        val topSet = sets.maxByOrNull { it.weightLbs ?: 0 }

        return HistorySummary(
            exerciseId = exerciseId,
            windowDays = windowDays,
            sessionCount = sessionCount,
            topSetWeightLbs = topSet?.weightLbs,
            topSetReps = topSet?.reps,
            recentSets = summaries.sortedByDescending { it.date }
        )
    }

    // ── getSimilarExercises ──────────────────────────────────────────

    override suspend fun getSimilarExercises(exerciseId: Long, k: Int): List<SimilarExercise> {
        val target = db.exerciseDao().getById(exerciseId) ?: return emptyList()
        val catalog = db.exerciseDao().getAll()
        return embeddingIndex.findSimilar(target, catalog, k)
    }

    // ── suggestWeight ────────────────────────────────────────────────

    override suspend fun suggestWeight(exerciseId: Long, targetReps: Int): WeightSuggestion {
        val exercise = db.exerciseDao().getById(exerciseId)
        val history = getExerciseHistory(exerciseId, windowDays = 30)
        return WeightRecommender.suggest(history, targetReps, exercise?.isBodyweight ?: false)
    }

    // ── suggestTransferWeight ────────────────────────────────────────

    override suspend fun suggestTransferWeight(
        targetExerciseId: Long,
        targetReps: Int
    ): WeightSuggestion {
        val target = db.exerciseDao().getById(targetExerciseId)
            ?: return noDataSuggestion(targetExerciseId, targetReps, "Target exercise not found.")

        // Find similar exercises that the user has actually logged
        val similar = getSimilarExercises(targetExerciseId, k = 10)
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

        // Apply transfer ratio if available, else use raw score as proxy
        val ratio = sourced.similarityScore  // Phase 3: similarity ≈ ratio
        val estimated = (sourceWeight * ratio).toInt().coerceAtLeast(5)
        // Round to nearest 5 lbs
        val rounded = ((estimated + 2) / 5) * 5

        return WeightSuggestion(
            exerciseId = targetExerciseId,
            targetReps = targetReps,
            suggestedWeightLbs = rounded,
            confidence = WeightSuggestion.Confidence.LOW,
            reasoning = "Transfer from ${sourced.name} (${sourceWeight}lbs, similarity ${
                "%.2f".format(sourced.similarityScore)
            }). Estimated: ${rounded}lbs for ${targetReps} reps. Log ${target.name} to calibrate."
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
}
