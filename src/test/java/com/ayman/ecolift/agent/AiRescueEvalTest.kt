package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.patches.PatchApplier
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.router.Intent
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.tools.AgentTools
import com.ayman.ecolift.agent.tools.ExerciseMatch
import com.ayman.ecolift.agent.tools.ExerciseSnapshot
import com.ayman.ecolift.agent.tools.HistorySummary
import com.ayman.ecolift.agent.tools.ProgressTrend
import com.ayman.ecolift.agent.tools.SessionSnapshot
import com.ayman.ecolift.agent.tools.SetSummary
import com.ayman.ecolift.agent.tools.SimilarExercise
import com.ayman.ecolift.agent.tools.WeightSuggestion
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

class AiRescueEvalTest {

    @Test
    fun `ai rescue fixture is valid and focused on deterministic hard cases`() = runTest {
        val cases = loadCases()
        val router = IntentRouter(engine = null)

        assertTrue(cases.size in 20..25)
        assertEquals(cases.size, cases.map { it.id }.toSet().size)
        assertTrue(cases.count { it.requiresModel } >= 12)
        assertTrue(cases.any { it.category == "progress_analysis" })
        assertTrue(cases.any { it.requiresConfirmation })
        assertTrue(cases.any { it.target == "Clarify" && !it.expectedMutation })
        cases.filter { it.target == "LogSet" }.forEach { case ->
            assertTrue("${case.id} should define expected set count", case.expectedSetCount != null)
            assertTrue("${case.id} should define expected reps", case.expectedReps != null)
        }
        cases.filter { !it.expectedMutation }.forEach { case ->
            assertTrue("${case.id} should define a response length floor", case.minResponseChars > 0)
        }

        var deterministicFallbacks = 0
        for (case in cases.filter { it.requiresModel }) {
            val baseline = router.route(case.text, allowModelFallback = false)
            if (baseline.source == IntentRouter.RoutingResult.Source.FALLBACK) {
                deterministicFallbacks++
            }
        }

        assertTrue(
            "Expected the rescue fixture to stay focused on prompts deterministic routing does not already solve.",
            deterministicFallbacks >= 10
        )
    }

    @Test
    fun `scripted rescue eval spends one intent call and one extraction call for hard log`() = runTest {
        val case = loadCases()
            .single { it.id == "ai_006" }
            .copy(text = "bechh press one thirty five for seven")
        val summary = evaluateLive(cases = listOf(case), engine = ScriptedEvalEngine())

        assertEquals(1, summary.getInt("totalPrompts"))
        assertEquals(2, summary.getInt("modelCalls"))
        assertEquals(1, summary.getInt("rescuedFallbacks"))
        assertEquals(1.0, summary.getDouble("successRate"), 0.0)
        assertEquals(1.0, summary.getDouble("patchFieldAccuracy"), 0.0)
    }

    @Test
    fun `scripted rescue eval spaces case starts without sleeping after the final case`() = runTest {
        val case = loadCases()
            .single { it.id == "ai_006" }
            .copy(text = "bechh press one thirty five for seven")
        val delays = mutableListOf<Long>()

        val summary = evaluateLive(
            cases = listOf(case, case.copy(id = "ai_006_repeat")),
            engine = ScriptedEvalEngine(),
            caseDelayMs = 5_000L,
            delayBetweenCases = { delays += it }
        )

        assertEquals(2, summary.getInt("totalPrompts"))
        assertEquals(5_000L, summary.getLong("caseDelayMs"))
        assertEquals(listOf(5_000L), delays)
    }

    @Test
    fun `scripted rescue eval accepts plural calf alias from model extraction`() = runTest {
        val case = loadCases().single { it.id == "ai_001" }
        val summary = evaluateLive(
            cases = listOf(case),
            engine = ScriptedEvalEngine(
                logExtractionJson = """
                    {
                      "exerciseQuery": "calves",
                      "date": "2026-05-16",
                      "confidence": 0.92,
                      "sets": [
                        { "weightLbs": 90, "reps": 12 },
                        { "weightLbs": 90, "reps": 10 },
                        { "weightLbs": 90, "reps": 8 }
                      ]
                    }
                """.trimIndent()
            )
        )

        assertEquals(1.0, summary.getDouble("successRate"), 0.0)
        assertEquals(1.0, summary.getDouble("patchFieldAccuracy"), 0.0)
    }

    @Test
    fun `scripted rescue eval counts confirmation patches without applying them`() = runTest {
        val case = loadCases().single { it.id == "ai_014" }
        val summary = evaluateLive(cases = listOf(case), engine = ScriptedEvalEngine())

        assertEquals(1.0, summary.getDouble("successRate"), 0.0)
        assertEquals(1.0, summary.getDouble("destructiveConfirmationRate"), 0.0)
        assertEquals(1.0, summary.getDouble("destructiveNoSilentApplyRate"), 0.0)
    }

    @Test
    fun `scripted rescue eval writes durable transcript artifacts with raw model responses`() = runTest {
        val case = loadCases()
            .single { it.id == "ai_006" }
            .copy(text = "bechh press one thirty five for seven")
        val summary = evaluateLive(cases = listOf(case), engine = ScriptedEvalEngine())
        val runDir = writeSummary(summary, runId = "unit-${System.nanoTime()}")

        val summaryFile = File(runDir, "summary.json")
        val transcriptFile = File(runDir, "transcript.md")
        val casesFile = File(runDir, "cases.jsonl")

        assertTrue(summaryFile.exists())
        assertTrue(transcriptFile.exists())
        assertTrue(casesFile.exists())
        assertTrue(transcriptFile.readText().contains("Raw model response"))
        assertTrue(transcriptFile.readText().contains("Bench Press"))
        assertTrue(summary.getDouble("modelOutputParseRate") > 0.0)
        assertTrue(summary.getDouble("databaseMutationReadyRate") > 0.0)
    }

    @Test
    fun `api key resolver prefers eval specific key and falls back to groq key`() {
        assertEquals(
            "eval-key",
            OpenAiCompatibleEvalEngine.resolveApiKeyForTest(
                evalKey = "eval-key",
                groqKey = "groq-key",
                buildConfigKey = "build-key",
            )
        )
        assertEquals(
            "groq-key",
            OpenAiCompatibleEvalEngine.resolveApiKeyForTest(
                evalKey = "",
                groqKey = "groq-key",
                buildConfigKey = "build-key",
            )
        )
        assertEquals(
            "build-key",
            OpenAiCompatibleEvalEngine.resolveApiKeyForTest(
                evalKey = "",
                groqKey = "",
                buildConfigKey = "build-key",
            )
        )
    }

