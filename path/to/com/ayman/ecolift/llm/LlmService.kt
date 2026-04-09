package com.ayman.ecolift.llm

import android.content.Context
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.io.streams.inputStream

@Serializable
data class ParsedSet(val exercise: String, val weight: Double, val reps: Int)

@Serializable
private data class ParsedResponse(val sets: List<ParsedSet>)

class LlmService(private val context: Context) {
    private var llmInference: LlmInference? = null
    private val modelFile: File = File(context.filesDir, "gemma_e4b.task")

    fun isModelDownloaded(): Boolean = modelFile.exists() && modelFile.length() > 0

    suspend fun downloadModel(onProgress: (Float) -> Unit): Result<Unit> {
        if (isModelDownloaded()) return Result.success(Unit)

        val url = URL("https://huggingface.co/AYmanEcolift/gemma-e4b-lite/releases/download/v1.0/gemma_e4b.task")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 60_000
        connection.readTimeout = 60_000

        var totalBytesRead = 0L
        var totalContentLength = connection.contentLengthLong
        if (totalContentLength == -1) {
            return Result.failure(Exception("Failed to get content length"))
        }

        val outputStream = modelFile.outputStream()
        val buffer = ByteArray(4096)
        var bytesRead: Int

        while (connection.inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
            onProgress(totalBytesRead.toFloat() / totalContentLength)
        }

        outputStream.close()
        connection.disconnect()

        if (totalBytesRead < totalContentLength) {
            modelFile.delete()
            return Result.failure(Exception("Download failed"))
        }

        return Result.success(Unit)
    }

    suspend fun init(): Result<Unit> {
        if (llmInference != null) return Result.success(Unit)

        try {
            val options = LlmInference.LlmInferenceOptions.Builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .build()

            llmInference = LlmInference.create(options)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return Result.success(Unit)
    }

    suspend fun parseQuickLog(rawText: String): Result<List<ParsedSet>> {
        if (llmInference == null) return Result.failure(Exception("LLM not initialized"))

        val prompt = """
            You convert gym shorthand into JSON. Output ONLY a JSON object matching this schema:
            {"sets": [{"exercise": string, "weight": number, "reps": number}, ...]}
            Do NOT include any text outside the JSON.
            Do NOT include markdown fences.

            Examples:
            Input: "3x8 bench 185"
            Output: {"sets":[{"exercise":"bench press","weight":185,"reps":8},{"exercise":"bench press","weight":185,"reps":8},{"exercise":"bench press","weight":185,"reps":8}]}

            Input: "squats 225x5, 245x3"
            Output: {"sets":[{"exercise":"squat","weight":225,"reps":5},{"exercise":"squat","weight":245,"reps":3},{"exercise":"squat","weight":255,"reps":2}]}

            Input: "ohp 95 for 8 8 7"
            Output: {"sets":[{"exercise":"overhead press","weight":95,"reps":8},{"exercise":"overhead press","weight":95,"reps":8},{"exercise":"overhead press","weight":95,"reps":7}]}

            Input: "rdl 4x10 at 185"
            Output: {"sets":[{"exercise":"romanian deadlift","weight":185,"reps":10},{"exercise":"romanian deadlift","weight":185,"reps":10},{"exercise":"romanian deadlift","weight":185,"reps":10},{"exercise":"romanian deadlift","weight":185,"reps":10}]}

            Now convert this input. Output JSON only.
            Input: "%s"
        """.format(rawText)

        val response = withContext(Dispatchers.Default) {
            llmInference?.run(prompt)?.let { inference ->
                inference.outputJson
            } ?: ""
        }

        if (response.isEmpty()) return Result.failure(Exception("No response from LLM"))

        var cleaned = response.trim().removePrefix("\``json").removePrefix("```").removeSuffix("```").trim()
        try {
            val parsedResponse = Json.decodeFromString<ParsedResponse>(cleaned)
            return Result.success(parsedResponse.sets)
        } catch (e: SerializationException) {
            // Retry once
            cleaned = response.trim().removePrefix("\``json").removePrefix("```").removeSuffix("```").trim()
            try {
                val parsedResponse = Json.decodeFromString<ParsedResponse>(cleaned)
                return Result.success(parsedResponse.sets)
            } catch (e: SerializationException) {
                return Result.failure(e)
            }
        }
    }

    @VisibleForTesting
    fun setModelUrl(modelUrl: String) {
        this.modelFile = File(context.filesDir, "gemma_e4b.task")
    }
}
