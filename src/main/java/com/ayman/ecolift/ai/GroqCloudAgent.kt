package com.ayman.ecolift.ai

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GroqCloudAgent(
    private val apiKey: String,
    baseUrl: String,
    model: String,
) : AiModelAgent {
    private val resolvedBaseUrl = baseUrl.ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
    private val resolvedModel = model.ifBlank { DEFAULT_MODEL }

    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    override fun getStatus(): AiModelStatus {
        return if (isConfigured) {
            AiModelStatus(
                isReady = true,
                headline = "Groq Cloud ready",
                detail = "IronMind is using $resolvedModel through Groq Cloud.",
                modelPath = resolvedBaseUrl,
            )
        } else {
            AiModelStatus(
                isReady = false,
                headline = "IronMind unavailable",
                detail = IronMindFallbacks.TRY_AGAIN_LATER,
                modelPath = null,
            )
        }
    }

    override suspend fun respond(
        userMessage: String,
        history: List<AiConversationTurn>,
        runtimeContext: AiRuntimeContext,
        imageUri: Uri?,
    ): Result<AiModelOutput> = withContext(Dispatchers.IO) {
        runCatching {
            check(isConfigured) { "GROQ_API_KEY is not configured." }
            val prompt = IronMindPromptBuilder.build(
                userMessage = userMessage,
                history = history,
                runtimeContext = runtimeContext,
                hasImage = imageUri != null,
            )
            val responseBody = postChatCompletion(
                endpoint = "$resolvedBaseUrl/chat/completions",
                body = GroqCloudJson.buildChatCompletionRequest(
                    model = resolvedModel,
                    prompt = prompt,
                ),
            )
            GroqCloudJson.parseChatCompletion(responseBody)
        }
    }

    private fun postChatCompletion(endpoint: String, body: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            }
        }

        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                error("Groq request failed ($statusCode): ${GroqCloudJson.extractErrorMessage(response)}")
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
    }
}

internal object GroqCloudJson {
    private const val SYSTEM_GUARDRAIL = """
        You are a supportive workout coach inside IronMind.
        Help users understand workout and exercise progress without being demeaning.
        If progress is flat or trending down, stay constructive and suggest practical next steps.
        Do not give medical diagnoses. Encourage safe training, consistency, and tracking.
        Return only the JSON object requested by the user prompt.
    """

    fun buildChatCompletionRequest(model: String, prompt: String): String {
        return JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", SYSTEM_GUARDRAIL.trimIndent())
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", prompt)
                    )
            )
            .put("temperature", 0.2)
            .put("max_completion_tokens", 1024)
            .put("response_format", JSONObject().put("type", "json_object"))
            .toString()
    }

    fun parseChatCompletion(responseBody: String): AiModelOutput {
        val root = JSONObject(responseBody)
        val content = root
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content")
        return parseModelOutput(content)
    }

    fun extractErrorMessage(responseBody: String): String {
        return runCatching {
            JSONObject(responseBody)
                .optJSONObject("error")
                ?.optString("message")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: responseBody.ifBlank { "No response body." }
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

internal object IronMindFallbacks {
    const val TRY_AGAIN_LATER = "Please try again later."
    const val SERVICE_FAILURE = "IronMind is temporarily unavailable. Please try again later."

    fun userFacingServiceFailure(error: Throwable): String = SERVICE_FAILURE
}
