package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.patches.PatchApplier
import com.ayman.ecolift.agent.patches.PatchResult
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.tools.AgentTools
import com.ayman.ecolift.agent.tools.ExerciseMatch
import com.ayman.ecolift.agent.tools.HistorySummary
import com.ayman.ecolift.agent.tools.SetSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Edge-case coverage for [AgentOrchestrator] — weight/reps extraction
 * boundaries, bodyweight handling, date edges, rename parsing,
 * clarify fallthroughs.
 *
 * TODAY is fixed to 2026-04-16 (Thursday) so date math is deterministic.
 */
class OrchestratorEdgeCaseTest {

    private lateinit var tools: AgentTools
    private lateinit var applier: PatchApplier
    private lateinit var orchestrator: AgentOrchestrator

    private val TODAY = "2026-04-16" // Thursday
    private val BENCH = ExerciseMatch(1L, "Bench Press", isBodyweight = false, score = 0.0)
    private val PULLUP = ExerciseMatch(2L, "Pull Up",    isBodyweight = true,  score = 0.0)

    @Before
    fun setup() {
        tools = mock()
        applier = mock()
        orchestrator = AgentOrchestrator(
            router       = IntentRouter(engine = null),
            tools        = tools,
            patchApplier = applier,
            engine       = null,
            today        = { TODAY }
        )
    }

    // ── Weight/reps extraction boundaries ─────────────────────────────

