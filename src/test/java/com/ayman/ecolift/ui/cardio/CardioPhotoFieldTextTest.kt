package com.ayman.ecolift.ui.cardio

import com.ayman.ecolift.cardio.CardioEntryField
import com.ayman.ecolift.cardio.session
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.ui.viewmodel.CardioDraftLogic
import com.ayman.ecolift.web.ai.VisionStats
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardioPhotoFieldTextTest {
    private val today = LocalDate(2026, 10, 23)

    @Test
    fun failed_scan_default_is_not_described_as_photo_evidence() {
        val draft = CardioDraftLogic.newDraft(emptyList(), today)
        assertEquals(1800.0, draft.values[CardioEntryField.DURATION])
        assertNull(cardioPhotoFieldText(draft, CardioEntryField.DURATION))
    }

    @Test
    fun unread_prefill_and_its_manual_correction_are_not_photo_evidence() {
        val last = session("2026-10-22", type = CardioActivityType.TREADMILL, minutes = 30)
        val draft = CardioDraftLogic.fromScan(VisionStats(activityType = "TREADMILL", calories = 200), listOf(last), today)
        assertEquals(1800.0, draft.values[CardioEntryField.DURATION])
        assertNull(cardioPhotoFieldText(draft, CardioEntryField.DURATION))
        val edited = CardioDraftLogic.applyTyped(draft, CardioEntryField.DURATION, "20:00")
        assertEquals(1200.0, edited.values[CardioEntryField.DURATION])
        assertNull(cardioPhotoFieldText(edited, CardioEntryField.DURATION))
    }

    @Test
    fun actual_photo_value_keeps_its_original_label_after_editing() {
        val draft = CardioDraftLogic.fromScan(VisionStats(activityType = "TREADMILL", durationSec = 1800), emptyList(), today)
        assertEquals("read from photo: 30:00", cardioPhotoFieldText(draft, CardioEntryField.DURATION))
        val edited = CardioDraftLogic.applyTyped(draft, CardioEntryField.DURATION, "20:00")
        assertEquals(1200.0, edited.values[CardioEntryField.DURATION])
        assertEquals("read from photo: 30:00", cardioPhotoFieldText(edited, CardioEntryField.DURATION))
        val scrubbed = CardioDraftLogic.applyScrub(edited, CardioEntryField.DURATION, 1)
        assertEquals(1215.0, scrubbed.values[CardioEntryField.DURATION])
        assertEquals("read from photo: 30:00", cardioPhotoFieldText(scrubbed, CardioEntryField.DURATION))
    }
}