    @Test
    fun `live ai rescue eval writes report when explicitly enabled`() = runTest {
        assumeTrue(
            "Set AI_RESCUE_EVAL_ENABLED=true and AI_RESCUE_EVAL_API_KEY or GROQ_API_KEY to run live API rescue eval.",
            env("AI_RESCUE_EVAL_ENABLED").equals("true", ignoreCase = true)
        )

        val engine = OpenAiCompatibleEvalEngine.fromEnvironment()
        assumeTrue("AI_RESCUE_EVAL_API_KEY or GROQ_API_KEY is required for live API rescue eval.", engine.isReady)

        val maxCases = envInt("AI_RESCUE_EVAL_MAX_CASES", 24).coerceIn(1, 24)
        val caseDelayMs = envLong("AI_RESCUE_EVAL_CASE_DELAY_MS", 0L).coerceIn(0L, 60_000L)
        val summary = evaluateLive(
            cases = selectCasesForBudget(loadCases(), maxCases),
            engine = engine,
            caseDelayMs = caseDelayMs,
        )
        writeSummary(summary)

        assertEquals(0, summary.getInt("crashes"))
        assertEquals(0, summary.getInt("unsafeSilentMutationCount"))
        assertTrue(
            "Target agreement fell below the configured live AI eval floor.",
            summary.getDouble("targetAgreementRate") >= envDouble("AI_RESCUE_EVAL_MIN_TARGET_AGREEMENT", 0.65)
        )
        assertTrue(
            "Overall hard-case success fell below the configured live AI eval floor.",
            summary.getDouble("successRate") >= envDouble("AI_RESCUE_EVAL_MIN_SUCCESS_RATE", 0.55)
        )
        assertTrue(
            "Destructive requests must not be silently applied.",
            summary.getDouble("destructiveNoSilentApplyRate") == 1.0
        )
    }

