package com.ayman.ecolift.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqCloudAgentTest {

    @Test
    fun `buildChatCompletionRequest uses Groq model and message payload`() {
        val body = GroqCloudJson.buildChatCompletionRequest(
            model = "llama-3.3-70b-versatile",
            prompt = "Return a workout tool call."
        )

        val json = JSONObject(body)
        assertEquals("llama-3.3-70b-versatile", json.getString("model"))
        assertEquals(2, json.getJSONArray("messages").length())
        assertEquals("system", json.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertTrue(json.getJSONArray("messages").getJSONObject(0).getString("content").contains("supportive workout coach"))
        assertEquals("user", json.getJSONArray("messages").getJSONObject(1).getString("role"))
        assertTrue(json.getJSONArray("messages").getJSONObject(1).getString("content").contains("workout"))
        assertFalse(json.has("n"))
    }

    @Test
    fun `IronMind prompt contains supportive progress analysis guardrails`() {
        val prompt = IronMindPromptBuilder.build(
            userMessage = "How is my bench progressing?",
            history = emptyList(),
            runtimeContext = AiRuntimeContext(
                today = "2026-05-13",
                cycleActive = true,
                cycleNumTypes = 3,
                nextSessionLabel = "Day A",
                currentSessionCompletionPercent = 40,
                currentSessionSummary = "2 of 5 sets marked complete today.",
                pendingReviewCount = 0,
                availableExercises = listOf("Bench Press"),
                exerciseProgressJson = "{\"exercises\":[{\"name\":\"Bench Press\"}]}",
            ),
            hasImage = false,
        )

        assertTrue(prompt.contains("coach analyzing a client's exercise progress"))
        assertTrue(prompt.contains("Never demean, shame, insult, or discourage the user"))
        assertTrue(prompt.contains("If progress is flat or trending downward"))
        assertTrue(prompt.contains("start fresh and track progress from now on"))
        assertTrue(prompt.contains("Exercise progress summary JSON"))
        assertTrue(prompt.contains("Bench Press"))
    }

    @Test
    fun `fallback message hides provider errors and asks user to try later`() {
        val message = IronMindFallbacks.userFacingServiceFailure(
            RuntimeException("Groq request failed (429): rate limit reached")
        )

        assertEquals("IronMind is temporarily unavailable. Please try again later.", message)
    }

    @Test
    fun `parseChatCompletion extracts assistant JSON content`() {
        val response = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\"assistant_message\":\"Logged it.\",\"tool\":\"calculate_1rm\",\"requires_confirmation\":false,\"parameters\":{\"weight\":225,\"reps\":5}}"
                  }
                }
              ]
            }
        """.trimIndent()

        val output = GroqCloudJson.parseChatCompletion(response)

        assertEquals("Logged it.", output.assistantMessage)
        assertEquals(AiToolName.Calculate1Rm, output.toolCall?.tool)
        assertEquals(225, output.toolCall?.weight)
        assertEquals(5, output.toolCall?.reps)
    }
}
