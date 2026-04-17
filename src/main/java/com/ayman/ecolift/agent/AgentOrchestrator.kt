package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.engine.Prompts
import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.patches.PatchApplier
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.router.Intent
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.router.PatchType
import com.ayman.ecolift.agent.router.ReadType
import com.ayman.ecolift.agent.tools.AgentTools
import com.ayman.ecolift.agent.tools.ExerciseMatch
import com.ayman.ecolift.agent.tools.HistorySummary
import com.ayman.ecolift.agent.tools.WeightSuggestion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Phase 6 — single entry point from the UI into the agent layer.
 *
 * Flow:
 *   process(text) → route intent → ground via AgentTools → build patch → confirm gate → apply
 *
 * [today] is injectable so tests can control date-sensitive behaviour.
 * [engine] is nullable — if null, read results use static formatting only.
 */
class AgentOrchestrator(
    private val router: IntentRouter,
    private val tools: AgentTools,
    private val patchApplier: PatchApplier,
    private val engine: LocalGenAiEngine? = null,
    private val today: () -> String = { LocalDate.now().toString() }
) {

    /** Main entry. Called for every user message. */
    suspend fun process(userText: String): AgentTurn {
        val routing = router.route(userText)
        return when (val intent = routing.intent) {
            is Intent.Write   -> handleWrite(intent, userText)
            is Intent.Read    -> handleRead(intent, userText)
            is Intent.Clarify -> AgentTurn.TextResponse(intent.question)
        }
    }

    /**
     * Confirm a pending destructive patch batch.
     * Called by the UI after the user taps "Confirm" on a [AgentTurn.NeedsConfirmation] card.
     */
    suspend fun confirm(requestId: String, patches: List<DbPatch>): AgentTurn {
        return when (val r = patchApplier.applyPatches(requestId, patches, userConfirmed = true)) {
            is PatchResult.Applied  -> AgentTurn.Applied(
                text    = "Done — ${r.patchCount} change(s) applied.",
                auditId = r.auditId
            )
            is PatchResult.Rejected -> AgentTurn.Error(r.reason)
            is PatchResult.Failed   -> AgentTurn.Error(r.error)
        }
    }

    /**
     * Undo a previously applied patch batch.
     * Called by the UI when the user taps "Undo" on an [AgentTurn.Applied] snackbar.
     */
    suspend fun undo(auditId: Long): AgentTurn {
        return when (val r = patchApplier.undo(auditId)) {
            is PatchResult.Applied  -> AgentTurn.Applied("Undone.", r.auditId)
            is PatchResult.Rejected -> AgentTurn.Error(r.reason)
            is PatchResult.Failed   -> AgentTurn.Error(r.error)
        }
    }

    // ── Write handling ─────────────────────────────────────────────────

    private suspend fun handleWrite(intent: Intent.Write, userText: String): AgentTurn {
        val patch = generatePatch(intent, userText)
            ?: return AgentTurn.TextResponse(
                "I couldn't determine the details. Could you be more specific?"
            )

        val requestId = UUID.randomUUID().toString()

        // Gate destructive patches on explicit user confirmation
        if (DbPatch.isDestructive(patch)) {
            return AgentTurn.NeedsConfirmation(
                summary   = "Are you sure you want to ${describePatch(patch)}?",
                patches   = listOf(patch),
                requestId = requestId
            )
        }

        return when (val r = patchApplier.applyPatches(requestId, listOf(patch), userConfirmed = false)) {
            is PatchResult.Applied  -> AgentTurn.Applied(confirmText(patch), r.auditId)
            is PatchResult.Rejected -> AgentTurn.TextResponse("Couldn't apply: ${r.reason}")
            is PatchResult.Failed   -> AgentTurn.Error(r.error)
        }
    }

    // ── Read handling ──────────────────────────────────────────────────

    private suspend fun handleRead(intent: Intent.Read, userText: String): AgentTurn {
        val text = when (intent.queryType) {
            ReadType.AskHistory -> {
                val ex = extractAndFindExercise(userText)
                    ?: return AgentTurn.TextResponse("Which exercise? I couldn't identify one in your message.")
                val history = tools.getExerciseHistory(ex.exerciseId, windowDays = 30)
                formatHistory(ex.name, history)
            }
            ReadType.AskSimilar -> {
                val ex = extractAndFindExercise(userText)
                    ?: return AgentTurn.TextResponse("Which exercise would you like alternatives for?")
                val similar = tools.getSimilarExercises(ex.exerciseId)
                if (similar.isEmpty()) "No similar exercises found in your catalog."
                else "Similar to ${ex.name}: ${similar.joinToString(", ") { it.name }}"
            }
            ReadType.AskRecommendation -> {
                val ex = extractAndFindExercise(userText)
                    ?: return AgentTurn.TextResponse("Which exercise? I couldn't identify one.")
                val suggestion = tools.suggestWeight(ex.exerciseId, targetReps = 8)
                formatSuggestion(ex.name, suggestion)
            }
        }

        // Optionally polish with model
        val final = if (engine?.isReady == true && text.isNotBlank()) {
            try {
                engine.generateStructured(
                    Prompts.formatReadResult(intent.queryType.name, text, userText),
                    "{}"
                ).trim().ifBlank { text }
            } catch (_: Exception) {
                text
            }
        } else {
            text
        }

        return AgentTurn.TextResponse(final)
    }

    // ── Patch generation ───────────────────────────────────────────────

    private suspend fun generatePatch(intent: Intent.Write, userText: String): DbPatch? =
        when (intent.patchType) {
            PatchType.LogSet         -> generateLogSet(userText)
            PatchType.EditSet        -> generateEditSet(userText)
            PatchType.DeleteSet      -> generateDeleteSet(userText)
            PatchType.MoveWorkoutDay -> generateMoveWorkoutDay(userText)
            PatchType.RenameExercise -> generateRenameExercise(userText)
        }

    private suspend fun generateLogSet(userText: String): DbPatch? {
        val ex = extractAndFindExercise(userText) ?: return null
        val (weight, reps) = extractWeightAndReps(userText) ?: return null
        val todayStr = today()
        val recentSets = tools.getRecentSets(ex.exerciseId, limit = 20)
        val nextSet = (recentSets.filter { it.date == todayStr }.maxOfOrNull { it.setNumber } ?: 0) + 1
        return DbPatch.LogSet(
            exerciseId   = ex.exerciseId,
            date         = todayStr,
            setNumber    = nextSet,
            weightLbs    = weight,
            reps         = reps ?: return null,
            isBodyweight = ex.isBodyweight
        )
    }

    private suspend fun generateEditSet(userText: String): DbPatch? {
        val ex = extractAndFindExercise(userText)
        val recentSets = if (ex != null) tools.getRecentSets(ex.exerciseId, limit = 1) else emptyList()
        val setId = recentSets.firstOrNull()?.setId ?: return null
        val (weight, reps) = extractWeightAndReps(userText) ?: Pair(null, null)
        if (weight == null && reps == null) return null
        return DbPatch.EditSet(setId = setId, weightLbs = weight, reps = reps)
    }

    private suspend fun generateDeleteSet(userText: String): DbPatch? {
        val ex = extractAndFindExercise(userText)
        val recentSets = if (ex != null) tools.getRecentSets(ex.exerciseId, limit = 1) else emptyList()
        val setId = recentSets.firstOrNull()?.setId ?: return null
        return DbPatch.DeleteSet(setId = setId)
    }

    private fun generateMoveWorkoutDay(userText: String): DbPatch? {
        val current = today()
        val target = extractTargetDate(userText, current) ?: return null
        return DbPatch.MoveWorkoutDay(currentDate = current, newDate = target)
    }

    private suspend fun generateRenameExercise(userText: String): DbPatch? {
        val ex = extractAndFindExercise(userText) ?: return null
        val newName = extractNewName(userText) ?: return null
        return DbPatch.RenameExercise(exerciseId = ex.exerciseId, newName = newName)
    }

    // ── Extraction helpers ─────────────────────────────────────────────

    private suspend fun extractAndFindExercise(text: String): ExerciseMatch? {
        // Strip numbers, units, and common filler words to isolate exercise name
        val candidate = text
            .replace(Regex("""\d+\s*(?:lbs?|kg|pounds?|kilos?|reps?|sets?)?"""), " ")
            .replace(
                Regex("""(?i)\b(and|the|a|an|at|for|my|last|that|this|to|from|is|was|i|it|x|did|just|finished|add|log|logged|record|delete|remove|erase|get|rid|of|fix|correct|update|edit|change|rename|call|move|reschedule|shift|postpone|wrong|actually|meant)\b"""),
                " "
            )
            .replace(Regex("""\s+"""), " ")
            .trim()
        return if (candidate.isNotBlank()) tools.findExercise(candidate) else null
    }

    /**
     * Returns (weightLbs, reps). Either can be null if not found.
     * Returns null if neither weight nor reps were detected.
     */
    private fun extractWeightAndReps(text: String): Pair<Int?, Int?>? {
        val t = text.lowercase()

        // "NxM" — if first number > 20 it's weight×reps; otherwise sets×reps (weight unknown)
        val setNotation = Regex("""(\d+)\s*x\s*(\d+)""").find(t)
        if (setNotation != null) {
            val a = setNotation.groupValues[1].toInt()
            val b = setNotation.groupValues[2].toInt()
            return if (a > 20) Pair(a, b) else Pair(null, b)
        }

        val weight = Regex("""(\d+)\s*(?:lbs?|kg|pounds?)""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        val reps = (Regex("""(\d+)\s*reps?""").find(t) ?: Regex("""\bfor\s+(\d+)\b""").find(t))
            ?.groupValues?.get(1)?.toIntOrNull()

        if (weight != null || reps != null) return Pair(weight, reps)

        // Fallback: bare number after a context word — "it was 135", "i meant 135", "to 10 reps"
        val contextNum = Regex("""(?:was|meant|to|at)\s+(\d+)\b""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        return if (contextNum != null) Pair(contextNum, null) else null
    }

    private fun extractTargetDate(text: String, currentDate: String): String? {
        val t = text.lowercase()
        val current = LocalDate.parse(currentDate)

        val dayMap = mapOf(
            "monday"    to DayOfWeek.MONDAY,
            "tuesday"   to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY,
            "thursday"  to DayOfWeek.THURSDAY,
            "friday"    to DayOfWeek.FRIDAY,
            "saturday"  to DayOfWeek.SATURDAY,
            "sunday"    to DayOfWeek.SUNDAY
        )
        for ((name, dow) in dayMap) {
            if (t.contains(name)) {
                val target = current.with(TemporalAdjusters.nextOrSame(dow))
                return (if (target == current) target.plusWeeks(1) else target).toString()
            }
        }
        if (t.contains("tomorrow"))  return current.plusDays(1).toString()
        if (t.contains("next week")) return current.plusWeeks(1).toString()
        return null
    }

    private fun extractNewName(text: String): String? {
        val patterns = listOf(
            Regex("""(?i)rename\s+.+?\s+to\s+(.+)"""),
            Regex("""(?i)call\s+it\s+(.+)"""),
            Regex("""(?i)change\s+(?:the\s+)?name\s+of\s+.+?\s+to\s+(.+)""")
        )
        for (p in patterns) {
            val match = p.find(text)
            if (match != null) return match.groupValues[1].trim().trimEnd('.')
        }
        return null
    }

    // ── Formatting helpers ─────────────────────────────────────────────

    private fun confirmText(patch: DbPatch): String = when (patch) {
        is DbPatch.LogSet         -> "Logged ${patch.reps} reps${if (patch.weightLbs != null) " at ${patch.weightLbs} lbs" else ""}."
        is DbPatch.EditSet        -> "Set updated."
        is DbPatch.DeleteSet      -> "Set deleted."
        is DbPatch.MoveWorkoutDay -> "Workout moved to ${patch.newDate}."
        is DbPatch.RenameExercise -> "Renamed to \"${patch.newName}\"."
    }

    private fun describePatch(patch: DbPatch): String = when (patch) {
        is DbPatch.DeleteSet      -> "delete set ${patch.setId}"
        is DbPatch.RenameExercise -> "rename exercise to \"${patch.newName}\""
        else                      -> "apply this change"
    }

    private fun formatHistory(name: String, history: HistorySummary): String {
        if (history.sessionCount == 0)
            return "No $name history in the last ${history.windowDays} days."
        return buildString {
            append("$name — ${history.sessionCount} session(s) in the last ${history.windowDays} days. ")
            if (history.topSetWeightLbs != null)
                append("Best set: ${history.topSetWeightLbs} lbs × ${history.topSetReps} reps.")
        }
    }

    private fun formatSuggestion(name: String, s: WeightSuggestion): String = when (s.confidence) {
        WeightSuggestion.Confidence.NO_DATA ->
            "No history for $name yet. Start light and work up."
        else -> {
            val w = s.suggestedWeightLbs?.let { "$it lbs" } ?: "bodyweight"
            "$name: try $w for ${s.targetReps} reps. ${s.reasoning}"
        }
    }
}
