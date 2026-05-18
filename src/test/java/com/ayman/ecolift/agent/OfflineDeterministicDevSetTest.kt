package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.router.Intent
import com.ayman.ecolift.agent.router.PatchType
import com.ayman.ecolift.agent.router.ReadType
import com.ayman.ecolift.agent.router.RuleMatcher
import com.ayman.ecolift.data.WeightLbs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDeterministicDevSetTest {

    @Test
    fun `dev correction phrases route as edits before log parsing`() {
        listOf(
            "bench from last thursday should be 190x4 not 180x4",
            "deadlift yesterday was 3 reps not 5",
            "squat max on may 4 is wrong it was 285x1"
        ).forEach { text ->
            val match = RuleMatcher.match(text)
            assertNotNull("Expected rule match for '$text'", match)
            assertEquals(PatchType.EditSet, (match!!.intent as Intent.Write).patchType)
        }
    }

    @Test
    fun `dev historical destructive phrases route as delete`() {
        listOf(
            "remove last deadlift from may 10",
            "take out calf raise from last night",
            "delete extra squat set from yesterday"
        ).forEach { text ->
            val match = RuleMatcher.match(text)
            assertNotNull("Expected rule match for '$text'", match)
            assertEquals(PatchType.DeleteSet, (match!!.intent as Intent.Write).patchType)
        }
    }

    @Test
    fun `dev casual read prompts route without model`() {
        mapOf(
            "best deadlift?" to ReadType.AskHistory,
            "deadlift going up?" to ReadType.QueryProgress,
            "bench history" to ReadType.AskHistory,
            "pull up my workout from may 10" to ReadType.QueryDate,
            "anything like lat pulldown" to ReadType.AskSimilar,
            "what should i press for overhead next" to ReadType.AskRecommendation
        ).forEach { (text, expected) ->
            val match = RuleMatcher.match(text)
            assertNotNull("Expected rule match for '$text'", match)
            assertEquals(expected, (match!!.intent as Intent.Read).queryType)
        }
    }

    @Test
    fun `dev cleanup commands extract target exercise`() {
        mapOf(
            "the pending rows are lat pulldown" to "lat pulldown",
            "make pending rows bench press" to "bench press",
            "pending ones are calf raise" to "calf raise"
        ).forEach { (text, expected) ->
            assertEquals(expected, PendingReviewCleanupParser.extractExerciseName(text))
        }
    }

    @Test
    fun `dev ambiguous machine logs are recoverable drafts`() {
        listOf(
            "that cable machine yesterday was 80x12",
            "leg thing was 3 plates for 12 yesterday",
            "machine from last time was 90 for 10"
        ).forEach { text ->
            assertTrue("Expected recoverable workout-like text for '$text'", AgentRecoveryGuidance.looksLikeWorkoutLog(text))
        }
    }

    @Test
    fun `dev casual historical log parser handles bare weight for rep lists`() {
        val parsed = LogSetTextParser.parseOneExercise("forgot yesterday calves 90 for 12,10,8")

        requireNotNull(parsed)
        assertEquals("calves", parsed.exerciseQuery)
        assertEquals(
            listOf(
                WeightLbs.fromWholePounds(90),
                WeightLbs.fromWholePounds(90),
                WeightLbs.fromWholePounds(90)
            ),
            parsed.sets.map { it.weightLbs }
        )
        assertEquals(listOf(12, 10, 8), parsed.sets.map { it.reps })
    }

    @Test
    fun `dev log parser handles then-separated bare weight rep pairs`() {
        val parsed = LogSetTextParser.parseOneExercise("bench yesterday 185 for 5 then 165 for 8")

        requireNotNull(parsed)
        assertEquals("bench", parsed.exerciseQuery)
        assertEquals(
            listOf(
                WeightLbs.fromWholePounds(185),
                WeightLbs.fromWholePounds(165)
            ),
            parsed.sets.map { it.weightLbs }
        )
        assertEquals(listOf(5, 8), parsed.sets.map { it.reps })
    }

    @Test
    fun `dev log parser handles space-separated reps after for`() {
        val parsed = LogSetTextParser.parseOneExercise("monday lat pulldown 120 for 10 10 8")

        requireNotNull(parsed)
        assertEquals("lat pulldown", parsed.exerciseQuery)
        assertEquals(
            listOf(
                WeightLbs.fromWholePounds(120),
                WeightLbs.fromWholePounds(120),
                WeightLbs.fromWholePounds(120)
            ),
            parsed.sets.map { it.weightLbs }
        )
        assertEquals(listOf(10, 10, 8), parsed.sets.map { it.reps })
    }
}
