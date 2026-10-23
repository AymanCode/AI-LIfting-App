package com.ayman.ecolift.agent

import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** ISO day number: Monday=1 .. Sunday=7 */
val DayOfWeek.isoDayNumber: Int get() = when (this) {
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
    DayOfWeek.SUNDAY -> 7
    else -> throw IllegalArgumentException("Unknown DayOfWeek: $this")
}

/** ISO day number of the weekday of this date: Monday=1 .. Sunday=7 */
val LocalDate.isoDayNumber: Int get() = dayOfWeek.isoDayNumber

/**
 * Parses date references from natural language into ISO yyyy-MM-dd strings.
 *
 * Handles:
 *  - Relative: "yesterday", "today"
 *  - Day names: "Monday", "last Tuesday" -> most recent past occurrence
 *  - Month + day: "April 5th", "March 12"
 *  - ISO literals: "2026-04-05"
 *
 * Pure function - no side effects, fully testable without Android.
 */
object DateExtractor {

    fun extract(text: String, today: LocalDate = todayLocalDate()): String? {
        val t = text.lowercase()

        if (t.contains("yesterday")) return today.minusDays(1).toString()
        if (t.contains("today"))     return today.toString()

        val dayMap = mapOf(
            "monday"    to DayOfWeek.MONDAY,    "tuesday"  to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY,
            "friday"    to DayOfWeek.FRIDAY,    "saturday" to DayOfWeek.SATURDAY,
            "sunday"    to DayOfWeek.SUNDAY
        )
        for ((name, dow) in dayMap) {
            if (t.contains(name)) {
                // Find most recent past occurrence of dow (never same day - go back 1 week if today matches)
                val todayIso = today.isoDayNumber   // Mon=1..Sun=7
                val targetIso = dow.isoDayNumber
                val daysBack = when {
                    todayIso > targetIso -> (todayIso - targetIso).toLong()
                    todayIso == targetIso -> 7L  // same weekday -> go back a full week
                    else -> (7 - targetIso + todayIso).toLong()
                }
                val target = today.minusDays(daysBack)
                return target.toString()
            }
        }

        // "April 5th", "march 12", "January 1"
        val monthDayRe = Regex(
            """(january|february|march|april|may|june|july|august|september|october|november|december)\s+(\d{1,2})(?:st|nd|rd|th)?"""
        )
        monthDayRe.find(t)?.let { m ->
            val monthNames = listOf(
                "january","february","march","april","may","june",
                "july","august","september","october","november","december"
            )
            val month = monthNames.indexOf(m.groupValues[1]) + 1
            val day   = m.groupValues[2].toIntOrNull() ?: return@let
            return try {
                var d = LocalDate(today.year, month, day)
                if (d > today) d = d.minus(1, DateTimeUnit.YEAR)
                d.toString()
            } catch (_: Exception) { null }
        }

        Regex("""\d{4}-\d{2}-\d{2}""").find(t)?.let { return it.value }

        return null
    }
}
