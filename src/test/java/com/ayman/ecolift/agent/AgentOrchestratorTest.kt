package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.patches.PatchApplier
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.tools.AgentTools
import com.ayman.ecolift.agent.tools.ExerciseMatch
import com.ayman.ecolift.agent.tools.HistorySummary
import com.ayman.ecolift.agent.tools.SetSummary
import com.ayman.ecolift.agent.tools.SimilarExercise
import com.ayman.ecolift.agent.tools.WeightSuggestion
import com.ayman.ecolift.data.WeightLbs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

class AgentOrchestratorTest {

    private lateinit var tools: AgentTools
    private lateinit var patchApplier: PatchApplier
    private lateinit var orchestrator: AgentOrchestrator

    private val TODAY = "2026-04-16"
    private val BENCH_MATCH = ExerciseMatch(exerciseId = 1L, name = "Bench Press", isBodyweight = false, score = 0.0)
    private fun lbs(value: Int): Int = WeightLbs.fromWholePounds(value)!!

    private class ReadyExtractionEngine(
        private val extractionJson: String,
        private val intentLabel: String = "LogSet"
    ) : LocalGenAiEngine {
        override val isReady: Boolean = true
        override suspend fun warmup() = Unit
        override fun streamText(prompt: String): Flow<String> = flowOf(extractionJson)
        override suspend fun generateStructured(prompt: String, schema: String): String {
            return if (prompt.contains("Classify", ignoreCase = true)) intentLabel else extractionJson
        }
        override fun close() = Unit
    }

    @Before
    fun setup() {
        tools = mock()
        patchApplier = mock()
        orchestrator = AgentOrchestrator(
            router      = IntentRouter(engine = null),
            tools       = tools,
            patchApplier = patchApplier,
            engine      = null,
            today       = { TODAY }
        )
    }

    // ── LogSet happy path ─────────────────────────────────────────────

