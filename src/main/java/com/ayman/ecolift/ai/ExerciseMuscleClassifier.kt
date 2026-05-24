package com.ayman.ecolift.ai

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.LEGACY_DEFAULT_MUSCLE_GROUPS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MuscleClassification(
    val exerciseId: Long,
    val muscleGroups: String,
    val confidence: Double,
)

class ExerciseMuscleClassifier(
    private val apiKey: String,
    baseUrl: String,
    model: String,
) {
    private val resolvedBaseUrl = baseUrl.ifBlank { GroqCloudAgent.DEFAULT_BASE_URL }.trimEnd('/')
    private val resolvedModel = model.ifBlank { GroqCloudAgent.DEFAULT_MODEL }

    suspend fun classifyBatch(exercises: List<Exercise>): List<MuscleClassification> {
        val candidates = exercises.filter(::shouldClassify)
        if (candidates.isEmpty()) return emptyList()

        val remote = if (apiKey.isNotBlank()) {
            runCatching { classifyWithGroq(candidates) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val remoteById = remote.associateBy { it.exerciseId }

        return candidates.mapNotNull { exercise ->
            remoteById[exercise.id] ?: classifyLocally(exercise)
        }
    }

    private suspend fun classifyWithGroq(exercises: List<Exercise>): List<MuscleClassification> =
        withContext(Dispatchers.IO) {
            val body = GroqCloudJson.buildChatCompletionRequest(
                model = resolvedModel,
                prompt = buildPrompt(exercises),
            )
            val response = post("$resolvedBaseUrl/chat/completions", body)
            val content = JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
            parseRemoteJson(exercises, content)
        }

    private fun buildPrompt(exercises: List<Exercise>): String {
        val input = JSONArray().apply {
            exercises.forEach { exercise ->
                put(JSONObject().put("id", exercise.id).put("name", exercise.name))
            }
        }
        return """
            Classify each workout exercise into primary muscle groups.
            Allowed labels are: ${ALLOWED_GROUPS.joinToString(", ")}.
            Use at most three labels joined with " · ". If uncertain, use OTHER.
            Guardrail: set selfCheck=false if the label does not clearly match the exercise identity.
            Return only JSON:
            {"classifications":[{"id":1,"muscleGroups":"CHEST · TRICEPS","confidence":0.0,"selfCheck":true}]}

            Exercises:
            $input
        """.trimIndent()
    }

    private fun parseRemoteJson(
        exercises: List<Exercise>,
        raw: String,
    ): List<MuscleClassification> {
        val byId = exercises.associateBy { it.id }
        val root = JSONObject(extractJson(raw) ?: raw)
        val classifications = root.optJSONArray("classifications") ?: return emptyList()
        return buildList {
            for (i in 0 until classifications.length()) {
                val item = classifications.optJSONObject(i) ?: continue
                val exercise = byId[item.optLong("id")] ?: continue
                val selfCheck = item.optBoolean("selfCheck", false)
                val confidence = item.optDouble("confidence", 0.0)
                val groups = normalizeGroups(item.optString("muscleGroups")) ?: continue
                if (!selfCheck || confidence < REMOTE_MIN_CONFIDENCE) continue

                val local = classifyLocally(exercise)
                if (local != null && local.confidence >= LOCAL_STRONG_CONFIDENCE && !compatible(local.muscleGroups, groups)) {
                    add(local)
                } else {
                    add(MuscleClassification(exercise.id, groups, confidence))
                }
            }
        }
    }

    private fun post(endpoint: String, body: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            check(connection.responseCode in 200..299) { response }
            response
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val REMOTE_MIN_CONFIDENCE = 0.70
        private const val LOCAL_STRONG_CONFIDENCE = 0.82

        val ALLOWED_GROUPS = setOf(
            "CHEST",
            "BACK",
            "SHOULDERS",
            "BICEPS",
            "TRICEPS",
            "QUADS",
            "HAMSTRINGS",
            "GLUTES",
            "CALVES",
            "CORE",
            "FOREARMS",
            "FULL BODY",
            "CARDIO",
            "OTHER",
        )

        fun shouldClassify(exercise: Exercise): Boolean {
            val current = exercise.muscleGroups.trim()
            return current.isBlank() || current.equals(LEGACY_DEFAULT_MUSCLE_GROUPS, ignoreCase = true)
        }

        fun classifyLocally(exercise: Exercise): MuscleClassification? {
            val name = exercise.name.lowercase()
            val result = when {
                name.containsAny("lateral raise", "side raise", "rear delt", "face pull", "reverse fly") ->
                    "SHOULDERS" to 0.90
                name.containsAny("shoulder press", "overhead press", "military press", "arnold press") ->
                    "SHOULDERS · TRICEPS" to 0.86
                name.containsAny("bench", "chest press", "pec deck", "chest fly", "push up") ->
                    "CHEST · TRICEPS" to 0.86
                name.containsAny("dip") ->
                    "CHEST · TRICEPS" to 0.78
                name.contains("extension") && name.containsAny("leg", "quad") ->
                    "QUADS" to 0.90
                name.containsAny("tricep", "pushdown", "skull crusher", "extension rope") ->
                    "TRICEPS" to 0.88
                name.containsAny("curl", "preacher", "hammer") && !name.contains("leg curl") ->
                    "BICEPS" to 0.88
                name.containsAny("row", "pulldown", "pull down", "pull up", "chin up") ->
                    "BACK · BICEPS" to 0.86
                name.containsAny("deadlift", "rdl", "romanian") ->
                    "HAMSTRINGS · GLUTES · BACK" to 0.84
                name.containsAny("squat", "leg press", "lunge", "split squat") ->
                    "QUADS · GLUTES" to 0.84
                name.containsAny("leg curl", "hamstring curl") ->
                    "HAMSTRINGS" to 0.90
                name.containsAny("hip thrust", "glute bridge", "abduction") ->
                    "GLUTES" to 0.86
                name.containsAny("calf") ->
                    "CALVES" to 0.92
                name.containsAny("plank", "crunch", "sit up", "leg raise", "abs", "core") ->
                    "CORE" to 0.88
                else -> null
            } ?: return null

            return normalizeGroups(result.first)?.let {
                MuscleClassification(exercise.id, it, result.second)
            }
        }

        fun normalizeGroups(raw: String): String? {
            val parts = raw
                .uppercase()
                .replace("&", " · ")
                .replace(",", " · ")
                .split("·")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(3)
            if (parts.isEmpty() || parts.any { it !in ALLOWED_GROUPS }) return null
            if (parts.size == 1 && parts.first() == "OTHER") return null
            return parts.joinToString(" · ")
        }

        private fun compatible(left: String, right: String): Boolean {
            val a = left.split(" · ").toSet()
            val b = right.split(" · ").toSet()
            return a.intersect(b).isNotEmpty()
        }

        private fun String.containsAny(vararg needles: String): Boolean =
            needles.any { contains(it) }

        private fun extractJson(raw: String): String? {
            val cleaned = raw
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else null
        }
    }
}