    private suspend fun evaluateLive(
        cases: List<RescueCase>,
        engine: InstrumentedEvalEngine,
        caseDelayMs: Long = 0L,
        delayBetweenCases: suspend (Long) -> Unit = { delay(it) },
    ): JSONObject {
        val deterministicRouter = IntentRouter(engine = null)
        val modelRouter = IntentRouter(engine = engine)
        val tools = RescueEvalTools()

        var crashes = 0
        var baselineFallbacks = 0
        var rescuedFallbacks = 0
        var targetMatches = 0
        var mutationCases = 0
        var mutationResolved = 0
        var patchTypeMatches = 0
        var destructiveCases = 0
        var destructiveConfirmed = 0
        var destructiveNoSilentApply = 0
        var unsafeSilentMutation = 0
        var analysisCases = 0
        var analysisResponses = 0
        var successfulCases = 0
        var patchFieldCases = 0
        var patchFieldMatches = 0
        var responseCases = 0
        var responseMatches = 0
        var databaseReadyMutationCases = 0
        var databaseReadyMutations = 0
        var logMutationCases = 0
        var logReadyMutations = 0
        var editMutationCases = 0
        var editReadyMutations = 0
        var modelOutputEvents = 0
        var parseableModelOutputEvents = 0
        var intentOutputEvents = 0
        var parseableIntentOutputEvents = 0
        var logExtractionOutputEvents = 0
        var parseableLogExtractionOutputEvents = 0
        var totalLatencyMs = 0L

        val categoryCounts = linkedMapOf<String, Int>()
        val targetCounts = linkedMapOf<String, Int>()
        val baselineRouteCounts = linkedMapOf<String, Int>()
        val modelRouteCounts = linkedMapOf<String, Int>()
        val resultCounts = linkedMapOf<String, Int>()
        val caseResults = JSONArray()

        for ((index, case) in cases.withIndex()) {
            if (index > 0 && caseDelayMs > 0L) {
                delayBetweenCases(caseDelayMs)
            }

            categoryCounts.increment(case.category)
            targetCounts.increment(case.target)

            val started = System.currentTimeMillis()
            val patchApplier = CapturingPatchApplier()
            try {
                engine.beginCase()
                val baseline = deterministicRouter.route(case.text, allowModelFallback = false)
                baselineRouteCounts.increment(baseline.source.name)
                if (baseline.source == IntentRouter.RoutingResult.Source.FALLBACK) {
                    baselineFallbacks++
                }

                val turn = AgentOrchestrator(
                    router = modelRouter,
                    tools = tools,
                    patchApplier = patchApplier,
                    engine = engine,
                    today = { EVAL_TODAY },
                ).process(case.text, AgentProcessingOptions(allowModelFallback = true))

                val latencyMs = System.currentTimeMillis() - started
                totalLatencyMs += latencyMs
                val trace = engine.currentCaseTrace()
                for (event in trace.promptEvents) {
                    modelOutputEvents++
                    if (event.parseable) parseableModelOutputEvents++
                    when (event.kind) {
                        PROMPT_KIND_INTENT -> {
                            intentOutputEvents++
                            if (event.parseable) parseableIntentOutputEvents++
                        }
                        PROMPT_KIND_LOG_EXTRACTION -> {
                            logExtractionOutputEvents++
                            if (event.parseable) parseableLogExtractionOutputEvents++
                        }
                    }
                }
                val modelTarget = trace.intentTargets.lastOrNull()
                val modelRoute = inferModelRoute(baseline, trace)
                modelRouteCounts.increment(modelRoute)
                val observedPatches = when (turn) {
                    is AgentTurn.NeedsConfirmation -> turn.patches
                    else -> patchApplier.lastPatches
                }

                val actualTarget = turn.toTargetLabel(
                    baselineIntent = baseline.intent,
                    modelTarget = modelTarget,
                    patches = observedPatches
                )
                val resultLabel = turn.resultLabel()
                val targetMatch = compatibleTarget(case.target, actualTarget)
                if (targetMatch) targetMatches++

                val patchType = observedPatches.firstOrNull()?.toTargetLabel()
                val patchFields = evaluatePatchFields(case, observedPatches)
                if (patchFields.checked) {
                    patchFieldCases++
                    if (patchFields.matches) patchFieldMatches++
                }
                if (case.expectedMutation) {
                    mutationCases++
                    databaseReadyMutationCases++
                    if (turn is AgentTurn.Applied || turn is AgentTurn.NeedsConfirmation || turn is AgentTurn.ImportApplied) {
                        mutationResolved++
                    }
                    if (patchType == case.target) {
                        patchTypeMatches++
                    }
                    if (case.target == "LogSet") logMutationCases++
                    if (case.target == "EditSet") editMutationCases++
                } else if (turn is AgentTurn.Applied || turn is AgentTurn.ImportApplied || turn is AgentTurn.NeedsConfirmation) {
                    unsafeSilentMutation++
                }

                val responseUseful = responseLooksUseful(case, turn)
                if (!case.expectedMutation) {
                    responseCases++
                    if (responseUseful) responseMatches++
                }

                if (case.requiresConfirmation) {
                    destructiveCases++
                    if (turn is AgentTurn.NeedsConfirmation) {
                        destructiveConfirmed++
                        destructiveNoSilentApply++
                    } else if (turn !is AgentTurn.Applied && turn !is AgentTurn.ImportApplied) {
                        destructiveNoSilentApply++
                    }
                }

                if (case.category == "progress_analysis" || case.tags.contains("coach_analysis")) {
                    analysisCases++
                    if (responseUseful) {
                        analysisResponses++
                    }
                }

                val passed = casePassed(
                    case = case,
                    turn = turn,
                    targetMatch = targetMatch,
                    patchType = patchType,
                    patchFields = patchFields,
                    responseUseful = responseUseful,
                )
                if (passed) successfulCases++
                if (case.expectedMutation && mutationIsDatabaseReady(case, turn, patchType, patchFields)) {
                    databaseReadyMutations++
                    if (case.target == "LogSet") logReadyMutations++
                    if (case.target == "EditSet") editReadyMutations++
                }
                if (baseline.source == IntentRouter.RoutingResult.Source.FALLBACK && passed) {
                    rescuedFallbacks++
                }

                resultCounts.increment(resultLabel)
                caseResults.put(
                    case.toResultJson(
                        actualTarget = actualTarget,
                        baselineRoute = baseline.source.name,
                        modelRoute = modelRoute,
                        result = resultLabel,
                        patchType = patchType,
                        patchCount = observedPatches.size,
                        targetMatched = targetMatch,
                        patchFieldsMatched = patchFields.matches,
                        patchFieldFailure = patchFields.failureReason,
                        responseUseful = responseUseful,
                        passed = passed,
                        databaseReady = mutationIsDatabaseReady(case, turn, patchType, patchFields),
                        expected = case.expectedJson(),
                        actualPatches = patchesToJson(observedPatches),
                        promptEvents = trace.promptEvents,
                        promptKinds = trace.promptKinds,
                        intentTargets = trace.intentTargets,
                        latencyMs = latencyMs,
                        error = null,
                    )
                )
            } catch (e: Exception) {
                crashes++
                resultCounts.increment("Crash")
                caseResults.put(
                    case.toResultJson(
                        actualTarget = "Error",
                        baselineRoute = "UNKNOWN",
                        modelRoute = "UNKNOWN",
                        result = "Crash",
                        patchType = null,
                        patchCount = patchApplier.lastPatches.size,
                        targetMatched = false,
                        patchFieldsMatched = false,
                        patchFieldFailure = "crash",
                        responseUseful = false,
                        passed = false,
                        databaseReady = false,
                        expected = case.expectedJson(),
                        actualPatches = patchesToJson(patchApplier.lastPatches),
                        promptEvents = emptyList(),
                        promptKinds = emptyList(),
                        intentTargets = emptyList(),
                        latencyMs = System.currentTimeMillis() - started,
                        error = "${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                    )
                )
            }
        }

        return JSONObject()
            .put("evaluatorMode", "live_api_rescue")
            .put("promptBank", "src/test/resources/agent_eval/ironmind_ai_rescue_cases.jsonl")
            .put("providerBaseUrl", engine.redactedBaseUrl)
            .put("model", engine.model)
            .put("maxCompletionTokens", engine.maxCompletionTokens)
            .put("totalPrompts", cases.size)
            .put("modelCalls", engine.calls)
            .put("apiRequests", engine.apiRequests)
            .put("apiRetries", engine.retries)
            .put("apiRetryableFailures", engine.retryableFailures)
            .put("apiTerminalFailures", engine.terminalFailures)
            .put("crashes", crashes)
            .put("baselineFallbacks", baselineFallbacks)
            .put("rescuedFallbacks", rescuedFallbacks)
            .put("rescueRate", ratio(rescuedFallbacks, baselineFallbacks))
            .put("successfulCases", successfulCases)
            .put("successRate", ratio(successfulCases, cases.size))
            .put("targetAgreementRate", ratio(targetMatches, cases.size))
            .put("mutationResolutionRate", ratio(mutationResolved, mutationCases))
            .put("databaseReadyMutations", databaseReadyMutations)
            .put("databaseReadyMutationCases", databaseReadyMutationCases)
            .put("databaseMutationReadyRate", ratio(databaseReadyMutations, databaseReadyMutationCases))
            .put("logMutationReadyRate", ratio(logReadyMutations, logMutationCases))
            .put("editMutationReadyRate", ratio(editReadyMutations, editMutationCases))
            .put("patchTypeAccuracy", ratio(patchTypeMatches, mutationCases))
            .put("patchFieldAccuracy", ratio(patchFieldMatches, patchFieldCases))
            .put("patchFieldCases", patchFieldCases)
            .put("modelOutputEvents", modelOutputEvents)
            .put("parseableModelOutputEvents", parseableModelOutputEvents)
            .put("modelOutputParseRate", ratio(parseableModelOutputEvents, modelOutputEvents))
            .put("intentOutputParseRate", ratio(parseableIntentOutputEvents, intentOutputEvents))
            .put("logExtractionOutputParseRate", ratio(parseableLogExtractionOutputEvents, logExtractionOutputEvents))
            .put("responseUsabilityRate", ratio(responseMatches, responseCases))
            .put("destructiveConfirmationRate", ratio(destructiveConfirmed, destructiveCases))
            .put("destructiveNoSilentApplyRate", ratio(destructiveNoSilentApply, destructiveCases))
            .put("unsafeSilentMutationCount", unsafeSilentMutation)
            .put("analysisResponseRate", ratio(analysisResponses, analysisCases))
            .put("averageLatencyMs", ratioLong(totalLatencyMs, cases.size))
            .put("averageModelCallsPerCase", ratio(engine.calls, cases.size))
            .put("caseSelection", "category_round_robin")
            .put("caseDelayMs", caseDelayMs)
            .put("categoryCounts", JSONObject(categoryCounts))
            .put("targetCounts", JSONObject(targetCounts))
            .put("baselineRouteCounts", JSONObject(baselineRouteCounts))
            .put("modelRouteCounts", JSONObject(modelRouteCounts))
            .put("resultCounts", JSONObject(resultCounts))
            .put("caseResults", caseResults)
    }

    private fun loadCases(): List<RescueCase> {
        val stream = javaClass.classLoader
            ?.getResourceAsStream("agent_eval/ironmind_ai_rescue_cases.jsonl")
            ?: error("Missing AI rescue eval fixture")
        return stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { line ->
                val root = JSONObject(line)
                val tags = root.getJSONArray("tags").let { arr ->
                    List(arr.length()) { index -> arr.getString(index) }
                }
                RescueCase(
                    id = root.getString("id"),
                    text = root.getString("text"),
                    category = root.getString("category"),
                    tags = tags,
                    target = root.getString("target"),
                    expectedMutation = root.getBoolean("expectedMutation"),
                    requiresConfirmation = root.getBoolean("requiresConfirmation"),
                    requiresModel = root.getBoolean("requiresModel"),
                    expectedExercise = root.optString("expectedExercise").ifBlank { null },
                    expectedDate = root.optString("expectedDate").ifBlank { null },
                    expectedSetCount = if (root.has("expectedSetCount")) root.getInt("expectedSetCount") else null,
                    expectedWeightsLbs = root.nullableIntListOrNull("expectedWeightsLbs"),
                    expectedReps = root.intListOrNull("expectedReps"),
                    minResponseChars = if (root.has("minResponseChars")) root.getInt("minResponseChars") else 0,
                )
            }.toList()
        }
    }

    private fun selectCasesForBudget(cases: List<RescueCase>, maxCases: Int): List<RescueCase> {
        val queues = CATEGORY_ORDER.map { category ->
            cases.filter { it.category == category }.toMutableList()
        }.filter { it.isNotEmpty() }
        val selected = mutableListOf<RescueCase>()
        while (selected.size < maxCases && queues.any { it.isNotEmpty() }) {
            for (queue in queues) {
                if (selected.size >= maxCases) break
                if (queue.isNotEmpty()) selected += queue.removeAt(0)
            }
        }
        return selected
    }

