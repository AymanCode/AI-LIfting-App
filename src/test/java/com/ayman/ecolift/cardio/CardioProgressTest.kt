package com.ayman.ecolift.cardio

import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.web.util.minusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardioProgressTest {
    private val today = LocalDate(2026, 9, 9)

    @Test
    fun chips_per_machine_match_the_table() {
        assertEquals(
            listOf(ProgressMetric.SPEED_AT_HR, ProgressMetric.SPEED, ProgressMetric.PACE, ProgressMetric.DISTANCE),
            CardioProgress.metricsFor(CardioActivityType.TREADMILL)
        )
        assertEquals(
            listOf(ProgressMetric.SPEED_AT_HR, ProgressMetric.SPEED, ProgressMetric.PACE, ProgressMetric.DISTANCE),
            CardioProgress.metricsFor(CardioActivityType.RUN)
        )
        assertEquals(
            listOf(ProgressMetric.SPEED_AT_HR, ProgressMetric.SPEED, ProgressMetric.PACE, ProgressMetric.DISTANCE),
            CardioProgress.metricsFor(CardioActivityType.WALK)
        )
        assertEquals(
            listOf(ProgressMetric.SPLIT_AT_HR, ProgressMetric.SPLIT, ProgressMetric.DISTANCE, ProgressMetric.STROKE_RATE),
            CardioProgress.metricsFor(CardioActivityType.ROW)
        )
        assertEquals(
            listOf(ProgressMetric.POWER_AT_HR, ProgressMetric.POWER, ProgressMetric.CADENCE, ProgressMetric.DISTANCE),
            CardioProgress.metricsFor(CardioActivityType.BIKE)
        )
        assertEquals(
            listOf(ProgressMetric.CADENCE, ProgressMetric.RESISTANCE, ProgressMetric.DISTANCE, ProgressMetric.DURATION),
            CardioProgress.metricsFor(CardioActivityType.ELLIPTICAL)
        )
        assertEquals(
            listOf(ProgressMetric.FLOORS_PER_MIN, ProgressMetric.FLOORS, ProgressMetric.DURATION),
            CardioProgress.metricsFor(CardioActivityType.STAIR_CLIMBER)
        )
        assertEquals(
            listOf(ProgressMetric.PACE_100, ProgressMetric.DISTANCE, ProgressMetric.DURATION),
            CardioProgress.metricsFor(CardioActivityType.SWIM)
        )
        assertEquals(
            listOf(ProgressMetric.DURATION, ProgressMetric.DISTANCE),
            CardioProgress.metricsFor(CardioActivityType.OTHER)
        )
    }

    @Test
    fun metric_value_unit_conversions() {
        val sSpeed = session("2026-09-01", speedMps = 1.0)
        assertEquals(2.23694, CardioProgress.metricValue(sSpeed, ProgressMetric.SPEED)!!, 1e-4)

        val sDistMiles = session("2026-09-01", metres = 1609.344)
        assertEquals(1.0, CardioProgress.metricValue(sDistMiles, ProgressMetric.DISTANCE)!!, 1e-4)

        val sDistRow = session("2026-09-01", type = CardioActivityType.ROW, metres = 2000.0)
        assertEquals(2000.0, CardioProgress.metricValue(sDistRow, ProgressMetric.DISTANCE)!!, 1e-4)

        val sPace = session("2026-09-01", minutes = 10, metres = 1609.344)
        assertEquals(600.0, CardioProgress.metricValue(sPace, ProgressMetric.PACE)!!, 1e-4)
    }

    @Test
    fun six_eligible_required_for_availability() {
        val sessions5 = (1..5).map { i ->
            session(today.minusDays(i.toLong() * 2).toString(), minutes = 30, hr = 150, speedMps = 2.68224, id = i.toLong())
        }
        val p5 = CardioProgress.machineProgress(sessions5, CardioActivityType.TREADMILL, ProgressMetric.SPEED_AT_HR, today)
        assertFalse(p5.available)
        assertEquals("needs 6 sessions with heart rate", p5.unavailableReason)
        assertEquals("Not enough sessions yet. needs 6 sessions with heart rate.", p5.sentence)

        val sessions6 = sessions5 + session(today.minusDays(12).toString(), minutes = 30, hr = 150, speedMps = 2.68224, id = 6L)
        val p6 = CardioProgress.machineProgress(sessions6, CardioActivityType.TREADMILL, ProgressMetric.SPEED_AT_HR, today)
        assertTrue(p6.available)
        assertNull(p6.unavailableReason)
    }

    @Test
    fun filters_for_bpm_and_duration_exclude_and_count_correctly() {
        // Latest session: 30 min duration, hr 150
        val base = (1..6).map { i ->
            session(today.minusDays(i.toLong() * 3).toString(), minutes = 30, hr = 150, speedMps = 2.68224, id = i.toLong())
        }
        // Excluded: duration 45 min (> 25% of 30 min)
        val exDur = session(today.minusDays(25).toString(), minutes = 45, hr = 150, speedMps = 2.68224, id = 10L)
        // Excluded: hr 170 (> 15 bpm away from ref 150)
        val exHr = session(today.minusDays(30).toString(), minutes = 30, hr = 170, speedMps = 2.68224, id = 11L)

        val p = CardioProgress.machineProgress(base + exDur + exHr, CardioActivityType.TREADMILL, ProgressMetric.SPEED_AT_HR, today)
        assertEquals(6, p.eligibleCount)
        assertEquals(2, p.excludedCount)
        assertEquals(2, p.excluded.size)
    }

    @Test
    fun normalisation_math_speed_6_mph_at_158_bpm_with_ref_148() {
        // speed 6 mph at 158 bpm with ref 148 -> 6 * 148 / 158 = 5.62025...
        // Build 6 sessions with ref = 148: e.g. five sessions with hr = 148, one with 158
        val s1 = session(today.minusDays(10).toString(), minutes = 30, hr = 148, speedMps = 6.0 / 2.23694, id = 1L)
        val s2 = session(today.minusDays(8).toString(), minutes = 30, hr = 148, speedMps = 6.0 / 2.23694, id = 2L)
        val s3 = session(today.minusDays(6).toString(), minutes = 30, hr = 148, speedMps = 6.0 / 2.23694, id = 3L)
        val s4 = session(today.minusDays(4).toString(), minutes = 30, hr = 148, speedMps = 6.0 / 2.23694, id = 4L)
        val s5 = session(today.minusDays(2).toString(), minutes = 30, hr = 148, speedMps = 6.0 / 2.23694, id = 5L)
        val s6 = session(today.minusDays(0).toString(), minutes = 30, hr = 158, speedMps = 6.0 / 2.23694, id = 6L)

        val p = CardioProgress.machineProgress(listOf(s1, s2, s3, s4, s5, s6), CardioActivityType.TREADMILL, ProgressMetric.SPEED_AT_HR, today)
        assertEquals(148, p.referenceHeartRate)
        val s6Point = p.points.first { it.sessionId == 6L }
        assertEquals(5.62, s6Point.value, 0.01)
    }

    @Test
    fun baseline_and_latest_means_from_earliest_three_and_latest_three() {
        val s = listOf(
            session(today.minusDays(50).toString(), minutes = 30, speedMps = 5.0 / 2.23694, id = 1L),
            session(today.minusDays(40).toString(), minutes = 30, speedMps = 5.0 / 2.23694, id = 2L),
            session(today.minusDays(30).toString(), minutes = 30, speedMps = 5.0 / 2.23694, id = 3L),
            session(today.minusDays(20).toString(), minutes = 30, speedMps = 6.0 / 2.23694, id = 4L),
            session(today.minusDays(10).toString(), minutes = 30, speedMps = 6.0 / 2.23694, id = 5L),
            session(today.minusDays(0).toString(), minutes = 30, speedMps = 6.0 / 2.23694, id = 6L),
        )
        val p = CardioProgress.machineProgress(s, CardioActivityType.TREADMILL, ProgressMetric.SPEED, today)
        assertEquals(5.0, p.baseline!!, 0.01)
        assertEquals(6.0, p.latest!!, 0.01)
        assertEquals(1.0, p.delta!!, 0.01)
    }

    @Test
    fun delta_sign_and_faster_slower_word_for_pace() {
        // Pace baseline = 600s, latest = 540s (faster)
        val sFaster = listOf(
            session(today.minusDays(50).toString(), minutes = 10, metres = 1609.344, id = 1L),
            session(today.minusDays(40).toString(), minutes = 10, metres = 1609.344, id = 2L),
            session(today.minusDays(30).toString(), minutes = 10, metres = 1609.344, id = 3L),
            session(today.minusDays(20).toString(), minutes = 9, metres = 1609.344, id = 4L),
            session(today.minusDays(10).toString(), minutes = 9, metres = 1609.344, id = 5L),
            session(today.minusDays(0).toString(), minutes = 9, metres = 1609.344, id = 6L),
        )
        val pFaster = CardioProgress.machineProgress(sFaster, CardioActivityType.TREADMILL, ProgressMetric.PACE, today)
        assertTrue(pFaster.sentence.contains("faster"))
        assertFalse(pFaster.sentence.contains("slower"))

        // Pace baseline = 540s, latest = 600s (slower)
        val sSlower = listOf(
            session(today.minusDays(50).toString(), minutes = 9, metres = 1609.344, id = 1L),
            session(today.minusDays(40).toString(), minutes = 9, metres = 1609.344, id = 2L),
            session(today.minusDays(30).toString(), minutes = 9, metres = 1609.344, id = 3L),
            session(today.minusDays(20).toString(), minutes = 10, metres = 1609.344, id = 4L),
            session(today.minusDays(10).toString(), minutes = 10, metres = 1609.344, id = 5L),
            session(today.minusDays(0).toString(), minutes = 10, metres = 1609.344, id = 6L),
        )
        val pSlower = CardioProgress.machineProgress(sSlower, CardioActivityType.TREADMILL, ProgressMetric.PACE, today)
        assertTrue(pSlower.sentence.contains("slower"))
        assertFalse(pSlower.sentence.contains("faster"))
    }

    @Test
    fun holding_steady_sentence_when_delta_is_zero() {
        val sSteady = (0..5).map { i ->
            session(today.minusDays((5 - i).toLong() * 10).toString(), minutes = 30, speedMps = 5.0 / 2.23694, id = (i + 1).toLong())
        }
        val p = CardioProgress.machineProgress(sSteady, CardioActivityType.TREADMILL, ProgressMetric.SPEED, today)
        assertTrue(p.sentence.startsWith("Holding steady at 5.0 mph since"))
    }

    @Test
    fun no_hr_sessions_listed_in_excluded_at_zero() {
        val base = (1..6).map { i ->
            session(today.minusDays(i.toLong() * 2).toString(), minutes = 30, hr = 150, speedMps = 2.68224, id = i.toLong())
        }
        val noHr = session(today.minusDays(20).toString(), minutes = 30, hr = null, speedMps = 2.68224, id = 99L)
        val p = CardioProgress.machineProgress(base + noHr, CardioActivityType.TREADMILL, ProgressMetric.SPEED_AT_HR, today)
        val ex = p.excluded.firstOrNull { it.sessionId == 99L }
        assertNotNull(ex)
        assertEquals(0.0, ex.value)
    }
}
