package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.engine.LocalGenAiEngine
import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.model.InMemoryAuditStore
import com.ayman.ecolift.agent.patches.PatchService
import com.ayman.ecolift.agent.patches.PatchValidator
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.tools.AgentToolsImpl
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class IronMindRegressionTest {
    private val bench = Exercise(1, "Bench Press")
    private val yesterday = WorkoutSet(1, 1, "2026-10-22", 1, 2250, 5, completed = true)
    private val today = WorkoutSet(2, 1, "2026-10-23", 1, 2750, 5, completed = true)

    @Test
    fun historical_correction_changes_only_the_requested_day() = runTest {
        val repos = AgentMutationRepos(listOf(bench), listOf(yesterday, today))
        val turn = orchestrator(repos).process("fix my bench from yesterday to 185x5")
        assertTrue(turn.toString(), turn is AgentTurn.Applied)
        assertEquals(1850, repos.currentSets.value.first { it.id == 1L }.weightLbs)
        assertEquals(today, repos.currentSets.value.first { it.id == 2L })
    }

    @Test
    fun historical_delete_targets_the_requested_day() = runTest {
        val repos = AgentMutationRepos(listOf(bench), listOf(yesterday, today))
        val turn = orchestrator(repos).process("delete the extra bench set from yesterday") as AgentTurn.NeedsConfirmation
        assertEquals(listOf(DbPatch.DeleteSet(1)), turn.patches)
        assertEquals(listOf(yesterday, today), repos.currentSets.value)
    }

    @Test
    fun compact_set_count_logs_three_sets() = runTest {
        val repos = AgentMutationRepos(listOf(bench))
        val turn = orchestrator(repos).process("yesterday bench 225x5x3")
        assertTrue(turn.toString(), turn is AgentTurn.Applied)
        assertEquals(listOf(1, 2, 3), repos.currentSets.value.map { it.setNumber })
        assertEquals(listOf(2250 to 5, 2250 to 5, 2250 to 5), repos.currentSets.value.map { it.weightLbs to it.reps })
    }

    @Test
    fun unknown_exercises_do_not_write_or_receive_unrelated_advice() = runTest {
        val repos = AgentMutationRepos(listOf(Exercise(3, "Deadlift"), Exercise(4, "Seated Dumbbell Shoulder Press")))
        val tools = AgentToolsImpl(repos)
        assertNull(tools.findExercise("Zorblift"))
        val service = orchestrator(repos)
        assertTrue(service.process("Zorblift 45x8") is AgentTurn.RecoverableFailure)
        assertEquals(0, repos.currentSets.value.size)
        val advice = service.process("what weight should I use for Qzxqz") as AgentTurn.TextResponse
        assertFalse(advice.text, advice.text.contains("Shoulder"))
        assertTrue(service.process("deadlift 45x8") is AgentTurn.Applied)
        assertEquals(3L, repos.currentSets.value.single().exerciseId)
    }

    @Test
    fun unavailable_engine_does_not_offer_try_ai() = runTest {
        val repos = AgentMutationRepos(listOf(bench))
        val engine = object : LocalGenAiEngine {
            override val isReady = false
            override suspend fun warmup() = Unit
            override fun streamText(prompt: String): Flow<String> = error("Unavailable")
            override suspend fun generateStructured(prompt: String, schema: String): String = error("Unavailable")
            override fun close() = Unit
        }
        val turn = orchestrator(repos, engine).process("log leg thing three plates for twelve") as AgentTurn.RecoverableFailure
        assertFalse(turn.toString(), turn.canTryModel)
    }

    @Test
    fun invalid_weights_do_not_create_completed_sets() = runTest {
        val repos = AgentMutationRepos(listOf(bench))
        val service = orchestrator(repos)
        for (message in listOf("bench 0x5", "bench 999999999999x5", "bench -135x5")) {
            val turn = service.process(message)
            assertTrue("$message $turn", turn is AgentTurn.RecoverableFailure || turn is AgentTurn.TextResponse)
            assertEquals(message, 0, repos.currentSets.value.size)
        }
        assertTrue(service.process("bench 135.5x5") is AgentTurn.Applied)
        assertEquals(1355, repos.currentSets.value.single().weightLbs)
    }

    @Test
    fun weight_advice_uses_the_suggested_rep_count() = runTest {
        val repos = AgentMutationRepos(listOf(bench), listOf(today.copy(weightLbs = 2200, reps = 11)))
        val turn = orchestrator(repos).process("what weight should I use for bench press") as AgentTurn.TextResponse
        assertTrue(turn.text, turn.text.contains("for 12 reps"))
        assertFalse(turn.text, turn.text.contains("for 8 reps"))
    }

    private fun orchestrator(repos: AgentMutationRepos, engine: LocalGenAiEngine? = null) = AgentOrchestrator(
        IntentRouter(),
        AgentToolsImpl(repos),
        PatchService(repos, PatchValidator(), InMemoryAuditStore()),
        engine,
        today = { "2026-10-23" },
    )
}