    private fun JSONObject.intListOrNull(name: String): List<Int>? {
        if (!has(name) || isNull(name)) return null
        val arr = getJSONArray(name)
        return List(arr.length()) { index -> arr.getInt(index) }
    }

    private fun JSONObject.nullableIntListOrNull(name: String): List<Int?>? {
        if (!has(name) || isNull(name)) return null
        val arr = getJSONArray(name)
        return List(arr.length()) { index ->
            if (arr.isNull(index)) null else arr.getInt(index)
        }
    }

    private fun writeSummary(
        summary: JSONObject,
        runId: String = "ai-rescue-${Instant.now().toString().replace(":", "").replace("-", "").replace(".", "")}"
    ): File {
        val root = File("build/reports/agent-eval").apply { mkdirs() }
        val runDir = File(root, "live/$runId").apply { mkdirs() }
        summary.put("runId", runId)
        summary.put("reportDir", runDir.invariantSeparatorsPath)

        File(root, "ai-rescue-summary.json").writeText(summary.toString(2))
        File(runDir, "summary.json").writeText(summary.toString(2))
        File(runDir, "cases.jsonl").writeText(
            summary.getJSONArray("caseResults").let { cases ->
                (0 until cases.length()).joinToString(separator = "\n", postfix = "\n") { index ->
                    cases.getJSONObject(index).toString()
                }
            }
        )
        File(runDir, "transcript.md").writeText(renderTranscript(summary))
        return runDir
    }

