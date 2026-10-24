package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.patches.PatchApplier
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.tools.AgentTools
import com.ayman.ecolift.agent.tools.ExerciseMatch
import com.ayman.ecolift.agent.tools.HistorySummary
import com.ayman.ecolift.agent.tools.ProgressTrend
import com.ayman.ecolift.agent.tools.SessionSnapshot
import com.ayman.ecolift.agent.tools.SetSummary
import com.ayman.ecolift.agent.tools.SimilarExercise
import com.ayman.ecolift.agent.tools.WeightSuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentOrchestratorTest {
    @Test
    fun model_log_set_uses_model_extracted_date() = runTest {
        val applier = RecordingPatchApplier()
        val orchestrator = AgentOrchestrator(
            router = IntentRouter(),
            tools = BenchOnlyTools(),
            patchApplier = applier,
            engine = StaticEngine(
                """
                {
                  "exerciseQuery": "Bench Press",
                  "date": "2026-06-29",
                  "confidence": 0.95,
                  "sets": [
                    { "weightLbs": 135, "reps": 12 }
                  ]
                }
                """.trimIndent()
            ),
            today = { "2026-07-01" },
        )

        val turn = orchestrator.process("logged bench two days ago one thirty five times twelve")

        assertTrue(turn is AgentTurn.Applied)
        val patch = applier.applied.single() as DbPatch.LogSet
        assertEquals("2026-06-29", patch.date)
        assertEquals(1350, patch.weightLbs)
        assertEquals(12, patch.reps)
    }

    private class StaticEngine(
        private val response: String,
    ) : LocalGenAiEngine {
        override val isReady = true
        override suspend fun warmup() {}
        override fun streamText(prompt: String): Flow<String> = flowOf(response)
        override suspend fun generateStructured(prompt: String, schema: String): String = response
        override fun close() {}
    }

    private class RecordingPatchApplier : PatchApplier {
        var applied: List<DbPatch> = emptyList()

        override suspend fun applyPatches(
            requestId: String,
            patches: List<DbPatch>,
            userConfirmed: Boolean
        ): PatchResult {
            applied = patches
            return PatchResult.Applied(auditId = 1L, patchCount = patches.size)
        }

        override suspend fun undo(auditId: Long): PatchResult = error("unused")
    }

    private class BenchOnlyTools : AgentTools {
        override suspend fun findExercise(fuzzyName: String): ExerciseMatch? =
            if (fuzzyName.contains("bench", ignoreCase = true)) {
                ExerciseMatch(exerciseId = 1L, name = "Bench Press", isBodyweight = false, score = 0.0)
            } else {
                null
            }

        override suspend fun getRecentSets(exerciseId: Long, limit: Int): List<SetSummary> = emptyList()
        override suspend fun getExerciseHistory(exerciseId: Long, windowDays: Int): HistorySummary = error("unused")
        override suspend fun getSimilarExercises(exerciseId: Long, k: Int): List<SimilarExercise> = error("unused")
        override suspend fun suggestWeight(exerciseId: Long, targetReps: Int): WeightSuggestion = error("unused")
        override suspend fun suggestTransferWeight(targetExerciseId: Long, targetReps: Int): WeightSuggestion = error("unused")
        override suspend fun getSessionByDate(date: String): SessionSnapshot = error("unused")
        override suspend fun getProgressTrend(exerciseId: Long): ProgressTrend = error("unused")
    }
}
