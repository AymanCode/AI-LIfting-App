package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.patches.PatchApplier
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.tools.AgentTools
import com.ayman.ecolift.agent.tools.ExerciseMatch
import com.ayman.ecolift.agent.tools.HistorySummary
import com.ayman.ecolift.agent.tools.ProgressTrend
import com.ayman.ecolift.agent.tools.SessionSnapshot
import com.ayman.ecolift.agent.tools.SetSummary
import com.ayman.ecolift.agent.tools.SimilarExercise
import com.ayman.ecolift.agent.tools.WeightSuggestion

/**
 * Read and patch stand-ins. Writer A's AgentToolsImpl and PatchService still take AppDatabase.
 * Delete this file when those constructors take AppRepos.
 */
internal object UnavailableAgentTools : AgentTools {
    override suspend fun findExercise(fuzzyName: String): ExerciseMatch? = null

    override suspend fun getRecentSets(exerciseId: Long, limit: Int): List<SetSummary> = emptyList()

    override suspend fun getExerciseHistory(exerciseId: Long, windowDays: Int): HistorySummary =
        HistorySummary(
            exerciseId = exerciseId,
            windowDays = windowDays,
            sessionCount = 0,
            topSetWeightLbs = null,
            topSetReps = null,
            recentSets = emptyList(),
        )

    override suspend fun getSimilarExercises(exerciseId: Long, k: Int): List<SimilarExercise> = emptyList()

    override suspend fun suggestWeight(exerciseId: Long, targetReps: Int): WeightSuggestion =
        WeightSuggestion(
            exerciseId = exerciseId,
            targetReps = targetReps,
            suggestedWeightLbs = null,
            confidence = WeightSuggestion.Confidence.NO_DATA,
            reasoning = "Assist is not wired to the demo repositories yet.",
        )

    override suspend fun suggestTransferWeight(targetExerciseId: Long, targetReps: Int): WeightSuggestion =
        suggestWeight(targetExerciseId, targetReps)

    override suspend fun getSessionByDate(date: String): SessionSnapshot =
        SessionSnapshot(date = date, exercises = emptyList())

    override suspend fun getProgressTrend(exerciseId: Long): ProgressTrend =
        ProgressTrend(
            exerciseId = exerciseId,
            name = "",
            sessionCount = 0,
            prWeightLbs = null,
            prDate = null,
            est1Rm = null,
            deltaPercent = null,
            recentSessions = emptyList(),
        )
}

internal object UnavailablePatchApplier : PatchApplier {
    override suspend fun applyPatches(
        requestId: String,
        patches: List<DbPatch>,
        userConfirmed: Boolean,
    ): PatchResult = PatchResult.Rejected("Patches are not wired to the demo repositories yet.")

    override suspend fun undo(auditId: Long): PatchResult =
        PatchResult.Rejected("Undo is not wired to the demo repositories yet.")
}
