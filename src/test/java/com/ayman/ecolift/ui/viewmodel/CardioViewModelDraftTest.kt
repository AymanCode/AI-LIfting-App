package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.cardio.CardioEntryField
import com.ayman.ecolift.cardio.session
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.web.ai.VisionStats
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioViewModelDraftTest {
    private val today = LocalDate(2026, 9, 9)

    @Test
    fun prefill_picks_duration_and_distance_but_not_hr() {
        val s = session("2026-09-08", type = CardioActivityType.TREADMILL, minutes = 25, metres = 3218.688, hr = 150)
        val draft = CardioDraftLogic.newDraft(listOf(s), today)
        assertEquals(CardioActivityType.TREADMILL, draft.type)
        assertEquals(25.0 * 60, draft.values[CardioEntryField.DURATION])
        assertEquals(2.0, draft.values[CardioEntryField.DISTANCE_MILES]!!, 1e-6)
        assertNull(draft.values[CardioEntryField.AVG_HEART_RATE])
        assertNull(draft.values[CardioEntryField.MAX_HEART_RATE])
    }

    @Test
    fun stairs_prefill_picks_floors() {
        val s = session("2026-09-08", type = CardioActivityType.STAIR_CLIMBER, minutes = 20, floors = 65, hr = 155)
        val draft = CardioDraftLogic.newDraft(listOf(s), today)
        assertEquals(CardioActivityType.STAIR_CLIMBER, draft.type)
        assertEquals(20.0 * 60, draft.values[CardioEntryField.DURATION])
        assertEquals(65.0, draft.values[CardioEntryField.FLOORS])
        assertNull(draft.values[CardioEntryField.DISTANCE_MILES])
        assertNull(draft.values[CardioEntryField.AVG_HEART_RATE])
    }

    @Test
    fun no_history_duration_1800() {
        val draft = CardioDraftLogic.newDraft(emptyList(), today)
        assertEquals(CardioActivityType.TREADMILL, draft.type)
        assertEquals(1800.0, draft.values[CardioEntryField.DURATION])
        assertEquals(1, draft.values.size)
    }

    @Test
    fun scrub_clamps_at_min_and_max_and_uses_row_metre_step() {
        val baseDraft = CardioDraft(
            type = CardioActivityType.ROW,
            values = mapOf(CardioEntryField.DISTANCE_MILES to 200.0),
            notes = "",
        )
        // ROW distance step is 50.0 m, min is 50.0 m, max is 50000.0 m
        val stepped = CardioDraftLogic.applyScrub(baseDraft, CardioEntryField.DISTANCE_MILES, 2)
        assertEquals(300.0, stepped.values[CardioEntryField.DISTANCE_MILES])

        val clampedMin = CardioDraftLogic.applyScrub(baseDraft, CardioEntryField.DISTANCE_MILES, -20)
        assertEquals(50.0, clampedMin.values[CardioEntryField.DISTANCE_MILES])

        val clampedMax = CardioDraftLogic.applyScrub(baseDraft, CardioEntryField.DISTANCE_MILES, 2000)
        assertEquals(50000.0, clampedMax.values[CardioEntryField.DISTANCE_MILES])
    }

    @Test
    fun typed_abc_marks_invalid_and_blocks_save() {
        val baseDraft = CardioDraft(
            type = CardioActivityType.TREADMILL,
            values = mapOf(CardioEntryField.DURATION to 1800.0),
            notes = "",
        )
        assertTrue(CardioDraftLogic.canSave(baseDraft))

        val invalidDraft = CardioDraftLogic.applyTyped(baseDraft, CardioEntryField.AVG_HEART_RATE, "abc")
        assertTrue(invalidDraft.invalid.contains(CardioEntryField.AVG_HEART_RATE))
        assertEquals(1800.0, invalidDraft.values[CardioEntryField.DURATION])
        assertNull(invalidDraft.values[CardioEntryField.AVG_HEART_RATE])
        assertFalse(CardioDraftLogic.canSave(invalidDraft))
    }

    @Test
    fun edit_keeps_distance_m_unchanged_to_1e_9_after_a_scrub_on_another_field() {
        val originalDistanceM = 1609.344000123
        val s = session("2026-09-08", type = CardioActivityType.TREADMILL, minutes = 30, metres = originalDistanceM, id = 42L)
        val editDraft = CardioDraftLogic.editDraft(s)
        val scrubbed = CardioDraftLogic.applyScrub(editDraft, CardioEntryField.DURATION, 2)
        val write = CardioDraftLogic.toWrite(scrubbed)
        assertEquals(originalDistanceM, write.distanceM!!, 1e-9)
        assertEquals(42L, write.editingId)
    }

    @Test
    fun scan_with_confidence_0_4_sets_low_confidence() {
        val stats = VisionStats(
            activityType = "TREADMILL",
            durationSec = 1800,
            confidence = 0.4,
        )
        val draft = CardioDraftLogic.fromScan(stats, emptyList(), today)
        assertEquals(0.4, draft.scanConfidence)
        assertTrue(draft.scanConfidence != null && draft.scanConfidence!! < 0.6)
    }

    @Test
    fun scanned_incline_of_60_becomes_check() {
        val stats = VisionStats(
            activityType = "TREADMILL",
            durationSec = 1200,
            avgInclinePercent = 60.0,
            confidence = 0.9,
        )
        val draft = CardioDraftLogic.fromScan(stats, emptyList(), today)
        assertEquals(ReadState.CHECK, draft.readStates[CardioEntryField.AVG_INCLINE_PERCENT])
        assertEquals(ReadState.READ, draft.readStates[CardioEntryField.DURATION])
    }

    @Test
    fun unread_hr_stays_absent() {
        val last = session("2026-09-08", type = CardioActivityType.TREADMILL, minutes = 30, hr = 152)
        val stats = VisionStats(
            activityType = "TREADMILL",
            durationSec = 1500,
            avgHeartRate = null,
            confidence = 0.9,
        )
        val draft = CardioDraftLogic.fromScan(stats, listOf(last), today)
        assertEquals(ReadState.UNREAD, draft.readStates[CardioEntryField.AVG_HEART_RATE])
        assertNull(draft.values[CardioEntryField.AVG_HEART_RATE])
    }
}
