package com.ayman.ecolift.agent.assist

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalysisValidatorTest {

    private val facts = ExerciseAnalysisFacts(
        name = "Bench Press",
        sessionCount = 12,
        prDisplayLbs = 225,
        prDate = "2026-05-30",
        est1RmDisplayLbs = 250,
        deltaPercent = 6f,
        recentSessions = listOf("2026-06-10: 225x5", "2026-06-03: 215x5"),
    )

    @Test
    fun acceptsConsistentProse() {
        val prose = "Bench Press is trending up, about +6%. Your best is 225 lb and recent top sets sit near 215 lb."
        assertTrue(AnalysisValidator.validate(facts, prose))
    }

    @Test
    fun rejectsInventedLoad() {
        val prose = "Great work — you just hit 315 lb on bench!"
        assertFalse(AnalysisValidator.validate(facts, prose))
    }

    @Test
    fun rejectsFabricatedPrWhenNoPr() {
        val noPr = facts.copy(prDisplayLbs = null, prDate = null)
        val prose = "You set a new personal record this week."
        assertFalse(AnalysisValidator.validate(noPr, prose))
    }

    @Test
    fun rejectsReversedTrend() {
        val prose = "Your bench is clearly declining lately."
        assertFalse(AnalysisValidator.validate(facts, prose)) // delta is +6, not negative
    }

    @Test
    fun rejectsWrongPercent() {
        val prose = "Up about 40% over the last month."
        assertFalse(AnalysisValidator.validate(facts, prose)) // only ~6% allowed
    }
}
