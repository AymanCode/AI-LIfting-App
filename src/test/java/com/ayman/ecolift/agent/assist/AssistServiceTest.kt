package com.ayman.ecolift.agent.assist

import com.ayman.ecolift.agent.tools.WeightSuggestion
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssistServiceTest {

    private fun trend() = com.ayman.ecolift.agent.tools.ProgressTrend(
        exerciseId = 1L, name = "Bench Press", sessionCount = 12,
        prWeightLbs = 2250, prDate = "2026-05-30", est1Rm = 250,
        deltaPercent = 6f, recentSessions = listOf("2026-06-10: 225x5"),
    )

    @Test
    fun suggestSetEntry_mapsWeightSuggestion() = runTest {
        val tools = FakeAgentTools(
            weightSuggestion = WeightSuggestion(
                exerciseId = 1L, targetReps = 5, suggestedWeightLbs = 2250,
                confidence = WeightSuggestion.Confidence.HIGH, reasoning = "hold",
            ),
        )
        val service = AssistService(tools, engine = null)

        val s = service.suggestSetEntry(exerciseId = 1L, targetReps = 5)

        assertEquals(2250, s.weightStored)
        assertEquals(5, s.reps)
        assertEquals(WeightSuggestion.Confidence.HIGH, s.confidence)
        assertTrue(s.hasData)
    }

    @Test
    fun suggestSetEntry_mapsSuggestedRepsWhenPresent() = runTest {
        val tools = FakeAgentTools(
            weightSuggestion = WeightSuggestion(
                exerciseId = 1L, targetReps = 8, suggestedWeightLbs = 2400,
                confidence = WeightSuggestion.Confidence.HIGH, reasoning = "one more",
                suggestedReps = 9,
            ),
        )
        val service = AssistService(tools, engine = null)

        val s = service.suggestSetEntry(exerciseId = 1L, targetReps = 8)

        assertEquals(2400, s.weightStored)
        assertEquals(9, s.reps)
    }

    @Test
    fun suggestSetEntry_noDataHasNoData() = runTest {
        val tools = FakeAgentTools(
            weightSuggestion = WeightSuggestion(
                exerciseId = 1L, targetReps = 5, suggestedWeightLbs = null,
                confidence = WeightSuggestion.Confidence.NO_DATA, reasoning = "none",
            ),
        )
        val service = AssistService(tools, engine = null)

        assertFalse(service.suggestSetEntry(1L, 5).hasData)
    }

    @Test
    fun checkInput_flagsOrderOfMagnitudeTypo() = runTest {
        val tools = FakeAgentTools(
            history = com.ayman.ecolift.agent.tools.HistorySummary(
                exerciseId = 1L, windowDays = 3650, sessionCount = 5,
                topSetWeightLbs = 2250, topSetReps = 5, recentSets = emptyList(),
            ),
        )
        val service = AssistService(tools, engine = null)

        // 2250 lb typed -> 22500 stored; usual top is 2250 stored (225 lb).
        val v = service.checkInput(1L, SetDraft(weightStored = 22500, reps = 5))

        assertTrue(v is InputVerdict.Suspect)
        assertEquals(2250, (v as InputVerdict.Suspect).suggestedFix?.weightStored)
    }

    @Test
    fun checkInput_allowsPlausibleJump() = runTest {
        val tools = FakeAgentTools(
            history = com.ayman.ecolift.agent.tools.HistorySummary(
                exerciseId = 1L, windowDays = 3650, sessionCount = 5,
                topSetWeightLbs = 2250, topSetReps = 5, recentSets = emptyList(),
            ),
        )
        val service = AssistService(tools, engine = null)

        // 235 lb -> 2350 stored, a normal progression. Not flagged.
        assertEquals(InputVerdict.Ok, service.checkInput(1L, SetDraft(weightStored = 2350, reps = 5)))
    }

    @Test
    fun checkInput_flagsImpossibleReps() = runTest {
        val service = AssistService(FakeAgentTools(), engine = null)
        assertTrue(service.checkInput(1L, SetDraft(weightStored = null, reps = 250)) is InputVerdict.Suspect)
    }

    @Test
    fun analyze_nullEngine_returnsLocalSummary() = runTest {
        val service = AssistService(FakeAgentTools(trend = trend()), engine = null)
        val a = service.analyzeExercise(1L)
        assertEquals(AnalysisSource.Local, a.source)
        assertTrue(a.text.contains("Bench Press"))
    }

    @Test
    fun analyze_validProse_returnsGroq() = runTest {
        val engine = FakeEngine("Bench Press is trending up about +6%. Your best is 225 lb.")
        val service = AssistService(FakeAgentTools(trend = trend()), engine = engine)
        val a = service.analyzeExercise(1L)
        assertEquals(AnalysisSource.Groq, a.source)
        assertTrue(a.validated)
    }

    @Test
    fun analyze_contradictoryProse_fallsBackToLocal() = runTest {
        val engine = FakeEngine("Massive new PR of 405 lb, up 50%!")
        val service = AssistService(FakeAgentTools(trend = trend()), engine = engine)
        val a = service.analyzeExercise(1L)
        assertEquals(AnalysisSource.Local, a.source)
        assertFalse(a.validated)
    }

    @Test
    fun analyze_blankProse_fallsBackToLocal() = runTest {
        val service = AssistService(FakeAgentTools(trend = trend()), engine = FakeEngine(""))
        assertEquals(AnalysisSource.Local, service.analyzeExercise(1L).source)
    }
}
