package com.ayman.ecolift.data

import kotlin.test.Test
import kotlin.test.assertEquals

class MonthPaceUtilTest {

    @Test
    fun projects_correctly_mid_month() {
        // 5 workouts in 10 days, 30 days in month -> projected should be 15
        assertEquals(15, MonthPaceUtil.projectedMonthCount(5, 10, 30))
    }

    @Test
    fun rounds_correctly() {
        // 5 workouts in 12 days, 30 days in month -> 5/12 * 30 = 12.5 -> rounds to 13
        assertEquals(13, MonthPaceUtil.projectedMonthCount(5, 12, 30))

        // 4 workouts in 13 days, 30 days in month -> 4/13 * 30 = 9.23 -> rounds to 9
        assertEquals(9, MonthPaceUtil.projectedMonthCount(4, 13, 30))
    }

    @Test
    fun handles_zero_days_elapsed_safely() {
        assertEquals(0, MonthPaceUtil.projectedMonthCount(0, 0, 30))
        assertEquals(0, MonthPaceUtil.projectedMonthCount(5, 0, 30))
    }
}
