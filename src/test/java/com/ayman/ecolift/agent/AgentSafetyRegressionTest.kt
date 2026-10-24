package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.model.InMemoryAuditStore
import com.ayman.ecolift.agent.patches.PatchService
import com.ayman.ecolift.agent.patches.PatchValidator
import com.ayman.ecolift.agent.router.IntentRouter
import com.ayman.ecolift.agent.tools.AgentToolsImpl
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.web.demo.DemoData
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSafetyRegressionTest {
    private val catalog = DemoData.generate(LocalDate.parse("2026-10-23")).exercises

    @Test
    fun historical_correction_describes_the_row_it_changes() = runTest {
        val squat = catalog.single { it.name == "Back Squat" }
        val repos = AgentMutationRepos(
            catalog,
            listOf(
                WorkoutSet(id = 101, exerciseId = squat.id, date = "2026-10-22", setNumber = 3, weightLbs = 2250, reps = 5, completed = true),
                WorkoutSet(id = 102, exerciseId = squat.id, date = "2026-10-23", setNumber = 1, weightLbs = 2750, reps = 5, completed = true),
            ),
        )
        val result = orchestrator(repos).process("fix my squat from yesterday to 185x5")
        assertTrue(result.toString(), result is AgentTurn.Applied)
        assertEquals(listOf("Back Squat · set 3: 225 → 185 lb · 5 → 5 reps"), (result as AgentTurn.Applied).changes)
        assertEquals(listOf(1850, 2750), repos.currentSets.value.map { it.weightLbs })
    }

    @Test
    fun date_phrases_target_the_squat_on_the_requested_day() = runTest {
        val squat = catalog.single { it.name == "Back Squat" }
        for ((phrase, date) in listOf("last saturday" to "2026-10-17", "May 10" to "2026-05-10")) {
            val repos = AgentMutationRepos(
                catalog,
                listOf(
                    WorkoutSet(id = 101, exerciseId = squat.id, date = date, setNumber = 1, reps = 5, completed = true),
                    WorkoutSet(id = 102, exerciseId = squat.id, date = "2026-10-23", setNumber = 1, reps = 8, completed = true),
                ),
            )
            val orchestrator = orchestrator(repos)
            val pending = orchestrator.process("delete the extra back squat set from $phrase")
            assertTrue("$phrase produced $pending", pending is AgentTurn.NeedsConfirmation)
            pending as AgentTurn.NeedsConfirmation
            assertEquals(listOf(DbPatch.DeleteSet(101)), pending.patches)
            assertTrue(orchestrator.confirm(pending.requestId, pending.patches) is AgentTurn.Applied)
            assertEquals(listOf(102L), repos.currentSets.value.map { it.id })
        }
    }

    @Test
    fun flat_bench_matches_real_catalog_and_exact_bench_catalog() = runTest {
        for (exercises in listOf(catalog, listOf(Exercise(id = 1, name = "Bench Press")))) {
            val repos = AgentMutationRepos(exercises)
            val turn = orchestrator(repos).process("flat bench press 135x5")
            assertTrue(turn.toString(), turn is AgentTurn.Applied)
            val saved = repos.currentSets.value.single()
            assertEquals(exercises.single { it.name.endsWith("Bench Press") && !it.name.startsWith("Incline") }.id, saved.exerciseId)
            assertEquals(1350, saved.weightLbs)
            assertEquals(5, saved.reps)
        }
    }

    @Test
    fun matcher_keeps_unknown_names_and_meaningful_modifiers_unresolved() = runTest {
        val tools = AgentToolsImpl(AgentMutationRepos(catalog))
        for (query in listOf("Zorblift", "Zorblift bench press", "incline zorblift press", "deficit deadlift", "bench pressure")) {
            assertNull(query, tools.findExercise(query))
        }
        val custom = AgentToolsImpl(AgentMutationRepos(listOf(Exercise(id = 1, name = "Bench Press"), Exercise(id = 2, name = "Flat Bench Press"))))
        assertEquals(2L, custom.findExercise("flat bench press")?.exerciseId)
        assertEquals(1L, custom.findExercise("bench press please")?.exerciseId)
        for ((query, name) in listOf("bechh press" to "Barbell Bench Press", "calf raises" to "Standing Calf Raise", "deadlift" to "Deadlift")) {
            assertEquals(query, catalog.single { it.name == name }.id, tools.findExercise(query)?.exerciseId)
        }
    }

    @Test
    fun mixed_notation_saves_literal_ordinals_in_written_order() = runTest {
        val repos = AgentMutationRepos(catalog)
        val result = orchestrator(repos).process("bench 135x8, 145x5x3")
        assertTrue(result.toString(), result is AgentTurn.Applied)
        assertEquals(
            listOf(Triple(1, 1350, 8), Triple(2, 1450, 5), Triple(3, 1450, 5), Triple(4, 1450, 5)),
            repos.currentSets.value.map { Triple(it.setNumber, it.weightLbs, it.reps) },
        )
        assertEquals(
            listOf(1350 to 8, 1450 to 5, 1450 to 5, 1550 to 4),
            LogSetTextParser.parseOneExercise("bench 135 lb x 8, 145x5x2, 155x4")!!.sets.map { it.weightLbs to it.reps },
        )
    }

    private fun orchestrator(repos: AgentMutationRepos) = AgentOrchestrator(
        router = IntentRouter(),
        tools = AgentToolsImpl(repos),
        patchApplier = PatchService(repos, PatchValidator(), InMemoryAuditStore()),
        today = { "2026-10-23" },
    )
}
