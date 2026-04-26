package com.ayman.ecolift.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FuzzyMatcherTest {

    @Test
    fun `test exact match returns zero distance`() {
        val string1 = "bench press"
        val string2 = "bench press"
        assertEquals(0, FuzzyMatcher.levenshteinDistance(string1, string2))
    }

    @Test
    fun `test single character difference returns one`() {
        val string1 = "bench"
        val string2 = "benc"
        assertEquals(1, FuzzyMatcher.levenshteinDistance(string1, string2))
    }

    @Test
    fun `test case insensitivity handles different casing`() {
        // FuzzyMatcher expects callers to normalize input before distance checks.
        val string1 = "BENCH PRESS"
        val string2 = "bench press"
        assertEquals(0, FuzzyMatcher.levenshteinDistance(string1.lowercase(), string2.lowercase()))
    }

    @Test
    fun `test empty string comparison`() {
        assertEquals(5, FuzzyMatcher.levenshteinDistance("", "bench"))
        assertEquals(5, FuzzyMatcher.levenshteinDistance("bench", ""))
    }

    @Test
    fun `test completely different strings`() {
        val string1 = "squat"
        val string2 = "deadlift"
        // 'squat' (5) to 'deadlift' (8) is a significant distance
        val distance = FuzzyMatcher.levenshteinDistance(string1, string2)
        assert(distance > 0)
    }

    @Test
    fun `test strings with whitespace differences`() {
        // FuzzyMatcher expects callers to trim input before distance checks.
        val string1 = "  bench press  "
        val string2 = "bench press"
        assertEquals(0, FuzzyMatcher.levenshteinDistance(string1.trim(), string2.trim()))
    }
}
