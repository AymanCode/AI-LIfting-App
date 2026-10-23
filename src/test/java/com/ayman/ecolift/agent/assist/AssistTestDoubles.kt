package com.ayman.ecolift.agent.assist

import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.tools.AgentTools
import com.ayman.ecolift.agent.tools.HistorySummary
import com.ayman.ecolift.agent.tools.ProgressTrend
import com.ayman.ecolift.agent.tools.WeightSuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Minimal AgentTools fake. Only the three methods AssistService uses are configurable. */
class FakeAgentTools(
    private val weightSuggestion: WeightSuggestion? = null,
    private val history: HistorySummary? = null,
    private val trend: ProgressTrend? = null,
) : AgentTools {
    override suspend fun findExercise(fuzzyName: String) = error("unused")
    override suspend fun getRecentSets(exerciseId: Long, limit: Int) = error("unused")
    override suspend fun getExerciseHistory(exerciseId: Long, windowDays: Int): HistorySummary =
        history ?: error("FakeAgentTools: no history configured")
    override suspend fun getSimilarExercises(exerciseId: Long, k: Int) = error("unused")
    override suspend fun suggestWeight(exerciseId: Long, targetReps: Int): WeightSuggestion =
        weightSuggestion ?: error("FakeAgentTools: no weightSuggestion configured")
    override suspend fun suggestTransferWeight(targetExerciseId: Long, targetReps: Int) = error("unused")
    override suspend fun getSessionByDate(date: String) = error("unused")
    override suspend fun getProgressTrend(exerciseId: Long): ProgressTrend =
        trend ?: error("FakeAgentTools: no trend configured")
}

/** Engine fake returning a fixed response (or blank to simulate failure). */
class FakeEngine(
    private val response: String,
    override val isReady: Boolean = true,
) : LocalGenAiEngine {
    override suspend fun warmup() {}
    override fun streamText(prompt: String): Flow<String> = flowOf(response)
    override suspend fun generateStructured(prompt: String, schema: String): String = response
    override fun close() {}
}
