package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.web.util.countLabel

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.hub.HubBuild
import com.ayman.ecolift.data.hub.UncheckedSetsItem
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.data.splits.learnedGroupLabel
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class DeskLiftUi(val exerciseId: Long, val name: String, val muscleGroups: String, val sessionDots: List<Boolean>)

data class DeskDecisionUi(
    val id: String,
    val slotId: Long,
    val title: String,
    val sessions: List<SlotSessionUi>,
    val evidence: String,
    val lifts: List<DeskLiftUi>,
    /** Null means Keep; an overlap opens this group's existing Splits card. */
    val overlapsName: String? = null,
)

enum class NoticedKind { BeatPrevious, FlatLoad }

data class NoticedPointUi(val x: Float, val y: Float)

/** Unit coordinates computed with the snapshot, so drawing never scans history. */
private fun noticedTrend(loads: List<Int>): List<NoticedPointUi> {
    val low = loads.minOrNull() ?: 0
    val span = ((loads.maxOrNull() ?: low) - low).coerceAtLeast(1)
    return loads.mapIndexed { index, load ->
        NoticedPointUi(index.toFloat() / (loads.size - 1).coerceAtLeast(1), (load - low).toFloat() / span)
    }
}

data class NoticedUi(
    val exerciseId: Long,
    val name: String,
    val muscleGroups: String,
    val kind: NoticedKind,
    val loadLabel: String,
    val evidence: String,
    val loads: List<Int>,
    val win: ChangelogWin? = null,
    val question: String? = null,
) {
    val trend: List<NoticedPointUi> = noticedTrend(loads)
}

data class AskedUi(val messageId: Long, val question: String, val dateLabel: String)

data class IronMindDeskUi(
    val decisions: List<DeskDecisionUi> = emptyList(),
    val noticed: List<NoticedUi> = emptyList(),
    val asked: List<AskedUi> = emptyList(),
    val uncheckedSets: Int = 0,
) {
    val decisionCount: Int get() = decisions.size
}

private fun deskRange(dates: List<String>): String =
    "${shortDate(dates.first())} – ${shortDate(dates.last())}"

