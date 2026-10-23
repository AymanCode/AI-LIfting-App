package com.ayman.ecolift.ai

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.LEGACY_DEFAULT_MUSCLE_GROUPS
import com.ayman.ecolift.data.selectableBodyPartGroupSet

data class MuscleClassification(
    val exerciseId: Long,
    val muscleGroups: String,
    val confidence: Double,
)

/**
 * Web port: local pattern classification only. The Android remote path calls
 * Groq with a baked-in key, which must not ship in a browser client.
 * PHASE4: route classifyBatch through the groq-chat edge function.
 */
class ExerciseMuscleClassifier {
    suspend fun classifyBatch(exercises: List<Exercise>): List<MuscleClassification> = emptyList()

    companion object {
        private const val REMOTE_MIN_CONFIDENCE = 0.70
        private const val LOCAL_STRONG_CONFIDENCE = 0.82

        val ALLOWED_GROUPS: Set<String> = selectableBodyPartGroupSet

        fun shouldClassify(exercise: Exercise): Boolean {
            val current = exercise.muscleGroups.trim()
            return current.isBlank() || current.equals(LEGACY_DEFAULT_MUSCLE_GROUPS, ignoreCase = true)
        }

        fun classifyLocally(exercise: Exercise): MuscleClassification? {
            val name = exercise.name.normalizedForMatching()
            val result = when {
                name.containsAny("lateral raise", "side raise", "rear delt", "face pull", "reverse fly") ->
                    "SHOULDERS" to 0.90
                name.containsAny("front raise", "upright row", "shrug") ->
                    "SHOULDERS" to 0.84
                name.containsAny("rear tether") ->
                    "SHOULDERS" to 0.84
                name.containsAny("shoulder press", "overhead press", "military press", "arnold press") ->
                    "SHOULDERS" to 0.86
                name.containsAny("bench", "chest press", "pec deck", "chest fly", "push up", "pushup") ->
                    "CHEST" to 0.86
                name.containsAny("dip") ->
                    "CHEST" to 0.78
                name.contains("extension") && name.containsAny("leg", "quad") ->
                    "QUADS" to 0.90
                name.containsAny("tricep", "pushdown", "skull crusher", "skullcrusher", "extension rope") ->
                    "TRICEPS" to 0.88
                name.containsAny("wrist curl", "reverse curl", "farmer carry", "farmers carry", "grip") ->
                    "FOREARMS" to 0.84
                name.containsAny("curl", "preacher", "hammer") && !name.contains("leg curl") ->
                    "BICEPS" to 0.88
                name.containsAny("row", "pulldown", "pull down", "pull up", "pullup", "chin up", "chinup") ->
                    "BACK" to 0.86
                name.containsAny("deadlift", "rdl", "romanian", "good morning", "back extension", "hyperextension") ->
                    "HAMSTRINGS" to 0.84
                name.containsAny("squat", "leg press", "lunge", "split squat", "bulgarian", "step up") ->
                    "QUADS" to 0.84
                name.containsAny("leg curl", "hamstring curl") ->
                    "HAMSTRINGS" to 0.90
                name.containsAny("hip thrust", "glute bridge", "abduction", "kickback") ->
                    "GLUTES" to 0.86
                name.containsAny("calf") ->
                    "CALVES" to 0.92
                name.containsAny("plank", "crunch", "sit up", "situp", "leg raise", "abs", "core") ->
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
                .replace("/", " · ")
                .replace("+", " · ")
                .split("·")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (parts.isEmpty() || parts.any { it !in ALLOWED_GROUPS }) return null
            return parts.first()
        }

        private fun compatible(left: String, right: String): Boolean {
            val a = left.split(" · ").toSet()
            val b = right.split(" · ").toSet()
            return a.intersect(b).isNotEmpty()
        }

        private fun String.containsAny(vararg needles: String): Boolean =
            needles.any { contains(it) }

        private fun String.normalizedForMatching(): String =
            lowercase()
                .replace(NON_ALPHANUMERIC, " ")
                .replace(WHITESPACE, " ")
                .trim()

        private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
        private val WHITESPACE = Regex("\\s+")
    }
}
