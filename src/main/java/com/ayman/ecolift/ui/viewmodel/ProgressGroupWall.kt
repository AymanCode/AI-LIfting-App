package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutMetrics
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.epleyEstimate
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.web.util.noun
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

internal data class GroupWallSource(
    val sections: List<HeldSectionUi> = emptyList(),
    val model: LearnedModel = LearnedModel.EMPTY,
    val exercises: List<Exercise> = emptyList(),
    val sets: List<WorkoutSet> = emptyList(),
    val days: List<WorkoutDay> = emptyList(),
    val query: String = "",
)

/** Calendar time, never session index: a missing week keeps its space on every tile. */
internal fun groupWallDateFraction(date: LocalDate, start: LocalDate, end: LocalDate): Float =
    ((date.toEpochDays() - start.toEpochDays()).toFloat() /
        (end.toEpochDays() - start.toEpochDays()).coerceAtLeast(1)).coerceIn(0f, 1f)

/** What a tile's line plots, and the label printed under its y gutter. */
internal enum class GroupWallUnit(val label: String) { ONE_RM("1RM"), ADDED_LB("+LB"), REPS("REPS") }

/** One session on the line: its own date and its score in [GroupWallUnit]. */
internal data class GroupWallPoint(val date: LocalDate, val value: Double)

private val GROUP_WALL_MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

private fun windowDays(start: LocalDate, end: LocalDate): Long = end.toEpochDays().toLong() - start.toEpochDays().toLong()

/**
 * What the line plots. Plain lifts and bodyweight lifts with a known bodyweight score
 * (bodyweight + added) × (1 + reps/30), so a rep gain at the same load rises. With no bodyweight a
 * lift that was ever loaded plots added load × (1 + reps/30) as `+LB`; one that never was plots reps.
 */
internal fun groupWallUnit(isBodyweight: Boolean, bodyweightLbs: Int?, tops: List<TopSet>): GroupWallUnit = when {
    !isBodyweight -> GroupWallUnit.ONE_RM
    bodyweightLbs != null && bodyweightLbs > 0 -> GroupWallUnit.ONE_RM
    tops.any { it.loadStored > 0 } -> GroupWallUnit.ADDED_LB
    else -> GroupWallUnit.REPS
}

internal fun groupWallScore(top: TopSet, isBodyweight: Boolean, bodyweightLbs: Int?, unit: GroupWallUnit): Double {
    val lbs = WeightLbs.toLbs(top.loadStored)
    return when (unit) {
        GroupWallUnit.REPS -> top.reps.toDouble()
        GroupWallUnit.ADDED_LB -> epleyEstimate(lbs, top.reps)
        GroupWallUnit.ONE_RM -> epleyEstimate(if (isBodyweight) (bodyweightLbs ?: 0) + lbs else lbs, top.reps)
    }
}

/**
 * Long windows keep one point per week (46–400 days, Monday start) or per calendar month (over 400
 * days): the session with the highest value in that bucket, a later session winning a tie, drawn at
 * its own date. Windows of 45 days or less keep every session.
 */
internal fun groupWallBuckets(points: List<GroupWallPoint>, start: LocalDate, end: LocalDate): List<GroupWallPoint> {
    val len = windowDays(start, end)
    if (len <= 45) return points
    fun key(date: LocalDate): Long =
        if (len > 400) date.year * 12L + date.month.ordinal
        else date.toEpochDays().toLong() - date.dayOfWeek.ordinal // Monday-start week
    val best = LinkedHashMap<Long, GroupWallPoint>()
    points.forEach { p ->
        val k = key(p.date)
        val current = best[k]
        if (current == null || p.value >= current.value) best[k] = p
    }
    return best.values.sortedBy { it.date.toEpochDays() }
}

/** The y range and the 1–3 round gridlines inside it. */
internal data class GroupWallYAxis(val ticks: List<Double>, val lo: Double, val hi: Double)

private fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0

/**
 * Flat or single-point data gets one gridline on the value (whole pounds for 1RM, exact otherwise)
 * with the range padded 5% around it. Otherwise the range hugs the data by 8% and the step is the
 * first of 1, 2, 2.5, 5 × 10^k (k from −1 up) that puts one to three gridlines inside it.
 */
