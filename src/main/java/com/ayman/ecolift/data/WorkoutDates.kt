package com.ayman.ecolift.data

import com.ayman.ecolift.web.util.DAY_ABBREV
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.plusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

// Web port of the Android WorkoutDates (java.time -> kotlinx-datetime).
object WorkoutDates {
    fun today(): String = todayLocalDate().toString()

    fun addDays(date: String, delta: Long): String =
        LocalDate.parse(date).plusDays(delta).toString()

    fun formatHeader(date: String): String {
        if (date == today()) return "Today"
        val ld = LocalDate.parse(date)
        return "${DAY_ABBREV[ld.dayOfWeek.isoDayNumber - 1]}, ${MONTH_ABBREV[ld.month.ordinal]} ${ld.day}"
    }

    fun formatAxis(date: String): String {
        val ld = LocalDate.parse(date)
        return "${MONTH_ABBREV[ld.month.ordinal]} ${ld.day}"
    }

    fun startOfWeek(date: String): String {
        val ld = LocalDate.parse(date)
        return ld.minusDays((ld.dayOfWeek.isoDayNumber % 7).toLong()).toString()
    }
}
