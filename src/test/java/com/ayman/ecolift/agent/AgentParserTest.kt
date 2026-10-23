package com.ayman.ecolift.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentParserTest {
    @Test
    fun log_set_parser_reads_basic_line() {
        // LogSetTextParser uses parseOneExercise (not parse)
        val parsed = LogSetTextParser.parseOneExercise("bench press 185x8")
        assertTrue(parsed != null)
    }

    @Test
    fun date_extractor_handles_yesterday() {
        val result = DateExtractor.extract("what did I do yesterday")
        assertTrue(result != null)
    }

    @Test
    fun kilograms_are_converted_to_stored_pounds() {
        val parsed = LogSetTextParser.parseOneExercise("bench 100 kg for 5")
        assertEquals(listOf(LogSetTextParser.ParsedSet(weightLbs = 2205, reps = 5)), parsed?.sets)
    }

    @Test
    fun pounds_and_a_bare_weight_stay_as_typed() {
        val expected = listOf(LogSetTextParser.ParsedSet(weightLbs = 1000, reps = 5))
        assertEquals(expected, LogSetTextParser.parseOneExercise("bench 100 lbs for 5")?.sets)
        assertEquals(expected, LogSetTextParser.parseOneExercise("bench 100 for 5")?.sets)
    }
}
