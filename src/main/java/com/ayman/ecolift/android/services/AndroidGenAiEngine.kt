package com.ayman.ecolift.android.services

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.ayman.ecolift.BuildConfig
import com.ayman.ecolift.agent.engine.GeminiNanoEngine
import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.ai.GroqCloudAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Groq when the debug key is present, otherwise Gemini Nano on API 31 and above.
 * A failed Groq call stays a Groq failure. It does not fall through to Nano.
 *
 * [GroqCloudAgent.respond] wraps the prompt in the IronMind tool schema.
 * The web engine returns plain text, or JSON for the caller's schema, so this adapter posts the prompt itself.
 */
class AndroidGenAiEngine(context: Context) : LocalGenAiEngine {
    private val appContext = context.applicationContext
    private val backend: Backend = when {
        BuildConfig.GROQ_API_KEY.isNotBlank() -> Backend.Groq
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Backend.Nano(createNano(appContext))
        else -> Backend.None
    }

    override val isReady: Boolean
        get() = when (val current = backend) {
            Backend.Groq -> true
            is Backend.Nano -> current.engine.isReady
            Backend.None -> false
        }

    override suspend fun warmup() {
        val current = backend
        if (current is Backend.Nano) current.engine.warmup()
    }

    override fun streamText(prompt: String): Flow<String> = when (val current = backend) {
        Backend.Groq -> flow { emit(groqChat(prompt) ?: "") }.flowOn(Dispatchers.IO)
        is Backend.Nano -> current.engine.streamText(prompt)
        Backend.None -> emptyFlow()
    }

    override suspend fun generateStructured(prompt: String, schema: String): String = when (val current = backend) {
        Backend.Groq -> withContext(Dispatchers.IO) {
            groqChat("$prompt\n\nRespond with JSON matching this schema: $schema") ?: "{}"
        }
        is Backend.Nano -> current.engine.generateStructured(prompt, schema)
        Backend.None -> "{}"
    }

    override fun close() {
        val current = backend
        if (current is Backend.Nano) current.engine.close()
    }

    private fun groqChat(prompt: String): String? {
        val key = BuildConfig.GROQ_API_KEY
        if (key.isBlank()) return null
        val base = BuildConfig.GROQ_API_BASE_URL.ifBlank { GroqCloudAgent.DEFAULT_BASE_URL }.trimEnd('/')
        val model = BuildConfig.GROQ_MODEL.ifBlank { GroqCloudAgent.DEFAULT_MODEL }
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", prompt),
                ),
            )
            .put("temperature", 0.2)
            .put("max_completion_tokens", 1024)
            .toString()
        return runCatching { postChat("$base/chat/completions", key, body) }.getOrNull()
    }

    private fun postChat(endpoint: String, key: String, body: String): String? {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { stream -> stream.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) return null
            JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
                .ifBlank { null }
        } finally {
            connection.disconnect()
        }
    }

    private sealed interface Backend {
        data object Groq : Backend
        class Nano(val engine: LocalGenAiEngine) : Backend
        data object None : Backend
    }

    companion object {
        @RequiresApi(Build.VERSION_CODES.S)
        private fun createNano(context: Context): GeminiNanoEngine = GeminiNanoEngine(context)
    }
}