/** Session-only history; no conversation store exists today. Latest questions first. */
@OptIn(kotlin.time.ExperimentalTime::class)
internal fun projectAsked(messages: List<AiMessageUi>): List<AskedUi> = messages
    .filter { it.isUser && it.text.isNotBlank() }.takeLast(3).asReversed().map {
        val date = Instant.fromEpochMilliseconds(it.timestampMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        AskedUi(it.id, it.text, shortDate(date.toString()))
    }

/**
 * Replacement seam for a later observations engine. Uses the report engine's working-session
 * truth; a comparison with the previous session is never presented as a cause.
 * One newest qualifying lift per rule, at most two rows. Flat describes load, even if reps rose.
 */
internal fun projectNoticed(
    model: LearnedModel,
    setsByLift: Map<Long, List<WorkoutSet>>,
    exercisesById: Map<Long, Exercise>,
    asOf: LocalDate,
): List<NoticedUi> {
    val beats = mutableListOf<Pair<String, NoticedUi>>()
    val flats = mutableListOf<Pair<String, NoticedUi>>()
    for ((id, history) in setsByLift) {
        val exercise = exercisesById[id] ?: continue
        val sessions = reportSessions(history, exercise.isBodyweight, asOf)
        val last = sessions.lastOrNull() ?: continue
        val previous = sessions.getOrNull(sessions.lastIndex - 1)
        val load = formatLoad(last.top.loadStored, exercise.isBodyweight) + if (exercise.isBodyweight && last.top.loadStored == 0) "" else " lb"
        if (previous != null && (last.top.loadStored > previous.top.loadStored ||
                (last.top.loadStored == previous.top.loadStored && last.top.reps > previous.top.reps))) {
            val shown = sessions.takeLast(5)
            val report = buildExerciseReport(history, exercise.isBodyweight, LocalDate.parse(shown.first().date), asOf)
            val first = shown.first().top
            val comparison = if (last.top.loadStored != first.loadStored) {
                "${formatLoad(first.loadStored, exercise.isBodyweight)} → $load"
            } else "${formatTopSet(first, exercise.isBodyweight)} → ${formatTopSet(last.top, exercise.isBodyweight)}"
            beats += last.date to NoticedUi(id, exercise.name, exercise.muscleGroups, NoticedKind.BeatPrevious, load,
                "$comparison · ${countLabel(shown.size, "session")} · ${deskRange(shown.map { it.date })}",
                shown.map { it.top.loadStored }, report.events.firstOrNull()?.win)
        }
        val run = sessions.takeLastWhile { it.top.loadStored == last.top.loadStored }
        if (id in model.roles && run.size >= 4) {
            flats += last.date to NoticedUi(id, exercise.name, exercise.muscleGroups, NoticedKind.FlatLoad, load,
                "${countLabel(run.size, "session")} · ${deskRange(run.map { it.date })}", run.map { it.top.loadStored },
                question = "Why does ${exercise.name} look flat?")
        }
    }
    val ordering = compareByDescending<Pair<String, NoticedUi>> { it.first }.thenBy { it.second.exerciseId }
    val beat = beats.sortedWith(ordering).firstOrNull()?.second
    val flat = flats.sortedWith(ordering).firstOrNull { it.second.exerciseId != beat?.exerciseId }?.second
        ?: flats.sortedWith(ordering).firstOrNull()?.second?.takeIf { beat == null }
    return listOfNotNull(beat, flat)
}

/** One snapshot projection owned by HubViewModel, never by a composable. */
internal fun projectIronMindDesk(
    model: LearnedModel,
    sets: List<WorkoutSet>,
    exercises: List<Exercise>,
    hub: HubBuild,
    messages: List<AiMessageUi> = emptyList(),
    asOf: LocalDate,
): IronMindDeskUi {
    val byId = exercises.associateBy { it.id }
    fun canonical(id: Long): Long {
        var current = id
        val seen = mutableSetOf<Long>()
        while (seen.add(current)) current = byId[current]?.mergedIntoId ?: return current
        return id
    }
    val setsByLift = sets.groupBy { canonical(it.exerciseId) }
    val groups = model.visibleGroups
    val decisions = buildList {
        for (group in groups.filter { it.learned && !it.isDormant }) {
            val dates = group.sessions.filter { it <= asOf.toString() }.distinct().sorted()
            val sessions = dates.map { date ->
                val parsed = LocalDate.parse(date)
                SlotSessionUi(date, parsed.month.name.take(3).uppercase(), parsed.dayOfMonth.toString())
            }
            val lifts = (group.ticketOrder + group.members).distinct().mapNotNull { id ->
                val exercise = byId[id] ?: return@mapNotNull null
                DeskLiftUi(id, exercise.name, exercise.muscleGroups, dates.map { id in model.presence[it].orEmpty() })
            }
            val title = learnedGroupLabel(group, byId)
            val evidence = "${countLabel(dates.size, "session")}" + if (dates.isEmpty()) "" else " · ${deskRange(dates)}"
            if (group.isUnnamed) add(DeskDecisionUi("keep:${group.key}", group.slotId, title, sessions, evidence, lifts))
            if (group.members.size == 4) {
                val named = groups.filter { !it.isUnnamed && !it.isDormant && it.key != group.key }
                    // A pair of named groups needs one overlap decision, not reciprocal cards.
                    .filter { group.isUnnamed || group.key < it.key }
                    .map { it to group.members.count { id -> id in it.members } }
                    .filter { it.second >= 3 }.sortedWith(compareByDescending<Pair<com.ayman.ecolift.data.splits.LearnedGroup, Int>> { it.second }.thenBy { it.first.key })
                    .firstOrNull()
                if (named != null) {
                    val (other, shared) = named
                    val fullDay = dates.lastOrNull { date ->
                        model.presence[date].orEmpty().containsAll(group.members) && model.attribution[date]?.groupKey == other.key
                    }
                    val memberCount = group.members.size
                    val note = "$shared/$memberCount lifts in ${other.name}" +
                        (fullDay?.let { " · ${shortDate(it)} had all $memberCount" } ?: "")
                    add(DeskDecisionUi("overlap:${group.key}:${other.key}", group.slotId, title, sessions, note, emptyList(), other.name))
                }
            }
        }
    }
    val unchecked = hub.days.sumOf { day -> day.items.filterIsInstance<UncheckedSetsItem>().sumOf { it.openSets.size } } +
        (hub.backlog?.setCount ?: 0)
    return IronMindDeskUi(decisions, projectNoticed(model, setsByLift, byId, asOf), projectAsked(messages), unchecked)
}
