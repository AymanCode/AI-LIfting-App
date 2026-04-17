package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.router.Intent
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.router.PatchType
import com.ayman.ecolift.agent.router.ReadType
import com.ayman.ecolift.agent.router.RuleMatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class IntentRouterTest {

    // Router without engine — unmatched rules → Clarify, no model calls
    private val router = IntentRouter(engine = null)

    // ── Table-driven rule coverage ────────────────────────────────────

    private data class Case(val text: String, val expected: Class<out Intent>, val patchType: PatchType? = null, val readType: ReadType? = null)

    private val cases = listOf(
        // ── LogSet ──
        Case("bench press 135 lbs for 8 reps", Intent.Write::class.java, PatchType.LogSet),
        Case("log bench press 3x10 at 135", Intent.Write::class.java, PatchType.LogSet),
        Case("i did squat 225 lbs 5 reps", Intent.Write::class.java, PatchType.LogSet),
        Case("just did deadlift 315lbs x5", Intent.Write::class.java, PatchType.LogSet),
        Case("add a set: overhead press 95kg 8 reps", Intent.Write::class.java, PatchType.LogSet),
        Case("just finished bench 5x5 at 185 pounds", Intent.Write::class.java, PatchType.LogSet),
        Case("logged 3 sets of curls at 40 lbs 12 reps", Intent.Write::class.java, PatchType.LogSet),

        // ── EditSet ──
        Case("fix my last set weight was actually 145", Intent.Write::class.java, PatchType.EditSet),
        Case("that was wrong it was 8 reps not 6", Intent.Write::class.java, PatchType.EditSet),
        Case("correct my last bench press", Intent.Write::class.java, PatchType.EditSet),
        Case("i meant 135 not 125", Intent.Write::class.java, PatchType.EditSet),
        Case("update my last set to 10 reps", Intent.Write::class.java, PatchType.EditSet),
        Case("wrong weight on that last set", Intent.Write::class.java, PatchType.EditSet),

        // ── DeleteSet ──
        Case("delete my last set", Intent.Write::class.java, PatchType.DeleteSet),
        Case("remove my last bench press entry", Intent.Write::class.java, PatchType.DeleteSet),
        Case("erase that set", Intent.Write::class.java, PatchType.DeleteSet),
        Case("get rid of that last entry", Intent.Write::class.java, PatchType.DeleteSet),

        // ── MoveWorkoutDay ──
        Case("reschedule my workout to friday", Intent.Write::class.java, PatchType.MoveWorkoutDay),
        Case("move my workout to tomorrow", Intent.Write::class.java, PatchType.MoveWorkoutDay),
        Case("postpone today's session", Intent.Write::class.java, PatchType.MoveWorkoutDay),
        Case("shift my workout to saturday", Intent.Write::class.java, PatchType.MoveWorkoutDay),

        // ── RenameExercise ──
        Case("rename bench press to flat bench", Intent.Write::class.java, PatchType.RenameExercise),
        Case("call it incline press from now on", Intent.Write::class.java, PatchType.RenameExercise),
        Case("change the name of squat to back squat", Intent.Write::class.java, PatchType.RenameExercise),

        // ── AskRecommendation ──
        Case("how much should i bench press today", Intent.Read::class.java, readType = ReadType.AskRecommendation),
        Case("what weight should i use for squats", Intent.Read::class.java, readType = ReadType.AskRecommendation),
        Case("suggest a weight for overhead press", Intent.Read::class.java, readType = ReadType.AskRecommendation),
        Case("what should i use for incline bench", Intent.Read::class.java, readType = ReadType.AskRecommendation),
        Case("starting weight for deadlift", Intent.Read::class.java, readType = ReadType.AskRecommendation),

        // ── AskSimilar ──
        Case("what's a good alternative to bench press", Intent.Read::class.java, readType = ReadType.AskSimilar),
        Case("something similar to pull ups", Intent.Read::class.java, readType = ReadType.AskSimilar),
        Case("what can i do instead of deadlift", Intent.Read::class.java, readType = ReadType.AskSimilar),
        Case("alternatives for squats", Intent.Read::class.java, readType = ReadType.AskSimilar),

        // ── AskHistory ──
        Case("show me my bench press history", Intent.Read::class.java, readType = ReadType.AskHistory),
        Case("how did i do last monday", Intent.Read::class.java, readType = ReadType.AskHistory),
        Case("my progress on squat", Intent.Read::class.java, readType = ReadType.AskHistory),
        Case("last time i did deadlift", Intent.Read::class.java, readType = ReadType.AskHistory),
        Case("what's my personal record on bench", Intent.Read::class.java, readType = ReadType.AskHistory),
        Case("my best squat ever", Intent.Read::class.java, readType = ReadType.AskHistory),
    )

    @Test
    fun `all table cases matched by rule matcher`() {
        var ruleHits = 0
        var ruleMisses = 0
        val missed = mutableListOf<String>()

        for (case in cases) {
            val match = RuleMatcher.match(case.text)
            if (match != null) {
                ruleHits++
                assertEquals(
                    "Wrong intent class for '${case.text}'",
                    case.expected, match.intent::class.java
                )
                when (val intent = match.intent) {
                    is Intent.Write -> assertEquals(
                        "Wrong PatchType for '${case.text}'",
                        case.patchType, intent.patchType
                    )
                    is Intent.Read -> assertEquals(
                        "Wrong ReadType for '${case.text}'",
                        case.readType, intent.queryType
                    )
                    is Intent.Clarify -> {}
                }
            } else {
                ruleMisses++
                missed.add(case.text)
            }
        }

        val coveragePct = ruleHits.toDouble() / cases.size * 100
        println("Rule coverage: $ruleHits/${cases.size} (${coveragePct.toInt()}%)")
        println("Missed by rules: $missed")

        // Require at least 75% rule coverage
        assertTrue(
            "Rule coverage $coveragePct% < 75%. Missed: $missed",
            coveragePct >= 75.0
        )
    }

    // ── Individual rule tests ─────────────────────────────────────────

    @Test
    fun `weight+reps pattern detected as LogSet`() {
        listOf(
            "135 lbs 8 reps",
            "bench 3x10 at 185",
            "225 pounds for 5",
            "deadlift 5 x 3 at 315kg",
        ).forEach { text ->
            val match = RuleMatcher.match(text)
            assertNotNull("Expected match for '$text'", match)
            assertEquals(Intent.Write::class.java, match!!.intent::class.java)
            assertEquals(PatchType.LogSet, (match.intent as Intent.Write).patchType)
        }
    }

    @Test
    fun `delete keywords match DeleteSet`() {
        listOf("delete my last set", "erase that", "remove my entry").forEach { text ->
            val match = RuleMatcher.match(text)
            assertNotNull("Expected match for '$text'", match)
            assertEquals(PatchType.DeleteSet, (match!!.intent as Intent.Write).patchType)
        }
    }

    @Test
    fun `edit keywords match EditSet`() {
        listOf("fix my last set", "that was wrong", "i meant 135").forEach { text ->
            val match = RuleMatcher.match(text)
            assertNotNull("Expected match for '$text'", match)
            assertEquals(PatchType.EditSet, (match!!.intent as Intent.Write).patchType)
        }
    }

    @Test
    fun `recommendation keywords match AskRecommendation`() {
        listOf("how much should i lift", "what weight should i use").forEach { text ->
            val match = RuleMatcher.match(text)
            assertNotNull("Expected match for '$text'", match)
            assertEquals(ReadType.AskRecommendation, (match!!.intent as Intent.Read).queryType)
        }
    }

    @Test
    fun `delete takes priority over log when both could match`() {
        // "delete my last bench press set" should be DeleteSet, not LogSet
        val match = RuleMatcher.match("delete my last bench press set")
        assertNotNull(match)
        assertEquals(PatchType.DeleteSet, (match!!.intent as Intent.Write).patchType)
    }

    @Test
    fun `rule confidence is between 0 and 1`() {
        cases.forEach { case ->
            val match = RuleMatcher.match(case.text) ?: return@forEach
            assertTrue(
                "Confidence ${match.confidence} out of range for '${case.text}'",
                match.confidence in 0f..1f
            )
        }
    }

    // ── Router fallback ───────────────────────────────────────────────

    @Test
    fun `unmatched text with no engine returns Clarify`() = runTest {
        val result = router.route("blorp morp snorp")
        assertTrue(result.intent is Intent.Clarify)
        assertEquals(IntentRouter.RoutingResult.Source.FALLBACK, result.source)
    }

    @Test
    fun `matched rule returns RULE source`() = runTest {
        val result = router.route("delete my last set")
        assertEquals(IntentRouter.RoutingResult.Source.RULE, result.source)
    }

    @Test
    fun `rawText preserved in Write intent`() = runTest {
        val text = "log bench 135x8"
        val result = router.route(text)
        val intent = result.intent
        assertTrue(intent is Intent.Write)
        assertEquals(text, (intent as Intent.Write).rawText)
    }

    @Test
    fun `rawText preserved in Read intent`() = runTest {
        val text = "show me my squat history"
        val result = router.route(text)
        val intent = result.intent
        assertTrue(intent is Intent.Read)
        assertEquals(text, (intent as Intent.Read).rawText)
    }

    // ── Model fallback with fake engine ──────────────────────────────

    @Test
    fun `model fallback parses LogSet label`() = runTest {
        val fakeEngine = FakeReadyEngine("LogSet")
        val routerWithModel = IntentRouter(engine = fakeEngine)
        val result = routerWithModel.route("blorp morp")  // won't match rules
        val intent = result.intent
        assertTrue("Expected Write but got $intent", intent is Intent.Write)
        assertEquals(PatchType.LogSet, (intent as Intent.Write).patchType)
        assertEquals(IntentRouter.RoutingResult.Source.MODEL, result.source)
    }

    @Test
    fun `model fallback parses AskHistory label`() = runTest {
        val fakeEngine = FakeReadyEngine("AskHistory")
        val routerWithModel = IntentRouter(engine = fakeEngine)
        val result = routerWithModel.route("flimble")
        val intent = result.intent
        assertTrue(intent is Intent.Read)
        assertEquals(ReadType.AskHistory, (intent as Intent.Read).queryType)
    }

    @Test
    fun `model returning unknown label falls back to Clarify`() = runTest {
        val fakeEngine = FakeReadyEngine("banana")
        val routerWithModel = IntentRouter(engine = fakeEngine)
        val result = routerWithModel.route("flimble")
        assertTrue(result.intent is Intent.Clarify)
    }
}
