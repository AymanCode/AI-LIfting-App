package com.ayman.ecolift.data

import kotlin.test.Test
import kotlin.test.assertEquals

class OneRepMaxTest {
    @Test fun doubleArithmeticRetainsRawUncappedValues() {
        assertEquals(100.0, epleyEstimate(100.0, 0))
        assertEquals(103.33333333333334, epleyEstimate(100.0, 1))
        assertEquals(166.66666666666666, epleyEstimate(100.0, 20))
        assertEquals(200.0, epleyEstimate(100.0, 30))
        assertEquals(96.66666666666667, epleyEstimate(100.0, -1))
    }

    @Test fun floatArithmeticRetainsOriginalPrecision() {
        assertEquals(100f.toBits(), epleyEstimate(100f, 0).toBits())
        assertEquals(103.33332824707031f.toBits(), epleyEstimate(100f, 1).toBits())
        assertEquals(166.6666717529297f.toBits(), epleyEstimate(100f, 20).toBits())
        assertEquals(200f.toBits(), epleyEstimate(100f, 30).toBits())
        assertEquals((-16.666667938232422f).toBits(), epleyEstimate(-10f, 20).toBits())
    }
}
