package com.ayman.ecolift.web.demo

import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.web.AppServices
import com.ayman.ecolift.web.VisionService
import com.ayman.ecolift.web.ai.VisionStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

const val DemoAiDisabledMessage =
    "This is the read-only demo - IronMind's live AI is switched off here. In the full app I can analyze your training and answer questions about your progress."

class DemoGenAiEngine : LocalGenAiEngine {
    override val isReady: Boolean get() = true

    override suspend fun warmup() = Unit

    override fun streamText(prompt: String): Flow<String> = flowOf(DemoAiDisabledMessage)

    override suspend fun generateStructured(prompt: String, schema: String): String = ""

    override fun close() = Unit
}

class DemoAppServices : AppServices {
    override fun genAiEngine(): LocalGenAiEngine = DemoGenAiEngine()

    override val vision: VisionService = object : VisionService {
        override suspend fun scanStats(b64: String, mime: String): VisionStats? = null
        override suspend fun scansLeftToday(): Int? = null
    }
}
