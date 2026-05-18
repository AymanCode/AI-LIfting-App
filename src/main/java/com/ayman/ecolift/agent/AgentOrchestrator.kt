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
import com.ayman.ecolift.agent.tools.ProgressTrend
import com.ayman.ecolift.agent.tools.SessionSnapshot
import com.ayman.ecolift.agent.tools.WeightSuggestion
import com.ayman.ecolift.data.WeightLbs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class AgentProcessingOptions(
    val allowModelFallback: Boolean = true
)

/**
 * Single entry point from the UI into the IronMind agent layer.
 *
 * Flow: route intent, ground against local workout data, build a typed patch,
 * require confirmation when needed, then apply through the patch service.
 *
 * [today] is injectable so tests can control date-sensitive behavior.
 * [engine] is optional; when absent, read results use deterministic formatting.
 */
class AgentOrchestrator(
    private val router: IntentRouter,
    private val tools: AgentTools,
    private val patchApplier: PatchApplier,
    private val engine: LocalGenAiEngine? = null,
    private val today: () -> String = { LocalDate.now().toString() }
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Main entry. Called for every user message. */
    suspend fun process(
        userText: String,
        options: AgentProcessingOptions = AgentProcessingOptions()
    ): AgentTurn {
        handleWorkoutImport(userText)?.let { return it }

        val routing = router.route(userText, allowModelFallback = options.allowModelFallback)
        return when (val intent = routing.intent) {
            is Intent.Write   -> handleWrite(intent, userText, options)
            is Intent.Read    -> handleRead(intent, userText, options)
            is Intent.Clarify -> {
                if (AgentRecoveryGuidance.looksLikeWorkoutLog(userText)) {
                    recoverLogSetFailure(userText)
                } else {
                    AgentTurn.TextResponse(intent.question)
                }
            }
        }
    }

    /**
     * Confirm a pending destructive patch batch.
     * Called by the UI after the user taps "Confirm" on a [AgentTurn.NeedsConfirmation] card.
     */
    suspend fun confirm(requestId: String, patches: List<DbPatch>): AgentTurn {
        return when (val r = patchApplier.applyPatches(requestId, patches, userConfirmed = true)) {
            is PatchResult.Applied  -> AgentTurn.Applied(
                text    = "Done - ${r.patchCount} change(s) applied.",
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

    // Write handling

    private suspend fun handleWorkoutImport(userText: String): AgentTurn? {
        if (!WorkoutImportTextParser.looksLikeImport(userText, today())) return null
        val draft = WorkoutImportTextParser.parse(userText, today()) ?: return null
        val pendingReviews = draft.unresolvedLines.map {
            AgentTurn.PendingReviewDraft(
                rawInput = it.rawLine,
                dateLogged = it.date,
                reason = it.reason
            )
        }.toMutableList()
        val patches = mutableListOf<DbPatch>()
        val nextSetByExerciseAndDate = mutableMapOf<Pair<Long, String>, Int>()

        for (entry in draft.entries) {
            val ex = tools.findExercise(entry.exerciseQuery)
            if (ex == null) {
                pendingReviews += AgentTurn.PendingReviewDraft(
                    rawInput = entry.rawLine,
                    dateLogged = entry.date,
                    reason = "Exercise not found: ${entry.exerciseQuery}"
                )
                continue
            }

            val key = ex.exerciseId to entry.date
            var nextSet = nextSetByExerciseAndDate.getOrPut(key) {
                val recentSets = tools.getRecentSets(ex.exerciseId, limit = 50)
                (recentSets.filter { it.date == entry.date }.maxOfOrNull { it.setNumber } ?: 0) + 1
            }

            for (set in entry.sets) {
                patches += DbPatch.LogSet(
                    exerciseId = ex.exerciseId,
                    date = entry.date,
                    setNumber = nextSet,
                    weightLbs = if (ex.isBodyweight) null else set.weightLbs,
                    reps = set.reps,
                    isBodyweight = ex.isBodyweight
                )
                nextSet += 1
            }
            nextSetByExerciseAndDate[key] = nextSet
        }

        if (patches.isEmpty()) {
            return AgentTurn.ImportApplied(
                text = importSummary(appliedPatchCount = 0, pendingReviewCount = pendingReviews.size),
                auditId = null,
                appliedPatchCount = 0,
                pendingReviews = pendingReviews
            )
        }

        val requestId = UUID.randomUUID().toString()
        return when (val r = patchApplier.applyPatches(requestId, patches, userConfirmed = false)) {
            is PatchResult.Applied -> AgentTurn.ImportApplied(
                text = importSummary(
                    appliedPatchCount = r.patchCount,
                    pendingReviewCount = pendingReviews.size
                ),
                auditId = r.auditId,
                appliedPatchCount = r.patchCount,
                pendingReviews = pendingReviews
            )
            is PatchResult.Rejected -> AgentTurn.TextResponse("Couldn't import: ${r.reason}")
            is PatchResult.Failed -> AgentTurn.Error(r.error)
        }
    }

    private suspend fun handleWrite(
        intent: Intent.Write,
        userText: String,
        options: AgentProcessingOptions
    ): AgentTurn {
        val patches = generatePatches(intent, userText, options)
            ?: return if (intent.patchType == PatchType.LogSet && AgentRecoveryGuidance.looksLikeWorkoutLog(userText)) {
                recoverLogSetFailure(userText)
            } else {
                AgentTurn.TextResponse(
                    "I couldn't determine the details. Could you be more specific?"
                )
            }

        val requestId = UUID.randomUUID().toString()

        // Gate destructive patches on explicit user confirmation.
        if (patches.any { DbPatch.isDestructive(it) }) {
            return AgentTurn.NeedsConfirmation(
                summary   = "Are you sure you want to ${describePatch(patches.first())}?",
                patches   = patches,
                requestId = requestId
            )
        }

        return when (val r = patchApplier.applyPatches(requestId, patches, userConfirmed = false)) {
            is PatchResult.Applied  -> AgentTurn.Applied(confirmText(patches), r.auditId)
            is PatchResult.Rejected -> AgentTurn.TextResponse("Couldn't apply: ${r.reason}")
            is PatchResult.Failed   -> AgentTurn.Error(r.error)
        }
    }

    // Read handling

    private suspend fun handleRead(
        intent: Intent.Read,
        userText: String,
        options: AgentProcessingOptions
    ): AgentTurn {
        val text = when (intent.queryType) {
            ReadType.QueryDate -> {
                val date = extractDate(userText, today())
                    ?: return AgentTurn.TextResponse("Which date? I couldn't parse one from your message.")
                val snapshot = tools.getSessionByDate(date)
                summarizeSession(snapshot)
            }
            ReadType.QueryProgress -> {
                val ex = extractAndFindExercise(userText)
                    ?: return AgentTurn.TextResponse("Which exercise? I couldn't identify one in your message.")
                val trend = tools.getProgressTrend(ex.exerciseId)
                summarizeTrend(trend)
            }
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

        // Let a ready local model polish read-only responses; deterministic text is the fallback.
        val final = if (options.allowModelFallback && engine?.isReady == true && text.isNotBlank()) {
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

    // Patch generation

    private data class ExtractedSet(
        val weightLbs: Int?,
        val reps: Int
    )

    private data class ExtractedLog(
        val exerciseQuery: String,
        val date: String?,
        val sets: List<ExtractedSet>
    )

    private suspend fun generatePatches(
        intent: Intent.Write,
        userText: String,
        options: AgentProcessingOptions
    ): List<DbPatch>? =
        when (intent.patchType) {
            PatchType.LogSet         -> generateLogSetPatches(userText, options.allowModelFallback)
            PatchType.EditSet        -> generateEditSet(userText)?.let(::listOf)
            PatchType.DeleteSet      -> generateDeleteSet(userText)?.let(::listOf)
            PatchType.MoveWorkoutDay -> generateMoveWorkoutDay(userText)?.let(::listOf)
            PatchType.RenameExercise -> generateRenameExercise(userText)?.let(::listOf)
        }

    private suspend fun generateLogSetPatches(
        userText: String,
        allowModelFallback: Boolean
    ): List<DbPatch>? {
        val targetDate = extractDate(userText, today()) ?: today()
        val parsed = LogSetTextParser.parseOneExercise(userText)
        if (parsed != null) {
            val ex = tools.findExercise(parsed.exerciseQuery)
            if (ex != null) {
                return buildLogSetPatches(
                    ex = ex,
                    targetDate = targetDate,
                    sets = parsed.sets.map { ExtractedSet(it.weightLbs, it.reps) }
                )
            }
        }

        if (AgentRecoveryGuidance.shouldAvoidModelLogMutation(userText)) return null

        val legacy = generateLegacyLogSetPatch(userText, targetDate)
        if (legacy != null) return legacy

        return if (allowModelFallback) generateModelLogSetPatches(userText, targetDate) else null
    }

    private fun recoverLogSetFailure(userText: String): AgentTurn.RecoverableFailure =
        AgentRecoveryGuidance.logSetFailure(
            originalText = userText,
            saveDate = today(),
            canTryModel = engine != null
        )

    private suspend fun generateLegacyLogSetPatch(userText: String, targetDate: String): List<DbPatch>? {
        val ex = extractAndFindExercise(userText) ?: return null
        val (weight, reps) = extractWeightAndReps(userText) ?: return null
        return buildLogSetPatches(
            ex = ex,
            targetDate = targetDate,
            sets = listOf(ExtractedSet(weight, reps ?: return null))
        )
    }

    private suspend fun generateModelLogSetPatches(userText: String, defaultDate: String): List<DbPatch>? {
        val eng = engine ?: return null
        if (!eng.isReady) return null

        val raw = try {
            eng.generateStructured(
                Prompts.logSetExtraction(userText, defaultDate),
                Prompts.LOG_SET_EXTRACTION_SCHEMA
            )
        } catch (_: Exception) {
            return null
        }
        val extracted = parseModelLogSet(raw) ?: return null
        val ex = tools.findExercise(extracted.exerciseQuery) ?: return null
        return buildLogSetPatches(ex, defaultDate, extracted.sets)
    }

    private suspend fun buildLogSetPatches(
        ex: ExerciseMatch,
        targetDate: String,
        sets: List<ExtractedSet>
    ): List<DbPatch>? {
        if (sets.isEmpty()) return null

        val recentSets = tools.getRecentSets(ex.exerciseId, limit = 20)
        val nextSet = (recentSets.filter { it.date == targetDate }.maxOfOrNull { it.setNumber } ?: 0) + 1
        return sets.mapIndexed { index, set ->
            DbPatch.LogSet(
                exerciseId   = ex.exerciseId,
                date         = targetDate,
                setNumber    = nextSet + index,
                weightLbs    = if (ex.isBodyweight) null else set.weightLbs,
                reps         = set.reps,
                isBodyweight = ex.isBodyweight
            )
        }
    }

    private fun parseModelLogSet(raw: String): ExtractedLog? {
        val jsonText = extractJsonObject(raw) ?: return null
        val root = runCatching { json.parseToJsonElement(jsonText) as? JsonObject }
            .getOrNull() ?: return null

        val confidence = root.numberOrNull("confidence") ?: return null
        if (confidence < MODEL_LOG_EXTRACTION_MIN_CONFIDENCE) return null

        val exerciseQuery = root.stringOrNull("exerciseQuery", "exercise", "exerciseName")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val date = root.stringOrNull("date")
            ?.takeIf { ISO_DATE.matches(it) }

        val setItems = root["sets"] as? JsonArray ?: return null
        val sets = setItems.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val reps = obj.intOrNull("reps") ?: return@mapNotNull null
            if (reps !in 1..100) return@mapNotNull null
            ExtractedSet(
                weightLbs = obj.weightOrNull("weightLbs", "weight", "lbs"),
                reps = reps
            )
        }.take(10)

        if (sets.isEmpty()) return null
        return ExtractedLog(exerciseQuery = exerciseQuery, date = date, sets = sets)
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

    // Extraction helpers

    private suspend fun extractAndFindExercise(text: String): ExerciseMatch? {
        // 1. Explicit @mention has highest priority
        val mention = Regex("""@([^@\s]+(?: [^@\s]+)*)""").find(text)?.groupValues?.get(1)
        if (mention != null) {
            val match = tools.findExercise(mention)
            if (match != null) return match
        }

        // 2. Heuristic fallback
        val candidate = text
            .replace(Regex("""@[^@\s]+(?: [^@\s]+)*"""), " ") // Remove @mention if it failed above
            .replace(Regex("""\d+\s*(?:lbs?|kg|pounds?|kilos?|reps?|sets?)?"""), " ")
            .replace(
                Regex("""(?i)\b(show|me|history|progress|trend|trending|how|what|tell|see|view|check|recent|session|sessions|times|trained|worked|hit|doing|been|have|all|much|improving|am|getting|stronger|and|the|a|an|at|for|my|last|that|this|to|from|is|was|i|it|x|did|just|finished|add|log|logged|record|delete|remove|erase|get|rid|of|fix|correct|update|edit|change|rename|call|move|reschedule|shift|postpone|wrong|actually|meant|on|over|time|past|weeks?|months?|days?|today|yesterday)\b"""),
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

        // "NxM": if the first number is greater than 20, treat it as weight x reps; otherwise sets x reps.
        val setNotation = Regex("""(\d+(?:\.\d+)?)\s*x\s*(\d+)""").find(t)
        if (setNotation != null) {
            val aRaw = setNotation.groupValues[1]
            val a = aRaw.toDoubleOrNull() ?: return null
            val b = setNotation.groupValues[2].toInt()
            return if (a > 20) Pair(WeightLbs.parseInputToStorage(aRaw), b) else Pair(null, b)
        }

        val weight = Regex("""(\d+(?:\.\d+)?)\s*(?:lbs?|kg|pounds?)""")
            .find(t)
            ?.groupValues
            ?.get(1)
            ?.let(WeightLbs::parseInputToStorage)
        val reps = (Regex("""(\d+)\s*reps?""").find(t) ?: Regex("""\bfor\s+(\d+)\b""").find(t))
            ?.groupValues?.get(1)?.toIntOrNull()
        val bareWeightBeforeFor = Regex("""\b(\d+(?:\.\d+)?)\s+for\s+\d+\b""")
            .find(t)
            ?.groupValues
            ?.get(1)
            ?.takeIf { (it.toDoubleOrNull() ?: 0.0) > 20.0 }
            ?.let(WeightLbs::parseInputToStorage)
        val resolvedWeight = weight ?: bareWeightBeforeFor

        if (resolvedWeight != null || reps != null) return Pair(resolvedWeight, reps)

        extractSpokenWeightAndReps(t)?.let { return it }

        // Fallback: bare number after a context word, such as "it was 135".
        val contextNum = Regex("""(?:was|meant|to|at)\s+(\d+(?:\.\d+)?)\b""")
            .find(t)
            ?.groupValues
            ?.get(1)
            ?.let(WeightLbs::parseInputToStorage)
        return if (contextNum != null) Pair(contextNum, null) else null
    }

    private fun extractSpokenWeightAndReps(text: String): Pair<Int?, Int?>? {
        val match = SPOKEN_WEIGHT_FOR_REPS.find(text) ?: return null
        val weight = parseSpokenNumber(match.groupValues[1]) ?: return null
        val reps = parseSpokenNumber(match.groupValues[2]) ?: return null
        if (weight <= 20 || reps !in 1..100) return null
        return Pair(WeightLbs.fromWholePounds(weight), reps)
    }

    private fun parseSpokenNumber(phrase: String): Int? {
        phrase.trim().toIntOrNull()?.let { return it }
        val words = phrase.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        if (words.size == 1) return NUMBER_WORD_VALUES[words.first()]

        val hundredIndex = words.indexOf("hundred")
        if (hundredIndex > 0) {
            val prefix = parseSpokenNumber(words.take(hundredIndex).joinToString(" ")) ?: return null
            val suffix = words.drop(hundredIndex + 1).takeIf { it.isNotEmpty() }
                ?.let { parseSpokenNumber(it.joinToString(" ")) }
                ?: 0
            return prefix * 100 + suffix
        }

        if (words.size == 2) {
            val first = NUMBER_WORD_VALUES[words[0]] ?: return null
            val second = NUMBER_WORD_VALUES[words[1]] ?: return null
            if (first in 1..9 && second in 10..99) return first * 100 + second
            if (first >= 20 && second in 1..9) return first + second
        }

        if (words.size == 3) {
            val first = NUMBER_WORD_VALUES[words[0]] ?: return null
            val second = NUMBER_WORD_VALUES[words[1]] ?: return null
            val third = NUMBER_WORD_VALUES[words[2]] ?: return null
            if (first in 1..9 && second >= 20 && third in 1..9) {
                return first * 100 + second + third
            }
        }

        return null
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

    private fun extractDate(text: String, todayStr: String): String? =
        DateExtractor.extract(text, LocalDate.parse(todayStr))

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    private fun JsonObject.stringOrNull(vararg keys: String): String? {
        for (key in keys) {
            val element = this[key] ?: continue
            if (element == JsonNull) continue
            val value = (element as? JsonPrimitive)?.content?.trim()
            if (!value.isNullOrBlank() && !value.equals("null", ignoreCase = true)) return value
        }
        return null
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val element = this[key] ?: return null
        if (element == JsonNull) return null
        val content = (element as? JsonPrimitive)?.content?.trim() ?: return null
        return content.toIntOrNull() ?: content.toDoubleOrNull()?.toInt()
    }

    private fun JsonObject.numberOrNull(key: String): Double? {
        val element = this[key] ?: return null
        if (element == JsonNull) return null
        return (element as? JsonPrimitive)?.doubleOrNull
    }

    private fun JsonObject.weightOrNull(vararg keys: String): Int? {
        for (key in keys) {
            val element = this[key] ?: continue
            if (element == JsonNull) continue
            val content = (element as? JsonPrimitive)?.content?.trim() ?: continue
            if (content.isBlank() || content.equals("null", ignoreCase = true)) continue
            return WeightLbs.parseInputToStorage(content)
        }
        return null
    }

    /** Compact session summary suitable for direct display or model polishing. */
    private fun summarizeSession(snapshot: SessionSnapshot): String {
        if (snapshot.exercises.isEmpty())
            return "No workout logged on ${snapshot.date}."
        return buildString {
            append("On ${snapshot.date}: ")
            snapshot.exercises.joinTo(this, " | ") { ex ->
                val top = ex.sets.maxByOrNull { it.weightLbs ?: 0 }
                "${ex.name} (${ex.sets.size} sets, top ${top?.weightLbs?.let(WeightLbs::formatStored) ?: "bw"} x ${top?.reps ?: 0})"
            }
        }
    }

    /** Compact progress summary suitable for direct display or model polishing. */
    private fun summarizeTrend(trend: ProgressTrend): String {
        if (trend.sessionCount == 0) return "No history for ${trend.name} yet."
        return buildString {
            append("${trend.name}: ${trend.sessionCount} sessions. ")
            if (trend.prWeightLbs != null) append("PR ${WeightLbs.formatStored(trend.prWeightLbs)}lbs on ${trend.prDate}. ")
            if (trend.est1Rm != null)      append("Est 1RM ${trend.est1Rm}lbs. ")
            if (trend.deltaPercent != null) {
                val dir = if (trend.deltaPercent >= 0.5f) "up (growing)"
                else if (trend.deltaPercent <= -0.5f) "down (declining)"
                else "stable (plateau)"
                val sign = if (trend.deltaPercent >= 0) "+" else ""
                append("${sign}${String.format(java.util.Locale.US, "%.1f", trend.deltaPercent)}% progress ($dir). ")
            }
            if (trend.recentSessions.isNotEmpty())
                append("Recent: ${trend.recentSessions.take(3).joinToString(", ")}")
        }
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

    // Formatting helpers

    private fun confirmText(patch: DbPatch): String = when (patch) {
        is DbPatch.LogSet         -> "Logged ${patch.reps} reps${if (patch.weightLbs != null) " at ${WeightLbs.formatStored(patch.weightLbs)} lbs" else ""}."
        is DbPatch.EditSet        -> "Set updated."
        is DbPatch.DeleteSet      -> "Set deleted."
        is DbPatch.MoveWorkoutDay -> "Workout moved to ${patch.newDate}."
        is DbPatch.RenameExercise -> "Renamed to \"${patch.newName}\"."
    }

    private fun confirmText(patches: List<DbPatch>): String {
        if (patches.size == 1) return confirmText(patches.first())
        val logSets = patches.filterIsInstance<DbPatch.LogSet>()
        if (logSets.size == patches.size) return "Logged ${logSets.size} sets."
        return "Applied ${patches.size} changes."
    }

    private fun importSummary(appliedPatchCount: Int, pendingReviewCount: Int): String {
        val imported = when (appliedPatchCount) {
            0 -> "No sets were imported."
            1 -> "Imported 1 set."
            else -> "Imported $appliedPatchCount sets."
        }
        val review = when (pendingReviewCount) {
            0 -> ""
            1 -> " Saved 1 unclear line for review."
            else -> " Saved $pendingReviewCount unclear lines for review."
        }
        return imported + review
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
            append("$name - ${history.sessionCount} session(s) in the last ${history.windowDays} days. ")
            if (history.topSetWeightLbs != null)
                append("Best set: ${WeightLbs.formatStored(history.topSetWeightLbs)} lbs x ${history.topSetReps} reps.")
        }
    }

    private fun formatSuggestion(name: String, s: WeightSuggestion): String = when (s.confidence) {
        WeightSuggestion.Confidence.NO_DATA ->
            "No history for $name yet. Start light and work up."
        else -> {
            val w = s.suggestedWeightLbs?.let { "${WeightLbs.formatStored(it)} lbs" } ?: "bodyweight"
            "$name: try $w for ${s.targetReps} reps. ${s.reasoning}"
        }
    }

    companion object {
        private const val MODEL_LOG_EXTRACTION_MIN_CONFIDENCE = 0.70
        private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
        private val NUMBER_WORD_VALUES = mapOf(
            "zero" to 0,
            "one" to 1,
            "two" to 2,
            "three" to 3,
            "four" to 4,
            "five" to 5,
            "six" to 6,
            "seven" to 7,
            "eight" to 8,
            "nine" to 9,
            "ten" to 10,
            "eleven" to 11,
            "twelve" to 12,
            "thirteen" to 13,
            "fourteen" to 14,
            "fifteen" to 15,
            "sixteen" to 16,
            "seventeen" to 17,
            "eighteen" to 18,
            "nineteen" to 19,
            "twenty" to 20,
            "thirty" to 30,
            "forty" to 40,
            "fifty" to 50,
            "sixty" to 60,
            "seventy" to 70,
            "eighty" to 80,
            "ninety" to 90
        )
        private val SPOKEN_NUMBER_PATTERN = NUMBER_WORD_VALUES.keys
            .joinToString(separator = "|") { Regex.escape(it) }
        private val SPOKEN_WEIGHT_FOR_REPS = Regex(
            """(?i)\b(?:was|is|say|be|to|at|actually)\s+((?:(?:$SPOKEN_NUMBER_PATTERN|hundred)\s*){1,4})\s+for\s+((?:$SPOKEN_NUMBER_PATTERN)|\d{1,3})\b"""
        )
    }
}
