package com.ayman.ecolift.data

fun jaroWinkler(s1: String, s2: String): Double {
    if (s1.isEmpty() || s2.isEmpty()) return 0.0

    val len1 = s1.length
    val len2 = s2.length
    var matchDistance = Math.max(len1, len2) - 1

    var matches = 0
    var transpositions = 0
    var i = 0
    var j = 0

    while (i < len1 && j < len2) {
        if (s1[i] == s2[j]) {
            matches++
            i++
            j++
        } else {
            val maxIndex = Math.max(i, j)
            for (k in maxIndex until minOf(len1, len2)) {
                if (s1[k] == s2[j]) {
                    transpositions++
                    break
                }
            }
            i++
            j++
        }
    }

    var commonPrefixLength = 0
    while (i < len1 && j < len2 && s1[i] == s2[j]) {
        commonPrefixLength++
        i++
        j++
    }

    val transpositionFactor = if (transpositions > 0) 2.0 / transpositions else 0.0

    return (matches.toDouble() / len1 + matches.toDouble() / len2 + (commonPrefixLength.toDouble() - matchDistance) * transpositionFactor) / 3.0
}

fun match(query: String, exercises: List<Exercise>, threshold: Double = 0.7, limit: Int = 3): List<Exercise> {
    val lowerCaseQuery = query.lowercase()
    return exercises.map { exercise ->
        val canonicalScore = jaroWinkler(lowerCaseQuery, exercise.canonicalName)
        val aliasScores = exercise.aliases.split(",").mapNotNull { it.trim().lowercase() }.map { jaroWinkler(lowerCaseQuery, it) }
        Exercise(canonicalScore = canonicalScore, aliasScores = aliasScores)
    }.filter { it.canonicalScore >= threshold || it.aliasScores.any { it >= threshold } }
        .sortedByDescending { it.canonicalScore + it.aliasScores.maxOrNull() ?: 0.0 }
        .take(limit)
}

fun matchOne(query: String, exercises: List<Exercise>, threshold: Double = 0.6): Exercise? {
    val lowerCaseQuery = query.lowercase()
    return exercises.map { exercise ->
        val canonicalScore = jaroWinkler(lowerCaseQuery, exercise.canonicalName)
        val aliasScores = exercise.aliases.split(",").mapNotNull { it.trim().lowercase() }.map { jaroWinkler(lowerCaseQuery, it) }
        Exercise(canonicalScore = canonicalScore, aliasScores = aliasScores)
    }.filter { it.canonicalScore >= threshold || it.aliasScores.any { it >= threshold } }
        .sortedByDescending { it.canonicalScore + it.aliasScores.maxOrNull() ?: 0.0 }
        .firstOrNull()
}
