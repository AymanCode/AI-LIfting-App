package com.ayman.ecolift.web.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val MONTH_NAMES = listOf(
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December",
)

@OptIn(ExperimentalTime::class)
fun todayLocalDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** Minimal replacement for java.time.YearMonth; kotlinx-datetime does not provide one. */
data class YearMonthW(val year: Int, val month: Int) {
    init {
        require(month in 1..12) { "month must be in 1..12" }
    }

    fun lengthOfMonth(): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (isLeapYear(year)) 29 else 28
    }

    fun plusMonths(n: Int): YearMonthW {
        val totalMonths = year * 12 + (month - 1) + n
        val nextYear = floorDiv(totalMonths, 12)
        return YearMonthW(nextYear, totalMonths - nextYear * 12 + 1)
    }

    fun minusMonths(n: Int): YearMonthW = plusMonths(-n)

    fun atDay(day: Int): LocalDate = LocalDate(year, month, day)

    fun atEndOfMonth(): LocalDate = atDay(lengthOfMonth())

    fun label(): String = "${MONTH_NAMES[month - 1]} $year"

    companion object {
        fun from(date: LocalDate): YearMonthW = YearMonthW(date.year, date.month.ordinal + 1)

        @OptIn(ExperimentalTime::class)
        fun now(): YearMonthW = from(todayLocalDate())
    }
}

/** Replaces String.format("%.2f", value).trimEnd('0').trimEnd('.') for wasm. */
fun formatTrimmedDecimal(value: Double): String {
    val cents = (abs(value) * 100).roundToLong()
    val sign = if (value < 0) "-" else ""
    val whole = cents / 100
    val frac = (cents % 100).toInt()
    return when {
        frac == 0 -> "$sign$whole"
        frac % 10 == 0 -> "$sign$whole.${frac / 10}"
        else -> "$sign$whole.${frac.toString().padStart(2, '0')}"
    }
}

/** Replaces String.format("%.1f", tenths / 10.0) for stored WeightLbs tenths. */
fun formatTenths(tenths: Int): String =
    if (tenths % 10 == 0) {
        (tenths / 10).toString()
    } else {
        "${tenths / 10}.${abs(tenths % 10)}"
    }

fun LocalDate.plusDays(days: Long): LocalDate = plus(DatePeriod(days = days.toInt()))

fun LocalDate.minusDays(days: Long): LocalDate = minus(DatePeriod(days = days.toInt()))

fun LocalDate.minusMonths(months: Long): LocalDate = minus(DatePeriod(months = months.toInt()))

fun LocalDate.minusYears(years: Long): LocalDate = minus(DatePeriod(years = years.toInt()))

fun LocalDate.isBefore(other: LocalDate): Boolean = this < other

private fun isLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

private fun floorDiv(value: Int, divisor: Int): Int {
    var quotient = value / divisor
    val remainder = value % divisor
    if (remainder != 0 && (value xor divisor) < 0) {
        quotient--
    }
    return quotient
}

@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

val MONTH_ABBREV = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** ISO day numbers 1..7 (Mon..Sun) -> abbreviations matching java.time "EEE". */
val DAY_ABBREV = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** "8:51 PM" for an instant in [zone]. */
@OptIn(ExperimentalTime::class)
fun clockLabel(instant: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = instant.toLocalDateTime(zone).time
    val hour12 = ((time.hour + 11) % 12) + 1
    val minutes = time.minute.toString().padStart(2, '0')
    val amPm = if (time.hour < 12) "AM" else "PM"
    return "$hour12:$minutes $amPm"
}

@OptIn(ExperimentalTime::class)
fun nowClockLabel(): String = clockLabel(Clock.System.now())
