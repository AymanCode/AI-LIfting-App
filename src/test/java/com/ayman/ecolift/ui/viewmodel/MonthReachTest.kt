package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.web.util.YearMonthW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How far back the arrows reach. */
class MonthReachTest {

    private fun set(date: String, completed: Boolean = true) = WorkoutSet(
        exerciseId = 1,
        date = date,
        setNumber = 1,
        weightLbs = 1_000,
        reps = 10,
        completed = completed,
    )

    @Test
    fun the_earliest_month_is_the_month_of_the_oldest_checked_set() {
        val sets = listOf(set("2026-05-04"), set("2025-11-02"), set("2026-08-01"))

        assertEquals(YearMonthW(2025, 11), earliestMonthWithSessions(sets))
    }

    @Test
    fun an_unchecked_set_never_extends_the_reach() {
        val sets = listOf(set("2024-01-01", completed = false), set("2026-05-04"))

        assertEquals(YearMonthW(2026, 5), earliestMonthWithSessions(sets))
    }

    @Test
    fun a_log_with_nothing_checked_has_no_earliest_month() {
        assertNull(earliestMonthWithSessions(listOf(set("2026-05-04", completed = false))))
        assertNull(earliestMonthWithSessions(emptyList()))
    }

    @Test
    fun the_earliest_month_itself_cannot_step_back() {
        assertFalse(canGoEarlier(selected = YearMonthW(2025, 11), earliest = YearMonthW(2025, 11)))
    }

    @Test
    fun a_later_month_can_step_back() {
        assertTrue(canGoEarlier(selected = YearMonthW(2025, 12), earliest = YearMonthW(2025, 11)))
        assertTrue(canGoEarlier(selected = YearMonthW(2026, 1), earliest = YearMonthW(2025, 11)))
    }

    @Test
    fun a_month_before_the_earliest_cannot_step_back_further() {
        assertFalse(canGoEarlier(selected = YearMonthW(2025, 10), earliest = YearMonthW(2025, 11)))
        assertFalse(canGoEarlier(selected = YearMonthW(2024, 12), earliest = YearMonthW(2025, 11)))
    }
}
