package com.ayman.ecolift.ui.cardio

import com.ayman.ecolift.cardio.CardioInsights
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.web.util.DAY_ABBREV
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.minusDays
import kotlinx.datetime.LocalDate

/** The last session on one machine, as the card under its name prints it: "30:00" and "Wed". */
data class MachineLast(val duration: String, val whenLabel: String)

/**
 * The newest counting session on [type], or null when that machine has never been used. The day
 * reads the way the Cardio tab's own rows read it: Today, Yesterday, a weekday inside the last week,
 * then a date (with the year once it is not this year's).
 */
internal fun machineLast(sessions: List<CardioSession>, type: CardioActivityType, today: LocalDate): MachineLast? {
    val last = CardioInsights.lastOnMachine(sessions, type) ?: return null
    val seconds = last.durationSec ?: return null
    val date = runCatching { LocalDate.parse(last.date) }.getOrNull() ?: return null
    val whenLabel = when {
        date == today -> "Today"
        date == today.minusDays(1L) -> "Yesterday"
        date > today.minusDays(7L) && date < today -> DAY_ABBREV[date.dayOfWeek.ordinal]
        date.year == today.year -> "${MONTH_ABBREV[date.monthNumber - 1]} ${date.dayOfMonth}"
        else -> "${MONTH_ABBREV[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"
    }
    return MachineLast(formatDuration(seconds), whenLabel)
}

/**
 * The machine cards before "More": the four most-used machines, plus the machine the draft is on
 * when it came from the More sheet, so the chosen machine always has a card of its own.
 */
internal fun machineCardOrder(machineOrder: List<CardioActivityType>, selected: CardioActivityType): List<CardioActivityType> {
    val top = machineOrder.take(4)
    return if (selected in top) top else top + selected
}
