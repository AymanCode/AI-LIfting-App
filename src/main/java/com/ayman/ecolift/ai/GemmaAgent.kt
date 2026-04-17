package com.ayman.ecolift.ai

import android.content.Context
import android.net.Uri
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class GemmaAgent(private val context: Context) {
    @Volatile
    private var llmInference: LlmInference? = null

    fun getStatus(): GemmaStatus {
        val modelFile = locateModelFile()
        return if (modelFile != null) {
            GemmaStatus(
                isReady = true,
                headline = "Gemma E2B ready",
                detail = "On-device agent mode is available. All tool calls stay local to your phone.",
                modelPath = modelFile.absolutePath,
            )
        } else {
            GemmaStatus(
                isReady = false,
                headline = "Gemma E2B model missing",
                detail = "Place the .task model file in the app files/models folder or external app storage, then reopen the tab.",
                modelPath = expectedModelPath(),
            )
        }
    }

    suspend fun respond(
        userMessage: String,
        history: List<AiConversationTurn>,
        runtimeContext: AiRuntimeContext,
        imageUri: Uri? = null,
    ): Result<AiModelOutput> = withContext(Dispatchers.IO) {
        runCatching {
            val inference = ensureInference()
            val prompt = buildPrompt(
                userMessage = userMessage,
                history = history,
                runtimeContext = runtimeContext,
                hasImage = imageUri != null,
            )
            // Vision modality not supported in standard 0.10.14 LlmInference.
            // Simplified to text-only to resolve build errors.
            val raw = inference.generateResponse(prompt).trim()
            parseModelOutput(raw)
        }
    }

    private fun ensureInference(): LlmInference {
        llmInference?.let { return it }
        val modelFile = locateModelFile()
            ?: error("Gemma E2B model file was not found. Expected path: ${expectedModelPath()}")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .build()
        return LlmInference.createFromOptions(context, options).also {
            llmInference = it
        }
    }

    private fun locateModelFile(): File? {
        val externalRoot = context.getExternalFilesDir(null)
        val candidates = buildList {
            add(File(context.filesDir, "models/gemma_e2b.task"))
            add(File(context.filesDir, "gemma_e2b.task"))
            add(File(context.filesDir, "models/gemma-e2b.task"))
            add(File(context.filesDir, "gemma-e2b.task"))
            if (externalRoot != null) {
                add(File(externalRoot, "models/gemma_e2b.task"))
                add(File(externalRoot, "gemma_e2b.task"))
            }
        }
        return candidates.firstOrNull { it.exists() && it.length() > 0L }
    }

    private fun expectedModelPath(): String {
        return File(context.filesDir, "models/gemma_e2b.task").absolutePath
    }

    private fun buildPrompt(
        userMessage: String,
        history: List<AiConversationTurn>,
        runtimeContext: AiRuntimeContext,
        hasImage: Boolean,
    ): String {
        val recentConversation = history.takeLast(8).joinToString(separator = "\n") { turn ->
            "${turn.role.uppercase()}: ${turn.message}"
        }
        val exerciseList = runtimeContext.availableExercises.take(50).joinToString()
        return """
            You are IronMind, an on-device workout AI agent inside a native Android app.
            Your job is to translate the user's natural language into safe local app actions.
            The app will execute tool calls locally against the workout database.

            Return exactly one JSON object and nothing else.
            Valid schema:
            {
              "assistant_message": "short response for the user",
              "tool": "none" | "update_set_log" | "modify_cycle" | "calculate_1rm" | "get_split_alternatives" | "create_temp_swap" | "analyze_equipment" | "estimate_relative_load",
              "requires_confirmation": true | false,
              "parameters": {
                "exercise": "Bench Press",
                "target_exercise": "Incline Dumbbell Press",
                "date": "YYYY-MM-DD",
                "field": "weight" | "reps",
                "new_value": 235,
                "set_selector": "max_weight" | "last",
                "weight": 225,
                "reps": 5,
                "active_session_type": 1,
                "active_session_label": "Day B",
                "target_session_type": 2,
                "target_session_label": "Day C",
                "machine_name": "Plate-Loaded Chest Press",
                "machine_mechanics": "converging axis"
              }
            }

            Tool rules:
            - Use update_set_log for fixing past sessions.
            - Use modify_cycle for switching or overriding the next workout session.
            - Use calculate_1rm for quick strength estimates.
            - Use get_split_alternatives when a lift is blocked and you want the app to find a progression-safe swap from another split day.
            - Use create_temp_swap only after a swap is clearly decided. This writes a temporary week-only swap into the local database.
            - Use analyze_equipment when the user wants help identifying a machine or movement from context or a photo.
            - Use estimate_relative_load when the user wants a starting weight recommendation on a machine or alternate movement.
            - For update_set_log, modify_cycle, or create_temp_swap always set requires_confirmation to true.
            - For calculate_1rm, get_split_alternatives, analyze_equipment, and estimate_relative_load set requires_confirmation to false.
            - If the user is only asking a question or you need clarification, use tool = "none".
            - Never invent tools or fields beyond this schema.
            - Dates must be ISO format YYYY-MM-DD.
            - If the user mentions "last set", use set_selector = "last".
            - If the user mentions correcting the max or top set, use set_selector = "max_weight".
            - If the user says "Day A", map that to active_session_label = "Day A". If the slot index is obvious, also include active_session_type.
            - If the user says a station is full or unavailable, prefer get_split_alternatives before create_temp_swap.
            - For machine help, keep assistant_message practical and use the machine_name / machine_mechanics fields when the equipment is clear.

            Current app context:
            - Today: ${runtimeContext.today}
            - Cycle active: ${runtimeContext.cycleActive}
            - Number of split day types: ${runtimeContext.cycleNumTypes}
            - Next session hint: ${runtimeContext.nextSessionLabel ?: "none"}
            - Current session completion: ${runtimeContext.currentSessionCompletionPercent}%
            - Current session summary: ${runtimeContext.currentSessionSummary}
            - Pending review count: ${runtimeContext.pendingReviewCount}
            - Known exercises: $exerciseList
            - Last session JSON: ${runtimeContext.lastSessionJson}
            - Current target session JSON: ${runtimeContext.currentTargetSessionJson}
            - Gym context manifest JSON: ${runtimeContext.manifestJson}
            - Image attached: ${if (hasImage) "yes" else "no"}

            Recent conversation:
            ${recentConversation.ifBlank { "No prior messages." }}

            USER: $userMessage
        """.trimIndent()
    }

    private fun parseModelOutput(raw: String): AiModelOutput {
        val jsonText = extractJson(raw)
        if (jsonText == null) {
            return AiModelOutput(
                assistantMessage = raw.ifBlank { "I could not understand that request." },
                rawResponse = raw,
            )
        }
        val root = JSONObject(jsonText)
        val assistantMessage = root.optString("assistant_message")
            .ifBlank { "I parsed your request." }
        val toolName = AiToolName.fromWireName(root.optString("tool"))
        val requiresConfirmation = root.optBoolean("requires_confirmation", false)
        val parameters = root.optJSONObject("parameters") ?: JSONObject()
        val toolCall = if (toolName == AiToolName.None) {
            null
        } else {
            AiToolCall(
                tool = toolName,
                requiresConfirmation = requiresConfirmation,
                exercise = parameters.optString("exercise").ifBlank { null },
                date = parameters.optString("date").ifBlank { null },
                field = parameters.optString("field").ifBlank { null },
                newValue = parameters.optIntOrNull("new_value"),
                setSelector = parameters.optString("set_selector").ifBlank { null },
                weight = parameters.optIntOrNull("weight"),
                reps = parameters.optIntOrNull("reps"),
                activeSessionType = parameters.optIntOrNull("active_session_type"),
                activeSessionLabel = parameters.optString("active_session_label").ifBlank { null },
                targetExercise = parameters.optString("target_exercise").ifBlank { null },
                targetSessionType = parameters.optIntOrNull("target_session_type"),
                targetSessionLabel = parameters.optString("target_session_label").ifBlank { null },
                machineName = parameters.optString("machine_name").ifBlank { null },
                machineMechanics = parameters.optString("machine_mechanics").ifBlank { null },
            )
        }
        return AiModelOutput(
            assistantMessage = assistantMessage,
            toolCall = toolCall,
            rawResponse = raw,
        )
    }

    private fun extractJson(raw: String): String? {
        val cleaned = raw
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start == -1 || end <= start) {
            return null
        }
        return cleaned.substring(start, end + 1)
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    return if (has(key) && !isNull(key)) optInt(key) else null
}
