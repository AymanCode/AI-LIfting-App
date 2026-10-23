package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.web.util.noun

import androidx.compose.runtime.Immutable
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutMetrics
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.web.util.MONTH_ABBREV
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The exercise report: a changelog of milestones in the numbers the user logs (reps and pounds),
 * plus the derived pieces around it (next step, rep ladder, tiles, pace). Pure; unit-tested.
 * Spec: docs/superpowers/specs/2026-09-12-progress-report-changelog-design.md.
 */
enum class ChangelogKind { FIRST_SESSION, MOVED_UP, MOVED_DOWN, WEIGHT_AT_REPS, REP_PR, HELD }

@Immutable data class TopSet(val loadStored: Int, val reps: Int)

/** One run of text in a caption: data (a number, set, date, count) or the filler words between. */
@Immutable data class DataPart(val text: String, val isData: Boolean)

fun data(text: String) = DataPart(text, isData = true)
fun filler(text: String) = DataPart(text, isData = false)

@Immutable data class ChangelogEvent(
    val kind: ChangelogKind,
    val date: String,
    val from: TopSet?,
    val to: TopSet,
    val sets: List<TopSet>,
    val deltaLabel: String,
    val note: List<DataPart>,
    val aside: List<DataPart>,
    val e1rmLbs: Int?,
    /** The chip this session earns, or null when it beat nothing (see [eventFor]). */
    val win: ChangelogWin? = null,
)

/** What a session beat, as its chip reads: reps (a rep PR) or weight (a move up, same reps at more weight). */
@Immutable data class ChangelogWin(val label: String, val isReps: Boolean)

/** A run of consecutive sessions at one top-set load; events newest first, like the report's. */
@Immutable data class ChangelogChapter(val loadStored: Int, val events: List<ChangelogEvent>)

/** Groups newest-first events into runs by top-set load; a return to an earlier load starts a new chapter. */
internal fun chaptersFor(events: List<ChangelogEvent>): List<ChangelogChapter> {
    val chapters = mutableListOf<ChangelogChapter>()
    events.forEach { event ->
        val last = chapters.lastOrNull()
        if (last != null && last.loadStored == event.to.loadStored) {
            chapters[chapters.lastIndex] = last.copy(events = last.events + event)
        } else {
            chapters += ChangelogChapter(event.to.loadStored, listOf(event))
        }
    }
    return chapters
}

@Immutable data class NextStep(
    val targetLoadStored: Int,
    val triggerReps: Int,
    val repsRangeLabel: String,
    val currentReps: Int,
    val currentLoadStored: Int,
    val sessionsOnCurrentLoad: Int,
    val medianSessionsPerStep: Int,
)

@Immutable data class LadderRung(val loadStored: Int, val bestReps: Int, val date: String, val isCurrent: Boolean)

@Immutable data class Pace(val targetLbs: Int, val date: String)

/** A session whose top load was the heaviest yet, drawn as a notch on the Trend chart. */
@Immutable data class LoadMark(val date: String, val loadStored: Int)

@Immutable data class ExerciseReport(
    val events: List<ChangelogEvent> = emptyList(),
    val nextStep: NextStep? = null,
    val ladder: List<LadderRung> = emptyList(),
    val topSet: TopSet? = null,
    val firstTopSet: TopSet? = null,
    val firstDate: String? = null,
    val latestDate: String? = null,
    val latestE1rmLbs: Int? = null,
    /** The latest session's set behind [latestE1rmLbs] (the chart's latest Trend value). */
    val latestE1rmSet: TopSet? = null,
    val topSetE1rmLbs: Int? = null,
    val stepsInWindow: Int = 0,
    val workingSets: Int = 0,
    val setsPerWeek: Float = 0f,
    val heaviestStored: Int = 0,
    val heaviestSince: String? = null,
    val sessions: Int = 0,
    val daysBetweenSessions: Float? = null,
    val unbeatenSince: String? = null,
    val pace: Pace? = null,
    val newHeaviest: List<LoadMark> = emptyList(),
    val chapters: List<ChangelogChapter> = emptyList(),
    /** Sessions in the window that beat something (a [ChangelogEvent.win]). */
    val records: Int = 0,
    /** Best reps ever logged at the latest session's load (any set, back-off sets too), plus one; null with no session in the window. */
    val suggestedReps: Int? = null,
    /** Latest session up to the window end, even when the window itself is empty. */
    val lastSessionDate: String? = null,
)

internal data class ReportSession(
    val date: String,
    val top: TopSet,
    val sets: List<TopSet>,
    val e1rm: Double?,
    val topE1rm: Double?,
    val e1rmSet: TopSet? = null,
) {
    val reps: List<Int> get() = sets.map { it.reps }
}

