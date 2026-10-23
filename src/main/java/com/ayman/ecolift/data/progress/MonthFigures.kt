package com.ayman.ecolift.data.progress

import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.formatRecordedVolumeLbs

/** One closing figure: a large value and a small caption. */
data class MonthFigure(
    val value: String,
    val caption: String,
)

/**
 * The three closing figures.
 *
 * A session without a split is not a finding — plenty of training simply isn't part of
 * a split — so no figure counts them. These state, they never advise. Permitted verbs are took / ran / was. A figure whose
 * precondition fails is dropped rather than softened, so a balanced month simply shows
 * fewer figures instead of manufacturing a problem.
 */
object MonthFigures {

    fun build(rollup: MonthRollupResult): List<MonthFigure> = listOfNotNull(
        heaviestDay(rollup),
        rotation(rollup),
    )

    private fun heaviestDay(rollup: MonthRollupResult): MonthFigure? {
        val day = rollup.heaviestDay ?: return null
        val month = MONTH_NAMES.getOrNull(day.date.month.ordinal) ?: return null
        return MonthFigure(
            value = formatRecordedVolumeLbs(day.loadLbs),
            caption = "HEAVIEST DAY\n$month ${day.date.day} · ${countLabel(day.sets, "SET")}",
        )
    }

    /** Fires only when at least two slots ran and their counts differ. */
    private fun rotation(rollup: MonthRollupResult): MonthFigure? {
        val runs = rollup.slotRuns
        if (runs.size < 2) return null
        val fewest = runs.minByOrNull { it.runs } ?: return null
        val most = runs.maxByOrNull { it.runs } ?: return null
        if (fewest.runs == most.runs) return null
        val others = runs.filter { it.slotId != fewest.slotId }
        return MonthFigure(
            value = "×${fewest.runs}",
            caption = "${fewest.name.uppercase()} RUNS\n" +
                others.joinToString(" · ") { "${it.name.uppercase()} ×${it.runs}" },
        )
    }

    private val MONTH_NAMES = listOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
    )
}
