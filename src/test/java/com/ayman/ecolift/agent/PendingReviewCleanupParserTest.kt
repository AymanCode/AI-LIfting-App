package com.ayman.ecolift.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingReviewCleanupParserTest {

    @Test
    fun `extracts exercise from all pending are command`() {
        assertEquals(
            "Hip Abduction",
            PendingReviewCleanupParser.extractExerciseName("all pending review items are Hip Abduction")
        )
    }

    @Test
    fun `extracts exercise from treat pending as command`() {
        assertEquals(
            "Bench Press",
            PendingReviewCleanupParser.extractExerciseName("treat all pending as Bench Press")
        )
    }

    @Test
    fun `ignores unrelated prompts`() {
        assertNull(PendingReviewCleanupParser.extractExerciseName("how is my bench trending"))
    }
}