/** Match both values: the same rep count at another weight is a different set. */
fun ChangelogEvent.otherSets(): List<TopSet> {
    val topIndex = sets.indexOf(to)
    return sets.filterIndexed { index, _ -> index != topIndex }
}

fun supportingSetLabel(set: TopSet, top: TopSet, isBodyweight: Boolean): String =
    if (set.loadStored == top.loadStored) set.reps.toString() else formatTopSet(set, isBodyweight)

fun formatLoad(loadStored: Int, isBodyweight: Boolean): String =
    if (isBodyweight) (if (loadStored == 0) "BW" else "BW +${WeightLbs.formatStored(loadStored)}")
    else WeightLbs.formatStored(loadStored)

fun formatTopSet(top: TopSet, isBodyweight: Boolean): String = "${formatLoad(top.loadStored, isBodyweight)} × ${top.reps}"

fun shortDate(iso: String): String = LocalDate.parse(iso).let { "${MONTH_ABBREV[it.month.ordinal]} ${it.day}" }

fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

fun kindLabel(kind: ChangelogKind): String = when (kind) {
    ChangelogKind.FIRST_SESSION -> "First session"
    ChangelogKind.MOVED_UP -> "Moved up"
    ChangelogKind.MOVED_DOWN -> "Moved down"
    ChangelogKind.WEIGHT_AT_REPS -> "Same reps, more weight"
    ChangelogKind.REP_PR -> "Rep PR"
    ChangelogKind.HELD -> "Held"
}

private fun lbDelta(fromStored: Int, toStored: Int): String {
    val delta = toStored - fromStored
    val sign = if (delta < 0) "−" else "+"
    return "$sign${WeightLbs.formatStored(abs(delta))} lb"
}

private fun sessionSetsText(session: ReportSession, isBodyweight: Boolean) =
    session.sets.joinToString(" · ") { supportingSetLabel(it, session.top, isBodyweight) }

internal fun reportSessions(history: List<WorkoutSet>, isBodyweight: Boolean, windowEnd: LocalDate): List<ReportSession> =
    history
        .filter { WorkoutMetrics.isStrengthEligible(it) && it.date <= windowEnd.toString() }
        .groupBy { it.date }
        .entries
        .sortedBy { it.key }
        .map { (date, sets) ->
            val ordered = sets.sortedBy { it.setNumber }
            val top = ordered.maxWith(compareBy<WorkoutSet> { it.weightLbs ?: 0 }.thenBy { it.reps ?: 0 })
            val bestEstimate = if (isBodyweight) null else ordered.filter { WorkoutMetrics.estimatedOneRepMax(it) != null }
                .maxByOrNull { WorkoutMetrics.estimatedOneRepMax(it) ?: 0.0 }
            ReportSession(
                date = date,
                top = TopSet(top.weightLbs ?: 0, top.reps ?: 0),
                sets = ordered.map { TopSet(it.weightLbs ?: 0, it.reps ?: 0) },
                e1rm = if (isBodyweight) null else ordered.mapNotNull { WorkoutMetrics.estimatedOneRepMax(it) }.maxOrNull(),
                topE1rm = WorkoutMetrics.estimatedOneRepMax(top, isBodyweight),
                e1rmSet = bestEstimate?.let { TopSet(it.weightLbs ?: 0, it.reps ?: 0) },
            )
        }