internal fun groupWallYAxis(min: Double, max: Double, exactFlat: Boolean): GroupWallYAxis {
    if (max - min < maxOf(0.5, abs(max) * 0.01)) {
        val v = (min + max) / 2
        val tick = if (exactFlat) v else v.roundToLong().toDouble()
        val pad = maxOf(abs(v) * 0.05, 1.0)
        return GroupWallYAxis(listOf(tick), v - pad, v + pad)
    }
    val span = maxOf(max - min, abs(max) * 0.04, 1.0)
    val lo = min - span * 0.08
    val hi = max + span * 0.08
    for (k in -1..6) for (m in listOf(1.0, 2.0, 2.5, 5.0)) {
        val step = m * 10.0.pow(k)
        val first = ceil(lo / step).toLong()
        val ticks = generateSequence(first) { it + 1 }.map { it * step }.takeWhile { it <= hi + 1e-9 }
            .map(::round2).take(4).toList()
        if (ticks.size in 1..3) return GroupWallYAxis(ticks, lo, hi)
    }
    return GroupWallYAxis(emptyList(), lo, hi)
}

/** Integers print bare; everything else gets one decimal. */
internal fun groupWallAxisLabel(v: Double): String {
    if (v == floor(v)) return v.toLong().toString()
    val tenths = (abs(v) * 10).roundToLong()
    return (if (v < 0) "−" else "") + (tenths / 10) + "." + (tenths % 10)
}

/**
 * The marks under the baseline, by window length: up to 45 days every Monday with its day of month
 * (the month prefixing the first and each change); to 200 days Mondays as minor marks plus labelled
 * month starts; to 400 days month starts with the quarters labelled and January as `’26`; beyond that
 * quarter starts as minor marks and January 1 labelled with the year.
 */
internal fun groupWallXTicks(start: LocalDate, end: LocalDate): List<GroupWallXTick> {
    val a = start.toEpochDays().toLong()
    val b = end.toEpochDays().toLong()
    val len = b - a
    val span = maxOf(1L, len).toFloat()
    val out = mutableListOf<Pair<Long, GroupWallXTick>>()
    fun add(n: Long, label: String?, major: Boolean) { out += n to GroupWallXTick((n - a) / span, label, major) }
    fun monthStarts(): List<LocalDate> {
        val starts = mutableListOf<LocalDate>()
        var d = LocalDate(start.year, start.month, 1).plus(DatePeriod(months = 1))
        while (d.toEpochDays().toLong() <= b) { starts += d; d = d.plus(DatePeriod(months = 1)) }
        return starts
    }
    fun mondays(each: (Long, LocalDate) -> Unit) {
        for (n in a..b) {
            val date = LocalDate.fromEpochDays(n.toInt())
            if (date.dayOfWeek == DayOfWeek.MONDAY) each(n, date)
        }
    }
    when {
        len <= 45 -> {
            var previousMonth = -1
            mondays { n, date ->
                val month = date.month.ordinal
                add(n, (if (month != previousMonth) GROUP_WALL_MONTHS[month] + " " else "") + date.day, true)
                previousMonth = month
            }
        }
        len <= 200 -> {
            mondays { n, _ -> add(n, null, false) }
            monthStarts().forEach { add(it.toEpochDays().toLong(), GROUP_WALL_MONTHS[it.month.ordinal], true) }
        }
        len <= 400 -> monthStarts().forEach { d ->
            val month = d.month.ordinal
            val quarter = month % 3 == 0
            val label = if (!quarter) null else if (month == 0) "’" + (d.year % 100).toString().padStart(2, '0') else GROUP_WALL_MONTHS[month]
            add(d.toEpochDays().toLong(), label, quarter)
        }
        else -> monthStarts().forEach { d ->
            val month = d.month.ordinal
            if (month % 3 == 0) add(d.toEpochDays().toLong(), if (month == 0) d.year.toString() else null, month == 0)
        }
    }
    return out.sortedBy { it.first }.map { it.second }
}

internal fun groupWallTopSet(top: TopSet, isBodyweight: Boolean): GroupWallTopSetUi = when {
    isBodyweight && top.loadStored > 0 -> GroupWallTopSetUi("BW+${WeightLbs.formatStored(top.loadStored)}", top.reps.toString(), true)
    isBodyweight -> GroupWallTopSetUi("BW", top.reps.toString(), false)
    else -> GroupWallTopSetUi(WeightLbs.formatStored(top.loadStored), top.reps.toString(), true)
}

