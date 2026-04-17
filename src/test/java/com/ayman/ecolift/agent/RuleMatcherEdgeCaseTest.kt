package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.router.Intent
import com.ayman.ecolift.agent.router.PatchType
import com.ayman.ecolift.agent.router.ReadType
import com.ayman.ecolift.agent.router.RuleMatcher
import org.junit.Assert.*
import org.junit.Test

/**
 * Edge-case coverage for [RuleMatcher] — boundaries, substring pitfalls,
 * whitespace, casing, priority order, and SET_NOTATION thresholds.
 */
class RuleMatcherEdgeCaseTest {

    // ── Empty / whitespace input ──────────────────────────────────────

    @Test
    fun `empty string returns null`() {
        assertNull(RuleMatcher.match(""))
    }

    @Test
    fun `whitespace-only returns null`() {
        assertNull(RuleMatcher.match("    "))
        assertNull(RuleMatcher.match("\n\t"))
    }

    @Test
    fun `single word returns null`() {
        // "bench" alone has no weight+reps and no keyword
        assertNull(RuleMatcher.match("bench"))
    }

    // ── Case insensitivity ────────────────────────────────────────────

    @Test
    fun `uppercase DELETE matches DeleteSet`() {
        val match = RuleMatcher.match("DELETE MY LAST SET")
        assertNotNull(match)
        assertEquals(PatchType.DeleteSet, (match!!.intent as Intent.Write).patchType)
    }

    @Test
    fun `mixed case 3X10 matches LogSet`() {
        val match = RuleMatcher.match("Bench 3X10 At 185")
        assertNotNull(match)
        assertEquals(PatchType.LogSet, (match!!.intent as Intent.Write).patchType)
    }

    // ── SET_NOTATION boundary ─────────────────────────────────────────

    @Test
    fun `3x10 classified as LogSet`() {
        val match = RuleMatcher.match("bench 3x10")
        assertNotNull(match)
        assertEquals(PatchType.LogSet, (match!!.intent as Intent.Write).patchType)
    }

    @Test
    fun `set notation with extra spaces`() {
        val match = RuleMatcher.match("bench 3 x 10")
        assertNotNull(match)
        assertEquals(PatchType.LogSet, (match!!.intent as Intent.Write).patchType)
    }

    // ── "at N" weight detector ────────────────────────────────────────

    @Test
    fun `at 225 for 5 counts as weight plus reps`() {
        val match = RuleMatcher.match("bench at 225 for 5")
        assertNotNull(match)
        assertEquals(PatchType.LogSet, (match!!.intent as Intent.Write).patchType)
    }

    // ── Priority: Delete wins over EditSet/LogSet signals ────────────

    @Test
    fun `delete wins over weight+reps pattern`() {
        val match = RuleMatcher.match("delete my bench 135x8 entry")
        assertNotNull(match)
        assertEquals(PatchType.DeleteSet, (match!!.intent as Intent.Write).patchType)
    }

    @Test
    fun `edit wins over LogSet keyword`() {
        // "fix my" is EditSet, "logged" is LogSet — edit checked first
        val match = RuleMatcher.match("fix my logged bench set")
        assertNotNull(match)
        assertEquals(PatchType.EditSet, (match!!.intent as Intent.Write).patchType)
    }

    @Test
    fun `rename wins over delete when both appear`() {
        // "rename" check runs after "delete" — delete should still win for
        // a phrase that starts with delete.
        val match = RuleMatcher.match("delete and rename my bench")
        assertNotNull(match)
        assertEquals(PatchType.DeleteSet, (match!!.intent as Intent.Write).patchType)
    }

    // ── Substring false positives (known limitations) ────────────────

    @Test
    fun `call it quits substring false positive hits RenameExercise rule`() {
        // "call it " is a naive keyword — "call it quits" wrongly matches.
        // This is a KNOWN LIMITATION: orchestrator rejects it downstream
        // because no exercise is found, so user sees a clarify message.
        val match = RuleMatcher.match("call it quits")
        assertNotNull(match)
        assertEquals(PatchType.RenameExercise, (match!!.intent as Intent.Write).patchType)
    }

