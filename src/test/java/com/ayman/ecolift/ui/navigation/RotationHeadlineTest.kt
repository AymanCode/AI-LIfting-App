package com.ayman.ecolift.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RotationHeadlineTest {
    @Test
    fun ordinaryEstimateKeepsTheRestingSize() {
        assertEquals(50f, rotationHeadlineFontSp({ 80f }, 100f))
    }

    @Test
    fun hugeEstimateShrinksToTheLargestSizeThatFits() {
        val size = rotationHeadlineFontSp({ candidate -> candidate * 4f }, 100f)
        assertEquals(22f, size)
        assertTrue(size * 4f <= 100f)
    }

    @Test
    fun unmeasuredColumnKeepsTheRestingSize() {
        assertEquals(50f, rotationHeadlineFontSp({ 1_000f }, 0f))
    }

    @Test
    fun compactFormKeepsScaleAndLeavesOrdinaryNumbersAlone() {
        assertEquals("20", rotationHeadlineCompact(20))
        assertEquals("500", rotationHeadlineCompact(500))
        assertEquals("1.43M", rotationHeadlineCompact(1_433_332))
        assertEquals("12.5K", rotationHeadlineCompact(12_500))
    }
}
