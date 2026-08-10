package com.ayman.ecolift.cardio

import com.ayman.ecolift.data.CardioActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioActivitySpecsTest {
    @Test
    fun `treadmill exposes speed and incline fields`() {
        val spec = CardioActivitySpecs.specFor(CardioActivityType.TREADMILL)

        assertEquals("Treadmill", spec.label)
        assertTrue(CardioEntryField.AVG_SPEED_MPH in spec.fields)
        assertTrue(CardioEntryField.AVG_INCLINE_PERCENT in spec.fields)
        assertTrue(CardioEntryField.MAX_HEART_RATE in spec.fields)
    }

    @Test
    fun `bike exposes cadence resistance and watts`() {
        val fields = CardioActivitySpecs.specFor(CardioActivityType.BIKE).fields

        assertTrue(CardioEntryField.CADENCE_RPM in fields)
        assertTrue(CardioEntryField.RESISTANCE_LEVEL in fields)
        assertTrue(CardioEntryField.AVG_POWER_WATTS in fields)
    }

    @Test
    fun `rower exposes split and stroke rate`() {
        val fields = CardioActivitySpecs.specFor(CardioActivityType.ROW).fields

        assertTrue(CardioEntryField.PACE_500M in fields)
        assertTrue(CardioEntryField.STROKE_RATE_SPM in fields)
    }

    @Test
    fun `stair climber exposes floors steps and level`() {
        val fields = CardioActivitySpecs.specFor(CardioActivityType.STAIR_CLIMBER).fields

        assertTrue(CardioEntryField.FLOORS in fields)
        assertTrue(CardioEntryField.STEPS in fields)
        assertTrue(CardioEntryField.RESISTANCE_LEVEL in fields)
    }
}
