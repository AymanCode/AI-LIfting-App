package com.ayman.ecolift.agent.router

import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.engine.Prompts

/**
 * Two-stage intent router:
 *
 * 1. Rule-based fast path via [RuleMatcher] — zero model cost, covers ~80% of queries.
 * 2. Model fallback via [LocalGenAiEngine] — for ambiguous or novel phrasing.
 * 3. Clarify — if model output doesn't parse to a known label.
 *
 * [engine] is nullable: if null (model not ready), falls back to Clarify for
 * unmatched rules rather than blocking.
 */
class IntentRouter(
    private val engine: LocalGenAiEngine? = null
) {
    private val tag = "IntentRouter"

    data class RoutingResult(
        val intent: Intent,
        val source: Source,
        val confidence: Float
    ) {
        enum class Source { RULE, MODEL, FALLBACK }
    }

    suspend fun route(userText: String): RoutingResult {
        // 1. Rule fast path
        val ruleMatch = RuleMatcher.match(userText)
        if (ruleMatch != null) {
            return RoutingResult(ruleMatch.intent, RoutingResult.Source.RULE, ruleMatch.confidence)
        }

        // 2. Model fallback
        val eng = engine
        if (eng != null && eng.isReady) {
            val intent = classifyWithModel(userText, eng)
            if (intent != null) {
                return RoutingResult(intent, RoutingResult.Source.MODEL, 0.70f)
            }
        }

        // 3. Clarify fallback
        val question = if (eng != null && eng.isReady) {
            tryGenerateClarification(userText, eng)
        } else {
            "Could you be more specific? I wasn't sure what you meant."
        }
        return RoutingResult(Intent.Clarify(question), RoutingResult.Source.FALLBACK, 0.0f)
    }

    // ── Model classification ─────────────────────────────────────────

    private suspend fun classifyWithModel(text: String, eng: LocalGenAiEngine): Intent? {
        val prompt = Prompts.intentClassification(text)
        return try {
            val raw = eng.generateStructured(prompt, INTENT_LABEL_SCHEMA).trim()
            parseLabelToIntent(raw.lines().firstOrNull()?.trim() ?: raw, text)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun tryGenerateClarification(text: String, eng: LocalGenAiEngine): String {
        return try {
            val raw = eng.generateStructured(Prompts.clarify(text), "{}").trim()
            raw.ifBlank { "Could you be more specific?" }
        } catch (e: Exception) {
            "Could you be more specific?"
        }
    }

    // ── Label parsing ────────────────────────────────────────────────

    private fun parseLabelToIntent(label: String, rawText: String): Intent? {
        val clean = label.trim().removeSuffix(".").lowercase()
        return when (clean) {
            "logset"            -> Intent.Write(PatchType.LogSet, rawText)
            "editset"           -> Intent.Write(PatchType.EditSet, rawText)
            "deleteset"         -> Intent.Write(PatchType.DeleteSet, rawText)
            "moveworkoutday"    -> Intent.Write(PatchType.MoveWorkoutDay, rawText)
            "renameexercise"    -> Intent.Write(PatchType.RenameExercise, rawText)
            "askrecommendation" -> Intent.Read(ReadType.AskRecommendation, rawText)
            "asksimilar"        -> Intent.Read(ReadType.AskSimilar, rawText)
            "askhistory"        -> Intent.Read(ReadType.AskHistory, rawText)
            "querydate"         -> Intent.Read(ReadType.QueryDate, rawText)
            "queryprogress"     -> Intent.Read(ReadType.QueryProgress, rawText)
            "clarify"           -> null
            else                -> null
        }
    }

    companion object {
        private val INTENT_LABEL_SCHEMA = """
            One of: LogSet | EditSet | DeleteSet | MoveWorkoutDay | RenameExercise |
            AskRecommendation | AskSimilar | AskHistory | QueryDate | QueryProgress | Clarify
        """.trimIndent()
    }
}
