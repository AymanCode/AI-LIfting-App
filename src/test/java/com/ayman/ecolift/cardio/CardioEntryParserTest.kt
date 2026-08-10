package com.ayman.ecolift.cardio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioEntryParserTest {
    @Test
    fun `parses treadmill speed incline and max heart rate inputs`() {
        assertEquals(1935, CardioEntryParser.parseDurationSeconds("32:15"))
        assertEquals(2.772, CardioEntryParser.parseMphToMetersPerSecond("6.2")!!, 0.001)
        assertEquals(5.5, CardioEntryParser.parseDouble("5.5")!!, 0.0)
        assertEquals(166, CardioEntryParser.parseInt("166"))
    }

    @Test
    fun `parses bike cadence resistance and watts`() {
        assertEquals(82, CardioEntryParser.parseInt("82"))
        assertEquals(8.0, CardioEntryParser.parseDouble("8")!!, 0.0)
        assertEquals(185, CardioEntryParser.parseInt("185"))
    }

    @Test
    fun `parses rower split and stroke rate`() {
        assertEquals(120, CardioEntryParser.parsePaceSeconds("2:00"))
        assertEquals(28, CardioEntryParser.parseInt("28"))
    }

    @Test
    fun `requires at least one metric or note to save`() {
        assertFalse(
            CardioEntryParser.hasSaveableMetric(
                durationSec = null,
                distanceM = null,
                calories = null,
                avgHeartRate = null,
                maxHeartRate = null,
                avgSpeed = null,
                avgInclinePercent = null,
                cadenceRpm = null,
                resistanceLevel = null,
                avgPowerWatts = null,
                strokeRateSpm = null,
                paceSecPer500m = null,
                floors = null,
                steps = null,
                notes = "",
            )
        )

        assertTrue(
            CardioEntryParser.hasSaveableMetric(
                durationSec = null,
                distanceM = null,
                calories = null,
                avgHeartRate = null,
                maxHeartRate = null,
                avgSpeed = null,
                avgInclinePercent = null,
                cadenceRpm = null,
                resistanceLevel = null,
                avgPowerWatts = null,
                strokeRateSpm = null,
                paceSecPer500m = null,
                floors = 42,
                steps = null,
                notes = "",
            )
        )
        assertNotNull(CardioEntryParser.parseMilesToMeters("2.5"))
    }
}
