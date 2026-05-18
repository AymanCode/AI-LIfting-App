package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.patches.PatchApplier
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.router.Intent
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.router.PatchType
import com.ayman.ecolift.agent.router.ReadType
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AgentEvalHarnessTest {

    @Test
    fun `offline eval reports routing and safety metrics`() = runTest {
        val cases = loadCases()
        assertEquals("Eval fixture must contain exactly 200 cases", 200, cases.size)

        val labelsByText = cases.associate { it.text to it.expectedIntent }
        val engine = EvalEngine(labelsByText)
        val router = IntentRouter(engine)

        var crashes = 0
        var intentCorrect = 0
        var sourceCorrect = 0
        val sourceCounts = linkedMapOf("RULE" to 0, "MODEL" to 0, "FALLBACK" to 0)

        var destructiveCases = 0
        var destructiveCorrect = 0
        var patchFieldCases = 0
        var patchFieldCorrect = 0

        for (case in cases) {
            val routing = try {
                router.route(case.text)
            } catch (_: Throwable) {
                crashes++
                continue
            }

            val actualIntent = routing.intent.toEvalLabel()
            if (actualIntent == case.expectedIntent) intentCorrect++
            if (routing.source.name == case.expectedSource) sourceCorrect++
            sourceCounts[routing.source.name] = sourceCounts.getValue(routing.source.name) + 1

            if (case.runAgent) {
                val patchApplier = CapturingPatchApplier()
                val turn = AgentOrchestrator(
                    router = router,
                    tools = EvalTools(),
                    patchApplier = patchApplier,
                    engine = null,
                    today = { "2026-05-16" },
                ).process(case.text)

                if (case.expectDestructiveConfirmation) {
                    destructiveCases++
                    if (turn is AgentTurn.NeedsConfirmation) destructiveCorrect++
                }

                val patch = when (turn) {
                    is AgentTurn.NeedsConfirmation -> turn.patches.firstOrNull()
                    else -> patchApplier.lastPatches.firstOrNull()
                }

                if (case.expectedPatchType != null) {
                    patchFieldCases++
                    if (patch != null && patch.matches(case)) {
                        patchFieldCorrect++
                    }
                }
            }
        }

        val intentAccuracy = intentCorrect.toDouble() / cases.size
        val sourceAccuracy = sourceCorrect.toDouble() / cases.size
        val ruleCoverage = sourceCounts.getValue("RULE").toDouble() / cases.size
        val fallbackRate = sourceCounts.getValue("FALLBACK").toDouble() / cases.size
        val destructiveAccuracy = destructiveCorrect.toDouble() / destructiveCases
        val patchFieldAccuracy = patchFieldCorrect.toDouble() / patchFieldCases

        writeSummary(
            JSONObject()
                .put("totalCases", cases.size)
                .put("crashes", crashes)
                .put("intentAccuracy", intentAccuracy)
                .put("routeSourceAccuracy", sourceAccuracy)
                .put("ruleCoverage", ruleCoverage)
                .put("fallbackRate", fallbackRate)
                .put("modelCalls", engine.calls)
                .put("destructiveConfirmationAccuracy", destructiveAccuracy)
                .put("patchFieldAccuracy", patchFieldAccuracy)
                .put("sourceCounts", JSONObject(sourceCounts)),
        )

        assertEquals("Eval harness should not crash on labeled prompts", 0, crashes)
        assertTrue("Intent accuracy $intentAccuracy < 0.90", intentAccuracy >= 0.90)
        assertTrue("Rule coverage $ruleCoverage < 0.80", ruleCoverage >= 0.80)
        assertEquals("Destructive prompts must require confirmation", 1.0, destructiveAccuracy, 0.0)
        assertTrue("Patch field accuracy $patchFieldAccuracy < 0.85", patchFieldAccuracy >= 0.85)
    }

    private fun loadCases(): List<EvalCase> {
        val stream = javaClass.classLoader
            ?.getResourceAsStream("agent_eval/ironmind_eval_cases.jsonl")
            ?: error("Missing agent eval fixture")
        return stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { line ->
                val obj = JSONObject(line)
                EvalCase(
                    id = obj.getString("id"),
                    text = obj.getString("text"),
                    expectedIntent = obj.getString("expectedIntent"),
                    expectedSource = obj.getString("expectedSource"),
                    expectedPatchType = obj.optString("expectedPatchType").ifBlank { null },
                    expectedWeightLbs = if (obj.has("expectedWeightLbs")) obj.getInt("expectedWeightLbs") else null,
                    expectedReps = if (obj.has("expectedReps")) obj.getInt("expectedReps") else null,
                    expectDestructiveConfirmation = obj.optBoolean("expectDestructiveConfirmation", false),
                    runAgent = obj.optBoolean("agent", true),
                )
            }.toList()
        }
    }

    private fun writeSummary(summary: JSONObject) {
        val dir = File("build/reports/agent-eval").apply { mkdirs() }
        File(dir, "summary.json").writeText(summary.toString(2))
    }

    private fun Intent.toEvalLabel(): String = when (this) {
        is Intent.Write -> patchType.name
        is Intent.Read -> queryType.name
        is Intent.Clarify -> "Clarify"
    }

    private fun DbPatch.matches(case: EvalCase): Boolean {
        if (this::class.simpleName != case.expectedPatchType) return false
        return when (this) {
            is DbPatch.LogSet ->
                (case.expectedWeightLbs == null || weightLbs == case.expectedWeightLbs) &&
                    (case.expectedReps == null || reps == case.expectedReps)
            is DbPatch.EditSet ->
                (case.expectedWeightLbs == null || weightLbs == case.expectedWeightLbs) &&
                    (case.expectedReps == null || reps == case.expectedReps)
            is DbPatch.DeleteSet -> true
            is DbPatch.MoveWorkoutDay -> true
            is DbPatch.RenameExercise -> newName.isNotBlank()
        }
    }

    private data class EvalCase(
        val id: String,
        val text: String,
        val expectedIntent: String,
        val expectedSource: String,
        val expectedPatchType: String?,
        val expectedWeightLbs: Int?,
        val expectedReps: Int?,
        val expectDestructiveConfirmation: Boolean,
        val runAgent: Boolean,
    )

    private class EvalEngine(private val labelsByText: Map<String, String>) : LocalGenAiEngine {
        var calls = 0
            private set

        override val isReady: Boolean = true
        override suspend fun warmup() {}
        override fun streamText(prompt: String): Flow<String> = flowOf("")
        override suspend fun generateStructured(prompt: String, schema: String): String {
            calls++
            val text = labelsByText.keys.firstOrNull { prompt.contains(it) }
            return text?.let(labelsByText::get) ?: "Clarify"
        }
        override fun close() {}
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

    private class EvalTools : AgentTools {
        override suspend fun findExercise(fuzzyName: String): ExerciseMatch? {
            val t = fuzzyName.lowercase()
            val name = when {
                "incline" in t && "bench" in t -> "Incline Bench Press"
                "bench" in t -> "Bench Press"
                "squat" in t -> "Back Squat"
                "deadlift" in t -> "Deadlift"
                "overhead" in t || "shoulder" in t -> "Overhead Press"
                "barbell row" in t || "row" in t -> "Barbell Row"
                "pull up" in t || "pullup" in t -> "Pull Up"
                "curl" in t -> "Dumbbell Curl"
                "leg press" in t -> "Leg Press"
                "pulldown" in t -> "Lat Pulldown"
                "triceps" in t -> "Triceps Pushdown"
                else -> null
            } ?: return null
            return ExerciseMatch(exerciseId = name.hashCode().toLong().let { if (it < 0) -it else it }, name = name, isBodyweight = false, score = 0.0)
        }

        override suspend fun getRecentSets(exerciseId: Long, limit: Int): List<SetSummary> =
            if (limit == 1) listOf(SetSummary(99L, "2026-05-16", 1, WeightLbs.fromWholePounds(135), 8, false)) else emptyList()

        override suspend fun getExerciseHistory(exerciseId: Long, windowDays: Int): HistorySummary =
            HistorySummary(exerciseId, windowDays, 3, WeightLbs.fromWholePounds(225), 5, emptyList())

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
}
