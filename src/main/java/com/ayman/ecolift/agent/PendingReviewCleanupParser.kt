package com.ayman.ecolift.agent

/**
 * Extracts the target exercise from pending-review cleanup commands.
 *
 * Use case: after a bulk import, users should not have to resolve every failed
 * line one by one when several rows failed for the same reason, such as a
 * misspelled exercise name. This tiny deterministic parser recognizes explicit
 * "treat these pending rows as X" style commands and leaves everything else to
 * normal agent routing.
 */
object PendingReviewCleanupParser {

    fun extractExerciseName(text: String): String? {
        val cleaned = text.trim().trimEnd('.')
        for (pattern in PATTERNS) {
            val match = pattern.matchEntire(cleaned) ?: continue
            val exercise = match.groupValues[1].trim()
            if (exercise.isNotBlank()) return exercise
        }
        return null
    }

    private val PATTERNS = listOf(
        Regex("""(?i)(?:all\s+)?(?:the\s+)?pending(?:\s+review)?(?:\s+items|\s+rows|\s+lines|\s+ones)?\s+(?:are|as)\s+(.+)"""),
        Regex("""(?i)treat\s+(?:all\s+)?pending(?:\s+review)?(?:\s+items|\s+rows|\s+lines)?\s+as\s+(.+)"""),
        Regex("""(?i)(?:make|set)\s+(?:all\s+)?(?:the\s+)?pending(?:\s+review)?(?:\s+items|\s+rows|\s+lines|\s+ones)?(?:\s+(?:as|to))?\s+(.+)"""),
        Regex("""(?i)(?:these|those)\s+(?:are|as)\s+(?:all\s+)?(.+)""")
    )
}
