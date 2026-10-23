package com.ayman.ecolift.cardio

import com.ayman.ecolift.data.CardioActivityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioFieldRulesTest {
    private val tm = CardioActivityType.TREADMILL
    private val row = CardioActivityType.ROW
    private val stairs = CardioActivityType.STAIR_CLIMBER

    @Test
    fun table_for_every_field() {
        val rDur = CardioFieldRules.rule(CardioEntryField.DURATION, tm)
        assertEquals(FieldUnit.SECONDS, rDur.unit)
        assertEquals(15.0, rDur.step)
        assertEquals(60.0, rDur.min)
        assertEquals(21600.0, rDur.max)
        assertFalse(rDur.zeroAllowed)

        val rFloors = CardioFieldRules.rule(CardioEntryField.FLOORS, stairs)
        assertEquals(FieldUnit.FLOORS, rFloors.unit)
        assertEquals(1.0, rFloors.step)
        assertEquals(1.0, rFloors.min)
        assertEquals(500.0, rFloors.max)

        val rHr = CardioFieldRules.rule(CardioEntryField.AVG_HEART_RATE, tm)
        assertEquals(FieldUnit.BPM, rHr.unit)
        assertEquals(1.0, rHr.step)
        assertEquals(40.0, rHr.min)
        assertEquals(220.0, rHr.max)

        val rSpeed = CardioFieldRules.rule(CardioEntryField.AVG_SPEED_MPH, tm)
        assertEquals(FieldUnit.MPH, rSpeed.unit)
        assertEquals(0.1, rSpeed.step)
        assertEquals(0.5, rSpeed.min)
        assertEquals(25.0, rSpeed.max)

        val rInc = CardioFieldRules.rule(CardioEntryField.AVG_INCLINE_PERCENT, tm)
        assertEquals(FieldUnit.PERCENT, rInc.unit)
        assertEquals(0.5, rInc.step)
        assertEquals(-3.0, rInc.min)
        assertEquals(40.0, rInc.max)
        assertTrue(rInc.zeroAllowed)

        val rCal = CardioFieldRules.rule(CardioEntryField.CALORIES, tm)
        assertEquals(FieldUnit.KCAL, rCal.unit)
        assertEquals(5.0, rCal.step)
        assertEquals(1.0, rCal.min)
        assertEquals(3000.0, rCal.max)

        val rCad = CardioFieldRules.rule(CardioEntryField.CADENCE_RPM, CardioActivityType.BIKE)
        assertEquals(FieldUnit.RPM, rCad.unit)
        assertEquals(1.0, rCad.step)
        assertEquals(20.0, rCad.min)
        assertEquals(200.0, rCad.max)

        val rRes = CardioFieldRules.rule(CardioEntryField.RESISTANCE_LEVEL, CardioActivityType.BIKE)
        assertEquals(FieldUnit.LEVEL, rRes.unit)
        assertEquals(0.5, rRes.step)
        assertEquals(0.0, rRes.min)
        assertEquals(50.0, rRes.max)
        assertTrue(rRes.zeroAllowed)

        val rPow = CardioFieldRules.rule(CardioEntryField.AVG_POWER_WATTS, CardioActivityType.BIKE)
        assertEquals(FieldUnit.WATTS, rPow.unit)
        assertEquals(5.0, rPow.step)
        assertEquals(10.0, rPow.min)
        assertEquals(1500.0, rPow.max)

        val rStr = CardioFieldRules.rule(CardioEntryField.STROKE_RATE_SPM, row)
        assertEquals(FieldUnit.SPM, rStr.unit)
        assertEquals(1.0, rStr.step)
        assertEquals(10.0, rStr.min)
        assertEquals(60.0, rStr.max)

        val rPace500 = CardioFieldRules.rule(CardioEntryField.PACE_500M, row)
        assertEquals(FieldUnit.SEC_PER_500, rPace500.unit)
        assertEquals(1.0, rPace500.step)
        assertEquals(60.0, rPace500.min)
        assertEquals(360.0, rPace500.max)

        val rSteps = CardioFieldRules.rule(CardioEntryField.STEPS, stairs)
        assertEquals(FieldUnit.STEPS, rSteps.unit)
        assertEquals(10.0, rSteps.step)
        assertEquals(10.0, rSteps.min)
        assertEquals(50000.0, rSteps.max)
    }

    @Test
    fun rule_distance_row_is_metres() {
        val rRow = CardioFieldRules.rule(CardioEntryField.DISTANCE_MILES, row)
        assertEquals(FieldUnit.METRES, rRow.unit)
        assertEquals(50.0, rRow.step)
        assertEquals(50.0, rRow.min)
        assertEquals(50000.0, rRow.max)
        assertEquals(0, rRow.decimals)

        val rTm = CardioFieldRules.rule(CardioEntryField.DISTANCE_MILES, tm)
        assertEquals(FieldUnit.MILES, rTm.unit)
        assertEquals(0.05, rTm.step)
        assertEquals(0.05, rTm.min)
        assertEquals(50.0, rTm.max)
        assertEquals(2, rTm.decimals)
    }

    @Test
    fun primary_fields_stair_climber() {
        assertEquals(
            listOf(CardioEntryField.DURATION, CardioEntryField.FLOORS, CardioEntryField.AVG_HEART_RATE),
            CardioFieldRules.primaryFields(stairs)
        )
        assertEquals(
            listOf(CardioEntryField.DURATION, CardioEntryField.DISTANCE_MILES, CardioEntryField.AVG_HEART_RATE),
            CardioFieldRules.primaryFields(tm)
        )
    }

    @Test
    fun more_fields_treadmill() {
        assertEquals(
            listOf(
                CardioEntryField.AVG_SPEED_MPH,
                CardioEntryField.AVG_INCLINE_PERCENT,
                CardioEntryField.CALORIES,
                CardioEntryField.MAX_HEART_RATE,
                CardioEntryField.NOTES,
            ),
            CardioFieldRules.moreFields(tm)
        )
    }

    @Test
    fun is_plausible_incline() {
        assertTrue(CardioFieldRules.isPlausible(CardioEntryField.AVG_INCLINE_PERCENT, tm, 0.0))
        assertFalse(CardioFieldRules.isPlausible(CardioEntryField.AVG_INCLINE_PERCENT, tm, -5.0))
        assertTrue(CardioFieldRules.isPlausible(CardioEntryField.AVG_INCLINE_PERCENT, tm, 5.0))
        assertFalse(CardioFieldRules.isPlausible(CardioEntryField.AVG_INCLINE_PERCENT, tm, 45.0))
    }

    @Test
    fun parse_field_values() {
        assertEquals(1935.0, CardioFieldRules.parse(CardioEntryField.DURATION, tm, "32:15"))
        assertEquals(4120.0, CardioFieldRules.parse(CardioEntryField.DISTANCE_MILES, row, "4,120"))
        assertNull(CardioFieldRules.parse(CardioEntryField.AVG_HEART_RATE, tm, "abc"))
    }

    @Test
    fun to_and_from_storage() {
        assertEquals(1609.344, CardioFieldRules.toStorage(CardioEntryField.DISTANCE_MILES, tm, 1.0))
        assertEquals(0.44704, CardioFieldRules.toStorage(CardioEntryField.AVG_SPEED_MPH, tm, 1.0))

        val distMiles = 3.5
        val storedDist = CardioFieldRules.toStorage(CardioEntryField.DISTANCE_MILES, tm, distMiles)
        assertEquals(distMiles, CardioFieldRules.fromStorage(CardioEntryField.DISTANCE_MILES, tm, storedDist), 1e-9)

        val speed = 6.2
        val storedSpeed = CardioFieldRules.toStorage(CardioEntryField.AVG_SPEED_MPH, tm, speed)
        assertEquals(speed, CardioFieldRules.fromStorage(CardioEntryField.AVG_SPEED_MPH, tm, storedSpeed), 1e-9)

        val rowMeters = 2000.0
        assertEquals(rowMeters, CardioFieldRules.toStorage(CardioEntryField.DISTANCE_MILES, row, rowMeters))
        assertEquals(rowMeters, CardioFieldRules.fromStorage(CardioEntryField.DISTANCE_MILES, row, rowMeters), 1e-9)
    }

    @Test
    fun display_formatting() {
        assertEquals("4,120", CardioFieldRules.display(CardioEntryField.DISTANCE_MILES, row, 4120.0))
        assertEquals("3.10", CardioFieldRules.display(CardioEntryField.DISTANCE_MILES, tm, 3.1))
        assertEquals("32:15", CardioFieldRules.display(CardioEntryField.DURATION, tm, 1935.0))
        assertEquals("148", CardioFieldRules.display(CardioEntryField.AVG_HEART_RATE, tm, 148.0))
        assertEquals("5.8", CardioFieldRules.display(CardioEntryField.AVG_SPEED_MPH, tm, 5.8))
        assertEquals("3.0", CardioFieldRules.display(CardioEntryField.AVG_INCLINE_PERCENT, tm, 3.0))
        assertEquals("2:04", CardioFieldRules.display(CardioEntryField.PACE_500M, row, 124.0))
    }
}