    @Test
    fun `logSet process returns Applied on success`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(emptyList())
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 42L, patchCount = 1))

        val result = orchestrator.process("bench press 135x8")

        assertTrue("Expected Applied but got $result", result is AgentTurn.Applied)
        assertEquals(42L, (result as AgentTurn.Applied).auditId)
    }

    @Test
    fun `logSet sets correct setNumber when no sets today`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(emptyList())
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 1L, patchCount = 1))

        orchestrator.process("bench press 135x8")

        argumentCaptor<List<DbPatch>> {
            verify(patchApplier).applyPatches(any(), capture(), eq(false))
            val patch = firstValue.first() as DbPatch.LogSet
            assertEquals(1, patch.setNumber)
            assertEquals(lbs(135), patch.weightLbs)
            assertEquals(8, patch.reps)
            assertEquals(TODAY, patch.date)
        }
    }

    @Test
    fun `logSet increments setNumber when sets already logged today`() = runTest {
        val existingSet = SetSummary(setId = 10L, date = TODAY, setNumber = 3, weightLbs = 135, reps = 8, isBodyweight = false)
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(listOf(existingSet))
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 1L, patchCount = 1))

        orchestrator.process("bench 225 lbs for 5")

        argumentCaptor<List<DbPatch>> {
            verify(patchApplier).applyPatches(any(), capture(), eq(false))
            val patch = firstValue.first() as DbPatch.LogSet
            assertEquals(4, patch.setNumber)
        }
    }

    @Test
    fun `messy shorthand log creates multiple patches for one fuzzy matched exercise`() = runTest {
        whenever(tools.findExercise(eq("Bechh Press"))).thenReturn(BENCH_MATCH)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(emptyList())
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 7L, patchCount = 3))

        val result = orchestrator.process("i did Bechh Press 135x7,125x10,.85x5.")

        assertTrue("Expected Applied but got $result", result is AgentTurn.Applied)
        argumentCaptor<List<DbPatch>> {
            verify(patchApplier).applyPatches(any(), capture(), eq(false))
            val patches = firstValue.map { it as DbPatch.LogSet }
            assertEquals(3, patches.size)
            assertEquals(listOf(1, 2, 3), patches.map { it.setNumber })
            assertEquals(listOf(lbs(135), lbs(125), lbs(85)), patches.map { it.weightLbs })
            assertEquals(listOf(7, 10, 5), patches.map { it.reps })
            assertTrue(patches.all { it.date == TODAY })
        }
    }

    @Test
    fun `messy unit log reuses weight across comma separated reps on parsed date`() = runTest {
        whenever(tools.findExercise(eq("Bechh Press"))).thenReturn(BENCH_MATCH)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(emptyList())
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 8L, patchCount = 3))

        orchestrator.process("yesterday i did Bechh Press around 135 pounds for 12 reps, 10, and 8")

        argumentCaptor<List<DbPatch>> {
            verify(patchApplier).applyPatches(any(), capture(), eq(false))
            val patches = firstValue.map { it as DbPatch.LogSet }
            assertEquals(3, patches.size)
            assertEquals(listOf(lbs(135), lbs(135), lbs(135)), patches.map { it.weightLbs })
            assertEquals(listOf(12, 10, 8), patches.map { it.reps })
            assertTrue(patches.all { it.date == "2026-04-15" })
        }
    }

    @Test
    fun `model fallback extracts spoken messy log when deterministic parser cannot`() = runTest {
        val engine = ReadyExtractionEngine(
            """
            {
              "exerciseQuery": "Bench Press",
              "date": "2026-04-15",
              "confidence": 0.91,
              "sets": [
                { "weightLbs": 135, "reps": 7 }
              ]
            }
            """.trimIndent()
        )
        val modelBackedOrchestrator = AgentOrchestrator(
            router       = IntentRouter(engine = engine),
            tools        = tools,
            patchApplier = patchApplier,
            engine       = engine,
            today        = { TODAY }
        )
        whenever(tools.findExercise(any())).thenAnswer { invocation ->
            if (invocation.getArgument<String>(0) == "Bench Press") BENCH_MATCH else null
        }
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(emptyList())
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 9L, patchCount = 1))

        val result = modelBackedOrchestrator.process("just did Bechh Press one thirty five for seven")

        assertTrue("Expected Applied but got $result", result is AgentTurn.Applied)
        argumentCaptor<List<DbPatch>> {
            verify(patchApplier).applyPatches(any(), capture(), eq(false))
            val patch = firstValue.single() as DbPatch.LogSet
            assertEquals(lbs(135), patch.weightLbs)
            assertEquals(7, patch.reps)
            assertEquals(TODAY, patch.date)
        }
    }

    @Test
    fun `model fallback does not mutate ambiguous plate based log`() = runTest {
        val engine = ReadyExtractionEngine(
            """
            {
              "exerciseQuery": "Leg Press",
              "date": "2026-04-15",
              "confidence": 0.94,
              "sets": [
                { "weightLbs": 135, "reps": 12 },
                { "weightLbs": 135, "reps": 12 }
              ]
            }
            """.trimIndent()
        )
        val modelBackedOrchestrator = AgentOrchestrator(
            router       = IntentRouter(engine = engine),
            tools        = tools,
            patchApplier = patchApplier,
            engine       = engine,
            today        = { TODAY }
        )
        whenever(tools.findExercise(any())).thenReturn(
            ExerciseMatch(exerciseId = 9L, name = "Leg Press", isBodyweight = false, score = 0.0)
        )

        val result = modelBackedOrchestrator.process("leg thing yesterday was like 3 plates for 12 and 12")

        assertTrue("Expected RecoverableFailure but got $result", result is AgentTurn.RecoverableFailure)
        verify(patchApplier, never()).applyPatches(any(), any(), any())
    }

    @Test
    fun `model log extraction uses deterministic date over wrong model date`() = runTest {
        val engine = ReadyExtractionEngine(
            """
            {
              "exerciseQuery": "Pull Up",
              "date": "2026-04-16",
              "confidence": 0.91,
              "sets": [
                { "weightLbs": null, "reps": 12 },
                { "weightLbs": null, "reps": 10 },
                { "weightLbs": null, "reps": 8 }
              ]
            }
            """.trimIndent()
        )
        val pullUp = ExerciseMatch(exerciseId = 2L, name = "Pull Up", isBodyweight = true, score = 0.0)
        val modelBackedOrchestrator = AgentOrchestrator(
            router       = IntentRouter(engine = engine),
            tools        = tools,
            patchApplier = patchApplier,
            engine       = engine,
            today        = { TODAY }
        )
        whenever(tools.findExercise(any())).thenReturn(pullUp)
        whenever(tools.getRecentSets(eq(2L), any())).thenReturn(emptyList())
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 11L, patchCount = 3))

        val result = modelBackedOrchestrator.process("yesterday pullups twelve ten eight")

        assertTrue("Expected Applied but got $result", result is AgentTurn.Applied)
        argumentCaptor<List<DbPatch>> {
            verify(patchApplier).applyPatches(any(), capture(), eq(false))
            assertTrue(firstValue.all { (it as DbPatch.LogSet).date == "2026-04-15" })
        }
    }

    @Test
    fun `deterministic only messy log returns recoverable draft without model request`() = runTest {
        val engine: LocalGenAiEngine = mock()
        whenever(engine.isReady).thenReturn(true)
        val deterministicFirstOrchestrator = AgentOrchestrator(
            router       = IntentRouter(engine = engine),
            tools        = tools,
            patchApplier = patchApplier,
            engine       = engine,
            today        = { TODAY }
        )

        val original = "just did Bechh Press one thirty five for seven"
        val result = deterministicFirstOrchestrator.process(
            original,
            AgentProcessingOptions(allowModelFallback = false)
        )

        assertTrue("Expected RecoverableFailure but got $result", result is AgentTurn.RecoverableFailure)
        val draft = result as AgentTurn.RecoverableFailure
        assertEquals(original, draft.originalText)
        assertEquals(TODAY, draft.saveDate)
        assertTrue(draft.canTryModel)
        assertTrue(draft.suggestedTemplate.contains("135x8"))
        verify(engine, never()).generateStructured(any(), any())
        verify(patchApplier, never()).applyPatches(any(), any(), any())
    }

    @Test
    fun `dated workout import applies matched rows and returns unknown rows for review`() = runTest {
        whenever(tools.findExercise(eq("Bench"))).thenReturn(BENCH_MATCH)
        whenever(tools.findExercise(eq("Hip Abdction"))).thenReturn(null)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(emptyList())
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 77L, patchCount = 3))

        val result = orchestrator.process(
            """
            5/12/26
            Bench 135x8, 155x5, 155x4
            Hip Abdction 150lbs for 10, 10, 10
            """.trimIndent()
        )

        assertTrue("Expected ImportApplied but got $result", result is AgentTurn.ImportApplied)
        val applied = result as AgentTurn.ImportApplied
        assertEquals(77L, applied.auditId)
        assertEquals(1, applied.pendingReviews.size)
        assertEquals("Hip Abdction 150lbs for 10, 10, 10", applied.pendingReviews.single().rawInput)
        assertEquals("2026-05-12", applied.pendingReviews.single().dateLogged)
        argumentCaptor<List<DbPatch>> {
            verify(patchApplier).applyPatches(any(), capture(), eq(false))
            val patches = firstValue.map { it as DbPatch.LogSet }
            assertEquals(3, patches.size)
            assertEquals(listOf(1, 2, 3), patches.map { it.setNumber })
            assertTrue(patches.all { it.date == "2026-05-12" })
            assertEquals(listOf(lbs(135), lbs(155), lbs(155)), patches.map { it.weightLbs })
            assertEquals(listOf(8, 5, 4), patches.map { it.reps })
        }
    }

    @Test
    fun `dated workout import increments set numbers per exercise and date`() = runTest {
        val rowMatch = ExerciseMatch(exerciseId = 2L, name = "Barbell Row", isBodyweight = false, score = 0.0)
        whenever(tools.findExercise(eq("bench"))).thenReturn(BENCH_MATCH)
        whenever(tools.findExercise(eq("rows"))).thenReturn(rowMatch)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(
            listOf(SetSummary(10L, "2026-05-03", 2, lbs(175), 5, false))
        )
        whenever(tools.getRecentSets(eq(2L), any())).thenReturn(emptyList())
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 78L, patchCount = 6))

        val result = orchestrator.process("May 3 - Bench 185 5 5 4; rows 135x10x3")

        assertTrue("Expected ImportApplied but got $result", result is AgentTurn.ImportApplied)
        argumentCaptor<List<DbPatch>> {
            verify(patchApplier).applyPatches(any(), capture(), eq(false))
            val patches = firstValue.map { it as DbPatch.LogSet }
            assertEquals(listOf(3, 4, 5), patches.filter { it.exerciseId == 1L }.map { it.setNumber })
            assertEquals(listOf(1, 2, 3), patches.filter { it.exerciseId == 2L }.map { it.setNumber })
        }
    }

    @Test
    fun `logSet exercise not found returns recoverable draft`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(null)

        val result = orchestrator.process("bench 135x8")

        assertTrue("Expected RecoverableFailure but got $result", result is AgentTurn.RecoverableFailure)
        assertEquals("bench 135x8", (result as AgentTurn.RecoverableFailure).originalText)
        verify(patchApplier, never()).applyPatches(any(), any(), any())
    }

    // ── DeleteSet — requires confirmation ────────────────────────────

    @Test
    fun `deleteSet returns NeedsConfirmation before applying`() = runTest {
        val existingSet = SetSummary(setId = 99L, date = TODAY, setNumber = 1, weightLbs = 135, reps = 8, isBodyweight = false)
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(listOf(existingSet))

        val result = orchestrator.process("delete my last bench press set")

        assertTrue("Expected NeedsConfirmation but got $result", result is AgentTurn.NeedsConfirmation)
        verify(patchApplier, never()).applyPatches(any(), any(), any())
        val nc = result as AgentTurn.NeedsConfirmation
        assertEquals(DbPatch.DeleteSet(setId = 99L), nc.patches.first())
    }

    @Test
    fun `confirm applies destructive patch with userConfirmed=true`() = runTest {
        whenever(patchApplier.applyPatches(any(), any(), eq(true)))
            .thenReturn(PatchResult.Applied(auditId = 55L, patchCount = 1))

        val patches = listOf(DbPatch.DeleteSet(setId = 99L))
        val result = orchestrator.confirm("req-123", patches)

        assertTrue(result is AgentTurn.Applied)
        assertEquals(55L, (result as AgentTurn.Applied).auditId)
        verify(patchApplier).applyPatches(eq("req-123"), eq(patches), eq(true))
    }

    // ── RenameExercise — also destructive ────────────────────────────

    @Test
    fun `renameExercise returns NeedsConfirmation`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)

        val result = orchestrator.process("rename bench press to flat bench")

        assertTrue("Expected NeedsConfirmation but got $result", result is AgentTurn.NeedsConfirmation)
        val patch = (result as AgentTurn.NeedsConfirmation).patches.first()
        assertTrue(patch is DbPatch.RenameExercise)
        assertEquals("flat bench", (patch as DbPatch.RenameExercise).newName)
    }

    // ── EditSet ───────────────────────────────────────────────────────

    @Test
    fun `editSet applies directly without confirmation`() = runTest {
        val existingSet = SetSummary(setId = 77L, date = TODAY, setNumber = 2, weightLbs = 125, reps = 8, isBodyweight = false)
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(listOf(existingSet))
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 10L, patchCount = 1))

        val result = orchestrator.process("fix my last set it was 135 not 125")

        assertTrue(result is AgentTurn.Applied)
        argumentCaptor<List<DbPatch>> {
            verify(patchApplier).applyPatches(any(), capture(), eq(false))
            val patch = firstValue.first() as DbPatch.EditSet
            assertEquals(77L, patch.setId)
            assertEquals(lbs(135), patch.weightLbs)
        }
    }

    // ── Undo ─────────────────────────────────────────────────────────

    @Test
    fun `editSet treats bare number before for as weight and number after for as reps`() = runTest {
        val existingSet = SetSummary(setId = 78L, date = TODAY, setNumber = 1, weightLbs = lbs(85), reps = 5, isBodyweight = false)
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(listOf(existingSet))
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 12L, patchCount = 1))

        val result = orchestrator.process("fix my last set it was 95 for 8")

        assertTrue("Expected Applied but got $result", result is AgentTurn.Applied)
        argumentCaptor<List<DbPatch>> {
            verify(patchApplier).applyPatches(any(), capture(), eq(false))
            val patch = firstValue.first() as DbPatch.EditSet
            assertEquals(lbs(95), patch.weightLbs)
            assertEquals(8, patch.reps)
        }
    }

    @Test
    fun `editSet extracts spoken weight and reps for historical correction`() = runTest {
        val deadlift = ExerciseMatch(exerciseId = 5L, name = "Deadlift", isBodyweight = false, score = 0.0)
        val existingSet = SetSummary(setId = 79L, date = TODAY, setNumber = 1, weightLbs = lbs(275), reps = 3, isBodyweight = false)
        whenever(tools.findExercise(any())).thenReturn(deadlift)
        whenever(tools.getRecentSets(eq(5L), any())).thenReturn(listOf(existingSet))
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 13L, patchCount = 1))

        val result = orchestrator.process("for last saturday I think my deadlift top set should say three fifteen for four not 275")

        assertTrue("Expected Applied but got $result", result is AgentTurn.Applied)
        argumentCaptor<List<DbPatch>> {
            verify(patchApplier).applyPatches(any(), capture(), eq(false))
            val patch = firstValue.first() as DbPatch.EditSet
            assertEquals(lbs(315), patch.weightLbs)
            assertEquals(4, patch.reps)
        }
    }

    @Test
    fun `undo delegates to patchApplier and returns Applied`() = runTest {
        whenever(patchApplier.undo(eq(42L)))
            .thenReturn(PatchResult.Applied(auditId = 43L, patchCount = 1))

        val result = orchestrator.undo(42L)

        assertTrue(result is AgentTurn.Applied)
        verify(patchApplier).undo(42L)
    }

    @Test
    fun `undo failure returns Error`() = runTest {
        whenever(patchApplier.undo(any()))
            .thenReturn(PatchResult.Failed("Audit not found"))

        val result = orchestrator.undo(999L)

        assertTrue(result is AgentTurn.Error)
        assertEquals("Audit not found", (result as AgentTurn.Error).message)
    }

    // ── Clarify fallback ──────────────────────────────────────────────

    @Test
    fun `unrecognised text returns TextResponse (clarify)`() = runTest {
        val result = orchestrator.process("blorp morp snorp")

        assertTrue("Expected TextResponse but got $result", result is AgentTurn.TextResponse)
        verify(patchApplier, never()).applyPatches(any(), any(), any())
    }

    // ── Read — AskHistory ─────────────────────────────────────────────

    @Test
    fun `askHistory returns TextResponse with session count`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.getExerciseHistory(eq(1L), any())).thenReturn(
            HistorySummary(
                exerciseId     = 1L,
                windowDays     = 30,
                sessionCount   = 5,
                topSetWeightLbs = lbs(225),
                topSetReps     = 5,
                recentSets     = emptyList()
            )
        )

        val result = orchestrator.process("show me my bench press history")

        assertTrue(result is AgentTurn.TextResponse)
        val text = (result as AgentTurn.TextResponse).text
        assertTrue("Should mention session count: $text", text.contains("5 session"))
        assertTrue("Should mention best set: $text", text.contains("225"))
    }

    @Test
    fun `askHistory with no data returns TextResponse`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.getExerciseHistory(eq(1L), any())).thenReturn(
            HistorySummary(1L, 30, 0, null, null, emptyList())
        )

        val result = orchestrator.process("show me my bench history")

        assertTrue(result is AgentTurn.TextResponse)
        assertTrue((result as AgentTurn.TextResponse).text.contains("No"))
    }

    // ── Read — AskSimilar ─────────────────────────────────────────────

    @Test
    fun `askSimilar returns comma-separated exercise names`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.getSimilarExercises(eq(1L), any())).thenReturn(
            listOf(
                SimilarExercise(2L, "Incline Press", 0.85, "horizontal_push"),
                SimilarExercise(3L, "Dumbbell Fly",  0.70, "horizontal_push")
            )
        )

        val result = orchestrator.process("what's a good alternative to bench press")

        assertTrue(result is AgentTurn.TextResponse)
        val text = (result as AgentTurn.TextResponse).text
        assertTrue(text.contains("Incline Press"))
        assertTrue(text.contains("Dumbbell Fly"))
    }

    // ── Read — AskRecommendation ──────────────────────────────────────

    @Test
    fun `askRecommendation with data returns weight suggestion`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.suggestWeight(eq(1L), any())).thenReturn(
            WeightSuggestion(
                exerciseId       = 1L,
                targetReps       = 8,
                suggestedWeightLbs = lbs(140),
                confidence       = WeightSuggestion.Confidence.HIGH,
                reasoning        = "Based on last session."
            )
        )

        val result = orchestrator.process("what weight should i use for bench")

        assertTrue(result is AgentTurn.TextResponse)
        val text = (result as AgentTurn.TextResponse).text
        assertTrue("Should mention suggested weight: $text", text.contains("140"))
    }

    @Test
    fun `askRecommendation with no data returns starter message`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.suggestWeight(eq(1L), any())).thenReturn(
            WeightSuggestion(1L, 8, null, WeightSuggestion.Confidence.NO_DATA, "No data.")
        )

        val result = orchestrator.process("how much should i bench today")

        assertTrue(result is AgentTurn.TextResponse)
        val text = (result as AgentTurn.TextResponse).text
        assertTrue("Should mention no history: $text", text.contains("No history") || text.contains("no") || text.contains("Start"))
    }

    // ── MoveWorkoutDay ────────────────────────────────────────────────

    @Test
    fun `moveWorkoutDay applies directly`() = runTest {
        whenever(patchApplier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(auditId = 5L, patchCount = 1))

        val result = orchestrator.process("reschedule my workout to friday")

        assertTrue("Expected Applied but got $result", result is AgentTurn.Applied)
        argumentCaptor<List<DbPatch>> {
            verify(patchApplier).applyPatches(any(), capture(), eq(false))
            val patch = firstValue.first() as DbPatch.MoveWorkoutDay
            assertEquals(TODAY, patch.currentDate)
            assertTrue("Target should be a Friday: ${patch.newDate}", patch.newDate.isNotBlank())
        }
    }

    // ── Patch applier failures ────────────────────────────────────────

    @Test
    fun `applyPatches rejected returns TextResponse not Error`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.getRecentSets(any(), any())).thenReturn(emptyList())
        whenever(patchApplier.applyPatches(any(), any(), any()))
            .thenReturn(PatchResult.Rejected("Validation failed"))

        val result = orchestrator.process("bench 135x8")

        assertTrue(result is AgentTurn.TextResponse)
        assertTrue((result as AgentTurn.TextResponse).text.contains("Couldn't apply"))
    }

    @Test
    fun `applyPatches failed returns Error`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH_MATCH)
        whenever(tools.getRecentSets(any(), any())).thenReturn(emptyList())
        whenever(patchApplier.applyPatches(any(), any(), any()))
            .thenReturn(PatchResult.Failed("DB exploded"))

        val result = orchestrator.process("bench 135x8")

        assertTrue(result is AgentTurn.Error)
        assertEquals("DB exploded", (result as AgentTurn.Error).message)
    }
}