private fun eventFor(sessions: List<ReportSession>, index: Int, isBodyweight: Boolean): ChangelogEvent {
    val s = sessions[index]
    val before = sessions.subList(0, index)
    val prev = before.lastOrNull()
    val e1 = s.e1rm?.roundToInt()
    val load = s.top.loadStored
    // What this session beat, measured against every earlier set, back-off sets included: a new
    // heaviest weight, or more reps than ever at this weight. Climbing back to a weight already
    // lifted, or matching an old back-off set, beats nothing (spec 2026-09-24: only wins get a chip).
    val earlierSets = before.flatMap { it.sets }
    val heaviestBefore = earlierSets.maxOfOrNull { it.loadStored }
    val repGain = earlierSets.filter { it.loadStored == load }.maxOfOrNull { it.reps }
        ?.let { best -> s.top.reps - best }?.takeIf { it > 0 }
    val repsWin = repGain?.let { ChangelogWin("+$it rep${if (it == 1) "" else "s"}", isReps = true) }

    if (prev == null) {
        return ChangelogEvent(ChangelogKind.FIRST_SESSION, s.date, null, s.top, s.sets, "", emptyList(),
            listOf(data("${s.reps.size}"), filler(" ${noun(s.reps.size, "set")}")), e1)
    }

    if (load != prev.top.loadStored) {
        val up = load > prev.top.loadStored
        val run = before.takeLastWhile { it.top.loadStored == prev.top.loadStored }
        val bestRepsAtPrev = run.maxOf { it.top.reps }
        val hitDates = run.filter { it.top.reps == bestRepsAtPrev }.takeLast(2).map { shortDate(it.date) }
        val note = if (up) {
            buildList {
                add(filler("Stepped after ")); add(data(formatTopSet(TopSet(prev.top.loadStored, bestRepsAtPrev), isBodyweight)))
                add(filler(" on ")); add(data(hitDates.first()))
                if (hitDates.size == 2) { add(filler(" and ")); add(data(hitDates[1])) }
                add(filler(". ")); add(data("${run.size}")); add(filler(" ${noun(run.size, "session")} on ")); add(data(formatLoad(prev.top.loadStored, isBodyweight))); add(filler("."))
            }
        } else {
            listOf(data("${run.size}"), filler(" ${noun(run.size, "session")} on "), data(formatLoad(prev.top.loadStored, isBodyweight)), filler("."))
        }
        val delta = lbDelta(prev.top.loadStored, load)
        val win = if (up && (heaviestBefore == null || load > heaviestBefore)) ChangelogWin(delta, isReps = false) else repsWin
        return ChangelogEvent(if (up) ChangelogKind.MOVED_UP else ChangelogKind.MOVED_DOWN, s.date, prev.top, s.top, s.sets,
            delta, note, emptyList(), e1, win)
    }

    val lower = before.filter { it.top.loadStored < load }
    val bestRepsAtLower = lower.maxOfOrNull { it.top.reps }
    val reachedAtOrAbove = before.any { it.top.loadStored >= load && it.top.reps >= s.top.reps }
    if (bestRepsAtLower != null && s.top.reps == bestRepsAtLower && !reachedAtOrAbove) {
        val exact = lower.filter { it.top.reps == s.top.reps }
        val from = exact.maxWith(compareBy<ReportSession> { it.top.reps }.thenBy { it.top.loadStored }.thenBy { it.date })
        val deltaLabel = "${lbDelta(from.top.loadStored, load)} at ${s.top.reps}"
        // A back-off set may already have done these reps at this weight or heavier.
        val neverDone = earlierSets.none { it.loadStored >= load && it.reps >= s.top.reps }
        return ChangelogEvent(ChangelogKind.WEIGHT_AT_REPS, s.date, from.top, s.top, s.sets,
            deltaLabel,
            listOf(filler("was "), data(formatTopSet(from.top, isBodyweight)), filler(" on "), data(shortDate(from.date))),
            emptyList(), e1, if (neverDone) ChangelogWin(deltaLabel, isReps = false) else repsWin)
    }

    val bestAtLoadBefore = before.filter { it.top.loadStored == load }.maxOf { it.top.reps }
    if (s.top.reps > bestAtLoadBefore) {
        val runLength = before.takeLastWhile { it.top.loadStored == load }.size + 1
        val gained = s.top.reps - bestAtLoadBefore
        val note = buildList {
            add(data(ordinal(runLength))); add(filler(" session on ")); add(data(formatLoad(load, isBodyweight)))
            add(filler(". Sets ")); add(data(sessionSetsText(s, isBodyweight))); add(filler(", up from ")); add(data(sessionSetsText(prev, isBodyweight))); add(filler("."))
            s.topE1rm?.roundToInt()?.let { add(filler(" ≈ ")); add(data("$it")); add(filler(" for one")) }
        }
        return ChangelogEvent(ChangelogKind.REP_PR, s.date, TopSet(load, bestAtLoadBefore), s.top, s.sets,
            "+$gained rep${if (gained == 1) "" else "s"}", note, emptyList(), e1, repsWin)
    }

    // Held = same load, no PR. Still say what happened against last session: a drop is a drop.
    val vsPrev = s.top.reps - prev.top.reps
    if (vsPrev != 0) {
        val n = abs(vsPrev)
        val label = (if (vsPrev > 0) "+" else "−") + "$n rep" + (if (n == 1) "" else "s")
        return ChangelogEvent(ChangelogKind.HELD, s.date, prev.top, s.top, s.sets, label, emptyList(), emptyList(), e1)
    }
    val topIndex = s.sets.indexOf(s.top)
    val improved = s.sets.indices.firstOrNull { i ->
        i != topIndex && i < prev.sets.size && s.sets[i].loadStored == prev.sets[i].loadStored && s.reps[i] > prev.reps[i]
    }
    val aside = buildList {
        add(filler("same top set"))
        if (improved != null) { add(filler(" · ")); add(data("+${s.reps[improved] - prev.reps[improved]}")); add(filler(" on set ")); add(data("${improved + 1}")) }
    }
    return ChangelogEvent(ChangelogKind.HELD, s.date, null, s.top, s.sets, "", emptyList(), aside, e1)
}

