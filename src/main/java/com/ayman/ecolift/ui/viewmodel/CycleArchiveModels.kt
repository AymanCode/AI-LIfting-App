package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.ArchivedCycle
import com.ayman.ecolift.web.supabase.ArchiveSummary
import com.ayman.ecolift.web.util.MONTH_ABBREV
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SplitTabMode {
    CURRENT,
    ARCHIVE,
}

data class ArchiveCardUi(
    val id: Long,
    val name: String,
    val startIso: String,
    val endIso: String,
    val dateRangeLabel: String,
    val splitCount: Int,
    val sessionCount: Int,
    val totalVolumeLbs: Long,
    /** Sessions per week across the cycle, for the album art; empty until loaded or when loading fails. */
    val weeklySessions: List<Int> = emptyList(),
)

fun formatArchiveDateRange(startIso: String, endIso: String): String {
    val start = runCatching { LocalDate.parse(startIso) }.getOrNull()
    val end = runCatching { LocalDate.parse(endIso) }.getOrNull()
    if (start == null || end == null) return "$startIso - $endIso"

    val startLabel = if (start.year == end.year) {
        "${MONTH_ABBREV[start.month.ordinal]} ${start.dayOfMonth}"
    } else {
        "${MONTH_ABBREV[start.month.ordinal]} ${start.dayOfMonth}, ${start.year}"
    }
    return "$startLabel - ${MONTH_ABBREV[end.month.ordinal]} ${end.dayOfMonth}, ${end.year}"
}

fun ArchivedCycle.toCardUi(): ArchiveCardUi = ArchiveCardUi(
    id = id,
    name = name,
    startIso = startDate,
    endIso = endDate,
    dateRangeLabel = formatArchiveDateRange(startDate, endDate),
    splitCount = splitCount,
    sessionCount = totalSessions,
    totalVolumeLbs = totalVolumeLbs,
)

fun ArchivedCycle.toCardUi(summary: ArchiveSummary): ArchiveCardUi = ArchiveCardUi(
    id = id,
    name = name,
    startIso = startDate,
    endIso = endDate,
    dateRangeLabel = formatArchiveDateRange(startDate, endDate),
    splitCount = summary.splitCount,
    sessionCount = summary.totalSessions,
    totalVolumeLbs = totalVolumeLbs,
)

fun formatSignedLbs(delta: Float): String = when {
    abs(delta) < 0.5f -> "no change"
    delta > 0f -> "+${delta.roundToInt()} lb"
    else -> "${delta.roundToInt()} lb"
}
