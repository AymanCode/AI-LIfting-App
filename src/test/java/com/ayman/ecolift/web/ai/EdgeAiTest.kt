package com.ayman.ecolift.web.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EdgeAiTest {
    @Test
    fun chat_response_parses_text() {
        assertEquals("hello", EdgeAi.parseChatText("""{"text":"hello"}"""))
    }

    @Test
    fun chat_response_error_is_null_text() {
        assertNull(EdgeAi.parseChatText("""{"error":"Daily chat limit reached."}"""))
    }

    @Test
    fun vision_response_parses_known_fields_and_ignores_garbage() {
        val stats = EdgeAi.parseVisionStats(
            """{"stats":{"activity_type":"TREADMILL","duration_sec":1800,"distance_m":4023.4,"calories":350,"confidence":0.82,"made_up_key":1}}""",
        )!!
        assertEquals("TREADMILL", stats.activityType)
        assertEquals(1800, stats.durationSec)
        assertEquals(350, stats.calories)
        assertEquals(0.82, stats.confidence)
    }

    @Test
    fun vision_response_error_is_null() {
        assertNull(EdgeAi.parseVisionStats("""{"error":"Could not read the photo"}"""))
    }
}
