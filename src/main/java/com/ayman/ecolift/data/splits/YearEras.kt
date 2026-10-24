package com.ayman.ecolift.data.splits

import com.ayman.ecolift.ui.viewmodel.ArchiveCardUi
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.plusDays
import kotlinx.datetime.LocalDate

data class EraBand(
    val archiveId: Long?,
    val name: String,
    val startFrac: Float,
    val endFrac: Float,
)

internal fun buildEraBands(
    archives: List<ArchiveCardUi>,
    earliestWorkout: LocalDate?,
    today: LocalDate,
): List<EraBand> {
    val currentMonday = today.minusDays(today.dayOfWeek.ordinal.toLong())
    val windowStart = currentMonday.minusDays(51L * 7L)
    val windowEnd = currentMonday.plusDays(6L)
    val windowSpanDays = (windowEnd.toEpochDays() - windowStart.toEpochDays()).toFloat()

    fun fraction(date: LocalDate): Float =
        ((date.toEpochDays() - windowStart.toEpochDays()) / windowSpanDays).coerceIn(0f, 1f)

    val parsedArchives = archives.mapNotNull { archive ->
        val start = runCatching { LocalDate.parse(archive.startIso) }.getOrNull()
            ?: return@mapNotNull null
        val end = runCatching { LocalDate.parse(archive.endIso) }.getOrNull()
            ?: return@mapNotNull null
        if (end < start) return@mapNotNull null
        ParsedArchive(archive, start, end)
    }

    val archivedBands = parsedArchives.mapNotNull { archive ->
        if (archive.end < windowStart || archive.start > windowEnd) return@mapNotNull null
        val clippedStart = maxOf(archive.start, windowStart)
        val clippedEnd = minOf(archive.end, windowEnd)
        EraBand(
            archiveId = archive.card.id,
            name = archive.card.name,
            startFrac = fraction(clippedStart),
            endFrac = fraction(clippedEnd),
        )
    }.sortedBy { it.startFrac }

    val latestArchiveEnd = parsedArchives.maxOfOrNull { it.end }
    val currentStart = latestArchiveEnd?.plusDays(1L) ?: earliestWorkout
    val currentBand = currentStart
        ?.takeIf { it <= today }
        ?.let { start ->
            EraBand(
                archiveId = null,
                name = "Current cycle",
                startFrac = fraction(maxOf(start, windowStart)),
                endFrac = fraction(today),
            )
        }

    return if (currentBand == null) archivedBands else archivedBands + currentBand
}

private data class ParsedArchive(
    val card: ArchiveCardUi,
    val start: LocalDate,
    val end: LocalDate,
)
