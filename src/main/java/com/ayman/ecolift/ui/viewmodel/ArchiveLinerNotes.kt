package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.CycleSnapshot
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.isCheckedWorking
import com.ayman.ecolift.data.progress.ComparisonWindow
import com.ayman.ecolift.data.progress.CycleComparison
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.countLabel
import com.ayman.ecolift.web.util.minusMonths
import kotlinx.datetime.LocalDate

data class ArchiveTopSet(val loadStored: Int, val reps: Int)
data class ArchiveWin(val label: String, val isReps: Boolean)

/** All labels and bar heights are ready to render; no history work runs in composition. */
data class ArchiveTrack(
    val exerciseId: Long,
    val name: String,
    val indexLabel: String = "",
    val sessions: Int,
    val weeksSpanned: Int,
    val fairComparison: Boolean,
    val isBodyweight: Boolean,
    val sessionLabel: String,
    val dateLabel: String,
    val firstLoggedLabel: String,
    val topSetsLabel: String,
    val waveform: List<Float>,
    val win: ArchiveWin?,
    val comparisonLabel: String? = null,
    val isNew: Boolean = false,
) {
    val description: String get() = listOf(indexLabel, name, if (isNew && indexLabel.isNotEmpty()) "NEW" else "",
        sessionLabel, dateLabel, topSetsLabel,
        comparisonLabel.orEmpty(), win?.label.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
}

data class ArchiveLinerNotes(
    val heroCounts: String,
    val weeklySets: List<Int>,
    val setlist: List<ArchiveTrack>,
    val earlyHints: List<ArchiveTrack>,
    val credits: List<ArchiveTrack>,
    val deepCuts: List<ArchiveTrack>,
    val versus: List<ArchiveTrack>,
) {
    val deepCutsLabel: String get() = "Deep cuts · ${countLabel(deepCuts.size, "lift")} · 1–2 sessions each"
}

internal fun archiveTopSetLabel(top: ArchiveTopSet?, bodyweight: Boolean): String {
    if (top == null) return ""
    val load = if (bodyweight) {
        if (top.loadStored == 0) "BW" else "BW+${WeightLbs.formatStored(top.loadStored)}"
    } else {
        WeightLbs.formatStored(top.loadStored)
    }
    return "$load×${top.reps}"
}

internal fun archiveWin(first: ArchiveTopSet?, last: ArchiveTopSet?): ArchiveWin? = when {
    first == null || last == null -> null
    last.loadStored > first.loadStored -> ArchiveWin("+${WeightLbs.formatStored(last.loadStored - first.loadStored)} lb", false)
    last.loadStored == first.loadStored && last.reps > first.reps ->
        ArchiveWin("+${last.reps - first.reps} rep${if (last.reps - first.reps == 1) "" else "s"}", true)
    else -> null
}

private data class ArchiveSession(val date: String, val top: ArchiveTopSet?)

/** Snapshot dates define membership; raw checked working sets supply the actual top sets.
 * A legacy snapshot has no top-set reps, so unavailable endpoints stay absent, never inferred.
 * Weeks are distinct seven-day buckets anchored at cycle start, as in board 08 C.
 */
fun buildArchiveLinerNotes(
    snapshot: CycleSnapshot,
    history: List<WorkoutSet>,
    window: ComparisonWindow,
    comparison: CycleComparison? = null,
): ArchiveLinerNotes {
    val start = LocalDate.parse(snapshot.startDate)
    val end = LocalDate.parse(snapshot.endDate)
    val span = (end.toEpochDays() - start.toEpochDays() + 1).toInt().coerceAtLeast(1)
    val weekCount = (span - 1) / 7 + 1
    val checked = history.filter { it.isCheckedWorking && it.date <= snapshot.endDate }
    val byLift = checked.groupBy { it.exerciseId }
    val catalog = snapshot.splits.flatMap { it.exercises }.groupBy { it.exerciseId }
    val dates = (checked.map { it.date } + catalog.values.flatten().flatMap { it.sessions.map { s -> s.date } })
        .distinct().associateWith { LocalDate.parse(it) }
    fun week(iso: String) = (dates.getValue(iso).toEpochDays() - start.toEpochDays()).toInt() / 7
    fun label(iso: String) = dates.getValue(iso).let { "${MONTH_ABBREV[it.month.ordinal]} ${it.day}" }
    fun top(rows: List<WorkoutSet>, bodyweight: Boolean): ArchiveTopSet? = rows
        .filter { (it.reps ?: 0) > 0 && (it.weightLbs != null || bodyweight || it.isBodyweight) }
        .maxWithOrNull(compareBy<WorkoutSet> { it.weightLbs ?: 0 }.thenBy { it.reps ?: 0 }
            .thenBy { -it.setNumber }.thenBy { -it.id })
        ?.let { ArchiveTopSet(it.weightLbs ?: 0, it.reps!!) }
    fun endpoints(sessions: List<ArchiveSession>, bodyweight: Boolean): String {
        val first = sessions.firstOrNull()?.top
        val last = sessions.lastOrNull()?.top
        return if (sessions.size >= 2 && first != null && last != null) {
            "${archiveTopSetLabel(first, bodyweight)} → ${archiveTopSetLabel(last, bodyweight)}"
        } else {
            archiveTopSetLabel(last ?: first, bodyweight)
        }
    }
    val windowStart = start.minusMonths(window.months.toLong()).toString()
    val firstEver = mutableMapOf<Long, String>()
    val previous = mutableMapOf<Long, List<ArchiveSession>>()
    val current = mutableMapOf<Long, List<ArchiveSession>>()
    val weeklySets = IntArray(weekCount)
    val trainingDates = mutableSetOf<String>()
    val tracks = catalog.mapNotNull { (id, exercises) ->
        val exercise = exercises.first()
        val liftRows = byLift[id].orEmpty()
        val byDate = liftRows.groupBy { it.date }
        val bodyweight = exercise.isBodyweight || liftRows.any { it.isBodyweight }
        val sessionDates = exercises.flatMap { it.sessions }.map { it.date }.distinct()
            .filter { it in snapshot.startDate..snapshot.endDate }.sorted()
        if (sessionDates.isEmpty()) return@mapNotNull null
        firstEver[id] = (liftRows.map { it.date } + sessionDates).min()
        val sessions = sessionDates.map { ArchiveSession(it, top(byDate[it].orEmpty(), bodyweight)) }
        val prior = byDate.entries.filter { it.key >= windowStart && it.key < snapshot.startDate }
            .sortedBy { it.key }.map { ArchiveSession(it.key, top(it.value, bodyweight)) }
        previous[id] = prior
        current[id] = sessions
        sessionDates.forEach { iso ->
            trainingDates += iso
            // Legacy snapshots retain set counts even when the corresponding rows are gone.
            val count = exercises.flatMap { it.sessions }.filter { it.date == iso }.maxOf { it.setCount }
            weeklySets[week(iso)] += count
        }
        val valid = sessions.mapNotNull { it.top }
        val useReps = valid.map { it.loadStored }.distinct().size <= 1
        val values = sessions.map { it.top?.let { t -> if (useReps) t.reps.toFloat() else t.loadStored.toFloat() } }
        val low = values.filterNotNull().minOrNull() ?: 0f
        val high = values.filterNotNull().maxOrNull() ?: low
        val heights = values.map { v -> if (v == null) 0f else if (high == low) .55f else .18f + .82f * (v - low) / (high - low) }
        val first = sessions.first().top
        val last = sessions.last().top
        ArchiveTrack(id, exercise.name, sessions = sessions.size,
            weeksSpanned = sessionDates.map(::week).distinct().size,
            fairComparison = valid.size >= 2 && first != null && last != null && prior.count { it.top != null } >= 2,
            isBodyweight = bodyweight,
            sessionLabel = "${sessions.size} session${if (sessions.size == 1) "" else "s"}",
            dateLabel = if (sessions.size == 1) label(sessionDates.first()) else "${label(sessionDates.first())} → ${label(sessionDates.last())}",
            firstLoggedLabel = "First logged ${label(firstEver.getValue(id))}",
            topSetsLabel = endpoints(sessions, bodyweight), waveform = heights,
            win = if (sessions.size >= 2) archiveWin(first, last) else null,
            isNew = firstEver.getValue(id) in snapshot.startDate..snapshot.endDate)
    }
    val rank = compareByDescending<ArchiveTrack> { it.sessions }.thenByDescending { it.weeksSpanned }
        .thenByDescending { it.fairComparison }.thenBy { it.name.lowercase() }.thenBy { it.exerciseId }
    // First cycles already carry the hero sticker; repeated newcomers still rank as tracks.
    val credits = if (tracks.all { it.isNew }) emptyList() else
        tracks.filter { it.isNew && it.sessions in 1..2 }.sortedWith(rank)
    val returning = tracks.filter { !it.isNew }.sortedWith(rank)
    val setlist = tracks.filter { it.sessions >= 3 }.sortedWith(rank).mapIndexed { i, track ->
        track.copy(indexLabel = (i + 1).toString().padStart(2, '0'))
    }
    val compared = comparison?.lifts.orEmpty().associateBy { it.exerciseId }
    val versus = returning.filter { it.fairComparison }.map { track ->
        val prior = previous.getValue(track.exerciseId)
        val first = prior.lastOrNull { it.top != null }?.top
        val last = current.getValue(track.exerciseId).last().top
        val pct = compared[track.exerciseId]?.vsPct?.takeIf { !track.isBodyweight }
        track.copy(topSetsLabel = listOf(archiveTopSetLabel(first, track.isBodyweight), archiveTopSetLabel(last, track.isBodyweight))
            .filter { it.isNotEmpty() }.joinToString(" → "),
            dateLabel = "${prior.size} prior · ${track.sessionLabel}",
            comparisonLabel = pct?.let { "${if (it >= 0) "+" else ""}${it.toInt()}% vs ${window.windowLabel()}" },
            win = archiveWin(first, last))
    }
    return ArchiveLinerNotes(
        heroCounts = "${countLabel(trainingDates.size, "session")} · ${spanLabel(span)} · ${countLabel(tracks.size, "lift")}",
        weeklySets = weeklySets.toList(), setlist = setlist.filter { it.sessions >= 4 },
        earlyHints = setlist.filter { it.sessions == 3 }, credits = credits,
        deepCuts = returning.filter { it.sessions in 1..2 }, versus = versus)
}

/** "2 weeks 3 days", dropping a zero part; the span is always at least one day. */
private fun spanLabel(span: Int): String = listOfNotNull(
    (span / 7).takeIf { it > 0 }?.let { countLabel(it, "week") },
    (span % 7).takeIf { it > 0 }?.let { countLabel(it, "day") },
).joinToString(" ")

/** Same labels as `ComparisonWindow.label()` in ui/navigation/CycleProgressComponents.kt. */
private fun ComparisonWindow.windowLabel(): String = when (this) {
    ComparisonWindow.M1 -> "1M"
    ComparisonWindow.M3 -> "3M"
    ComparisonWindow.M6 -> "6M"
}
