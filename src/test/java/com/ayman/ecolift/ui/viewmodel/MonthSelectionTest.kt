package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.web.util.YearMonthW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonthSelectionTest {

    @Test
    fun stepping_back_from_january_lands_in_the_previous_december() {
        assertEquals(YearMonthW(2025, 12), YearMonthW(2026, 1).minusMonths(1))
    }

    @Test
    fun stepping_forward_from_december_lands_in_the_next_january() {
        assertEquals(YearMonthW(2027, 1), YearMonthW(2026, 12).plusMonths(1))
    }

    @Test
    fun the_current_month_cannot_step_forward() {
        val now = YearMonthW.now()
        assertFalse(canGoLater(selected = now, now = now))
    }

    @Test
    fun a_past_month_can_step_forward() {
        val now = YearMonthW(2026, 5)
        assertTrue(canGoLater(selected = YearMonthW(2026, 4), now = now))
    }

    @Test
    fun a_future_month_cannot_step_forward_either() {
        val now = YearMonthW(2026, 5)
        assertFalse(canGoLater(selected = YearMonthW(2026, 6), now = now))
    }

    @Test
    fun the_week_index_is_stable_within_a_week() {
        assertEquals(weekIndexFor("2026-05-04"), weekIndexFor("2026-05-07"))
    }

    @Test
    fun the_week_index_changes_between_weeks() {
        assertTrue(weekIndexFor("2026-05-04") != weekIndexFor("2026-05-11"))
    }

    @Test
    fun the_week_index_advances_by_one_per_week() {
        assertEquals(weekIndexFor("2026-05-04") + 1, weekIndexFor("2026-05-11"))
    }

    @Test
    fun the_week_index_is_never_negative_for_realistic_dates() {
        assertTrue(weekIndexFor("2026-05-04") >= 0)
    }
}