/** `ONE SESSION` only when every tail lift has exactly one session in the window, else `+N LIFTS`. */
internal fun groupWallTailLabel(tail: List<GroupWallTailUi>): String = when {
    tail.isEmpty() -> ""
    tail.all { it.sessions == 1 } -> "ONE SESSION"
    else -> "+${tail.size} ${noun(tail.size, "LIFT")}"
}

internal fun groupWallTile(
    exercise: Exercise,
    history: List<WorkoutSet>,
    start: LocalDate,
    end: LocalDate,
    bodyweightLbs: Int? = null,
): GroupWallTileUi {
    // Use the report's session/top-set selection and its wins-only helper (including back-off sets).
    val report = buildExerciseReport(history, exercise.isBodyweight, start, end)
    val events = report.events.asReversed()
    // Each session's top set is scored by [groupWallScore]; long windows keep the best session per week or month.
    val unit = groupWallUnit(exercise.isBodyweight, bodyweightLbs, events.map { it.to })
    val points = groupWallBuckets(events.map { event ->
        GroupWallPoint(LocalDate.parse(event.date), groupWallScore(event.to, exercise.isBodyweight, bodyweightLbs, unit))
    }, start, end)
    val xTicks = groupWallXTicks(start, end)
    val chart = if (points.isEmpty()) GroupWallChartUi(unit = unit.label, xTicks = xTicks) else {
        val axis = groupWallYAxis(points.minOf { it.value }, points.maxOf { it.value }, exactFlat = unit != GroupWallUnit.ONE_RM)
        val range = axis.hi - axis.lo
        GroupWallChartUi(
            xs = points.map { groupWallDateFraction(it.date, start, end) },
            ys = points.map { ((it.value - axis.lo) / range).toFloat() },
            yTicks = axis.ticks.map { GroupWallYTick(((it - axis.lo) / range).toFloat(), groupWallAxisLabel(it)) },
            unit = unit.label,
            xTicks = xTicks,
        )
    }
    val topSet = report.topSet
    val top = topSet?.let { formatTicketTopSet(it, exercise.isBodyweight) } ?: "—"
    val count = "${report.sessions} session${if (report.sessions == 1) "" else "s"}"
    val win = report.windowTopSetWin()
    return GroupWallTileUi(
        exercise.id, exercise.name, chart, listOf(data(top)),
        topSet?.let { groupWallTopSet(it, exercise.isBodyweight) },
        report.sessions, win,
        win?.label?.substringBefore(' ').orEmpty(), win?.label?.substringAfter(' ', "")?.uppercase().orEmpty(),
        "Open ${exercise.name} report, $top, $count" + (win?.let { ", ${it.label}" } ?: ""),
    )
}

/**
 * Recent window sessions lead, with held order breaking ties; tails never enter the grid.
 * Faded membership stays with each source section (even if shared); deduplicate within a panel,
 * never across panels. Unheld lifts follow their latest attributed group in the window.
 */
