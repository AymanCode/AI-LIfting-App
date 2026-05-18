package com.ayman.ecolift.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRecoveryGuidanceTest {

    @Test
    fun `looksLikeWorkoutLog detects spoken messy workout logs`() {
        assertTrue(
            AgentRecoveryGuidance.looksLikeWorkoutLog(
                "just did Bechh Press one thirty five for seven"
            )
        )
    }

    @Test
    fun `looksLikeWorkoutLog detects shorthand weight rep logs`() {
        assertTrue(
            AgentRecoveryGuidance.looksLikeWorkoutLog(
                "i did Bechh Press 135x7, 125x10, .85x5"
            )
        )
    }

    @Test
    fun `looksLikeWorkoutLog ignores random unknown text`() {
        assertFalse(AgentRecoveryGuidance.looksLikeWorkoutLog("blorp morp snorp"))
    }

    @Test
    fun `shouldAvoidModelLogMutation detects ambiguous plate based logs`() {
        assertTrue(
            AgentRecoveryGuidance.shouldAvoidModelLogMutation(
                "leg thing yesterday was like 3 plates for 12 and 12"
            )
        )
    }

    @Test
    fun `logSetFailure preserves original text and gives editable template`() {
        val original = "just did Bechh Press one thirty five for seven"

        val draft = AgentRecoveryGuidance.logSetFailure(
            originalText = original,
            saveDate = "2026-04-16",
            canTryModel = false
        )

        assertEquals(original, draft.originalText)
        assertEquals("2026-04-16", draft.saveDate)
        assertFalse(draft.canTryModel)
        assertTrue(draft.detail.contains("I kept your text"))
        assertTrue(draft.detail.contains("exact exercise name"))
        assertTrue(draft.detail.contains("pounds"))
        assertTrue(draft.suggestedTemplate.contains("exercise name"))
        assertTrue(draft.suggestedTemplate.contains("135x8"))
    }
}
