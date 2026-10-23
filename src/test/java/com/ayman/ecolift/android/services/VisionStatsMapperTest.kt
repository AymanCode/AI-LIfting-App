package com.ayman.ecolift.android.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisionStatsMapperTest {
    @Test
    fun `treadmill console text becomes vision stats`() {
        val stats = visionStatsFromOcrText(
            """
            TREADMILL
            TIME 32:15
            DISTANCE 2.41 MI
            CALORIES 318
            HR 142 BPM
            """.trimIndent(),
        )

        assertEquals("TREADMILL", stats?.activityType)
        assertEquals(1935, stats?.durationSec)
        assertEquals(318, stats?.calories)
        assertEquals(142, stats?.avgHeartRate)
        assertEquals("treadmill", stats?.machineType)
        assertEquals(3878.51904, stats?.distanceM ?: 0.0, 0.001)
        assertEquals(0.95, stats?.confidence ?: 0.0, 0.0001)
        assertNull(stats?.avgSpeed)
    }

    @Test
    fun `bike console text becomes vision stats`() {
        val stats = visionStatsFromOcrText(
            """
            BIKE
            Duration 0:45:02
            Distance 18.4 km
            kJ 920
            Speed 24.5 km/h
            """.trimIndent(),
        )

        assertEquals("BIKE", stats?.activityType)
        assertEquals(2702, stats?.durationSec)
        assertEquals(18400.0, stats?.distanceM ?: 0.0, 0.001)
        assertEquals(219, stats?.calories)
        assertEquals("bike", stats?.machineType)
    }

    @Test
    fun `rower machine string maps to the row activity name`() {
        val stats = visionStatsFromOcrText(
            """
            ROWER
            TIME 10:00
            DISTANCE 2.0 km
            CALORIES 100
            """.trimIndent(),
        )

        assertEquals("ROW", stats?.activityType)
        assertEquals("rower", stats?.machineType)
        assertEquals(600, stats?.durationSec)
        assertEquals(2000.0, stats?.distanceM ?: 0.0, 0.001)
        assertEquals(100, stats?.calories)
    }

    @Test
    fun `unrelated text is not a scan`() {
        assertNull(visionStatsFromOcrText("hello world"))
    }
}
