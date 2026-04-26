package com.ayman.ecolift.agent

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

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

    fun extract(text: String, today: LocalDate = LocalDate.now()): String? {
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
                var target = today.with(TemporalAdjusters.previousOrSame(dow))
                if (target == today) target = target.minusWeeks(1)
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
                var d = LocalDate.of(today.year, month, day)
                if (d.isAfter(today)) d = d.minusYears(1)
                d.toString()
            } catch (_: Exception) { null }
        }

        Regex("""\d{4}-\d{2}-\d{2}""").find(t)?.let { return it.value }

        return null
    }
}
