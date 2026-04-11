package com.ayman.ecolift.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import org.json.JSONObject

class IronMindAgent(private val context: Context) {

    private var llmInference: LlmInference? = null

    private val systemPrompt = """
        You are the 'IronMind' AI Agent. You have access to the user's workout database. 
        Your job is to translate natural language into app actions.
        
        Available Tools:
        1. log_set(exercise: String, weight: Int, reps: Int): Log a new set for the current workout.
        2. update_set_log(exercise: String, date: String, weight: Int, reps: Int): Fix mistakes in past sessions.
        3. modify_cycle(next_session_index: Int): Manually set the next workout in the split.
        4. suggest_alternative(current_exercise: String, target_machine: String): Suggest an alternative when equipment is busy.
        5. calculate_1rm(weight: Int, reps: Int): Provide instant strength estimates.
        
        Rules:
        - Output ONLY a JSON object representing the tool call. No conversational filler or "Here is your JSON".
        - For voice inputs, be extremely precise.
        - Format: {"tool": "tool_name", "parameters": {...}}
    """.trimIndent()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (llmInference != null) return@withContext
        
        // Order of searching for the model:
        val persistentFile = File(context.filesDir, "gemma.bin")
        val externalFile = File(context.getExternalFilesDir(null), "gemma.bin")
        val cacheFile = File(context.cacheDir, "gemma.bin")
        
        val modelFile = when {
            persistentFile.exists() -> persistentFile
            externalFile.exists() -> externalFile
            cacheFile.exists() -> cacheFile
            else -> null
        }
        
        android.util.Log.d("IronMind", "Looking for model at: ${modelFile?.absolutePath}")

        if (modelFile == null || !modelFile.exists()) {
            return@withContext
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(512)
            .setTopK(40)
            .setTemperature(0.2f)
            .build()
        
        try {
            llmInference = LlmInference.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isInitialized(): Boolean = llmInference != null

    suspend fun processInput(input: String, contextData: String? = null): String = withContext(Dispatchers.IO) {
        val inference = llmInference ?: return@withContext """
            {
                "message": "IronMind isn't ready. Please place 'gemma.bin' in the app's file folder. \n\nTarget path: ${context.getExternalFilesDir(null)?.absolutePath}/gemma.bin"
            }
        """.trimIndent()
        val today = java.time.LocalDate.now().toString()
        val contextPrompt = if (contextData != null) "\nRecent Data:\n$contextData" else ""
        val prompt = "$systemPrompt\n\nCurrent Date: $today$contextPrompt\n\nUser: $input\nAgent:"
        try {
            inference.generateResponse(prompt)
        } catch (e: Exception) {
            "{\"message\": \"Error during inference: ${e.message}\"}"
        }
    }
}
