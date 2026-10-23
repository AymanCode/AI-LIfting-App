package com.ayman.ecolift.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MuscleSuggestionTest {
    @Test
    fun keyword_suggester_maps_representative_exercise_names() {
        val cases = mapOf(
            "Incline Bench Press" to "CHEST",
            "Lat Pulldown" to "BACK",
            "Hammer Curl" to "BICEPS",
            "Triceps Pushdown" to "TRICEPS",
            "Back Squat" to "QUADS",
            "Romanian Deadlift" to "HAMSTRINGS",
            "Lateral Raise" to "SHOULDERS",
            "Cable Crunch" to "CORE",
            "Standing Calf Raise" to "CALVES",
        )

        cases.forEach { (name, expected) ->
            val suggestion = suggestMuscleGroupFromKeywords(name)
            assertEquals(expected, suggestion?.group, name)
            assertTrue(suggestion?.isKeywordHit == true, name)
        }
    }

    @Test
    fun keyword_suggester_returns_null_when_no_keyword_matches() {
        assertNull(suggestMuscleGroupFromKeywords("Mystery Machine"))
    }
}
