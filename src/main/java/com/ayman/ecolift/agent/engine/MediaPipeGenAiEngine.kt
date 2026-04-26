package com.ayman.ecolift.agent.engine

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LocalGenAiEngine backed by MediaPipe tasks-genai LlmInference.
 *
 * Model resolution order (first existing file wins):
 *   1. filesDir/models/gemma_e2b.task
 *   2. filesDir/gemma_e2b.task
 *   3. filesDir/models/gemma-e2b.task
 *   4. filesDir/gemma-e2b.task
 *   5. externalFilesDir/models/gemma_e2b.task
 *   6. externalFilesDir/gemma_e2b.task
 * */
class MediaPipeGenAiEngine(
    private val context: Context,
    private val maxTokens: Int = 512,
    private val temperature: Float = 0.7f,
    private val topK: Int = 40
) : LocalGenAiEngine {

    private val tag = "MediaPipeGenAiEngine"

    @Volatile
    private var inference: LlmInference? = null

    override val isReady: Boolean get() = inference != null

    override suspend fun warmup() {
        if (isReady) return
        withContext(Dispatchers.IO) {
            val modelFile = locateModelFile()
            if (modelFile == null) {
                Log.w(tag, "Model file not found; engine will remain unavailable. " +
                    "Place gemma_e2b.task in ${context.filesDir}/models/")
                return@withContext
            }
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(maxTokens)
                    .setTemperature(temperature)
                    .setTopK(topK)
                    .build()
                inference = LlmInference.createFromOptions(context, options)
                Log.i(tag, "Warmed up. Model: ${modelFile.name}")
            } catch (e: Exception) {
                Log.e(tag, "Warmup failed", e)
            }
        }
    }

    /**
     * MediaPipe does not support true streaming in tasks-genai:0.10.14.
     * Emits the full response as a single flow item on the IO dispatcher.
     */
    override fun streamText(prompt: String): Flow<String> = flow {
        val raw = runInference(prompt)
        if (raw != null) emit(raw)
    }.flowOn(Dispatchers.IO)

    override suspend fun generateStructured(prompt: String, schema: String): String {
        val fullPrompt = buildString {
            append(prompt)
            append("\n\nRespond ONLY with valid JSON matching this schema:\n")
            append(schema)
            append("\nOutput JSON:")
        }
        return withContext(Dispatchers.IO) {
            runInference(fullPrompt) ?: "{}"
        }
    }

    override fun close() {
        inference?.close()
        inference = null
        Log.i(tag, "Engine closed.")
    }
    // Internal helpers

    private fun runInference(prompt: String): String? {
        val engine = inference ?: run {
            Log.w(tag, "runInference called before warmup; returning null")
            return null
        }
        return try {
            engine.generateResponse(prompt).trim()
        } catch (e: Exception) {
            Log.e(tag, "Inference error", e)
            null
        }
    }

    private fun locateModelFile(): File? {
        val ext = context.getExternalFilesDir(null)
        val candidates = buildList {
            add(File(context.filesDir, "models/gemma_e2b.task"))
            add(File(context.filesDir, "gemma_e2b.task"))
            add(File(context.filesDir, "models/gemma-e2b.task"))
            add(File(context.filesDir, "gemma-e2b.task"))
            if (ext != null) {
                add(File(ext, "models/gemma_e2b.task"))
                add(File(ext, "gemma_e2b.task"))
            }
        }
        return candidates.firstOrNull { it.exists() && it.length() > 0L }
    }
}
