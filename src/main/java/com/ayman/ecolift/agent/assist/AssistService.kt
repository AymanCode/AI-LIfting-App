package com.ayman.ecolift.agent.assist

import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.tools.AgentTools
import com.ayman.ecolift.agent.tools.WeightSuggestion
import com.ayman.ecolift.data.WeightLbs
import kotlinx.coroutines.flow.collect

/**
 * Read-only, button-shaped entry point to the smart layer for inline UI helpers.
 * Never writes — callers (ViewModels) apply results. `engine` is optional; Log
 * helpers pass null (local only), Progress passes GroqEdgeEngine for prose.
 */
class AssistService(
    private val tools: AgentTools,
    private val engine: LocalGenAiEngine? = null,
) {
    /** Local-only set suggestion. weightStored is tenths of a pound; reps echoes the requested target. */
    suspend fun suggestSetEntry(exerciseId: Long, targetReps: Int, beforeDate: String? = null): SetSuggestion {
        val s = if (beforeDate == null) {
            tools.suggestWeight(exerciseId, targetReps)
        } else {
            tools.suggestWeightBeforeDate(exerciseId, targetReps, beforeDate)
        }
        return SetSuggestion(
            weightStored = s.suggestedWeightLbs,
            reps = s.suggestedReps ?: s.targetReps,
            confidence = s.confidence,
            localReason = s.reasoning,
        )
    }

    /** Local advisory sanity-check. Never blocks; returns Ok or Suspect. */
    suspend fun checkInput(exerciseId: Long, draft: SetDraft): InputVerdict {
        val reps = draft.reps
        if (reps != null && (reps <= 0 || reps > MAX_REASONABLE_REPS)) {
            return InputVerdict.Suspect("$reps reps looks off — double-check that.")
        }
        val weight = draft.weightStored
        if (draft.isBodyweight || weight == null || weight <= 0) return InputVerdict.Ok

        val top = tools.getExerciseHistory(exerciseId, HISTORY_WINDOW_DAYS).topSetWeightLbs
        if (top == null || top <= 0) return InputVerdict.Ok

        if (weight >= top * ORDER_OF_MAGNITUDE_FACTOR) {
            val fixStored = weight / 10  // undo a single misplaced decimal
            return InputVerdict.Suspect(
                message = "${WeightLbs.formatStored(weight)} lb is far above your usual " +
                    "${WeightLbs.formatStored(top)} lb — did you mean ${WeightLbs.formatStored(fixStored)}?",
                suggestedFix = SetSuggestion(
                    weightStored = fixStored,
                    reps = reps,
                    confidence = WeightSuggestion.Confidence.LOW,
                    localReason = "Looks like a 10× typo.",
                ),
            )
        }
        return InputVerdict.Ok
    }

    /**
     * Local facts first; if an engine is present, Groq phrases them and the result is
     * validated. Any failure/blank/contradiction falls back to the local templated summary.
     */
    suspend fun analyzeExercise(exerciseId: Long): ExerciseAnalysis {
        val facts = ExerciseAnalysisFacts.from(tools.getProgressTrend(exerciseId))
        val local = ExerciseAnalysis(facts.toLocalSummary(), AnalysisSource.Local, validated = true)
        val eng = engine ?: return local

        val prose = runCatching {
            buildString { eng.streamText(facts.toPrompt()).collect { append(it) } }
        }.getOrNull()?.trim().orEmpty()

        if (prose.isBlank()) return local
        return if (AnalysisValidator.validate(facts, prose)) {
            ExerciseAnalysis(prose, AnalysisSource.Groq, validated = true)
        } else {
            ExerciseAnalysis(facts.toLocalSummary(), AnalysisSource.Local, validated = false)
        }
    }

    companion object {
        const val MAX_REASONABLE_REPS = 100
        const val HISTORY_WINDOW_DAYS = 3650  // ~10y: effectively all-time top set
        const val ORDER_OF_MAGNITUDE_FACTOR = 5
    }
}
