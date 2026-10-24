package com.ayman.ecolift.android.services

import android.content.Context
import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.web.AppServices
import com.ayman.ecolift.web.VisionService

/**
 * One engine instance for the process. Gemini Nano warmup has to stick,
 * and a fresh engine on every [genAiEngine] call would drop it.
 */
class AndroidAppServices(context: Context) : AppServices {
    private val appContext = context.applicationContext
    private val engine by lazy { AndroidGenAiEngine(appContext) }

    override fun genAiEngine(): LocalGenAiEngine = engine

    override val vision: VisionService = AndroidVisionService(appContext)
}