    private fun renderTranscript(summary: JSONObject): String = buildString {
        appendLine("# AI Rescue Eval Transcript")
        appendLine()
        appendLine("- Run ID: `${summary.optString("runId")}`")
        appendLine("- Model: `${summary.optString("model")}`")
        appendLine("- Provider: `${summary.optString("providerBaseUrl")}`")
        appendLine("- Max completion tokens: ${summary.optInt("maxCompletionTokens")}")
        appendLine("- Total cases: ${summary.optInt("totalPrompts")}")
        appendLine("- Success rate: ${formatRate(summary.optDouble("successRate"))}")
        appendLine("- Database mutation ready rate: ${formatRate(summary.optDouble("databaseMutationReadyRate"))}")
        appendLine("- Model output parse rate: ${formatRate(summary.optDouble("modelOutputParseRate"))}")
        appendLine("- Unsafe silent mutations: ${summary.optInt("unsafeSilentMutationCount")}")
        appendLine()
        val cases = summary.getJSONArray("caseResults")
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            appendLine("## ${case.optString("id")} ${if (case.optBoolean("passed")) "PASS" else "FAIL"}")
            appendLine()
            appendLine("- Category: `${case.optString("category")}`")
            appendLine("- Expected target: `${case.optString("target")}`")
            appendLine("- Actual target: `${case.optString("actualTarget")}`")
            appendLine("- Result: `${case.optString("result")}`")
            appendLine("- Database ready: `${case.optBoolean("databaseReady")}`")
            appendLine("- Patch fields matched: `${case.optBoolean("patchFieldsMatched")}`")
            case.optString("patchFieldFailure").takeIf { it.isNotBlank() && it != "null" }?.let {
                appendLine("- Patch failure: $it")
            }
            appendLine()
            appendLine("Expected:")
            appendLine("````json")
            appendLine(case.optJSONObject("expected")?.toString(2) ?: "{}")
            appendLine("````")
            appendLine()
            appendLine("Actual patches:")
            appendLine("````json")
            appendLine(case.optJSONArray("actualPatches")?.toString(2) ?: "[]")
            appendLine("````")
            appendLine()
            val promptEvents = case.optJSONArray("promptEvents") ?: JSONArray()
            if (promptEvents.length() == 0) {
                appendLine("Model responses: none")
                appendLine()
            } else {
                for (eventIndex in 0 until promptEvents.length()) {
                    val event = promptEvents.getJSONObject(eventIndex)
                    appendLine("Prompt ${eventIndex + 1}: `${event.optString("kind")}`")
                    appendLine()
                    appendLine("- Parseable: `${event.optBoolean("parseable")}`")
                    event.optString("parsedTarget").takeIf { it.isNotBlank() && it != "null" }?.let {
                        appendLine("- Parsed target: `$it`")
                    }
                    appendLine()
                    appendLine("Raw model response:")
                    appendLine("````")
                    appendLine(event.optString("rawResponse"))
                    appendLine("````")
                    appendLine()
                }
            }
        }
    }

    private fun AgentTurn.toTargetLabel(
        baselineIntent: Intent,
        modelTarget: String?,
        patches: List<DbPatch>,
    ): String =
        patches.firstOrNull()?.toTargetLabel() ?: when (this) {
            is AgentTurn.Applied -> modelTarget ?: baselineIntent.toTargetLabel()
            is AgentTurn.ImportApplied -> "BulkImport"
            is AgentTurn.NeedsConfirmation -> patches.firstOrNull()?.toTargetLabel() ?: modelTarget ?: baselineIntent.toTargetLabel()
            is AgentTurn.RecoverableFailure -> "Clarify"
            is AgentTurn.TextResponse -> modelTarget ?: baselineIntent.toTargetLabel()
            is AgentTurn.Error -> "Error"
        }

    private fun inferModelRoute(
        baseline: IntentRouter.RoutingResult,
        trace: CaseTrace,
    ): String = when {
        baseline.source == IntentRouter.RoutingResult.Source.RULE -> "RULE"
        trace.intentTargets.lastOrNull()?.let { it != "Clarify" } == true -> "MODEL"
        else -> "FALLBACK"
    }

    private fun casePassed(
        case: RescueCase,
        turn: AgentTurn,
        targetMatch: Boolean,
        patchType: String?,
        patchFields: PatchFieldEvaluation,
        responseUseful: Boolean,
    ): Boolean {
        val mutatingTurn = turn is AgentTurn.Applied ||
            turn is AgentTurn.ImportApplied ||
            turn is AgentTurn.NeedsConfirmation

        if (!case.expectedMutation) {
            return targetMatch && !mutatingTurn && responseUseful
        }

        if (!targetMatch || patchType != case.target) return false
        if (case.requiresConfirmation) return turn is AgentTurn.NeedsConfirmation
        return turn is AgentTurn.Applied && (!patchFields.checked || patchFields.matches)
    }

    private fun mutationIsDatabaseReady(
        case: RescueCase,
        turn: AgentTurn,
        patchType: String?,
        patchFields: PatchFieldEvaluation,
    ): Boolean {
        if (!case.expectedMutation) return false
        if (patchType != case.target) return false
        if (case.requiresConfirmation) {
            return turn is AgentTurn.NeedsConfirmation
        }
        return turn is AgentTurn.Applied && (!patchFields.checked || patchFields.matches)
    }

    private fun responseLooksUseful(case: RescueCase, turn: AgentTurn): Boolean {
        if (case.expectedMutation) return true
        val text = when (turn) {
            is AgentTurn.TextResponse -> turn.text
            is AgentTurn.RecoverableFailure -> "${turn.title} ${turn.detail}"
            else -> ""
        }.trim()
        if (text.length < case.minResponseChars) return false
        val lower = text.lowercase()
        if (GENERIC_UNRESOLVED.any { lower.contains(it) }) return false
        if (case.target == "Clarify") {
            return CLARIFY_TERMS.any { lower.contains(it) }
        }
        return true
    }

    private fun evaluatePatchFields(case: RescueCase, patches: List<DbPatch>): PatchFieldEvaluation {
        if (!case.hasExpectedPatchFields) return PatchFieldEvaluation(checked = false, matches = true)
        return when (case.target) {
            "LogSet" -> evaluateLogSetFields(case, patches.filterIsInstance<DbPatch.LogSet>())
            "EditSet" -> evaluateEditSetFields(case, patches.filterIsInstance<DbPatch.EditSet>())
            else -> PatchFieldEvaluation(checked = false, matches = true)
        }
    }

    private fun evaluateLogSetFields(case: RescueCase, patches: List<DbPatch.LogSet>): PatchFieldEvaluation {
        val expectedCount = case.expectedSetCount
        if (expectedCount != null && patches.size != expectedCount) {
            return PatchFieldEvaluation(true, false, "expected $expectedCount log patches but got ${patches.size}")
        }
        case.expectedExercise?.let { expected ->
            val expectedId = exerciseIdFor(expected)
            if (patches.any { it.exerciseId != expectedId }) {
                return PatchFieldEvaluation(true, false, "exercise mismatch for $expected")
            }
        }
        case.expectedDate?.let { expectedDate ->
            if (patches.any { it.date != expectedDate }) {
                return PatchFieldEvaluation(true, false, "date mismatch, expected $expectedDate")
            }
        }
        case.expectedReps?.let { expected ->
            val actual = patches.map { it.reps }
            if (actual != expected) return PatchFieldEvaluation(true, false, "reps mismatch, expected $expected got $actual")
        }
        case.expectedWeightsLbs?.let { expected ->
            val expectedStored = expected.map { it?.let(WeightLbs::fromWholePounds) }
            val actual = patches.map { it.weightLbs }
            if (actual != expectedStored) {
                return PatchFieldEvaluation(true, false, "weights mismatch, expected $expected got $actual")
            }
        }
        return PatchFieldEvaluation(true, true)
    }

    private fun evaluateEditSetFields(case: RescueCase, patches: List<DbPatch.EditSet>): PatchFieldEvaluation {
        val expectedCount = case.expectedSetCount
        if (expectedCount != null && patches.size != expectedCount) {
            return PatchFieldEvaluation(true, false, "expected $expectedCount edit patches but got ${patches.size}")
        }
        val patch = patches.firstOrNull()
            ?: return PatchFieldEvaluation(true, false, "missing edit patch")
        case.expectedReps?.firstOrNull()?.let { expected ->
            if (patch.reps != expected) return PatchFieldEvaluation(true, false, "edit reps mismatch")
        }
        case.expectedWeightsLbs?.firstOrNull()?.let { expected ->
            val expectedStored = WeightLbs.fromWholePounds(expected)
            if (patch.weightLbs != expectedStored) return PatchFieldEvaluation(true, false, "edit weight mismatch")
        }
        return PatchFieldEvaluation(true, true)
    }

    private fun patchesToJson(patches: List<DbPatch>): JSONArray =
        JSONArray().apply {
            patches.forEach { patch ->
                put(
                    when (patch) {
                        is DbPatch.LogSet -> JSONObject()
                            .put("type", "LogSet")
                            .put("exerciseId", patch.exerciseId)
                            .put("date", patch.date)
                            .put("setNumber", patch.setNumber)
                            .put("weightLbs", patch.weightLbs?.let(WeightLbs::toLbs))
                            .put("reps", patch.reps)
                            .put("isBodyweight", patch.isBodyweight)
                        is DbPatch.EditSet -> JSONObject()
                            .put("type", "EditSet")
                            .put("setId", patch.setId)
                            .put("weightLbs", patch.weightLbs?.let(WeightLbs::toLbs))
                            .put("reps", patch.reps)
                            .put("restTimeSeconds", patch.restTimeSeconds)
                        is DbPatch.DeleteSet -> JSONObject()
                            .put("type", "DeleteSet")
                            .put("setId", patch.setId)
                        is DbPatch.MoveWorkoutDay -> JSONObject()
                            .put("type", "MoveWorkoutDay")
                            .put("currentDate", patch.currentDate)
                            .put("newDate", patch.newDate)
                        is DbPatch.RenameExercise -> JSONObject()
                            .put("type", "RenameExercise")
                            .put("exerciseId", patch.exerciseId)
                            .put("newName", patch.newName)
                    }
                )
            }
        }

    private fun AgentTurn.resultLabel(): String = when (this) {
        is AgentTurn.Applied -> "Applied"
        is AgentTurn.ImportApplied -> "ImportApplied"
        is AgentTurn.NeedsConfirmation -> "NeedsConfirmation"
        is AgentTurn.RecoverableFailure -> "RecoverableFailure"
        is AgentTurn.TextResponse -> "TextResponse"
        is AgentTurn.Error -> "Error"
    }

    private fun Intent.toTargetLabel(): String = when (this) {
        is Intent.Write -> patchType.name
        is Intent.Read -> queryType.name
        is Intent.Clarify -> "Clarify"
    }

    private fun DbPatch.toTargetLabel(): String = when (this) {
        is DbPatch.LogSet -> "LogSet"
        is DbPatch.EditSet -> "EditSet"
        is DbPatch.DeleteSet -> "DeleteSet"
        is DbPatch.MoveWorkoutDay -> "MoveWorkoutDay"
        is DbPatch.RenameExercise -> "RenameExercise"
    }

    private fun compatibleTarget(expected: String, actual: String): Boolean =
        expected == actual || (expected == "Clarify" && actual == "Clarify")

    private fun MutableMap<String, Int>.increment(key: String) {
        this[key] = (this[key] ?: 0) + 1
    }

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 1.0 else numerator.toDouble() / denominator.toDouble()

    private fun ratioLong(numerator: Long, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator.toDouble()

    private fun formatRate(value: Double): String =
        "${"%.1f".format(value * 100.0)}%"

    private data class RescueCase(
        val id: String,
        val text: String,
        val category: String,
        val tags: List<String>,
        val target: String,
        val expectedMutation: Boolean,
        val requiresConfirmation: Boolean,
        val requiresModel: Boolean,
        val expectedExercise: String?,
        val expectedDate: String?,
        val expectedSetCount: Int?,
        val expectedWeightsLbs: List<Int?>?,
        val expectedReps: List<Int>?,
        val minResponseChars: Int,
    ) {
        val hasExpectedPatchFields: Boolean
            get() = expectedSetCount != null ||
                expectedWeightsLbs != null ||
                expectedReps != null ||
                expectedDate != null ||
                (target == "LogSet" && expectedExercise != null)

        fun toResultJson(
            actualTarget: String,
            baselineRoute: String,
            modelRoute: String,
            result: String,
            patchType: String?,
            patchCount: Int,
            targetMatched: Boolean,
            patchFieldsMatched: Boolean,
            patchFieldFailure: String?,
            responseUseful: Boolean,
            passed: Boolean,
            databaseReady: Boolean,
            expected: JSONObject,
            actualPatches: JSONArray,
            promptEvents: List<PromptTrace>,
            promptKinds: List<String>,
            intentTargets: List<String>,
            latencyMs: Long,
            error: String?,
        ): JSONObject =
            JSONObject()
                .put("id", id)
                .put("category", category)
                .put("target", target)
                .put("actualTarget", actualTarget)
                .put("baselineRoute", baselineRoute)
                .put("modelRoute", modelRoute)
                .put("result", result)
                .put("patchType", patchType)
                .put("patchCount", patchCount)
                .put("targetMatched", targetMatched)
                .put("patchFieldsMatched", patchFieldsMatched)
                .put("patchFieldFailure", patchFieldFailure)
                .put("responseUseful", responseUseful)
                .put("passed", passed)
                .put("databaseReady", databaseReady)
                .put("expected", expected)
                .put("actualPatches", actualPatches)
                .put("promptEvents", promptEventsToJsonArray(promptEvents))
                .put("promptKinds", JSONArray(promptKinds))
                .put("intentTargets", JSONArray(intentTargets))
                .put("latencyMs", latencyMs)
                .put("error", error)

        fun expectedJson(): JSONObject =
            JSONObject()
                .put("target", target)
                .put("expectedMutation", expectedMutation)
                .put("requiresConfirmation", requiresConfirmation)
                .put("exercise", expectedExercise)
                .put("date", expectedDate)
                .put("setCount", expectedSetCount)
                .put("weightsLbs", expectedWeightsLbs?.let { JSONArray(it) })
                .put("reps", expectedReps?.let { JSONArray(it) })
                .put("minResponseChars", minResponseChars)

        private fun promptEventsToJsonArray(promptEvents: List<PromptTrace>): JSONArray =
            JSONArray().apply {
                promptEvents.forEach { event ->
                    put(
                        JSONObject()
                            .put("kind", event.kind)
                            .put("parseable", event.parseable)
                            .put("parsedTarget", event.parsedTarget)
                            .put("rawResponse", event.rawResponse)
                    )
                }
            }
    }

    private data class PatchFieldEvaluation(
        val checked: Boolean,
        val matches: Boolean,
        val failureReason: String? = null,
    )

    private data class PromptTrace(
        val kind: String,
        val rawResponse: String,
        val parsedTarget: String?,
        val parseable: Boolean,
    )

    private data class CaseTrace(
        val promptEvents: List<PromptTrace>,
    ) {
        val promptKinds: List<String>
            get() = promptEvents.map { it.kind }

        val intentTargets: List<String>
            get() = promptEvents.mapNotNull { it.parsedTarget }
    }

    private interface InstrumentedEvalEngine : LocalGenAiEngine {
        val calls: Int
        val apiRequests: Int
        val retries: Int
        val retryableFailures: Int
        val terminalFailures: Int
        val redactedBaseUrl: String
        val model: String
        val maxCompletionTokens: Int

        fun beginCase()
        fun currentCaseTrace(): CaseTrace
    }

    private class CapturingPatchApplier : PatchApplier {
        var lastPatches: List<DbPatch> = emptyList()
            private set

        override suspend fun applyPatches(
            requestId: String,
            patches: List<DbPatch>,
            userConfirmed: Boolean,
        ): PatchResult {
            lastPatches = patches
            return PatchResult.Applied(auditId = 1L, patchCount = patches.size)
        }

        override suspend fun undo(auditId: Long): PatchResult =
            PatchResult.Applied(auditId = 2L, patchCount = 1)
    }

    private class ScriptedEvalEngine(
        private val intentTarget: String = "LogSet",
        private val logExtractionJson: String = DEFAULT_LOG_EXTRACTION_JSON,
        private val readFormattingText: String =
            "Bench Press is trending up. Keep the next session conservative and add load only if reps stay clean.",
    ) : InstrumentedEvalEngine {
        override val isReady: Boolean = true
        override val redactedBaseUrl: String = "scripted://local"
        override val model: String = "scripted-rescue-engine"
        override val maxCompletionTokens: Int = 0
        override var calls: Int = 0
            private set
        override var apiRequests: Int = 0
            private set
        override var retries: Int = 0
            private set
        override var retryableFailures: Int = 0
            private set
        override var terminalFailures: Int = 0
            private set

        private val casePromptEvents = mutableListOf<PromptTrace>()

        override suspend fun warmup() = Unit

        override fun streamText(prompt: String): Flow<String> = flowOf()

        override suspend fun generateStructured(prompt: String, schema: String): String {
            calls++
            val kind = promptKind(prompt)
            val response = when (kind) {
                PROMPT_KIND_INTENT -> {
                    intentTarget
                }
                PROMPT_KIND_LOG_EXTRACTION -> logExtractionJson
                else -> readFormattingText
            }
            casePromptEvents += PromptTrace(
                kind = kind,
                rawResponse = response,
                parsedTarget = if (kind == PROMPT_KIND_INTENT) parseIntentTarget(response) else null,
                parseable = responseParseable(kind, response),
            )
            return response
        }

        override fun beginCase() {
            casePromptEvents.clear()
        }

        override fun currentCaseTrace(): CaseTrace =
            CaseTrace(casePromptEvents.toList())

        override fun close() = Unit

        private companion object {
            const val DEFAULT_LOG_EXTRACTION_JSON = """
                {
                  "exerciseQuery": "Bench Press",
                  "date": "2026-05-17",
                  "confidence": 0.95,
                  "sets": [
                    { "weightLbs": 135, "reps": 7 }
                  ]
                }
            """
        }
    }

    private class OpenAiCompatibleEvalEngine(
        private val apiKey: String,
        private val baseUrl: String,
        override val model: String,
        override val maxCompletionTokens: Int,
        private val maxRetries: Int,
        private val initialBackoffMs: Long,
    ) : InstrumentedEvalEngine {
        override val isReady: Boolean get() = apiKey.isNotBlank()

        override var calls: Int = 0
            private set
        override var apiRequests: Int = 0
            private set
        override var retries: Int = 0
            private set
        override var retryableFailures: Int = 0
            private set
        override var terminalFailures: Int = 0
            private set

        private val casePromptEvents = mutableListOf<PromptTrace>()

        override val redactedBaseUrl: String get() = baseUrl.trimEnd('/')

        override suspend fun warmup() = Unit

        override fun streamText(prompt: String): Flow<String> = flowOf()

        override suspend fun generateStructured(prompt: String, schema: String): String = withContext(Dispatchers.IO) {
            val kind = promptKind(prompt)
            calls++
            var attempt = 0
            while (true) {
                try {
                    apiRequests++
                    val response = postChatCompletion(prompt, schema)
                    casePromptEvents += PromptTrace(
                        kind = kind,
                        rawResponse = response,
                        parsedTarget = if (kind == PROMPT_KIND_INTENT) parseIntentTarget(response) else null,
                        parseable = responseParseable(kind, response),
                    )
                    return@withContext response
                } catch (e: EvalApiException) {
                    if (!e.retryable || attempt >= maxRetries) {
                        terminalFailures++
                        throw e
                    }
                    retryableFailures++
                    retries++
                    delay(e.retryAfterMs ?: backoffMs(attempt))
                    attempt++
                }
            }
            error("Unreachable API retry state.")
        }

        override fun beginCase() {
            casePromptEvents.clear()
        }

        override fun currentCaseTrace(): CaseTrace =
            CaseTrace(casePromptEvents.toList())

        private fun postChatCompletion(prompt: String, schema: String): String {
            val connection = (URL("${baseUrl.trimEnd('/')}/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { stream ->
                    stream.write(buildRequest(prompt, schema).toByteArray(Charsets.UTF_8))
                }
            }

            return try {
                val status = connection.responseCode
                val retryAfter = connection.getHeaderField("Retry-After")?.let(::parseRetryAfterMs)
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    throw EvalApiException(
                        statusCode = status,
                        message = extractErrorMessage(response),
                        retryAfterMs = retryAfter,
                    )
                }
                JSONObject(response)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content")
                    .trim()
            } finally {
                connection.disconnect()
            }
        }

        private fun buildRequest(prompt: String, schema: String): String {
            val fullPrompt = if (schema.isBlank() || schema.trim() == "{}" || prompt.contains(schema.trim())) {
                prompt
            } else {
                "$prompt\n\nExpected output schema:\n$schema"
            }
            return JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                        .put(JSONObject().put("role", "user").put("content", fullPrompt))
                )
                .put("temperature", 0.1)
                .put("max_completion_tokens", maxCompletionTokens)
                .apply {
                    if (prompt.contains("Output ONLY valid JSON", ignoreCase = true)) {
                        put("response_format", JSONObject().put("type", "json_object"))
                    }
                }
                .toString()
        }

        private fun backoffMs(attempt: Int): Long =
            min(initialBackoffMs * (1L shl attempt.coerceAtMost(4)), 15_000L)

        private fun parseRetryAfterMs(value: String): Long? {
            value.toLongOrNull()?.let { return it * 1_000L }
            return runCatching {
                val retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
                val now = java.time.Instant.now()
                (retryAt.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0L)
            }.getOrNull()
        }

        private fun extractErrorMessage(responseBody: String): String =
            runCatching {
                JSONObject(responseBody)
                    .optJSONObject("error")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull() ?: responseBody.ifBlank { "No response body." }

        override fun close() = Unit

        companion object {
            fun fromEnvironment(): OpenAiCompatibleEvalEngine =
                OpenAiCompatibleEvalEngine(
                    apiKey = resolveApiKey(
                        evalKey = envValue("AI_RESCUE_EVAL_API_KEY"),
                        groqKey = envValue("GROQ_API_KEY"),
                        buildConfigKey = BuildConfig.GROQ_API_KEY,
                    ),
                    baseUrl = envValue("AI_RESCUE_EVAL_BASE_URL").ifBlank { "https://api.groq.com/openai/v1" },
                    model = envValue("AI_RESCUE_EVAL_MODEL").ifBlank { "llama-3.3-70b-versatile" },
                    maxCompletionTokens = envValue("AI_RESCUE_EVAL_MAX_TOKENS").toIntOrNull()?.coerceIn(64, 700)
                        ?: 300,
                    maxRetries = envValue("AI_RESCUE_EVAL_MAX_RETRIES").toIntOrNull()?.coerceIn(0, 6) ?: 3,
                    initialBackoffMs = (envValue("AI_RESCUE_EVAL_INITIAL_BACKOFF_MS").toLongOrNull() ?: 1_500L)
                        .coerceIn(250L, 30_000L),
                )

            fun resolveApiKeyForTest(
                evalKey: String,
                groqKey: String,
                buildConfigKey: String,
            ): String = resolveApiKey(evalKey, groqKey, buildConfigKey)

            private fun resolveApiKey(evalKey: String, groqKey: String, buildConfigKey: String): String =
                evalKey.ifBlank { groqKey.ifBlank { buildConfigKey } }

            private fun envValue(name: String): String = System.getenv(name).orEmpty()

            private const val SYSTEM_PROMPT = """
                You are IronMind, a careful workout coach and log editor inside a local-first Android app.
                Rescue only the user requests that deterministic parsing cannot safely handle.
                For logging, extract exact exercise, date, weight, and reps when they are present.
                Correct obvious exercise spelling mistakes, convert clear spoken numbers, and preserve user intent.
                Do not invent missing data. If the exercise or numbers are unclear, ask for clarification.
                For progress questions, analyze like a coach: explain the trend, likely training meaning, and one practical next step.
                Never shame the user, give medical diagnoses, or silently approve destructive changes.
            """
        }
    }

    private class EvalApiException(
        val statusCode: Int,
        override val message: String,
        val retryAfterMs: Long?,
    ) : RuntimeException("API request failed ($statusCode): $message") {
        val retryable: Boolean = statusCode == 408 || statusCode == 429 || statusCode in 500..599
    }

    private class RescueEvalTools : AgentTools {
        override suspend fun findExercise(fuzzyName: String): ExerciseMatch? {
            val t = fuzzyName.lowercase()
            val name = when {
                "thing" in t || "idk" in t -> null
                "incline" in t && ("db" in t || "dumbbell" in t) -> "Incline Dumbbell Press"
                "incline" in t && "bench" in t -> "Incline Bench Press"
                "bech" in t || "bench" in t -> "Bench Press"
                "squat" in t -> "Back Squat"
                "deadlift" in t -> "Deadlift"
                "ohp" in t || "overhead" in t || "shoulder press" in t -> "Overhead Press"
                "barbell row" in t -> "Barbell Row"
                "row machine" in t || "row machien" in t || "seated row" in t -> "Row Machine"
                "row" in t -> "Barbell Row"
                "pull up" in t || "pullup" in t || "pull ups" in t || "pullups" in t -> "Pull Up"
                "curl" in t -> "Dumbbell Curl"
                "leg press" in t -> "Leg Press"
                "leg extension" in t || "one legged extension" in t -> "Leg Extension"
                "pulldwn" in t || "pulldown" in t -> "Lat Pulldown"
                "pushdown" in t || "pushdowns" in t -> "Triceps Pushdown"
                "skull" in t -> "Skull Crusher"
                "calf" in t || "calves" in t -> "Standing Calf Raise Machine"
                "hip abdction" in t || "hip abduction" in t -> "Hip Abduction"
                "hip adduction" in t -> "Hip Adduction"
                "lateral" in t || "lat raise" in t -> "Lateral Raise Machine"
                "dip" in t -> "Dip Machine Weighted"
                "rear tether" in t || "rear delt" in t -> "Rear Tether"
                "rdl" in t -> "Romanian Deadlift"
                "cable fly" in t -> "Cable Fly"
                "chest press" in t || "converging" in t -> "Chest Press Machine"
                else -> null
            } ?: return null
            return ExerciseMatch(exerciseIdFor(name), name, isBodyweight = name == "Pull Up", score = 0.0)
        }

        override suspend fun getRecentSets(exerciseId: Long, limit: Int): List<SetSummary> =
            listOf(
                SetSummary(91L, "2026-05-16", 1, WeightLbs.fromWholePounds(135), 8, false),
                SetSummary(92L, "2026-05-10", 2, WeightLbs.fromWholePounds(185), 5, false),
            ).take(limit)

        override suspend fun getExerciseHistory(exerciseId: Long, windowDays: Int): HistorySummary =
            HistorySummary(
                exerciseId = exerciseId,
                windowDays = windowDays,
                sessionCount = 6,
                topSetWeightLbs = WeightLbs.fromWholePounds(225),
                topSetReps = 5,
                recentSets = listOf(
                    SetSummary(81L, "2026-05-15", 1, WeightLbs.fromWholePounds(225), 5, false),
                    SetSummary(82L, "2026-05-08", 1, WeightLbs.fromWholePounds(225), 4, false),
                    SetSummary(83L, "2026-05-01", 1, WeightLbs.fromWholePounds(215), 5, false),
                ),
            )

        override suspend fun getSimilarExercises(exerciseId: Long, k: Int): List<SimilarExercise> =
            listOf(
                SimilarExercise(2L, "Chest Supported Row", 0.92, "HorizontalPull"),
                SimilarExercise(3L, "Seated Cable Row", 0.88, "HorizontalPull"),
            ).take(k)

        override suspend fun suggestWeight(exerciseId: Long, targetReps: Int): WeightSuggestion =
            WeightSuggestion(
                exerciseId = exerciseId,
                targetReps = targetReps,
                suggestedWeightLbs = WeightLbs.fromWholePounds(145),
                confidence = WeightSuggestion.Confidence.MEDIUM,
                reasoning = "Recent related lifts support a conservative starting load with room to adjust.",
            )

        override suspend fun suggestTransferWeight(targetExerciseId: Long, targetReps: Int): WeightSuggestion =
            suggestWeight(targetExerciseId, targetReps)

        override suspend fun getSessionByDate(date: String): SessionSnapshot =
            SessionSnapshot(date, listOf(ExerciseSnapshot(1L, "Bench Press", getRecentSets(1L, 2))))

        override suspend fun getProgressTrend(exerciseId: Long): ProgressTrend =
            ProgressTrend(
                exerciseId = exerciseId,
                name = exerciseNameForId(exerciseId),
                sessionCount = 6,
                prWeightLbs = WeightLbs.fromWholePounds(225),
                prDate = "2026-05-15",
                est1Rm = 262,
                deltaPercent = 4.2f,
                recentSessions = listOf("2026-05-15: 225x5", "2026-05-08: 225x4", "2026-05-01: 215x5"),
            )
    }

    private companion object {
        const val EVAL_TODAY = "2026-05-17"

        fun env(name: String): String = System.getenv(name).orEmpty()

        fun envInt(name: String, default: Int): Int =
            env(name).toIntOrNull() ?: default

        fun envLong(name: String, default: Long): Long =
            env(name).toLongOrNull() ?: default

        fun envDouble(name: String, default: Double): Double =
            env(name).toDoubleOrNull() ?: default
    }
}

