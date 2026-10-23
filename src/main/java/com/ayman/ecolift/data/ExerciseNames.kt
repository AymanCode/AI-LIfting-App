package com.ayman.ecolift.data

object ExerciseNames {
    // Same normalization as the Android ExerciseRepository.normalizeName.
    fun normalize(name: String): String = name
        .trim()
        .lowercase()
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { first -> first.uppercase() }
        }
}
