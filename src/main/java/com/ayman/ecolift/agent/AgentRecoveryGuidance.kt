package com.ayman.ecolift.agent

/**
 * Deterministic guidance for prompts that look like workout logs but cannot be
 * safely converted into database patches.
 */
object AgentRecoveryGuidance {

    fun looksLikeWorkoutLog(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.isBlank()) return false

        val hasLogVerb = LOG_VERBS.any { t.contains(it) }
        val hasSetNotation = SET_NOTATION.containsMatchIn(t)
        val hasUnitOrRep = UNIT_OR_REP.containsMatchIn(t)
        val hasNumberWord = NUMBER_WORDS.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(t) }
        val hasExerciseTerm = EXERCISE_TERMS.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(t) }

        if (hasLogVerb && (hasExerciseTerm || hasSetNotation || hasUnitOrRep || hasNumberWord)) return true
        if (hasExerciseTerm && (hasSetNotation || hasUnitOrRep)) return true
        return false
    }

    fun shouldAvoidModelLogMutation(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.isBlank()) return false

        val ambiguousExercise = AMBIGUOUS_EXERCISE_TERMS.any {
            Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(t)
        }
        val plateLoad = PLATE_LOAD.containsMatchIn(t) ||
            (PLATE_WORD.containsMatchIn(t) && NUMBER_WORDS.any { word ->
                Regex("""\b${Regex.escape(word)}\b""").containsMatchIn(t)
            })

        return ambiguousExercise || plateLoad
    }

    fun logSetFailure(
        originalText: String,
        saveDate: String,
        canTryModel: Boolean
    ): AgentTurn.RecoverableFailure =
        AgentTurn.RecoverableFailure(
            title = "I kept this as a draft",
            detail = "I could not safely parse this without AI. I kept your text so you can edit it, add the exact exercise name and pounds, use a deterministic template, save it for review, or retry with AI.",
            originalText = originalText,
            suggestedTemplate = "exercise name 135x8, 125x10 today",
            saveDate = saveDate,
            canTryModel = canTryModel
        )

    private val LOG_VERBS = listOf(
        "i did",
        "just did",
        "just finished",
        "forgot to log",
        "log",
        "logged",
        "record",
        "add"
    )

    private val EXERCISE_TERMS = listOf(
        "bench",
        "press",
        "squat",
        "deadlift",
        "row",
        "curl",
        "raise",
        "pulldown",
        "pushdown",
        "extension",
        "abduction",
        "adduction",
        "calf",
        "hip",
        "lat",
        "shoulder",
        "chest",
        "back",
        "leg",
        "machine",
        "cable",
        "plate",
        "plates"
    )

    private val NUMBER_WORDS = listOf(
        "one",
        "two",
        "three",
        "four",
        "five",
        "six",
        "seven",
        "eight",
        "nine",
        "ten",
        "eleven",
        "twelve",
        "thirty",
        "forty",
        "fifty",
        "sixty",
        "seventy",
        "eighty",
        "ninety",
        "hundred"
    )

    private val AMBIGUOUS_EXERCISE_TERMS = listOf(
        "thing",
        "that machine",
        "some machine",
        "idk",
        "dont know",
        "don't know",
        "not sure"
    )

    private val PLATE_WORD = Regex("""(?i)\bplates?\b""")
    private val PLATE_LOAD = Regex("""(?i)\b\d+(?:\.\d+)?\s*plates?\b""")
    private val SET_NOTATION = Regex("""\d+(?:\.\d+)?\s*x\s*\d+""")
    private val UNIT_OR_REP = Regex("""(?:\d+(?:\.\d+)?\s*(?:lbs?|pounds?|kg|kilos?|reps?|sets?|plates?)|\d+(?:\.\d+)?\s+for\s+\d+)""")
}
