package com.ayman.ecolift.ui.cardio

import com.ayman.ecolift.web.util.DAY_ABBREV
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.minusDays
import kotlinx.datetime.LocalDate

fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    } else {
        "$minutes:${secs.toString().padStart(2, '0')}"
    }
}

fun formatDurationCompact(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        seconds <= 0 -> "0m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

fun formatThousands(n: Int): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val sb = StringBuilder()
    var count = 0
    for (i in s.indices.reversed()) {
        if (count > 0 && count % 3 == 0) sb.insert(0, ',')
        sb.insert(0, s[i])
        count++
    }
    return sb.toString()
}

fun formatDateLabel(date: String, today: LocalDate): String =
    runCatching {
        val parsed = LocalDate.parse(date)
        when {
            parsed == today -> "Today"
            parsed == today.minusDays(1L) -> "Yesterday"
            else -> {
                val dayAbbr = DAY_ABBREV.getOrNull(parsed.dayOfWeek.ordinal) ?: ""
                val monthAbbr = MONTH_ABBREV.getOrNull(parsed.month.ordinal) ?: ""
                "$dayAbbr, $monthAbbr ${parsed.dayOfMonth}"
            }
        }
    }.getOrDefault(date)
