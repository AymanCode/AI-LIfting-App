package com.ayman.ecolift.cardio.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioOcrParserTest {
    @Test
    fun `recognizes treadmill screen with time distance calories and heart rate`() {
        val result = CardioOcrParser.parse(
            """
            TREADMILL
            TIME 32:15
            DISTANCE 2.41 MI
            CALORIES 318
            HR 142 BPM
            """.trimIndent()
        )

        assertTrue("Expected recognition, got $result", result.recognizedCardioScreen)
        assertEquals(1935, result.durationSec)
        assertEquals(318, result.calories)
        assertEquals(142, result.avgHeartRate)
        assertEquals("treadmill", result.machineType)
        assertNotNull(result.distanceM)
    }

    @Test
    fun `normalizes metric bike screen values`() {
        val result = CardioOcrParser.parse(
            """
            BIKE
            Duration 0:45:02
            Distance 18.4 km
            kJ 920
            Speed 24.5 km/h
            """.trimIndent()
        )

        assertTrue("Expected recognition, got $result", result.recognizedCardioScreen)
        assertEquals(2702, result.durationSec)
        assertEquals(219, result.calories)
        assertEquals("bike", result.machineType)
        assertNotNull(result.avgSpeed)
    }

    @Test
    fun `recognizes split label and value lines from lcd ocr`() {
        val result = CardioOcrParser.parse(
            """
            TOTAL TIME
            : 19
            HEARTATE
            RESISTACE
            DsTANCe
            8.00
            CALORIEN
            """.trimIndent()
        )

        assertTrue("Expected recognition, got $result", result.recognizedCardioScreen)
        assertEquals(19, result.durationSec)
        assertNotNull(result.distanceM)
    }

    @Test
    fun `recognizes lcd time when colon is dropped by ocr`() {
        val result = CardioOcrParser.parse(
            """
            TIME
            06
            DISTANCE
            SPEED
            CALORIES
            8
            """.trimIndent()
        )

        assertTrue("Expected recognition, got $result", result.recognizedCardioScreen)
        assertEquals(6, result.durationSec)
        assertEquals(8, result.calories)
    }

    @Test
    fun `does not recognize unrelated gym text as cardio screen`() {
        val result = CardioOcrParser.parse(
            """
            BENCH PRESS
            SET 1
            185 X 5
            """.trimIndent()
        )

        assertFalse(result.recognizedCardioScreen)
        assertEquals(0.0, result.confidence, 0.0)
    }
}
