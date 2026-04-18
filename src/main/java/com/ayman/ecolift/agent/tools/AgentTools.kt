package com.ayman.ecolift.agent.tools

/** A resolved exercise match from a fuzzy name query. */
data class ExerciseMatch(
    val exerciseId: Long,
    val name: String,
    val isBodyweight: Boolean,
    val score: Double  // 0.0 = perfect match, higher = worse
)

/** One set from history, summarized for agent consumption. */
data class SetSummary(
    val setId: Long,
    val date: String,
    val setNumber: Int,
    val weightLbs: Int?,
    val reps: Int?,
    val isBodyweight: Boolean
)

/** Rolling history summary for an exercise over a time window. */
data class HistorySummary(
    val exerciseId: Long,
    val windowDays: Int,
    val sessionCount: Int,
    val topSetWeightLbs: Int?,   // max weight seen in window
    val topSetReps: Int?,        // reps at that max weight
    val recentSets: List<SetSummary>
)

/** An exercise deemed similar to a query exercise. */
data class SimilarExercise(
    val exerciseId: Long,
    val name: String,
    val similarityScore: Double,  // higher = more similar
    val sharedPattern: String
)

/**
 * A weight recommendation with explanation.
 * All weights in lbs to match WorkoutSet.weightLbs.
 */
data class WeightSuggestion(
    val exerciseId: Long,
    val targetReps: Int,
    val suggestedWeightLbs: Int?,   // null = bodyweight or no data
    val confidence: Confidence,
    val reasoning: String
) {
    enum class Confidence { HIGH, MEDIUM, LOW, NO_DATA }
}

/** All exercises logged on a single date. */
data class SessionSnapshot(
    val date: String,
    val exercises: List<ExerciseSnapshot>
)

/** One exercise's sets within a session. */
data class ExerciseSnapshot(
    val exerciseId: Long,
    val name: String,
    val sets: List<SetSummary>
)

/** Progress trend for an exercise across all logged history. */
data class ProgressTrend(
    val exerciseId: Long,
    val name: String,
    val sessionCount: Int,
    val prWeightLbs: Int?,
    val prDate: String?,
    val est1Rm: Int?,          // Epley formula on best set
    val deltaPercent: Float?,  // % change in est 1RM: last 30 days vs prior 30 days
    val recentSessions: List<String> // compact "date: WxR" strings, newest first
)

/** Read-side interface for the agent. No SQL, no arithmetic — pure Kotlin results. */
interface AgentTools {
    /** Fuzzy-match an exercise name from the catalog. Returns null if no plausible match. */
    suspend fun findExercise(fuzzyName: String): ExerciseMatch?

    /** Recent sets for an exercise, newest first. */
    suspend fun getRecentSets(exerciseId: Long, limit: Int = 10): List<SetSummary>

    /** Rolling history summary for an exercise over the last [windowDays] days. */
    suspend fun getExerciseHistory(exerciseId: Long, windowDays: Int): HistorySummary

    /**
     * Find exercises similar to [exerciseId] using movement-pattern matching.
     * Full embedding-based similarity will replace this in Phase 4.
     */
    suspend fun getSimilarExercises(exerciseId: Long, k: Int = 5): List<SimilarExercise>

    /** Suggest a working weight based on recent history. */
    suspend fun suggestWeight(exerciseId: Long, targetReps: Int): WeightSuggestion

    /**
     * Suggest a weight for an exercise the user has never logged,
     * by finding similar exercises and applying a transfer ratio.
     */
    suspend fun suggestTransferWeight(targetExerciseId: Long, targetReps: Int): WeightSuggestion

    /** All exercises logged on [date] (ISO yyyy-MM-dd). Empty exercises list = rest day. */
    suspend fun getSessionByDate(date: String): SessionSnapshot

    /** Progress trend for [exerciseId] across all logged history. */
    suspend fun getProgressTrend(exerciseId: Long): ProgressTrend
}
