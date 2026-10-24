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
import com.ayman.ecolift.agent.tools.SetSummary
import com.ayman.ecolift.agent.tools.WeightSuggestion
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.formatFixed
import com.ayman.ecolift.web.util.plusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class AgentProcessingOptions(
    val allowModelFallback: Boolean = true
)

private data class ReadResponse(
    val text: String,
    val card: AgentResponseCard? = null,
)

/**
 * Single entry point from the UI into the agent layer.
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
    private val today: () -> String = { todayLocalDate().toString() }
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Main entry. Called for every user message. */
    suspend fun process(
        userText: String,
        options: AgentProcessingOptions = AgentProcessingOptions()
    ): AgentTurn {
        if (AgentWeightInput.hasInvalidLoad(userText) && AgentRecoveryGuidance.looksLikeWorkoutLog(userText)) {
            return recoverLogSetFailure(userText)
        }
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
                text    = "${countLabel(r.patchCount, "change")} applied.",
                auditId = r.auditId,
                changes = patchChangeLabels(patches, null)
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
                    weightLbs = set.weightLbs,
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

        @OptIn(ExperimentalUuidApi::class)
        val requestId = Uuid.random().toString()
        return when (val r = patchApplier.applyPatches(requestId, patches, userConfirmed = false)) {
            is PatchResult.Applied -> AgentTurn.ImportApplied(
                text = importSummary(
                    appliedPatchCount = r.patchCount,
                    pendingReviewCount = pendingReviews.size
                ),
                auditId = r.auditId,
                appliedPatchCount = r.patchCount,
                pendingReviews = pendingReviews,
                changes = patchChangeLabels(patches, null)
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

        @OptIn(ExperimentalUuidApi::class)
        val requestId = Uuid.random().toString()

        // Gate destructive patches on explicit user confirmation.
        if (patches.any { DbPatch.isDestructive(it) }) {
            return AgentTurn.NeedsConfirmation(
                summary   = "Are you sure you want to ${describePatch(patches.first())}?",
                patches   = patches,
                requestId = requestId
            )
        }

        val changes = patchChangeLabels(patches, userText)
        return when (val r = patchApplier.applyPatches(requestId, patches, userConfirmed = false)) {
            is PatchResult.Applied  -> AgentTurn.Applied(confirmText(patches), r.auditId, changes)
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
        val response = when (intent.queryType) {
            ReadType.QueryDate -> {
                val date = extractDate(userText, today())
                    ?: return AgentTurn.TextResponse("Which date? I couldn't parse one from your message.")
                val snapshot = tools.getSessionByDate(date)
                ReadResponse(summarizeSession(snapshot))
            }
            ReadType.QueryProgress -> {
                val ex = extractAndFindExercise(userText)
                    ?: return AgentTurn.TextResponse("Which exercise? I couldn't identify one in your message.")
                val trend = tools.getProgressTrend(ex.exerciseId)
                ReadResponse(
                    text = summarizeTrend(trend),
                    card = trendHistoryCard(trend)
                )
            }
            ReadType.AskHistory -> {
                val ex = extractAndFindExercise(userText)
                    ?: return AgentTurn.TextResponse("Which exercise? I couldn't identify one in your message.")
                val history = tools.getExerciseHistory(ex.exerciseId, windowDays = 30)
                ReadResponse(
                    text = formatHistory(ex.name, history),
                    card = historyCard(ex.name, history)
                )
            }
            ReadType.AskSimilar -> {
                val ex = extractAndFindExercise(userText)
                    ?: return AgentTurn.TextResponse("Which exercise would you like alternatives for?")
                val similar = tools.getSimilarExercises(ex.exerciseId)
                ReadResponse(
                    if (similar.isEmpty()) "No similar exercises found in your catalog."
                    else "Similar to ${ex.name}: ${similar.joinToString(", ") { it.name }}"
                )
            }
            ReadType.AskRecommendation -> {
                val ex = extractAndFindExercise(userText)
                    ?: return AgentTurn.TextResponse("Which exercise? I couldn't identify one.")
                val suggestion = tools.suggestWeight(ex.exerciseId, targetReps = 8)
                ReadResponse(formatSuggestion(ex.name, suggestion))
            }
        }

        // Let a ready local model polish read-only responses; deterministic text is the fallback.
        val final = if (options.allowModelFallback && engine?.isReady == true && response.text.isNotBlank()) {
            try {
                humanizeIsoDatesInText(engine.generateStructured(
                    Prompts.formatReadResult(intent.queryType.name, response.text, userText),
                    "{}"
                ).trim().ifBlank { response.text })
            } catch (_: Exception) {
                response.text
            }
        } else {
            response.text
        }

        return AgentTurn.TextResponse(humanizeIsoDatesInText(final), response.card)
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
        val parsed = LogSetTextParser.parseOneExercise(DateExtractor.withoutDateReferences(userText, LocalDate.parse(today())))
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

        if (Regex("""\d+\s*x\s*\d+\s*x\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(userText)) return null

        if (AgentRecoveryGuidance.shouldAvoidModelLogMutation(userText)) return null

        val legacy = generateLegacyLogSetPatch(userText, targetDate)
        if (legacy != null) return legacy

        return if (allowModelFallback) generateModelLogSetPatches(userText, targetDate) else null
    }

    private fun recoverLogSetFailure(userText: String): AgentTurn.RecoverableFailure =
        AgentRecoveryGuidance.logSetFailure(
            originalText = userText,
            saveDate = today(),
            canTryModel = engine?.isReady == true
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
        return buildLogSetPatches(ex, extracted.date ?: defaultDate, extracted.sets)
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
                weightLbs    = set.weightLbs,
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
        val setId = mutationTarget(userText)?.setId ?: return null
        val (weight, reps) = extractWeightAndReps(userText) ?: Pair(null, null)
        if (weight == null && reps == null) return null
        return DbPatch.EditSet(setId = setId, weightLbs = weight, reps = reps)
    }

    private suspend fun generateDeleteSet(userText: String): DbPatch? {
        val setId = mutationTarget(userText)?.setId ?: return null
        return DbPatch.DeleteSet(setId = setId)
    }

    private suspend fun mutationTarget(userText: String): SetSummary? {
        val ex = extractAndFindExercise(userText) ?: return null
        val date = extractDate(userText, today())
        val setNumber = Regex("""(?i)\bset\s+(\d+)\b""").find(userText)?.groupValues?.get(1)?.toIntOrNull()
            ?: when {
                Regex("""(?i)\bfirst\b""").containsMatchIn(userText) -> 1
                Regex("""(?i)\bsecond\b""").containsMatchIn(userText) -> 2
                Regex("""(?i)\bthird\b""").containsMatchIn(userText) -> 3
                else -> null
            }
        if (date == null && setNumber == null) return tools.getRecentSets(ex.exerciseId, limit = 1).firstOrNull()
        val sets = tools.getSessionByDate(date ?: today()).exercises
            .firstOrNull { it.exerciseId == ex.exerciseId }?.sets.orEmpty()
        return if (setNumber != null) sets.firstOrNull { it.setNumber == setNumber } else sets.maxByOrNull { it.setNumber }
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
        val candidate = DateExtractor.withoutDateReferences(text, LocalDate.parse(today()))
            .replace(Regex("""(?i)\b(rename\s+.+?)\s+to\s+.+"""), "$1")
            .replace(Regex("""(?i)\b(?:set\s+\d+|sets?|first|second|third|extra|twice|accidental|should|use|weight)\b"""), " ")
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
            val b = setNotation.groupValues[2].toIntOrNull() ?: return null
            return if (a > 20) Pair(AgentWeightInput.parse(aRaw), b) else Pair(null, b)
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
                // nextOrSame: if today is that day, return today; else return next occurrence
                val currentIso = current.isoDayNumber   // Mon=1..Sun=7
                val targetIso = dow.isoDayNumber
                val daysAhead = when {
                    targetIso >= currentIso -> (targetIso - currentIso).toLong()
                    else -> (7 - currentIso + targetIso).toLong()
                }
                val target = current.plusDays(daysAhead)
                return (if (target == current) target.plusDays(7) else target).toString()
            }
        }
        if (t.contains("tomorrow"))  return current.plusDays(1).toString()
        if (t.contains("next week")) return current.plusDays(7).toString()
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
            return "No workout logged on ${humanDate(snapshot.date)}."
        return buildString {
            append("On ${humanDate(snapshot.date)}: ")
            snapshot.exercises.joinTo(this, " | ") { ex ->
                val top = ex.sets.maxByOrNull { it.weightLbs ?: 0 }
                "${ex.name} (${countLabel(ex.sets.size, "set")}, top ${top?.weightLbs?.let(WeightLbs::formatStored) ?: "BW"}×${top?.reps ?: 0})"
            }
        }
    }

    /** Compact progress summary suitable for direct display or model polishing. */
    private fun summarizeTrend(trend: ProgressTrend): String {
        if (trend.sessionCount == 0) return "No history for ${trend.name} yet."
        return buildString {
            append("${trend.name}: ${countLabel(trend.sessionCount, "session")}. ")
            if (trend.prWeightLbs != null) append("PR ${WeightLbs.formatStored(trend.prWeightLbs)} lb on ${humanDate(trend.prDate)}. ")
            if (trend.est1Rm != null)      append("Est. 1RM ${trend.est1Rm} lb. ")
            if (trend.deltaPercent != null) {
                val dir = if (trend.deltaPercent >= 0.5f) "up (growing)"
                else if (trend.deltaPercent <= -0.5f) "down (declining)"
                else "stable (plateau)"
                val sign = if (trend.deltaPercent >= 0) "+" else ""
                append("${sign}${formatFixed(trend.deltaPercent.toDouble(), 1)}% progress ($dir). ")
            }
            if (trend.recentSessions.isNotEmpty())
                append("Recent: ${trend.recentSessions.take(3).mapNotNull(::parseRecentSessionRow).joinToString(", ") { "${it.date} · ${it.setLabel}" }}")
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

    private fun humanDate(isoDate: String?): String {
        val date = isoDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return ""
        val currentYear = runCatching { LocalDate.parse(today()).year }.getOrDefault(todayLocalDate().year)
        val base = "${MONTH_ABBREV[date.monthNumber - 1]} ${date.dayOfMonth}"
        return if (date.year == currentYear) base else "$base, ${date.year}"
    }

    private fun humanizeIsoDatesInText(text: String): String =
        ISO_DATE.replace(text) { match -> humanDate(match.value) }

    private fun compactSetLabel(set: SetSummary): String {
        val load = if (set.isBodyweight) {
            set.weightLbs?.takeIf { it > 0 }?.let { "BW+${WeightLbs.formatStored(it)}" } ?: "BW"
        } else {
            set.weightLbs?.let(WeightLbs::formatStored) ?: "0"
        }
        return "$load×${set.reps ?: 0}"
    }

    private fun historyCard(name: String, history: HistorySummary): AgentResponseCard? {
        if (history.sessionCount == 0) return null
        val topSet = history.recentSets.maxWithOrNull(
            compareBy<SetSummary> { it.weightLbs ?: 0 }
                .thenBy { it.reps ?: 0 }
        )
        return AgentResponseCard.History(
            exerciseName = name,
            prLine = topSet?.let { "${compactSetLabel(it)} · ${humanDate(it.date)}" },
            sessions = history.recentSets
                .groupBy { it.date }
                .entries
                .sortedByDescending { it.key }
                .take(4)
                .mapNotNull { (date, sets) ->
                    val top = sets.maxWithOrNull(
                        compareBy<SetSummary> { it.weightLbs ?: 0 }
                            .thenBy { it.reps ?: 0 }
                    ) ?: return@mapNotNull null
                    HistoryRow(humanDate(date), compactSetLabel(top))
                }
        )
    }

    private fun trendHistoryCard(trend: ProgressTrend): AgentResponseCard? {
        if (trend.sessionCount == 0) return null
        val prSession = trend.prDate?.let { prDate ->
            trend.recentSessions
                .mapNotNull(::parseRecentSessionRowWithIso)
                .firstOrNull { it.first == prDate }
                ?.second
        }
        return AgentResponseCard.History(
            exerciseName = trend.name,
            prLine = trend.prWeightLbs?.let { weight ->
                "${prSession?.setLabel ?: WeightLbs.formatStored(weight)} · ${humanDate(trend.prDate)}"
            },
            sessions = trend.recentSessions.take(4).mapNotNull(::parseRecentSessionRow)
        )
    }

    private fun parseRecentSessionRow(raw: String): HistoryRow? {
        return parseRecentSessionRowWithIso(raw)?.second
    }

    private fun parseRecentSessionRowWithIso(raw: String): Pair<String, HistoryRow>? {
        val parts = raw.split(":", limit = 2)
        if (parts.size != 2) return null
        val setLabel = parts[1].trim().replace("x", "×")
        val isoDate = parts[0].trim()
        return isoDate to HistoryRow(humanDate(isoDate), setLabel)
    }

    private suspend fun patchChangeLabels(patches: List<DbPatch>, userText: String?): List<String> {
        val hintedExercise = userText?.let { extractAndFindExercise(it) }
        val hintedSets = hintedExercise?.takeIf {
            patches.any { patch -> patch is DbPatch.EditSet || patch is DbPatch.DeleteSet }
        }?.let { exercise ->
            val date = userText?.let { extractDate(it, today()) }
            if (date != null) {
                tools.getSessionByDate(date).exercises.firstOrNull { it.exerciseId == exercise.exerciseId }?.sets.orEmpty()
            } else {
                tools.getRecentSets(exercise.exerciseId, limit = 20)
            }
        }.orEmpty()
        return patches.map { patch ->
            val exerciseName = hintedExercise?.name ?: "Unknown lift"
            when (patch) {
                is DbPatch.LogSet -> {
                    val load = if (patch.isBodyweight) {
                        patch.weightLbs?.takeIf { it > 0 }?.let { "BW+${WeightLbs.formatStored(it)}" } ?: "BW"
                    } else {
                        patch.weightLbs?.let(WeightLbs::formatStored) ?: "0"
                    }
                    "$exerciseName · added $load×${patch.reps}"
                }
                is DbPatch.EditSet -> {
                    val before = hintedSets.firstOrNull { it.setId == patch.setId }
                    val setLabel = before?.setNumber?.let { "set $it" } ?: "set ${patch.setId}"
                    val detail = editPatchDetail(before, patch)
                    "$exerciseName · $setLabel: $detail"
                }
                is DbPatch.DeleteSet -> {
                    val setLabel = hintedSets.firstOrNull { it.setId == patch.setId }?.setNumber?.let { "set $it" } ?: "set ${patch.setId}"
                    "$exerciseName · deleted $setLabel"
                }
                is DbPatch.MoveWorkoutDay -> "Workout · moved ${humanDate(patch.currentDate)} → ${humanDate(patch.newDate)}"
                is DbPatch.RenameExercise -> "Exercise ${patch.exerciseId} · renamed to ${patch.newName}"
            }
        }
    }

    private fun patchExerciseId(patch: DbPatch): Long? = when (patch) {
        is DbPatch.LogSet -> patch.exerciseId
        is DbPatch.RenameExercise -> patch.exerciseId
        else -> null
    }

    private fun editPatchDetail(before: SetSummary?, patch: DbPatch.EditSet): String {
        val pieces = mutableListOf<String>()
        if (patch.weightLbs != null) {
            val from = before?.weightLbs?.let(WeightLbs::formatStored) ?: "?"
            pieces += "$from → ${WeightLbs.formatStored(patch.weightLbs)} lb"
        }
        if (patch.reps != null) {
            val from = before?.reps?.toString() ?: "?"
            pieces += "$from → ${countLabel(patch.reps, "rep")}"
        }
        return pieces.joinToString(" · ").ifBlank { "updated" }
    }

    private fun confirmText(patch: DbPatch): String = when (patch) {
        is DbPatch.LogSet         -> "Logged ${countLabel(patch.reps, "rep")}${if (patch.weightLbs != null) " at ${WeightLbs.formatStored(patch.weightLbs)} lb" else ""}."
        is DbPatch.EditSet        -> "Set updated."
        is DbPatch.DeleteSet      -> "Set deleted."
        is DbPatch.MoveWorkoutDay -> "Workout moved to ${humanDate(patch.newDate)}."
        is DbPatch.RenameExercise -> "Renamed to \"${patch.newName}\"."
    }

    private fun confirmText(patches: List<DbPatch>): String {
        if (patches.size == 1) return confirmText(patches.first())
        val logSets = patches.filterIsInstance<DbPatch.LogSet>()
        if (logSets.size == patches.size) return "Logged ${countLabel(logSets.size, "set")}."
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
            append("$name - ${countLabel(history.sessionCount, "session")} in the last ${history.windowDays} days. ")
            if (history.topSetWeightLbs != null)
                append("Best set: ${WeightLbs.formatStored(history.topSetWeightLbs)} lb${history.topSetReps?.let { " × ${countLabel(it, "rep")}" }.orEmpty()}.")
        }
    }

    private fun formatSuggestion(name: String, s: WeightSuggestion): String = when (s.confidence) {
        WeightSuggestion.Confidence.NO_DATA ->
            "No history for $name yet. Start light and work up."
        else -> {
            val w = s.suggestedWeightLbs?.let { "${WeightLbs.formatStored(it)} lb" } ?: "bodyweight"
            "$name: try $w for ${countLabel(s.suggestedReps ?: s.targetReps, "rep")}. ${s.reasoning}"
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
