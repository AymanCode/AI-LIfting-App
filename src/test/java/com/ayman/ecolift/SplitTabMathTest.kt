package com.ayman.ecolift

import com.ayman.ecolift.data.splits.dialAngleFor
import com.ayman.ecolift.data.splits.dialShortestHome
import com.ayman.ecolift.data.splits.dialSnapIndex
import com.ayman.ecolift.data.splits.isDialTap
import com.ayman.ecolift.data.splits.tickerSegments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SplitTabMathTest {
    @Test
    fun ticker_builds_all_segments_for_current_month_after_first_week() {
        assertEquals(
            listOf(
                "9 SESSIONS",
                "412K LB MOVED",
                "ON PACE FOR ~16",
                "AVG 15.5/MO",
            ),
            tickerSegments(
                monthSessions = 9,
                monthVolumeLbs = 412_000L,
                isCurrentMonth = true,
                dayOfMonth = 17,
                projected = 16,
                monthlyAvg = 15.5,
                lifetimeCount = 155,
                tierName = "Glossy",
            ),
        )
    }

    @Test
    fun ticker_omits_pace_outside_current_eligible_month() {
        val segments = tickerSegments(
            monthSessions = 9,
            monthVolumeLbs = 412_000L,
            isCurrentMonth = false,
            dayOfMonth = 17,
            projected = 16,
            monthlyAvg = 15.5,
            lifetimeCount = 155,
            tierName = "Glossy",
        )

        assertFalse(segments.any { it.startsWith("ON PACE") })
    }

    @Test
    fun angle_for_index_advances_counterclockwise() {
        assertEquals(-90f, dialAngleFor(index = 1, segmentCount = 4))
    }

    @Test
    fun snap_inverts_negative_ring_angle() {
        assertEquals(1, dialSnapIndex(totalDeg = -95f, segmentCount = 4))
    }

    @Test
    fun snap_normalizes_positive_ring_angle() {
        assertEquals(3, dialSnapIndex(totalDeg = 50f, segmentCount = 4))
    }

    @Test
    fun shortest_home_chooses_forward_from_last_position() {
        assertEquals(1, dialShortestHome(peekIndex = 3, segmentCount = 4))
    }

    @Test
    fun shortest_home_chooses_backward_from_first_position() {
        assertEquals(-1, dialShortestHome(peekIndex = 1, segmentCount = 4))
    }

    @Test
    fun dial_tap_requires_movement_under_eight_degrees() {
        assertTrue(isDialTap(7.99f))
        assertFalse(isDialTap(8f))
    }
}