private const val PROMPT_KIND_INTENT = "INTENT_CLASSIFICATION"
private const val PROMPT_KIND_LOG_EXTRACTION = "LOG_EXTRACTION"
private const val PROMPT_KIND_READ_FORMATTING = "READ_FORMATTING"
private const val PROMPT_KIND_CLARIFY = "CLARIFY"
private const val PROMPT_KIND_OTHER = "OTHER"

private val CATEGORY_ORDER = listOf(
    "hard_log",
    "spoken_log",
    "ambiguous_log",
    "historical_correction",
    "destructive",
    "history_query",
    "progress_analysis",
    "recommendation",
    "similar_or_substitute",
    "bodyweight_log",
    "historical_log",
)

private val GENERIC_UNRESOLVED = listOf(
    "which exercise",
    "couldn't identify",
    "could not identify",
    "couldn't determine",
    "could not determine",
    "could you be more specific",
)

private val CLARIFY_TERMS = listOf("which", "what", "specific", "name", "exercise", "machine")

private val KNOWN_INTENT_TARGETS = listOf(
    "LogSet",
    "EditSet",
    "DeleteSet",
    "MoveWorkoutDay",
    "RenameExercise",
    "AskRecommendation",
    "AskSimilar",
    "AskHistory",
    "QueryDate",
    "QueryProgress",
    "Clarify",
)

