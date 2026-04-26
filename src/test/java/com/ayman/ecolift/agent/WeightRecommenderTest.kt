package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.tools.HistorySummary
import com.ayman.ecolift.agent.tools.WeightRecommender
import com.ayman.ecolift.agent.tools.WeightSuggestion
import com.ayman.ecolift.data.WeightLbs
import org.junit.Assert.*
import org.junit.Test

class WeightRecommenderTest {

    private fun history(
        exerciseId: Long = 1L,
        sessionCount: Int = 3,
        topSetWeightLbs: Int? = lbs(135),
        topSetReps: Int? = 8
    ) = HistorySummary(
        exerciseId = exerciseId,
        windowDays = 30,
        sessionCount = sessionCount,
        topSetWeightLbs = topSetWeightLbs,
        topSetReps = topSetReps,
        recentSets = emptyList()
    )

    private fun lbs(value: Int): Int = WeightLbs.fromWholePounds(value)!!

    // ── No data ──────────────────────────────────────────────────────

    @Test
    fun `no history returns NO_DATA`() {
        val result = WeightRecommender.suggest(history(sessionCount = 0, topSetWeightLbs = null, topSetReps = null), 8, false)
        assertEquals(WeightSuggestion.Confidence.NO_DATA, result.confidence)
        assertNull(result.suggestedWeightLbs)
    }

    @Test
    fun `null weight returns NO_DATA`() {
        val result = WeightRecommender.suggest(history(topSetWeightLbs = null), 8, false)
        assertEquals(WeightSuggestion.Confidence.NO_DATA, result.confidence)
    }

    // ── Bodyweight ───────────────────────────────────────────────────

    @Test
    fun `bodyweight exercise returns null weight with MEDIUM confidence`() {
        val result = WeightRecommender.suggest(history(), 12, isBodyweight = true)
        assertNull(result.suggestedWeightLbs)
        assertEquals(WeightSuggestion.Confidence.MEDIUM, result.confidence)
    }

    // ── Increase ─────────────────────────────────────────────────────

    @Test
    fun `reps 2 above target triggers increase by 5`() {
        // Last top set: 135 × 10 reps. Target: 8 reps. 10 >= 8+2 → increase
        val result = WeightRecommender.suggest(history(topSetWeightLbs = lbs(135), topSetReps = 10), 8, false)
        assertEquals(lbs(140), result.suggestedWeightLbs)
        assertTrue(result.reasoning.contains("increase"))
    }

    @Test
    fun `reps exactly 2 above target triggers increase`() {
        val result = WeightRecommender.suggest(history(topSetWeightLbs = lbs(100), topSetReps = 10), 8, false)
        assertEquals(lbs(105), result.suggestedWeightLbs)
    }

    @Test
    fun `reps 3 above target triggers increase`() {
        val result = WeightRecommender.suggest(history(topSetWeightLbs = lbs(200), topSetReps = 11), 8, false)
        assertEquals(lbs(205), result.suggestedWeightLbs)
    }

    // ── Decrease ─────────────────────────────────────────────────────

    @Test
    fun `reps below target triggers decrease by 5`() {
        // Last top set: 135 × 5 reps. Target: 8 reps. 5 < 8 → decrease
        val result = WeightRecommender.suggest(history(topSetWeightLbs = lbs(135), topSetReps = 5), 8, false)
        assertEquals(lbs(130), result.suggestedWeightLbs)
        assertTrue(result.reasoning.contains("decrease"))
    }

    @Test
    fun `decrease floored at 5 lbs minimum`() {
        val result = WeightRecommender.suggest(history(topSetWeightLbs = lbs(5), topSetReps = 3), 8, false)
        assertEquals(lbs(5), result.suggestedWeightLbs)
    }

    // ── Hold ─────────────────────────────────────────────────────────

    @Test
    fun `reps exactly at target holds weight`() {
        val result = WeightRecommender.suggest(history(topSetWeightLbs = lbs(135), topSetReps = 8), 8, false)
        assertEquals(lbs(135), result.suggestedWeightLbs)
        assertTrue(result.reasoning.contains("hold") || result.reasoning.contains("Hold"))
    }

    @Test
    fun `reps 1 above target holds weight`() {
        // 9 reps, target 8 — not enough to increment (need +2)
        val result = WeightRecommender.suggest(history(topSetWeightLbs = lbs(135), topSetReps = 9), 8, false)
        assertEquals(lbs(135), result.suggestedWeightLbs)
    }

    // ── Confidence ───────────────────────────────────────────────────

    @Test
    fun `3 or more sessions gives HIGH confidence`() {
        val result = WeightRecommender.suggest(history(sessionCount = 3), 8, false)
        assertEquals(WeightSuggestion.Confidence.HIGH, result.confidence)
    }

    @Test
    fun `1 session gives MEDIUM confidence`() {
        val result = WeightRecommender.suggest(history(sessionCount = 1), 8, false)
        assertEquals(WeightSuggestion.Confidence.MEDIUM, result.confidence)
    }

    @Test
    fun `5 sessions gives HIGH confidence`() {
        val result = WeightRecommender.suggest(history(sessionCount = 5), 8, false)
        assertEquals(WeightSuggestion.Confidence.HIGH, result.confidence)
    }

    // ── exerciseId propagated ─────────────────────────────────────────

    @Test
    fun `exerciseId in result matches input`() {
        val result = WeightRecommender.suggest(history(exerciseId = 42L), 8, false)
        assertEquals(42L, result.exerciseId)
    }

    @Test
    fun `targetReps in result matches input`() {
        val result = WeightRecommender.suggest(history(), 5, false)
        assertEquals(5, result.targetReps)
    }
}
