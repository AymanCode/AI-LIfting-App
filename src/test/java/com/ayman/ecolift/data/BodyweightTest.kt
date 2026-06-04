package com.ayman.ecolift.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyweightTest {
    @Test
    fun `obvious bodyweight names are detected conservatively`() {
        assertTrue(looksLikeBodyweightExerciseName("Pull Up"))
        assertTrue(looksLikeBodyweightExerciseName("Weighted Dips"))
        assertTrue(looksLikeBodyweightExerciseName("Push-ups"))
        assertFalse(looksLikeBodyweightExerciseName("Lat Pulldown"))
        assertFalse(looksLikeBodyweightExerciseName("Dip Machine Weighted"))
    }

    @Test
    fun `bodyweight load normalization treats zero as bodyweight only`() {
        assertNull(normalizedBodyweightLoad(null))
        assertNull(normalizedBodyweightLoad(0))
        assertTrue(normalizedBodyweightLoad(250) == 250)
    }

    @Test
    fun `user bodyweight normalization rejects unset and nonpositive values`() {
        assertNull(normalizedUserBodyweightLbs(null))
        assertNull(normalizedUserBodyweightLbs(0))
        assertNull(normalizedUserBodyweightLbs(-185))
        assertTrue(normalizedUserBodyweightLbs(185) == 185)
    }
}
