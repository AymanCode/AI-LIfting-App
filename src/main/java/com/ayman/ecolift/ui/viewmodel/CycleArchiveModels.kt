package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.ArchivedCycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

enum class SplitTabMode {
    CURRENT,
    ARCHIVE,
}

data class ArchiveCardUi(
    val id: Long,
    val name: String,
    val dateRangeLabel: String,
    val splitCount: Int,
    val sessionCount: Int,
    val totalVolumeLbs: Long,
)

private val monthDayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val monthDayYearFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

fun formatArchiveDateRange(startIso: String, endIso: String): String {
    val start = runCatching { LocalDate.parse(startIso) }.getOrNull()
    val end = runCatching { LocalDate.parse(endIso) }.getOrNull()
    if (start == null || end == null) return "$startIso - $endIso"

    val startLabel = if (start.year == end.year) {
        start.format(monthDayFormatter)
    } else {
        start.format(monthDayYearFormatter)
    }
    return "$startLabel - ${end.format(monthDayYearFormatter)}"
}

fun ArchivedCycle.toCardUi(): ArchiveCardUi = ArchiveCardUi(
    id = id,
    name = name,
    dateRangeLabel = formatArchiveDateRange(startDate, endDate),
    splitCount = splitCount,
    sessionCount = totalSessions,
    totalVolumeLbs = totalVolumeLbs,
)

fun formatSignedLbs(delta: Float): String = when {
    abs(delta) < 0.5f -> "no change"
    delta > 0f -> "+%.0f lb".format(Locale.US, delta)
    else -> "%.0f lb".format(Locale.US, delta)
}
