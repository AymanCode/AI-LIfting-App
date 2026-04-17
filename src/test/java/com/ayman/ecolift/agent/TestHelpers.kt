package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Engine that is always ready and returns [fixedResponse] for every call. */
class FakeReadyEngine(private val fixedResponse: String = "{}") : LocalGenAiEngine {
    override val isReady = true
    override suspend fun warmup() {}
    override fun streamText(prompt: String): Flow<String> = flowOf(fixedResponse)
    override suspend fun generateStructured(prompt: String, schema: String): String = fixedResponse
    override fun close() {}
}
