package com.ayman.ecolift.ui.viewmodel

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressMonthDeltaTest {
    private fun point(
        date: String,
        maxWeight: Int,
        maxReps: Int = 0,
        repsAtTopWeight: Int = maxReps,
    ) = ProgressPointUi(
        date = date,
        label = date,
        volume = 0,
        estimated1RM = 0f,
        maxWeight = maxWeight,
        maxReps = maxReps,
        repsAtTopWeight = repsAtTopWeight,
    )

    private val monthStart = LocalDate(2026, 6, 3)

    @Test
    fun weighted_weight_increase_uses_lbs() {
        val delta = calculatePastMonthDeltaLabel(
            points = listOf(
                point("2026-06-10", maxWeight = 2300, repsAtTopWeight = 10),
                point("2026-07-01", maxWeight = 2400, repsAtTopWeight = 8),
            ),
            isBodyweight = false,
            monthStart = monthStart,
        )

        assertEquals("+10 lb", delta?.label)
        assertEquals(1, delta?.sign)
        assertEquals("past month", delta?.subLabel)
    }

    @Test
    fun weighted_flat_weight_with_rep_gain_uses_reps_at_top_weight() {
        val delta = calculatePastMonthDeltaLabel(
            points = listOf(
                point("2026-06-10", maxWeight = 2400, repsAtTopWeight = 10),
                point("2026-07-01", maxWeight = 2400, repsAtTopWeight = 13),
            ),
            isBodyweight = false,
            monthStart = monthStart,
        )

        assertEquals("+3 reps", delta?.label)
        assertEquals(1, delta?.sign)
        assertEquals("at 240 lb", delta?.subLabel)
    }

    @Test
    fun weighted_flat_weight_and_reps_is_steady() {
        val delta = calculatePastMonthDeltaLabel(
            points = listOf(
                point("2026-06-10", maxWeight = 2400, repsAtTopWeight = 10),
                point("2026-07-01", maxWeight = 2400, repsAtTopWeight = 10),
            ),
            isBodyweight = false,
            monthStart = monthStart,
        )

        assertEquals("steady", delta?.label)
        assertEquals(0, delta?.sign)
        assertEquals("past month", delta?.subLabel)
    }

    @Test
    fun bodyweight_rep_increase_uses_max_reps() {
        val delta = calculatePastMonthDeltaLabel(
            points = listOf(
                point("2026-06-10", maxWeight = 0, maxReps = 8),
                point("2026-07-01", maxWeight = 0, maxReps = 11),
            ),
            isBodyweight = true,
            monthStart = monthStart,
        )

        assertEquals("+3 reps", delta?.label)
        assertEquals(1, delta?.sign)
        assertEquals("past month", delta?.subLabel)
    }

    @Test
    fun single_month_point_has_no_delta() {
        val delta = calculatePastMonthDeltaLabel(
            points = listOf(point("2026-07-01", maxWeight = 2400, repsAtTopWeight = 10)),
            isBodyweight = false,
            monthStart = monthStart,
        )

        assertNull(delta)
    }
}