    @Test
    fun `3x10 treats 3 as sets, weight stays null`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH)
        whenever(tools.getRecentSets(any(), any())).thenReturn(emptyList())
        whenever(applier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(1L, 1))

        orchestrator.process("bench 3x10")

        argumentCaptor<List<DbPatch>> {
            verify(applier).applyPatches(any(), capture(), eq(false))
            val p = firstValue.first() as DbPatch.LogSet
            assertNull("3 <= 20 so treated as sets count, weight null", p.weightLbs)
            assertEquals(10, p.reps)
        }
    }

    @Test
    fun `20x10 boundary - 20 is still treated as sets not weight`() = runTest {
        // SET_NOTATION rule: first number must be > 20 to become weight
        whenever(tools.findExercise(any())).thenReturn(BENCH)
        whenever(tools.getRecentSets(any(), any())).thenReturn(emptyList())
        whenever(applier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(1L, 1))

        orchestrator.process("bench 20x10")

        argumentCaptor<List<DbPatch>> {
            verify(applier).applyPatches(any(), capture(), eq(false))
            val p = firstValue.first() as DbPatch.LogSet
            assertNull("a=20 not > 20, so weight null", p.weightLbs)
            assertEquals(10, p.reps)
        }
    }

    @Test
    fun `21x10 treats 21 as weight`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH)
        whenever(tools.getRecentSets(any(), any())).thenReturn(emptyList())
        whenever(applier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(1L, 1))

        orchestrator.process("bench 21x10")

        argumentCaptor<List<DbPatch>> {
            verify(applier).applyPatches(any(), capture(), eq(false))
            val p = firstValue.first() as DbPatch.LogSet
            assertEquals(21, p.weightLbs)
            assertEquals(10, p.reps)
        }
    }

    @Test
    fun `explicit unit overrides set notation path`() = runTest {
        // "bench 135 lbs for 8" — no NxM, so unit+reps regex fires
        whenever(tools.findExercise(any())).thenReturn(BENCH)
        whenever(tools.getRecentSets(any(), any())).thenReturn(emptyList())
        whenever(applier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(1L, 1))

        orchestrator.process("bench 135 lbs for 8")

        argumentCaptor<List<DbPatch>> {
            verify(applier).applyPatches(any(), capture(), eq(false))
            val p = firstValue.first() as DbPatch.LogSet
            assertEquals(135, p.weightLbs)
            assertEquals(8, p.reps)
        }
    }

    // ── Bodyweight exercise — null weight is valid ───────────────────

    @Test
    fun `pull up for 10 reps logs with null weight`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(PULLUP)
        whenever(tools.getRecentSets(any(), any())).thenReturn(emptyList())
        whenever(applier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(1L, 1))

        // "logged" keyword fires LogSet rule; bodyweight exercise → null weight allowed
        val result = orchestrator.process("logged pull up for 10 reps")

        assertTrue("Expected Applied but got $result", result is AgentTurn.Applied)
        argumentCaptor<List<DbPatch>> {
            verify(applier).applyPatches(any(), capture(), eq(false))
            val p = firstValue.first() as DbPatch.LogSet
            assertNull(p.weightLbs)
            assertEquals(10, p.reps)
            assertTrue(p.isBodyweight)
        }
    }

    // ── Reps-only input (no weight) — still logs ─────────────────────

    @Test
    fun `log bench press for 8 reps without weight still logs with null weight`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH)
        whenever(tools.getRecentSets(any(), any())).thenReturn(emptyList())
        whenever(applier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(1L, 1))

        // "logged bench for 8" triggers LogSet keyword + reps regex.
        // No weight unit → weight null. Current behavior: logs anyway.
        orchestrator.process("logged bench for 8 reps")

        argumentCaptor<List<DbPatch>> {
            verify(applier).applyPatches(any(), capture(), eq(false))
            val p = firstValue.first() as DbPatch.LogSet
            assertNull(p.weightLbs)
            assertEquals(8, p.reps)
        }
    }

    // ── Weight alone, no reps → no patch ─────────────────────────────

    @Test
    fun `weight without reps returns clarify`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH)
        whenever(tools.getRecentSets(any(), any())).thenReturn(emptyList())

        // "logged bench 135 lbs" — weight yes, reps no. generateLogSet
        // requires reps so returns null → TextResponse.
        val result = orchestrator.process("logged bench 135 lbs")

        assertTrue("Expected TextResponse but got $result", result is AgentTurn.TextResponse)
        verify(applier, never()).applyPatches(any(), any(), any())
    }

    // ── Exercise not found ───────────────────────────────────────────

    @Test
    fun `unknown exercise in LogSet returns TextResponse`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(null)

        val result = orchestrator.process("xyzzy 135x8")

        assertTrue(result is AgentTurn.TextResponse)
        verify(applier, never()).applyPatches(any(), any(), any())
    }

    @Test
    fun `unknown exercise in AskHistory returns clarify message`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(null)

        val result = orchestrator.process("show me my xyzzy history")

        assertTrue(result is AgentTurn.TextResponse)
        val text = (result as AgentTurn.TextResponse).text
        assertTrue("Should ask which exercise: $text", text.contains("exercise"))
    }

    @Test
    fun `unknown exercise in AskRecommendation returns clarify`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(null)

        val result = orchestrator.process("what weight should i use for xyzzy")

        assertTrue(result is AgentTurn.TextResponse)
    }

    // ── Delete/rename without exercise match → clarify ───────────────

    @Test
    fun `delete with unknown exercise returns clarify without confirmation`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(null)

        val result = orchestrator.process("delete my last xyzzy set")

        assertTrue("No confirmation should be asked: $result", result is AgentTurn.TextResponse)
        verify(applier, never()).applyPatches(any(), any(), any())
    }

    @Test
    fun `delete with no recent sets returns clarify`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(emptyList())

        val result = orchestrator.process("delete my last bench press set")

        assertTrue("Expected TextResponse, got $result", result is AgentTurn.TextResponse)
        verify(applier, never()).applyPatches(any(), any(), any())
    }

    // ── Date edges: target day = today ───────────────────────────────

    @Test
    fun `moving to today's weekday moves one week forward`() = runTest {
        whenever(applier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(1L, 1))

        // TODAY is 2026-04-16 Thursday → "move to thursday" → next week Thursday
        orchestrator.process("move my workout to thursday")

        argumentCaptor<List<DbPatch>> {
            verify(applier).applyPatches(any(), capture(), eq(false))
            val p = firstValue.first() as DbPatch.MoveWorkoutDay
            assertEquals("2026-04-23", p.newDate)
        }
    }

    @Test
    fun `move to tomorrow adds one day`() = runTest {
        whenever(applier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(1L, 1))

        orchestrator.process("move my workout to tomorrow")

        argumentCaptor<List<DbPatch>> {
            verify(applier).applyPatches(any(), capture(), eq(false))
            val p = firstValue.first() as DbPatch.MoveWorkoutDay
            assertEquals("2026-04-17", p.newDate)
        }
    }

    @Test
    fun `move to monday lands on next Monday`() = runTest {
        whenever(applier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(1L, 1))

        orchestrator.process("move my workout to monday")

        argumentCaptor<List<DbPatch>> {
            verify(applier).applyPatches(any(), capture(), eq(false))
            val p = firstValue.first() as DbPatch.MoveWorkoutDay
            assertEquals("2026-04-20", p.newDate)
        }
    }

    @Test
    fun `move with no recognizable target returns clarify`() = runTest {
        val result = orchestrator.process("reschedule my workout to next month")

        assertTrue(result is AgentTurn.TextResponse)
        verify(applier, never()).applyPatches(any(), any(), any())
    }

    // ── Rename extraction ────────────────────────────────────────────

    @Test
    fun `rename X to Y extracts newName correctly`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH)

        val result = orchestrator.process("rename bench press to flat bench")

        assertTrue(result is AgentTurn.NeedsConfirmation)
        val p = (result as AgentTurn.NeedsConfirmation).patches.first() as DbPatch.RenameExercise
        assertEquals("flat bench", p.newName)
    }

    @Test
    fun `rename without 'to' keyword returns clarify`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH)

        val result = orchestrator.process("rename bench press flat bench")

        assertTrue("Expected TextResponse got $result", result is AgentTurn.TextResponse)
    }

    @Test
    fun `rename trims trailing period`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH)

        val result = orchestrator.process("rename bench press to flat bench.")

        assertTrue(result is AgentTurn.NeedsConfirmation)
        val p = (result as AgentTurn.NeedsConfirmation).patches.first() as DbPatch.RenameExercise
        assertEquals("flat bench", p.newName)
    }

    @Test
    fun `change name of X to Y pattern extracts newName`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH)

        val result = orchestrator.process("change the name of bench press to incline bench")

        assertTrue(result is AgentTurn.NeedsConfirmation)
        val p = (result as AgentTurn.NeedsConfirmation).patches.first() as DbPatch.RenameExercise
        assertEquals("incline bench", p.newName)
    }

    // ── Context-number fallback ("was", "meant", "to", "at") ──────────

    @Test
    fun `editSet with 'i meant 145' extracts 145 as weight`() = runTest {
        val existing = SetSummary(setId = 88L, date = TODAY, setNumber = 1,
                                  weightLbs = 135, reps = 8, isBodyweight = false)
        whenever(tools.findExercise(any())).thenReturn(BENCH)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(listOf(existing))
        whenever(applier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(1L, 1))

        orchestrator.process("fix bench i meant 145")

        argumentCaptor<List<DbPatch>> {
            verify(applier).applyPatches(any(), capture(), eq(false))
            val p = firstValue.first() as DbPatch.EditSet
            assertEquals(88L, p.setId)
            assertEquals(145, p.weightLbs)
        }
    }

    // ── Input whitespace / empty ─────────────────────────────────────

    @Test
    fun `empty input returns clarify without calling tools`() = runTest {
        val result = orchestrator.process("")
        assertTrue(result is AgentTurn.TextResponse)
        verifyNoInteractions(tools)
        verifyNoInteractions(applier)
    }

    @Test
    fun `whitespace-only input returns clarify`() = runTest {
        val result = orchestrator.process("   \t\n")
        assertTrue(result is AgentTurn.TextResponse)
        verifyNoInteractions(tools)
        verifyNoInteractions(applier)
    }

    // ── Confirm / dismiss flow ───────────────────────────────────────

    @Test
    fun `confirm with rejected result returns Error`() = runTest {
        whenever(applier.applyPatches(any(), any(), eq(true)))
            .thenReturn(PatchResult.Rejected("policy violation"))

        val result = orchestrator.confirm("req-x", listOf(DbPatch.DeleteSet(1L)))

        assertTrue(result is AgentTurn.Error)
        assertEquals("policy violation", (result as AgentTurn.Error).message)
    }

    @Test
    fun `confirm with failed result returns Error`() = runTest {
        whenever(applier.applyPatches(any(), any(), eq(true)))
            .thenReturn(PatchResult.Failed("DB error"))

        val result = orchestrator.confirm("req-x", listOf(DbPatch.DeleteSet(1L)))

        assertTrue(result is AgentTurn.Error)
    }

    // ── AskHistory with no data ──────────────────────────────────────

    @Test
    fun `askHistory window size is 30 days`() = runTest {
        whenever(tools.findExercise(any())).thenReturn(BENCH)
        whenever(tools.getExerciseHistory(eq(1L), eq(30)))
            .thenReturn(HistorySummary(1L, 30, 0, null, null, emptyList()))

        orchestrator.process("show me my bench press history")

        verify(tools).getExerciseHistory(eq(1L), eq(30))
    }

    // ── LogSet with multiple sets today picks max+1 ──────────────────

    @Test
    fun `setNumber skips non-today sets`() = runTest {
        val yesterday = SetSummary(10L, "2026-04-15", setNumber = 5,
                                   weightLbs = 135, reps = 8, isBodyweight = false)
        val todayOnly = SetSummary(11L, TODAY, setNumber = 2,
                                   weightLbs = 135, reps = 8, isBodyweight = false)
        whenever(tools.findExercise(any())).thenReturn(BENCH)
        whenever(tools.getRecentSets(eq(1L), any())).thenReturn(listOf(yesterday, todayOnly))
        whenever(applier.applyPatches(any(), any(), eq(false)))
            .thenReturn(PatchResult.Applied(1L, 1))

        orchestrator.process("bench 135x8")

        argumentCaptor<List<DbPatch>> {
            verify(applier).applyPatches(any(), capture(), eq(false))
            val p = firstValue.first() as DbPatch.LogSet
            assertEquals("Should use today's max+1, not yesterday's", 3, p.setNumber)
        }
    }
}
