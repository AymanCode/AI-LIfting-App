package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.cardio.CardioEntryField
import com.ayman.ecolift.data.CardioActivityType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardioInputBoundsTest {
    private val draft = CardioDraft(CardioActivityType.TREADMILL, mapOf(CardioEntryField.DURATION to 1800.0), "")

    @Test
    fun zero_time_is_invalid_and_cannot_claim_an_active_session() {
        val result = CardioDraftLogic.applyTyped(draft, CardioEntryField.DURATION, "0")
        assertFalse(CardioDraftLogic.canSave(result))
        assertTrue(CardioEntryField.DURATION in result.invalid)
        assertEquals(1800.0, result.values[CardioEntryField.DURATION])
    }

    @Test
    fun huge_time_is_invalid_and_does_not_replace_the_valid_value() {
        val result = CardioDraftLogic.applyTyped(draft, CardioEntryField.DURATION, "9999999999")
        assertFalse(CardioDraftLogic.canSave(result))
        assertTrue(CardioEntryField.DURATION in result.invalid)
        assertEquals(1800.0, result.values[CardioEntryField.DURATION])
    }

    @Test
    fun supported_time_boundaries_save_exact_seconds() {
        for ((text, seconds) in listOf("1:00" to 60, "6:00:00" to 21600)) {
            val result = CardioDraftLogic.applyTyped(draft, CardioEntryField.DURATION, text)
            assertTrue(CardioDraftLogic.canSave(result))
            assertEquals(seconds, CardioDraftLogic.toWrite(result, LocalDate(2026, 10, 23)).durationSec)
        }
    }
}
