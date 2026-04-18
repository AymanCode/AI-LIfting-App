package com.ayman.ecolift.agent.router

/**
 * Regex + keyword rule-based intent classifier.
 * Covers ~80% of user queries with zero model cost.
 *
 * Returns a matched [Intent] or null if no rule fires with sufficient confidence.
 * Order matters — more specific rules are checked before general ones.
 */
object RuleMatcher {

    data class RuleMatch(val intent: Intent, val confidence: Float)

    fun match(text: String): RuleMatch? {
        val t = text.trim().lowercase()

        // ── DeleteSet ── (check before edit/log — "delete my last set")
        if (DELETE_SET.any { t.contains(it) }) {
            return RuleMatch(Intent.Write(PatchType.DeleteSet, text), 0.90f)
        }

        // ── RenameExercise ──
        if (RENAME_EXERCISE.any { t.contains(it) }) {
            return RuleMatch(Intent.Write(PatchType.RenameExercise, text), 0.90f)
        }

        // ── MoveWorkoutDay ──
        if (MOVE_WORKOUT.any { t.contains(it) }) {
            return RuleMatch(Intent.Write(PatchType.MoveWorkoutDay, text), 0.85f)
        }

        // ── EditSet ── (check before LogSet — "fix my last set", "change that weight")
        if (EDIT_SET.any { t.contains(it) }) {
            return RuleMatch(Intent.Write(PatchType.EditSet, text), 0.85f)
        }

        // ── LogSet ── (weight+reps pattern is high confidence)
        if (hasWeightAndReps(t)) {
            return RuleMatch(Intent.Write(PatchType.LogSet, text), 0.92f)
        }
        if (LOG_SET_KEYWORDS.any { t.contains(it) }) {
            return RuleMatch(Intent.Write(PatchType.LogSet, text), 0.78f)
        }

        // ── QueryDate — "what did I do on Tuesday" (full session for a date) ──
        // Checked before AskHistory — more specific match wins.
        if (QUERY_DATE.any { t.contains(it) }) {
            return RuleMatch(Intent.Read(ReadType.QueryDate, text), 0.90f)
        }

        // ── QueryProgress — "how's my bench trending?" ──
        if (QUERY_PROGRESS.any { t.contains(it) }) {
            return RuleMatch(Intent.Read(ReadType.QueryProgress, text), 0.88f)
        }

        // ── AskRecommendation ──
        if (ASK_RECOMMENDATION.any { t.contains(it) }) {
            return RuleMatch(Intent.Read(ReadType.AskRecommendation, text), 0.88f)
        }

        // ── AskSimilar ──
        if (ASK_SIMILAR.any { t.contains(it) }) {
            return RuleMatch(Intent.Read(ReadType.AskSimilar, text), 0.88f)
        }

        // ── AskHistory ──
        if (ASK_HISTORY.any { t.contains(it) }) {
            return RuleMatch(Intent.Read(ReadType.AskHistory, text), 0.85f)
        }

        return null  // no rule matched → fall through to model
    }

    // ── Patterns ──────────────────────────────────────────────────────

    private val DELETE_SET = listOf(
        "delete", "remove my", "erase", "undo that set", "cancel that set",
        "get rid of", "take out that"
    )

    private val RENAME_EXERCISE = listOf(
        "rename", "call it ", "change the name", "change name of"
    )

    private val MOVE_WORKOUT = listOf(
        "move my workout", "reschedule", "move today", "move monday",
        "move tuesday", "move wednesday", "move thursday", "move friday",
        "move saturday", "move sunday", "postpone", "shift my workout"
    )

    private val EDIT_SET = listOf(
        "fix my", "fix that", "correct my", "correct that", "change that",
        "update my last", "update that", "that was wrong", "i meant",
        "it was actually", "wrong weight", "wrong reps", "edit my", "edit that"
    )

    private val LOG_SET_KEYWORDS = listOf(
        "logged", "just did", "just finished", "add a set",
        "log a set", "record a set"
    )

    private val ASK_RECOMMENDATION = listOf(
        "how much should i", "what weight should", "recommend a weight",
        "suggest a weight", "what should i use", "starting weight",
        "how heavy", "what load", "how many pounds"
    )

    private val ASK_SIMILAR = listOf(
        "similar exercise", "alternative to", "instead of ", "substitute for",
        "replace ", "what else can i do", "alternatives for",
        "similar to ", "swap out "
    )

    // Full-session date queries — "what did I do on", "show me last Monday"
    private val QUERY_DATE = listOf(
        "what did i do on", "what was my workout on", "show me my workout",
        "what did i train on", "my session on", "what exercises did i do",
        "what did i lift on", "show workout for"
    )

    // Progress trend queries — "how's my bench trending", "am I getting stronger"
    private val QUERY_PROGRESS = listOf(
        "how is my", "how has my", "trending", "over time",
        "progress on", "am i improving", "am i getting stronger", "my gains",
        "how much have i improved", "how much stronger", "gained on"
    )

    private val ASK_HISTORY = listOf(
        "show me my", "how did i do", "my progress", "last time i",
        "when did i", "how many times", "my history", "show history",
        "what did i", "my pr", "personal record", "my best"
    )

    // High-confidence: text encodes both a weight value and reps.
    //
    // Weight signals:
    //   - explicit unit: "135lbs", "100kg", "225 pounds"
    //   - "at <number>": "bench 3x10 at 185"
    //   - set notation implies weight is the first number: "135x8", "3x10 at 185"
    //
    // Reps signals:
    //   - "8 reps", "for 5", set notation "3x10"
    private val WEIGHT_WITH_UNIT = Regex("""\d+\s*(lbs?|kg|pounds?|kilos?)""")
    private val WEIGHT_AT_NUM   = Regex("""\bat\s+\d+\b""")
    private val REPS_EXPLICIT   = Regex("""\d+\s*reps?""")
    private val FOR_REPS        = Regex("""\bfor\s+\d+\b""")
    private val SET_NOTATION    = Regex("""\d+\s*x\s*\d+""")

    private fun hasWeightAndReps(t: String): Boolean {
        // "NxM" alone is sufficient — first number = weight, second = reps (gym convention)
        if (SET_NOTATION.containsMatchIn(t)) return true
        val hasWeight = WEIGHT_WITH_UNIT.containsMatchIn(t) || WEIGHT_AT_NUM.containsMatchIn(t)
        val hasReps   = REPS_EXPLICIT.containsMatchIn(t) || FOR_REPS.containsMatchIn(t)
        return hasWeight && hasReps
    }
}