internal fun buildGroupWall(
    source: GroupWallSource,
    start: LocalDate,
    end: LocalDate,
    canonicalId: (Long) -> Long,
    bodyweightLbs: Int? = null,
): GroupWallUi {
    val byId = source.exercises.associateBy { it.id }
    val byLift = source.sets.groupBy { canonicalId(it.exerciseId) }
    val startIso = start.toString()
    val endIso = end.toString()
    val allWorkingByDate = source.sets.filter(WorkoutMetrics::isStrengthEligible).groupBy { it.date }
    val workingByDate = allWorkingByDate.filterKeys { it in startIso..endIso }
    val days = source.days.associateBy { it.date }
    // displaySlotId preserves frozen attribution outside the engine's memory/archived boundary.
    val allDatesByGroup = allWorkingByDate.keys.groupBy { date ->
        source.model.groupForSlot(source.model.displaySlotId(date, days[date]))?.key
    }
    val needle = source.query.trim().lowercase()
    val tailOwner = mutableMapOf<Long, String>()
    workingByDate.entries.sortedBy { it.key }.forEach { (date, sets) ->
        val group = source.model.groupForSlot(source.model.displaySlotId(date, days[date]))
        if (group != null) sets.forEach { set ->
            val id = canonicalId(set.exerciseId)
            if (id !in source.model.roles) tailOwner[id] = group.key
        }
    }
    val tileCache = mutableMapOf<Long, GroupWallTileUi>()
    fun tile(id: Long): GroupWallTileUi? {
        val exercise = byId[id] ?: return null
        return tileCache.getOrPut(id) { groupWallTile(exercise, byLift[id].orEmpty(), start, end, bodyweightLbs) }
    }
    val panels = source.sections.mapIndexed { index, section ->
        val tiles = section.rows.filterNot { it.faded }.mapNotNull { tile(it.exerciseId) }
        val tailIds = section.rows.filter { it.faded }.map { it.exerciseId } +
            tailOwner.filterValues { it == section.groupKey }.keys.sortedBy { byId[it]?.name }
        val tailLifts = tailIds.distinct().mapNotNull { id ->
            val t = tile(id) ?: return@mapNotNull null
            if (t.sessions == 0) return@mapNotNull null
            if (needle.isNotEmpty() && needle !in t.name.lowercase()) return@mapNotNull null
            id to t
        }
        // The `ONE SESSION` kicker already says it, so its chips drop the per-lift count.
        val kickerCarriesSessions = tailLifts.isNotEmpty() && tailLifts.all { (_, t) -> t.sessions == 1 }
        val tail = tailLifts.map { (id, t) ->
            val sessionLabel = "${t.sessions} session${if (t.sessions == 1) "" else "s"}"
            val sessionParts = if (kickerCarriesSessions) emptyList()
            else listOf(filler(" · "), data(t.sessions.toString()), filler(" session${if (t.sessions == 1) "" else "s"}"))
            GroupWallTailUi(id, listOf(filler(t.name + " ")) + t.topSetParts + sessionParts,
                "Open ${t.name} report, ${t.topSetParts.single().text}${if (kickerCarriesSessions) "" else ", $sessionLabel"}, tail",
                t.name, t.topSetParts.single().text, t.sessions, t.topSet?.weight.orEmpty(), t.topSet?.reps.orEmpty())
        }
        val attributedDates = if (section.groupKey != null) allDatesByGroup[section.groupKey].orEmpty() else
            section.rows.flatMap { byLift[it.exerciseId].orEmpty() }
                .filter(WorkoutMetrics::isStrengthEligible).map { it.date }.distinct()
        val windowDates = attributedDates.filter { it in startIso..endIso }
        val count = windowDates.size
        val last = attributedDates.maxOrNull()
        val lastLabel = last?.let(::shortDate)
        // Folded line reads as data: title, `0 sessions`, and a `last Sep 8` chip; no sentence.
        val foldedParts = listOf(data(section.title), filler(" · "), data(count.toString()),
            filler(" session${if (count == 1) "" else "s"}")) +
            (lastLabel?.let { listOf(filler(" · last "), data(it)) } ?: emptyList())
        val foldedDescription = "${section.title}, $count session${if (count == 1) "" else "s"} in this window" +
            (lastLabel?.let { ", last $it" } ?: "")
        GroupWallPanelUi(
            section.groupKey ?: "${section.kind}-$index", section.title, section.colorIndex,
            "Group ${section.title}, $count session${if (count == 1) "" else "s"}",
            listOf(data(count.toString()), filler(" session${if (count == 1) "" else "s"}")), tiles.chunked(2), tail,
            count, windowDates.maxOrNull(), last, foldedParts, foldedDescription, lastLabel, groupWallTailLabel(tail),
        )
    }.sortedByDescending { it.latestWindowDate.orEmpty() }
    val crossYear = start.year != end.year
    val period = shortDate(startIso) + (if (crossYear) ", ${start.year}" else "") +
        " – " + shortDate(endIso) + (if (crossYear) ", ${end.year}" else "")
    // Dates only (the selected range pill names the window), short years so a cross-year window fits one line.
    fun shortYear(year: Int) = " ’" + (year % 100).toString().padStart(2, '0')
    val shown = shortDate(startIso) + (if (crossYear) shortYear(start.year) else "") +
        " – " + shortDate(endIso) + (if (crossYear) shortYear(end.year) else "")
    return GroupWallUi(panels, period, listOf(data(shown)))
}