private fun median(values: List<Int>): Int = values.sorted().let { it[(it.size - 1) / 2] }

internal fun nextStepFor(sessions: List<ReportSession>): NextStep? {
    // A return after lowering the load is not evidence for a new progression increment.
    val lastDrop = sessions.indices.lastOrNull { i -> i > 0 && sessions[i].top.loadStored < sessions[i - 1].top.loadStored } ?: 0
    val stepIndices = sessions.indices.filter { i -> i > lastDrop && sessions[i].top.loadStored > sessions[i - 1].top.loadStored }.takeLast(4)
    if (stepIndices.size < 2) return null
    val last = sessions.last()
    val lastStep = stepIndices.last()
    val stepSize = sessions[lastStep].top.loadStored - sessions[lastStep - 1].top.loadStored
    // Only project a repeated modest step. Large jumps, BW→weighted transitions, and
    // a one-off different increment do not establish a trustworthy next load.
    val repeatedSteps = stepIndices.filter { i ->
        val previousLoad = sessions[i - 1].top.loadStored
        sessions[i].top.loadStored - previousLoad == stepSize && previousLoad > 0 && stepSize.toLong() * 4 <= previousLoad
    }
    if (lastStep !in repeatedSteps || repeatedSteps.size < 2) return null
    val triggerReps = repeatedSteps.map { sessions[it - 1].top.reps }
    val runLengths = repeatedSteps.map { i -> sessions.subList(lastDrop, i).takeLastWhile { it.top.loadStored == sessions[i - 1].top.loadStored }.size }
    val min = triggerReps.min(); val max = triggerReps.max()
    return NextStep(
        targetLoadStored = last.top.loadStored + stepSize,
        triggerReps = median(triggerReps),
        repsRangeLabel = if (min == max) "$min" else "$min–$max",
        currentReps = last.top.reps,
        currentLoadStored = last.top.loadStored,
        sessionsOnCurrentLoad = sessions.takeLastWhile { it.top.loadStored == last.top.loadStored }.size,
        medianSessionsPerStep = median(runLengths),
    )
}

internal fun ladderFor(sessions: List<ReportSession>): List<LadderRung> {
    val current = sessions.lastOrNull()?.top?.loadStored ?: return emptyList()
    val rungs = sessions.groupBy { it.top.loadStored }.map { (load, runs) ->
        val best = runs.maxOf { it.top.reps }
        LadderRung(load, best, runs.first { it.top.reps == best }.date, load == current)
    }.sortedByDescending { it.loadStored }
    if (rungs.size <= 6) return rungs
    val head = rungs.take(6)
    return if (head.any { it.isCurrent }) head else rungs.take(5) + rungs.first { it.isCurrent }
}

/** Smallest load increase that counts as a new heaviest weight: one 2.5 lb plate, in stored tenths. */
internal const val NEW_HEAVIEST_MIN_STEP_STORED = 25

/**
 * Window sessions that reached a new heaviest weight at least one 2.5 lb plate above the last mark.
 * The first session is the starting mark and sessions before the window count, so small bumps add
 * up until they clear a plate, while returning to a weight after a lighter day never marks (anything
 * a plate above the last mark is above every earlier load).
 */
internal fun newHeaviestMarks(sessions: List<ReportSession>, startIso: String): List<LoadMark> {
    var lastMark: Int? = null
    return buildList {
        sessions.forEach { s ->
            val load = s.top.loadStored
            val mark = lastMark
            if (mark == null) {
                lastMark = load
            } else if (load >= mark + NEW_HEAVIEST_MIN_STEP_STORED) {
                if (s.date >= startIso) add(LoadMark(s.date, load))
                lastMark = load
            }
        }
    }
}

internal fun paceFor(window: List<ReportSession>): Pace? {
    val points = window.mapNotNull { s -> s.e1rm?.let { LocalDate.parse(s.date).toEpochDays().toDouble() to it } }
    if (points.size < 4) return null
    val n = points.size
    val meanX = points.sumOf { it.first } / n
    val meanY = points.sumOf { it.second } / n
    val sxx = points.sumOf { (it.first - meanX) * (it.first - meanX) }
    if (sxx == 0.0) return null
    val slope = points.sumOf { (it.first - meanX) * (it.second - meanY) } / sxx
    if (slope <= 0.0) return null
    val latest = points.last()
    val target = ((latest.second / 5).toInt() + 1) * 5
    val days = (target - latest.second) / slope
    if (days > 120) return null
    return Pace(target, LocalDate.fromEpochDays((latest.first + days).toInt()).toString())
}

