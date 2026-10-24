package com.ayman.ecolift.web

import androidx.compose.runtime.staticCompositionLocalOf
import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.web.ai.VisionStats

interface VisionService {
    suspend fun scanStats(b64: String, mime: String): VisionStats?
    suspend fun scansLeftToday(): Int?
}

interface AppServices {
    fun genAiEngine(): LocalGenAiEngine
    val vision: VisionService
}

val LocalAppServices = staticCompositionLocalOf<AppServices> {
    error("LocalAppServices not provided")
}
