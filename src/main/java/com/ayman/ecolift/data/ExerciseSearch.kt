package com.ayman.ecolift.data

object ExerciseSearch {
    // Replaces the Android DAO LIKE + frequency-join queries: substring match
    // plus fuzzy fallback, ranked by how often the exercise has been logged.
    fun results(
        exercises: List<Exercise>,
        usageCounts: Map<Long, Int>,
        query: String,
        maxDistance: Int = 2,
        limit: Int = 6,
    ): List<Exercise> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val lower = trimmed.lowercase()

        val contains = exercises.filter { it.name.contains(trimmed, ignoreCase = true) }
        val fuzzy = exercises.filter {
            FuzzyMatcher.levenshteinDistance(lower, it.name.lowercase()) <= maxDistance
        }
        return (contains + fuzzy)
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Exercise> { usageCounts[it.id] ?: 0 }
                    .thenBy { it.name },
            )
            .take(limit)
    }
}
