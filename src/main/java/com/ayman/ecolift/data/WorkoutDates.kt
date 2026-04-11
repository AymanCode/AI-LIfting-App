package com.ayman.ecolift.data

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

object WorkoutDates {
    private val fullFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
    private val shortFormatter = DateTimeFormatter.ofPattern("MMM d")

    fun today(): String = LocalDate.now().toString()

    fun addDays(date: String, delta: Long): String =
        LocalDate.parse(date).plusDays(delta).toString()

    fun startOfWeek(date: String): String {
        return LocalDate.parse(date)
            .with(DayOfWeek.MONDAY)
            .toString()
    }

    fun formatHeader(date: String): String {
        return if (date == today()) {
            "Today"
        } else {
            LocalDate.parse(date).format(fullFormatter)
        }
    }

    fun formatAxis(date: String): String = LocalDate.parse(date).format(shortFormatter)
}
