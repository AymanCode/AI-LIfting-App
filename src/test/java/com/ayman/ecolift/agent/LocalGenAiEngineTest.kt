package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.engine.Prompts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for LocalGenAiEngine contract and Prompts templates.
 *
 * MediaPipeGenAiEngine cannot be unit-tested without a real model file —
 * that is covered by manual QA (see phase_4.md acceptance criteria).
 *
 * These tests verify:
 * 1. A compliant implementation satisfies the interface contract.
 * 2. Prompt templates contain required fields.
 * 3. Engine state transitions (not ready → warmup → ready).
 */
class LocalGenAiEngineTest {

    // ── Fake engine for contract testing ─────────────────────────────

    private class FakeEngine(private val response: String = "{}") : LocalGenAiEngine {
        private var _isReady = false
        override val isReady: Boolean get() = _isReady

        override suspend fun warmup() { _isReady = true }

        override fun streamText(prompt: String): Flow<String> = flowOf(response)

        override suspend fun generateStructured(prompt: String, schema: String): String = response

        override fun close() { _isReady = false }
    }

    // ── Interface contract ────────────────────────────────────────────

    @Test
    fun `engine not ready before warmup`() {
        val engine = FakeEngine()
        assertFalse(engine.isReady)
    }

    @Test
    fun `engine ready after warmup`() = runTest {
        val engine = FakeEngine()
        engine.warmup()
        assertTrue(engine.isReady)
    }

    @Test
    fun `warmup idempotent`() = runTest {
        val engine = FakeEngine()
        engine.warmup()
        engine.warmup()
        assertTrue(engine.isReady)
    }

    @Test
    fun `streamText emits at least one item`() = runTest {
        val engine = FakeEngine("hello")
        engine.warmup()
        val items = engine.streamText("test prompt").toList()
        assertTrue(items.isNotEmpty())
    }

    @Test
    fun `generateStructured returns non-empty string`() = runTest {
        val engine = FakeEngine("{\"intent\":\"LogSet\"}")
        engine.warmup()
        val result = engine.generateStructured("classify this", "{}")
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `close resets isReady`() = runTest {
        val engine = FakeEngine()
        engine.warmup()
        assertTrue(engine.isReady)
        engine.close()
        assertFalse(engine.isReady)
    }

    @Test
    fun `engine implements AutoCloseable`() {
        val engine: AutoCloseable = FakeEngine()
        // Can use try-with-resources pattern
        engine.use { }  // should not throw
    }

    // ── Prompts ───────────────────────────────────────────────────────

    @Test
    fun `intentClassification contains all intent labels`() {
        val prompt = Prompts.intentClassification("bench 3x10 at 135")
        listOf("LogSet", "EditSet", "DeleteSet", "MoveWorkoutDay", "RenameExercise",
            "AskRecommendation", "AskSimilar", "AskHistory", "Clarify"
        ).forEach { label ->
            assertTrue("Expected label '$label' in prompt", prompt.contains(label))
        }
    }

    @Test
    fun `intentClassification contains user text`() {
        val userText = "log bench press 135 for 8 reps"
        val prompt = Prompts.intentClassification(userText)
        assertTrue(prompt.contains(userText))
    }

    @Test
    fun `intentClassification includes context when provided`() {
        val prompt = Prompts.intentClassification("bench 3x10", recentContext = "last: squat day")
        assertTrue(prompt.contains("last: squat day"))
    }

    @Test
    fun `patchGeneration contains all key fields`() {
        val prompt = Prompts.patchGeneration(
            userText = "log 135x8 bench",
            intentLabel = "LogSet",
            groundedContext = "{\"exerciseId\": 1}",
            patchSchema = "{\"type\": \"LogSet\"}"
        )
        assertTrue(prompt.contains("LogSet"))
        assertTrue(prompt.contains("log 135x8 bench"))
        assertTrue(prompt.contains("{\"exerciseId\": 1}"))
        assertTrue(prompt.contains("{\"type\": \"LogSet\"}"))
    }

    @Test
    fun `explanation contains user text and patch summary`() {
        val prompt = Prompts.explanation(
            userText = "log 135x8",
            appliedPatches = "Logged Bench Press 135lbs x 8 reps"
        )
        assertTrue(prompt.contains("log 135x8"))
        assertTrue(prompt.contains("Logged Bench Press"))
    }

    @Test
    fun `formatReadResult contains query type and data`() {
        val prompt = Prompts.formatReadResult(
            queryType = "weight recommendation",
            resultJson = "{\"suggestedWeightLbs\": 140}",
            userText = "how much should I bench?"
        )
        assertTrue(prompt.contains("weight recommendation"))
        assertTrue(prompt.contains("140"))
        assertTrue(prompt.contains("how much should I bench?"))
    }

    @Test
    fun `clarify contains user text`() {
        val prompt = Prompts.clarify("do something with bench")
        assertTrue(prompt.contains("do something with bench"))
    }

    @Test
    fun `all prompts are non-empty`() {
        assertFalse(Prompts.intentClassification("test").isBlank())
        assertFalse(Prompts.patchGeneration("u", "LogSet", "{}", "{}").isBlank())
        assertFalse(Prompts.explanation("u", "p").isBlank())
        assertFalse(Prompts.formatReadResult("history", "{}", "u").isBlank())
        assertFalse(Prompts.clarify("unclear").isBlank())
    }
}
