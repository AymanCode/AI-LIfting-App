package com.ayman.ecolift.ai

import android.content.Context
import com.ayman.ecolift.data.WorkoutLog
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * IronMind Agent: Implementation using MediaPipe GenAI for local inference.
 */
class IronMindAgent(private val context: Context) {

    private var llmInference: LlmInference? = null
    private val json = Json { ignoreUnknownKeys = true }

    // Path where you should push the model file via ADB:
    // adb push model.bin /data/local/tmp/ironmind_model.bin
    private val modelPath = "/data/local/tmp/ironmind_model.bin"

    private val systemPrompt = """
        You are 'IronMind'. Extract exercise data from text.
        Output ONLY JSON: {"exercise": String, "sets": Int, "reps": Int, "weight": Float}
        Example: "3x10 bench at 155" -> {"exercise": "Bench Press", "sets": 3, "reps": 10, "weight": 155.0}
    """.trimIndent()

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(modelPath)
            if (!file.exists()) {
                android.util.Log.e("IronMind", "Model file not found at $modelPath. Please use 'adb push' to upload it.")
                return@withContext false
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setTopK(40)
                .setTemperature(0.7f)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            android.util.Log.e("IronMind", "Inference initialization failed", e)
            false
        }
    }

    fun isInitialized(): Boolean = llmInference != null

    suspend fun processWorkoutInput(input: String): WorkoutLog? = withContext(Dispatchers.Default) {
        val inference = llmInference ?: return@withContext null
        
        try {
            val prompt = "$systemPrompt\n\nInput: $input\nOutput:"
            val result = inference.generateResponse(prompt)
            
            val cleanJson = result.substringAfter("{").substringBeforeLast("}")
            val jsonString = "{$cleanJson}"
            
            json.decodeFromString<WorkoutLog>(jsonString)
        } catch (e: Exception) {
            android.util.Log.e("IronMind", "Parsing error", e)
            null
        }
    }
}