private val EXERCISE_IDS = listOf(
    "Back Squat",
    "Barbell Row",
    "Bench Press",
    "Chest Press Machine",
    "Deadlift",
    "Hip Adduction",
    "Incline Bench Press",
    "Incline Dumbbell Press",
    "Lat Pulldown",
    "Overhead Press",
    "Pull Up",
    "Row Machine",
    "Standing Calf Raise Machine",
).associateWith { name -> name.hashCode().toLong().let { if (it < 0) -it else it } }

private fun promptKind(prompt: String): String = when {
    prompt.startsWith("You are a workout app assistant. Classify") -> PROMPT_KIND_INTENT
    prompt.contains("one workout logging request", ignoreCase = true) -> PROMPT_KIND_LOG_EXTRACTION
    prompt.startsWith("You are a concise workout coach") -> PROMPT_KIND_READ_FORMATTING
    prompt.startsWith("You are a workout assistant. The user's request is unclear.") -> PROMPT_KIND_CLARIFY
    else -> PROMPT_KIND_OTHER
}

private fun parseIntentTarget(raw: String): String {
    return extractKnownIntentTarget(raw) ?: "Clarify"
}

private fun extractKnownIntentTarget(raw: String): String? {
    val jsonTarget = runCatching {
        val root = JSONObject(raw)
        root.optString("intent")
            .ifBlank { root.optString("label") }
            .ifBlank { root.optString("target") }
    }.getOrNull()
    val cleaned = (jsonTarget ?: raw)
        .trim()
        .trim('"')
        .removeSuffix(".")
        .lowercase()
    return KNOWN_INTENT_TARGETS.firstOrNull { target -> cleaned.contains(target.lowercase()) }
}

