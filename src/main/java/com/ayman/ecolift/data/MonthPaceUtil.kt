package com.ayman.ecolift.data

import kotlin.math.roundToInt

object MonthPaceUtil {
    fun projectedMonthCount(countSoFar: Int, daysElapsed: Int, daysInMonth: Int): Int {
        if (daysElapsed <= 0) return 0
        val projected = (countSoFar.toDouble() / daysElapsed) * daysInMonth
        return projected.roundToInt()
    }
}
