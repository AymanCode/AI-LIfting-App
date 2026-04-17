package com.ayman.ecolift.agent.engine

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * LocalGenAiEngine backed by Gemini Nano via Android AICore.
 *
 * Requires API 31+ and a supported device (Pixel 8+, Galaxy S24+) with
 * AICore system service installed. No model download — model lives in the OS.
 *
 * Detection: [prepareInferenceEngine] connects to AICore; a [ConnectionException]
 * or any other exception means the device isn't supported. [isReady] stays false
 * and the orchestrator falls back to rule-only mode transparently.
 */
@RequiresApi(Build.VERSION_CODES.S)
class GeminiNanoEngine(private val context: Context) : LocalGenAiEngine {

    private val tag = "GeminiNanoEngine"

    @Volatile
    private var model: GenerativeModel? = null

    override val isReady: Boolean get() = model != null

    override suspend fun warmup() {
        if (isReady) return
        withContext(Dispatchers.IO) {
            try {
                val config = generationConfig {
                    context = this@GeminiNanoEngine.context
                    temperature = 0.2f
                    topK = 16
                    maxOutputTokens = 512
                }
                val candidate = GenerativeModel(generationConfig = config)
                // prepareInferenceEngine() establishes the connection to AICore.
                // Throws ConnectionException if device doesn't support Gemini Nano,
                // or PreparationException if the model isn't ready yet.
                candidate.prepareInferenceEngine()
                model = candidate
                Log.i(tag, "Gemini Nano ready via AICore")
            } catch (e: Exception) {
                Log.w(tag, "Gemini Nano not available on this device: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    override fun streamText(prompt: String): Flow<String> = flow {
        val text = generate(prompt)
        if (text.isNotBlank()) emit(text)
    }.flowOn(Dispatchers.IO)

    override suspend fun generateStructured(prompt: String, schema: String): String {
        val fullPrompt = buildString {
            append(prompt)
            append("\n\nRespond ONLY with valid JSON matching this schema:\n")
            append(schema)
            append("\nOutput JSON:")
        }
        return generate(fullPrompt)
    }

    override fun close() {
        model?.close()
        model = null
        Log.i(tag, "Engine closed.")
    }

    private suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val m = model ?: run {
            Log.w(tag, "generate() called before warmup — returning empty")
            return@withContext ""
        }
        try {
            m.generateContent(prompt).text?.trim() ?: ""
        } catch (e: Exception) {
            Log.e(tag, "Generation error", e)
            ""
        }
    }
}
