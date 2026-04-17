package com.ayman.ecolift.data

object FuzzyMatcher {
    /**
     * Calculates the Levenshtein distance between two strings.
     * Note: This function assumes inputs are already normalized (e.g., trimmed and lowercase).
     */
    fun levenshteinDistance(source: String, target: String): Int {
        if (source.isEmpty()) return target.length
        if (target.isEmpty()) return source.length

        val costs = IntArray(target.length + 1) { it }
        for (sourceIndex in 1..source.length) {
            var previousDiagonal = sourceIndex - 1
            costs[0] = sourceIndex
            for (targetIndex in 1..target.length) {
                val previousTop = costs[targetIndex]
                val substitution = if (source[sourceIndex - 1] == target[targetIndex - 1]) {
                    previousDiagonal
                } else {
                    previousDiagonal + 1
                }
                costs[targetIndex] = minOf(
                    costs[targetIndex] + 1,
                    costs[targetIndex - 1] + 1,
                    substitution,
                )
                previousDiagonal = previousTop
            }
        }
        return costs[target.length]
    }
}