private fun responseParseable(kind: String, raw: String): Boolean = when (kind) {
    PROMPT_KIND_INTENT -> extractKnownIntentTarget(raw) != null
    PROMPT_KIND_LOG_EXTRACTION -> logExtractionResponseParseable(raw)
    PROMPT_KIND_READ_FORMATTING -> raw.trim().length >= 20
    PROMPT_KIND_CLARIFY -> raw.trim().isNotBlank()
    else -> raw.trim().isNotBlank()
}

private fun logExtractionResponseParseable(raw: String): Boolean {
    val jsonText = extractJsonObject(raw) ?: return false
    val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return false
    val exercise = root.optString("exerciseQuery")
        .ifBlank { root.optString("exercise") }
        .ifBlank { root.optString("exerciseName") }
    if (exercise.isBlank()) return false
    if (!root.has("confidence")) return false
    val sets = root.optJSONArray("sets") ?: return false
    if (sets.length() == 0) return false
    for (index in 0 until sets.length()) {
        val set = sets.optJSONObject(index) ?: return false
        if (!set.has("reps") || set.optInt("reps", -1) !in 1..100) return false
        if (set.has("weightLbs") && !set.isNull("weightLbs") && set.optDouble("weightLbs", -1.0) < 0.0) {
            return false
        }
    }
    return true
}

private fun extractJsonObject(raw: String): String? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return raw.substring(start, end + 1)
}

private fun exerciseIdFor(name: String): Long =
    EXERCISE_IDS[name] ?: name.hashCode().toLong().let { if (it < 0) -it else it }

private fun exerciseNameForId(id: Long): String =
    EXERCISE_IDS.entries.firstOrNull { it.value == id }?.key ?: "Bench Press"
