package com.ayman.ecolift.agent

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
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RealisticPromptBankOfflineEvalTest {

    @Test
    fun `offline prompt bank evaluator reports coverage and safety metrics`() = runTest {
        val summary = evaluatePromptBank()

        assertEquals(120, summary.getInt("totalPrompts"))
        assertEquals(0, summary.getInt("modelCalls"))
        assertEquals(0, summary.getInt("apiCalls"))
        assertEquals(0, summary.getInt("crashes"))
        assertTrue(summary.has("deterministicCoverage"))
        assertTrue(summary.has("fallbackRate"))
        assertTrue(summary.has("targetAgreementRate"))
        assertTrue(summary.has("mutationResolutionRate"))
        assertTrue(summary.has("safeFallbackPreservationRate"))
        assertTrue(summary.has("safeFallbackNoMutationRate"))
        assertTrue(summary.has("recoverableDraftRate"))
        assertTrue(summary.has("destructiveConfirmationRate"))
        assertTrue(summary.has("destructiveNoSilentApplyRate"))
        assertTrue(summary.has("importPendingReviewRate"))
        assertTrue(summary.has("categoryCounts"))
        assertTrue(summary.has("targetCounts"))
        assertTrue(summary.has("categoryMetrics"))
        assertTrue(summary.has("targetMetrics"))
        assertTrue(summary.has("routeCounts"))
        assertTrue(summary.has("resultCounts"))
        assertTrue(summary.has("gapCounts"))
        assertTrue(summary.has("caseResults"))
    }

    private suspend fun evaluatePromptBank(): JSONObject {
        val cases = loadPromptBank()
        val router = IntentRouter(engine = null)
        val tools = OfflineEvalTools()

        var crashes = 0
        var deterministic = 0
        var fallback = 0
        var targetMatches = 0
        var mutationCases = 0
        var mutationResolved = 0
        var safeFallbackCases = 0
        var safeFallbackPreserved = 0
        var safeFallbackNoMutation = 0
        var destructiveCases = 0
        var destructiveConfirmed = 0
        var destructiveNoSilentApply = 0
        var importCases = 0
        var importPendingReviewCases = 0

        val categoryCounts = linkedMapOf<String, Int>()
        val targetCounts = linkedMapOf<String, Int>()
        val categoryMetrics = linkedMapOf<String, BucketMetrics>()
        val targetMetrics = linkedMapOf<String, BucketMetrics>()
        val routeCounts = linkedMapOf("RULE" to 0, "FALLBACK" to 0, "IMPORT" to 0, "PENDING_CLEANUP" to 0)
        val resultCounts = linkedMapOf<String, Int>()
        val gapCounts = linkedMapOf(
            "targetMismatch" to 0,
            "expectedDeterministicButFallback" to 0,
            "destructiveWithoutConfirmation" to 0,
            "unsafeTextNotDrafted" to 0,
            "mutationNotResolved" to 0,
        )
        val caseResults = JSONArray()

        for (case in cases) {
            categoryCounts.increment(case.category)
            targetCounts.increment(case.target)
            val categoryBucket = categoryMetrics.getOrPut(case.category) { BucketMetrics() }
            val targetBucket = targetMetrics.getOrPut(case.target) { BucketMetrics() }
            categoryBucket.total++
            targetBucket.total++

            val patchApplier = CapturingPatchApplier()
            val agent = AgentOrchestrator(
                router = router,
                tools = tools,
                patchApplier = patchApplier,
                engine = null,
                today = { EVAL_TODAY },
            )

            try {
                val cleanupExercise = PendingReviewCleanupParser.extractExerciseName(case.text)
                if (cleanupExercise != null) {
                    deterministic++
                    categoryBucket.deterministic++
                    targetBucket.deterministic++
                    routeCounts.increment("PENDING_CLEANUP")
                    val actualTarget = "PendingCleanup"
                    if (actualTarget == case.target) {
                        targetMatches++
                        categoryBucket.targetMatches++
                        targetBucket.targetMatches++
                    } else {
                        gapCounts.increment("targetMismatch")
                    }
                    resultCounts.increment(actualTarget)
                    caseResults.put(case.toResultJson(actualTarget, "PENDING_CLEANUP", patchCount = 0, preserved = false))
                    continue
                }

                val importDraft = WorkoutImportTextParser.parse(case.text, EVAL_TODAY)
                val looksLikeImport = WorkoutImportTextParser.looksLikeImport(case.text, EVAL_TODAY) && importDraft != null
                val routing = router.route(case.text, allowModelFallback = false)
                val route = if (looksLikeImport) "IMPORT" else routing.source.name
                routeCounts.increment(route)
                if (route == "RULE" || route == "IMPORT") {
                    deterministic++
                    categoryBucket.deterministic++
                    targetBucket.deterministic++
                } else {
                    fallback++
                    categoryBucket.fallback++
                    targetBucket.fallback++
                    if (case.expectedPath.contains("deterministic")) {
                        gapCounts.increment("expectedDeterministicButFallback")
                    }
                }

                val turn = agent.process(case.text, AgentProcessingOptions(allowModelFallback = false))
                val actualTarget = turn.toTargetLabel(routing.intent)
                val patchCount = patchApplier.lastPatches.size
                val preserved = turn is AgentTurn.RecoverableFailure && turn.originalText == case.text
                val resultKind = turn::class.simpleName ?: "Unknown"
                resultCounts.increment(resultKind)

                if (actualTarget == case.target || compatibleTarget(case.target, actualTarget)) {
                    targetMatches++
                    categoryBucket.targetMatches++
                    targetBucket.targetMatches++
                } else {
                    gapCounts.increment("targetMismatch")
                }

                if (case.target in MUTATION_TARGETS) {
                    mutationCases++
                    categoryBucket.mutationCases++
                    targetBucket.mutationCases++
                    if (turn is AgentTurn.Applied || turn is AgentTurn.ImportApplied || turn is AgentTurn.NeedsConfirmation) {
                        mutationResolved++
                        categoryBucket.mutationResolved++
                        targetBucket.mutationResolved++
                    } else {
                        gapCounts.increment("mutationNotResolved")
                    }
                }

                if (case.target == "Clarify" || case.expectedPath.contains("preserve_text")) {
                    safeFallbackCases++
                    if (patchCount == 0) safeFallbackNoMutation++
                    if (preserved) {
                        safeFallbackPreserved++
                    } else {
                        gapCounts.increment("unsafeTextNotDrafted")
                    }
                }

                if (case.target == "DeleteSet" || case.category == "historical_destructive") {
                    destructiveCases++
                    if (turn is AgentTurn.NeedsConfirmation) destructiveConfirmed++
                    if (turn is AgentTurn.NeedsConfirmation || patchCount == 0) {
                        destructiveNoSilentApply++
                    } else {
                        gapCounts.increment("destructiveWithoutConfirmation")
                    }
                }

                if (case.target == "BulkImport") {
                    importCases++
                    if (turn is AgentTurn.ImportApplied && turn.pendingReviews.isNotEmpty()) {
                        importPendingReviewCases++
                    }
                }

                caseResults.put(case.toResultJson(actualTarget, route, patchCount, preserved))
            } catch (e: Throwable) {
                crashes++
                resultCounts.increment("Crash")
                caseResults.put(
                    case.toResultJson(
                        actualTarget = "Crash",
                        route = "ERROR",
                        patchCount = 0,
                        preserved = false,
                    ).put("error", e.message ?: e::class.java.name)
                )
            }
        }

        val summary = JSONObject()
            .put("evaluatorMode", "offline_deterministic_only")
            .put("promptBank", "agent_eval/ironmind_realistic_prompt_bank.jsonl")
            .put("modelFallbackAllowed", false)
            .put("totalPrompts", cases.size)
            .put("modelCalls", 0)
            .put("apiCalls", 0)
            .put("crashes", crashes)
            .put("deterministicCoverage", deterministic.ratio(cases.size))
            .put("fallbackRate", fallback.ratio(cases.size))
            .put("targetAgreementRate", targetMatches.ratio(cases.size))
            .put("mutationResolutionRate", mutationResolved.ratio(mutationCases))
            .put("safeFallbackPreservationRate", safeFallbackPreserved.ratio(safeFallbackCases))
            .put("safeFallbackNoMutationRate", safeFallbackNoMutation.ratio(safeFallbackCases))
            .put("recoverableDraftRate", safeFallbackPreserved.ratio(safeFallbackCases))
            .put("destructiveConfirmationRate", destructiveConfirmed.ratio(destructiveCases))
            .put("destructiveNoSilentApplyRate", destructiveNoSilentApply.ratio(destructiveCases))
            .put("importPendingReviewRate", importPendingReviewCases.ratio(importCases))
            .put("categoryCounts", JSONObject(categoryCounts))
            .put("targetCounts", JSONObject(targetCounts))
            .put("categoryMetrics", categoryMetrics.toJson())
            .put("targetMetrics", targetMetrics.toJson())
            .put("routeCounts", JSONObject(routeCounts))
            .put("resultCounts", JSONObject(resultCounts))
            .put("gapCounts", JSONObject(gapCounts))
            .put("caseResults", caseResults)

        writeSummary(summary)
        return summary
    }

    private fun loadPromptBank(): List<PromptCase> {
        val stream = javaClass.classLoader
            ?.getResourceAsStream("agent_eval/ironmind_realistic_prompt_bank.jsonl")
            ?: error("Missing realistic prompt bank fixture")
        return stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { line ->
                val obj = JSONObject(line)
                PromptCase(
                    id = obj.getString("id"),
                    text = obj.getString("text"),
                    category = obj.getString("category"),
                    expectedPath = obj.getString("expectedPath"),
                    target = obj.getString("target"),
                )
            }.toList()
        }
    }

    private fun writeSummary(summary: JSONObject) {
        val dir = File("build/reports/agent-eval").apply { mkdirs() }
        File(dir, "realistic-offline-summary.json").writeText(summary.toString(2))
    }

    private fun AgentTurn.toTargetLabel(intent: Intent): String = when (this) {
        is AgentTurn.ImportApplied -> "BulkImport"
        is AgentTurn.RecoverableFailure -> "Clarify"
        is AgentTurn.NeedsConfirmation -> intent.toTargetLabel()
        is AgentTurn.Applied -> intent.toTargetLabel()
        is AgentTurn.TextResponse -> intent.toTargetLabel()
        is AgentTurn.Error -> "Error"
    }

    private fun Intent.toTargetLabel(): String = when (this) {
        is Intent.Write -> patchType.name
        is Intent.Read -> queryType.name
        is Intent.Clarify -> "Clarify"
    }

    private fun compatibleTarget(expected: String, actual: String): Boolean =
        expected == "BulkImport" && actual == "LogSet"

    private fun MutableMap<String, Int>.increment(key: String) {
        this[key] = (this[key] ?: 0) + 1
    }

    private fun Int.ratio(denominator: Int): Double =
        if (denominator == 0) 1.0 else toDouble() / denominator.toDouble()

    private fun PromptCase.toResultJson(
        actualTarget: String,
        route: String,
        patchCount: Int,
        preserved: Boolean,
    ): JSONObject =
        JSONObject()
            .put("id", id)
            .put("category", category)
            .put("expectedTarget", target)
            .put("actualTarget", actualTarget)
            .put("route", route)
            .put("expectedPath", expectedPath)
            .put("patchCount", patchCount)
            .put("preservedText", preserved)

    private data class PromptCase(
        val id: String,
        val text: String,
        val category: String,
        val expectedPath: String,
        val target: String,
    )

    private data class BucketMetrics(
        var total: Int = 0,
        var deterministic: Int = 0,
        var fallback: Int = 0,
        var targetMatches: Int = 0,
        var mutationCases: Int = 0,
        var mutationResolved: Int = 0,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("total", total)
                .put("deterministicCoverage", ratio(deterministic, total))
                .put("fallbackRate", ratio(fallback, total))
                .put("targetAgreementRate", ratio(targetMatches, total))
                .put("mutationResolutionRate", ratio(mutationResolved, mutationCases))

        private fun ratio(numerator: Int, denominator: Int): Double =
            if (denominator == 0) 1.0 else numerator.toDouble() / denominator.toDouble()
    }

    private fun Map<String, BucketMetrics>.toJson(): JSONObject =
        JSONObject(mapValues { it.value.toJson() })

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

    private class OfflineEvalTools : AgentTools {
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
                "calf" in t -> "Standing Calf Raise Machine"
                "hip abdction" in t || "hip abduction" in t -> "Hip Abduction"
                "hip adduction" in t -> "Hip Adduction"
                "lateral" in t || "lat raise" in t -> "Lateral Raise Machine"
                "dip" in t -> "Dip Machine Weighted"
                "rear tether" in t || "rear delt" in t -> "Rear Tether"
                "rdl" in t -> "Romanian Deadlift"
                "cable fly" in t -> "Cable Fly"
                else -> null
            } ?: return null
            val id = name.hashCode().toLong().let { if (it < 0) -it else it }
            return ExerciseMatch(id, name, isBodyweight = name == "Pull Up", score = 0.0)
        }

        override suspend fun getRecentSets(exerciseId: Long, limit: Int): List<SetSummary> =
            listOf(SetSummary(99L, "2026-05-16", 1, WeightLbs.fromWholePounds(135), 8, false)).take(limit)

        override suspend fun getExerciseHistory(exerciseId: Long, windowDays: Int): HistorySummary =
            HistorySummary(exerciseId, windowDays, 4, WeightLbs.fromWholePounds(225), 5, emptyList())

        override suspend fun getSimilarExercises(exerciseId: Long, k: Int): List<SimilarExercise> =
            listOf(SimilarExercise(2L, "Incline Bench Press", 0.9, "HorizontalPress"))

        override suspend fun suggestWeight(exerciseId: Long, targetReps: Int): WeightSuggestion =
            WeightSuggestion(exerciseId, targetReps, WeightLbs.fromWholePounds(185), WeightSuggestion.Confidence.HIGH, "Recent top sets support this load.")

        override suspend fun suggestTransferWeight(targetExerciseId: Long, targetReps: Int): WeightSuggestion =
            suggestWeight(targetExerciseId, targetReps)

        override suspend fun getSessionByDate(date: String): SessionSnapshot =
            SessionSnapshot(date, listOf(ExerciseSnapshot(1L, "Bench Press", getRecentSets(1L, 1))))

        override suspend fun getProgressTrend(exerciseId: Long): ProgressTrend =
            ProgressTrend(exerciseId, "Bench Press", 5, WeightLbs.fromWholePounds(225), "2026-05-01", 260, 4.2f, listOf("2026-05-01: 225x5"))
    }

    private companion object {
        const val EVAL_TODAY = "2026-05-17"
        val MUTATION_TARGETS = setOf("LogSet", "BulkImport", "EditSet", "DeleteSet", "MoveWorkoutDay", "RenameExercise")
    }
}