fun buildExerciseReport(history: List<WorkoutSet>, isBodyweight: Boolean, windowStart: LocalDate, windowEnd: LocalDate): ExerciseReport {
    val sessions = reportSessions(history, isBodyweight, windowEnd)
    if (sessions.isEmpty()) return ExerciseReport()
    val startIso = windowStart.toString()
    val windowIndices = sessions.indices.filter { sessions[it].date >= startIso }
    val window = windowIndices.map { sessions[it] }
    val events = windowIndices.map { eventFor(sessions, it, isBodyweight) }.reversed()

    val latest = window.lastOrNull()
    val suggestedReps = latest?.let { l -> sessions.flatMap { it.sets }.filter { it.loadStored == l.top.loadStored }.maxOf { it.reps } + 1 }
    val first = window.firstOrNull()
    val heaviest = window.maxOfOrNull { it.top.loadStored } ?: 0
    val heaviestSince = window.lastOrNull { it.top.loadStored == heaviest }?.let { lastAtHeaviest ->
        window.subList(0, window.indexOf(lastAtHeaviest) + 1).takeLastWhile { it.top.loadStored == heaviest }.first().date
    }
    // Sets per week over the time actually trained: an all-time window starts in 2000, so measure
    // from the first session inside the window, never less than one week.
    val spanStart = maxOf(windowStart.toEpochDays(), first?.let { LocalDate.parse(it.date).toEpochDays() } ?: windowStart.toEpochDays())
    val days = (windowEnd.toEpochDays() - spanStart).coerceAtLeast(7)
    val workingSets = window.sumOf { it.reps.size }
    val unbeatenSince = if (isBodyweight) {
        val bestLoad = sessions.maxOf { it.top.loadStored }
        val bestReps = sessions.filter { it.top.loadStored == bestLoad }.maxOf { it.top.reps }
        sessions.first { it.top.loadStored == bestLoad && it.top.reps == bestReps }.date
    } else {
        val best = sessions.mapNotNull { it.e1rm }.maxOrNull()
        best?.let { b -> sessions.first { it.e1rm == b }.date }
    }
    return ExerciseReport(
        events = events,
        nextStep = nextStepFor(sessions),
        ladder = ladderFor(sessions),
        topSet = latest?.top,
        firstTopSet = first?.top,
        firstDate = first?.date,
        latestDate = latest?.date,
        latestE1rmLbs = latest?.e1rm?.roundToInt(),
        latestE1rmSet = latest?.e1rmSet,
        topSetE1rmLbs = latest?.topE1rm?.roundToInt(),
        stepsInWindow = events.count { it.kind == ChangelogKind.MOVED_UP },
        workingSets = workingSets,
        setsPerWeek = workingSets / (days / 7f),
        heaviestStored = heaviest,
        heaviestSince = heaviestSince,
        sessions = window.size,
        daysBetweenSessions = if (window.size >= 2 && latest != null && first != null) {
            (LocalDate.parse(latest.date).toEpochDays() - LocalDate.parse(first.date).toEpochDays()).toFloat() / (window.size - 1)
        } else null,
        unbeatenSince = unbeatenSince,
        pace = if (isBodyweight) null else paceFor(window),
        newHeaviest = newHeaviestMarks(sessions, startIso),
        chapters = chaptersFor(events),
        records = events.count { it.win != null },
        suggestedReps = suggestedReps,
        lastSessionDate = sessions.last().date,
    )
}

/** A compact window change, gated by the report's earned wins, with the first session as baseline. */
internal fun ExerciseReport.windowTopSetWin(): ChangelogWin? {
    if (sessions < 2) return null
    val first = firstTopSet ?: return null
    val last = topSet ?: return null
    val comparableEvents = events.filter { it.date != firstDate && it.to.loadStored == last.loadStored }
    return when {
        last.loadStored > first.loadStored && comparableEvents.any { it.win?.isReps == false } ->
            ChangelogWin(lbDelta(first.loadStored, last.loadStored), isReps = false)
        last.loadStored == first.loadStored && last.reps > first.reps && comparableEvents.any { it.win?.isReps == true && it.to.reps <= last.reps } -> {
            val gain = last.reps - first.reps
            ChangelogWin("+$gain rep${if (gain == 1) "" else "s"}", isReps = true)
        }
        else -> null
    }
}
