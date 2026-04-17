package com.ayman.ecolift.data

import org.json.JSONObject
import com.ayman.ecolift.ai.AiToolCall
import com.ayman.ecolift.ai.AiToolName
import kotlinx.serialization.json.Json

data class ParsedResponse(
    val toolCall: AiToolCall? = null,
    val message: String? = null,
    val error: String? = null
)

object ResponseParser {
    private val jsonSerializer = Json { ignoreUnknownKeys = true }
    
    fun parse(jsonStr: String): ParsedResponse {
        return try {
            val match = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(jsonStr)
            val cleanJson = match?.value ?: jsonStr
            val json = JSONObject(cleanJson)
            
            if (json.has("tool")) {
                val toolWireName = json.getString("tool")
                val toolName = AiToolName.fromWireName(toolWireName)
                val params = json.optJSONObject("parameters") ?: JSONObject()
                
                val toolCall = AiToolCall(
                    tool = toolName,
                    requiresConfirmation = json.optBoolean("requires_confirmation", false),
                    exercise = params.optString("exercise").takeIf { it.isNotBlank() },
                    date = params.optString("date").takeIf { it.isNotBlank() },
                    field = params.optString("field").takeIf { it.isNotBlank() },
                    newValue = params.optIntOrNull("new_value"),
                    setSelector = params.optString("set_selector").takeIf { it.isNotBlank() },
                    weight = params.optIntOrNull("weight"),
                    reps = params.optIntOrNull("reps"),
                    activeSessionType = params.optIntOrNull("active_session_type"),
                    activeSessionLabel = params.optString("active_session_label").takeIf { it.isNotBlank() },
                    targetExercise = params.optString("target_exercise").takeIf { it.isNotBlank() },
                    targetSessionType = params.optIntOrNull("target_session_type"),
                    targetSessionLabel = params.optString("target_session_label").takeIf { it.isNotBlank() },
                    machineName = params.optString("machine_name").takeIf { it.isNotBlank() },
                    machineMechanics = params.optString("machine_mechanics").takeIf { it.isNotBlank() }
                )

                if (toolName == AiToolName.UpdateSetLog) {
                    val weight = toolCall.weight ?: -1
                    val reps = toolCall.reps ?: -1
                    if (reps > 100 || weight > 1000) {
                        return ParsedResponse(
                            message = "I detected some unusual numbers ($weight lbs x $reps reps). Please verify them below.",
                            toolCall = toolCall
                        )
                    }
                }
                ParsedResponse(toolCall = toolCall)
            } else if (json.has("assistant_message")) {
                ParsedResponse(message = json.getString("assistant_message"))
            } else if (json.has("message")) {
                ParsedResponse(message = json.getString("message"))
            } else {
                ParsedResponse(error = "No recognized fields in response.")
            }
        } catch (e: Exception) {
            ParsedResponse(error = "Failed to parse JSON: ${e.localizedMessage}")
        }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        return if (has(key) && !isNull(key)) optInt(key) else null
    }

    fun parseWorkoutLog(input: String): WorkoutLog? {
        return try {
            val match = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(input)
            val cleanJson = match?.value ?: return null
            jsonSerializer.decodeFromString<WorkoutLog>(cleanJson)
        } catch (e: Exception) {
            null
        }
    }
}
