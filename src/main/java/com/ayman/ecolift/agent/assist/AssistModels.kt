package com.ayman.ecolift.agent.assist

import com.ayman.ecolift.agent.tools.ProgressTrend
import com.ayman.ecolift.agent.tools.WeightSuggestion
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.formatFixed
import kotlin.math.roundToInt

/** A non-committed set suggestion. weightStored is in tenths of a pound (see WeightLbs). */
data class SetSuggestion(
    val weightStored: Int?,
    val reps: Int?,
    val confidence: WeightSuggestion.Confidence,
    val localReason: String,
) {
    val hasData: Boolean get() = confidence != WeightSuggestion.Confidence.NO_DATA
}

/** A set value the user just entered, for sanity-checking. weightStored is tenths of a pound. */
data class SetDraft(
    val weightStored: Int?,
    val reps: Int?,
    val isBodyweight: Boolean = false,
)

sealed interface InputVerdict {
    data object Ok : InputVerdict
    data class Suspect(
        val message: String,
        val suggestedFix: SetSuggestion? = null,
    ) : InputVerdict
}

enum class AnalysisSource { Local, Groq }

data class ExerciseAnalysis(
    val text: String,
    val source: AnalysisSource,
    val validated: Boolean,
)

/**
 * The only facts the validator is allowed to check Groq prose against.
 * All loads here are DISPLAY pounds (already converted from stored tenths).
 */
data class ExerciseAnalysisFacts(
    val name: String,
    val sessionCount: Int,
    val prDisplayLbs: Int?,
    val prDate: String?,
    val est1RmDisplayLbs: Int?,
    val deltaPercent: Float?,
    val recentSessions: List<String>,
) {
    /** Deterministic fallback shown when Groq is unavailable or its prose fails validation. */
    fun toLocalSummary(): String {
        val trend = when {
            deltaPercent == null -> "not enough recent data to call a trend"
            deltaPercent > 2f -> "trending up"
            deltaPercent < -2f -> "trending down"
            else -> "holding steady"
        }
        val deltaText = deltaPercent?.let {
            " (${if (it >= 0) "+" else ""}${formatFixed(it.toDouble(), 0)}% est. 1RM vs prior 30 days)"
        } ?: ""
        val pr = prDisplayLbs?.let { "PR $it lb${prDate?.let { d -> " on $d" } ?: ""}." } ?: "No PR on record."
        return "$name is $trend$deltaText over ${countLabel(sessionCount, "session")}. $pr"
    }

    /** Strict, facts-only prompt for the Groq prose step. */
    fun toPrompt(): String = """
        You are a strength-coaching assistant. Using ONLY the facts below, write 2-3 short, plain
        sentences about this lift's recent progress. Do not invent numbers, dates, or PRs that are
        not in the facts. Do not prescribe specific weights or rep targets.

        Facts:
        - Exercise: $name
        - Sessions logged: $sessionCount
        - PR: ${prDisplayLbs?.let { "$it lb" } ?: "none"}${prDate?.let { " on $it" } ?: ""}
        - Estimated 1RM: ${est1RmDisplayLbs?.let { "$it lb" } ?: "unknown"}
        - 30-day est-1RM change: ${deltaPercent?.let { "${formatFixed(it.toDouble(), 0)}%" } ?: "unknown"}
        - Recent sessions (newest first): ${recentSessions.joinToString("; ").ifBlank { "none" }}
    """.trimIndent()

    companion object {
        fun from(t: ProgressTrend): ExerciseAnalysisFacts = ExerciseAnalysisFacts(
            name = t.name,
            sessionCount = t.sessionCount,
            // prWeightLbs is STORED tenths -> convert to display pounds.
            prDisplayLbs = t.prWeightLbs?.let { WeightLbs.toLbs(it).roundToInt() },
            prDate = t.prDate,
            // est1Rm is ALREADY display pounds.
            est1RmDisplayLbs = t.est1Rm,
            deltaPercent = t.deltaPercent,
            recentSessions = t.recentSessions,
        )
    }
}