    @Test
    fun `remove my substring false positive hits DeleteSet rule`() {
        // "remove my" is naive — "remove my socks" wrongly matches DeleteSet.
        // Orchestrator returns clarify because 'socks' isn't an exercise.
        val match = RuleMatcher.match("remove my socks")
        assertNotNull(match)
        assertEquals(PatchType.DeleteSet, (match!!.intent as Intent.Write).patchType)
    }

    @Test
    fun `replace substring hits AskSimilar rule`() {
        // "replace " triggers AskSimilar — so "replace my last set" is
        // wrongly classified as a read, not EditSet.
        val match = RuleMatcher.match("replace my last set with 145")
        assertNotNull(match)
        assertTrue(
            "Known limitation: 'replace' fires AskSimilar, should be EditSet",
            match!!.intent is Intent.Read
        )
    }

    // ── Trailing punctuation ──────────────────────────────────────────

    @Test
    fun `trailing punctuation does not break matching`() {
        val match = RuleMatcher.match("delete my last set!!!")
        assertNotNull(match)
        assertEquals(PatchType.DeleteSet, (match!!.intent as Intent.Write).patchType)
    }

    @Test
    fun `question mark after recommendation still matches`() {
        val match = RuleMatcher.match("how much should i bench???")
        assertNotNull(match)
        assertEquals(ReadType.AskRecommendation, (match!!.intent as Intent.Read).queryType)
    }

    // ── LogSet fallback by keyword only ───────────────────────────────

    @Test
    fun `just did without numbers still matches LogSet keyword`() {
        // "just did" is a keyword — orchestrator will later ask for details
        // but the router should still classify as LogSet.
        val match = RuleMatcher.match("just did a bench set")
        assertNotNull(match)
        assertEquals(PatchType.LogSet, (match!!.intent as Intent.Write).patchType)
    }

    // ── Unicode / non-ASCII digits don't fool SET_NOTATION ───────────

    @Test
    fun `fullwidth digits do not match set notation`() {
        // Fullwidth '３' is not ASCII \d, so should not match
        val match = RuleMatcher.match("bench ３x１０")
        // Either null OR something — just confirm no crash
        // If no keyword fires, null is correct.
        // If it somehow matches something else, fail loudly so we know.
        if (match != null) {
            // only acceptable if it somehow matches via another keyword (shouldn't here)
            fail("Unexpected match for fullwidth digits: ${match.intent}")
        }
    }

    // ── Very long input ───────────────────────────────────────────────

    @Test
    fun `very long input still matches first-priority rule`() {
        val long = "delete " + "a ".repeat(500) + "set"
        val match = RuleMatcher.match(long)
        assertNotNull(match)
        assertEquals(PatchType.DeleteSet, (match!!.intent as Intent.Write).patchType)
    }

    // ── Embedded numbers without context ──────────────────────────────

    @Test
    fun `bare number without unit or reps returns null`() {
        // "bench 135" with no unit and no reps — should not classify as LogSet
        assertNull(RuleMatcher.match("bench 135"))
    }

    @Test
    fun `weight with unit but no reps returns null`() {
        // "bench 135 lbs" alone — has weight but no reps. Per hasWeightAndReps,
        // both must be present (SET_NOTATION handles the short form).
        assertNull(RuleMatcher.match("bench 135 lbs"))
    }

    // ── Read keywords edge cases ──────────────────────────────────────

    @Test
    fun `my progress on Y matches AskHistory`() {
        val match = RuleMatcher.match("my progress has been great")
        assertNotNull(match)
        assertEquals(ReadType.AskHistory, (match!!.intent as Intent.Read).queryType)
    }

    @Test
    fun `personal record matches AskHistory`() {
        val match = RuleMatcher.match("what's my personal record")
        assertNotNull(match)
        assertEquals(ReadType.AskHistory, (match!!.intent as Intent.Read).queryType)
    }
}
