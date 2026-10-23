package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.WorkoutExerciseCard
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.datetime.LocalDate

/** ISO date string to epoch day. */
internal fun epochDay(iso: String): Int = LocalDate.parse(iso).toEpochDays().toInt()

internal fun epochDay(date: LocalDate): Int = date.toEpochDays().toInt()

/**
 * Monday-start week number counted from 1970. 1970-01-01 was a Thursday, so day 0 sits in week 0
 * with Monday 1969-12-29 (day -3). Two dates share an ISO week-year and week number exactly when
 * they share this index, so a Sunday and the following Monday are two weeks.
 */
internal fun isoWeekIndex(epochDay: Int): Int = (epochDay + 3).floorDiv(7)

/** [day] is inside the half-open window [start, asOf). */
internal fun inWindow(day: Int, start: Int, asOf: Int): Boolean = day >= start && day < asOf

/**
 * The [count]th most recent training day before [asOf] (1 = the most recent), or null when fewer
 * than [count] training days exist before [asOf]. [history.dates] is sorted ascending; this sorts
 * descending by epoch day so the result never depends on the input order.
 */
private fun kthMostRecentDayBefore(history: LiftHistory, asOf: Int, count: Int): Int? {
    val beforeAsOf = history.dates.map { epochDay(it) }.filter { it < asOf }.sortedDescending()
    return beforeAsOf.getOrNull(count - 1)
}

/**
 * The tempo-relative window starts for this pass (spec "The model" and "Generality"): each
 * window's start is `max(D − maxDays, epochDay(kth most recent training day before D))`, where k
 * is the window's session count; with fewer than k training days before D, the start is
 * `D − maxDays` (every training day inside the cap). Computed once per [LearnedSplitsEngine.compute].
 */
internal fun learnedWindows(history: LiftHistory, asOf: Int, tunables: LearnedTunables): LearnedWindows {
    fun start(sessions: Int, maxDays: Int): Int {
        val cap = asOf - maxDays
        val kth = kthMostRecentDayBefore(history, asOf, sessions) ?: return cap
        return maxOf(cap, kth)
    }
    return LearnedWindows(
        memoryStart = start(tunables.memorySessions, tunables.memoryMaxDays),
        recentStart = start(tunables.recentSessions, tunables.recentMaxDays),
        clusterStart = start(tunables.clusterSessions, tunables.clusterMaxDays),
    )
}

/**
 * The keeper for [id] from a merge graph (every exercise id → its merged_into_id, null for keepers).
 * A chain that points at a missing exercise, or loops back on itself, stops at the last id it
 * could resolve.
 */
internal fun resolveKeeper(id: Long, mergeGraph: Map<Long, Long?>): Long {
    var current = id
    val seen = HashSet<Long>()
    while (true) {
        seen += current
        val next = mergeGraph[current] ?: return current
        if (next !in mergeGraph || next in seen) return current
        current = next
    }
}

/** A memoised [resolveKeeper] over one merge graph: what the engine receives as its resolver. */
fun canonicalResolver(mergeGraph: Map<Long, Long?>): (Long) -> Long {
    val cache = HashMap<Long, Long>()
    return { id -> cache.getOrPut(id) { resolveKeeper(id, mergeGraph) } }
}

/**
 * The engine's view of history: for each date, the canonical lifts with at least one checked,
 * non-warm-up set, and each such lift's lowest persisted card position that day. A keeper and its
 * donor on one day count once.
 */
internal class LiftHistory(
    val presence: Map<String, Set<Long>>,
    private val positions: Map<String, Map<Long, Int>>,
) {
    val dates: List<String> = presence.keys.sorted()
    fun position(date: String, lift: Long): Int = positions[date]?.get(lift) ?: Int.MAX_VALUE
    fun lifts(date: String): Set<Long> = presence[date].orEmpty()
}

internal fun buildLiftHistory(
    sets: List<WorkoutSet>,
    cards: List<WorkoutExerciseCard>,
    resolve: (Long) -> Long,
): LiftHistory {
    val presence = HashMap<String, MutableSet<Long>>()
    for (set in sets) {
        if (!set.completed || set.isWarmup) continue
        presence.getOrPut(set.date) { mutableSetOf() }.add(resolve(set.exerciseId))
    }
    val positions = HashMap<String, MutableMap<Long, Int>>()
    for (card in cards) {
        val lifts = presence[card.date] ?: continue
        val lift = resolve(card.exerciseId)
        if (lift !in lifts) continue
        val byLift = positions.getOrPut(card.date) { mutableMapOf() }
        val current = byLift[lift]
        if (current == null || card.position < current) byLift[lift] = card.position
    }
    return LiftHistory(presence, positions)
}

/**
 * Held: trained in at least two distinct ISO weeks inside the memory window and at least once
 * inside the recent window. Returned ascending.
 */
internal fun heldLifts(history: LiftHistory, asOf: Int, windows: LearnedWindows): List<Long> {
    val weeks = HashMap<Long, MutableSet<Int>>()
    val recent = HashSet<Long>()
    for ((date, lifts) in history.presence) {
        val day = epochDay(date)
        if (!inWindow(day, windows.memoryStart, asOf)) continue
        val week = isoWeekIndex(day)
        for (lift in lifts) {
            weeks.getOrPut(lift) { mutableSetOf() }.add(week)
            if (inWindow(day, windows.recentStart, asOf)) recent += lift
        }
    }
    return weeks.filter { (lift, seen) -> seen.size >= 2 && lift in recent }.keys.sorted()
}

/** Training days inside the cluster window, ascending. */
internal fun clusterWindowDays(history: LiftHistory, asOf: Int, windows: LearnedWindows): List<String> =
    history.dates.filter { inWindow(epochDay(it), windows.clusterStart, asOf) }

/** Held lifts present on at least EVERYDAY_SHARE of the cluster-window training days. */
internal fun everydayLifts(
    held: List<Long>,
    history: LiftHistory,
    clusterDays: List<String>,
    tunables: LearnedTunables,
): Set<Long> {
    if (clusterDays.isEmpty()) return emptySet()
    return held.filter { lift ->
        val count = clusterDays.count { lift in history.lifts(it) }
        count.toDouble() / clusterDays.size >= tunables.everydayShare - 1e-9
    }.toSet()
}

/** Latest checked, non-warm-up date before D for every lift. */
internal fun lastWorkingDates(history: LiftHistory, asOf: Int): Map<Long, String> {
    val last = HashMap<Long, String>()
    for (date in history.dates) {
        if (epochDay(date) >= asOf) break
        for (lift in history.lifts(date)) last[lift] = date
    }
    return last
}

/**
 * The learned-splits engine: a pure function from [LearnedInputs] to a [LearnedModel]. Pipeline:
 * canonicalise → training days → held → everyday → affinity → clustering → group test → slot
 * matching → attribution → sessions, fading, due → actions. Same inputs, same model.
 */
object LearnedSplitsEngine {
    fun compute(inputs: LearnedInputs): LearnedModel {
        if (!inputs.smartSuggest) return manualSplitsModel(inputs)
        val tunables = inputs.tunables
        val asOf = epochDay(inputs.asOf)
        val history = buildLiftHistory(inputs.sets, inputs.cards, inputs.resolve)
        val windows = learnedWindows(history, asOf, tunables)
        val learned = learnClusters(history, asOf, windows, tunables)
        val anchors = anchorsOf(inputs.slotMembers, inputs.resolve)
        val options = inputs.alternatives.map { it.copy(sourceId = inputs.resolve(it.sourceId), alternativeId = inputs.resolve(it.alternativeId)) }
        val matchAnchors = learningAnchors(inputs.slots, inputs.slotMembers, inputs.memberChoices, inputs.resolve)
        val matching = matchSlots(learned.clusters, inputs.slots, matchAnchors, tunables, options)
        val attribution = attributeDays(history, matching.units, learned.everyday, asOf, windows)
        val groups = finishGroups(matching.units, attribution, history, asOf, tunables)
        val order = dueOrder(groups, asOf, windows)
        val lastArchiveEnd = inputs.archivedCycleEndDates.maxOrNull()
        val archives = (matching.duplicateArchives + autoArchives(matching.idleLearned, inputs.workoutDays, asOf, tunables))
            .distinct().filterNot { it in inputs.memberChoices }.sorted()
        val actions = LearnedActions(
            slotUpserts = slotUpserts(groups, matching.units, anchors,
                anchorsOf(inputs.slotMembers.filter { it.userAdded }, inputs.resolve)),
            slotArchives = archives,
            dayWrites = dayWrites(groups, attribution, inputs, history, asOf, lastArchiveEnd, windows),
        )
        val memberIds = groups.filterNot { it.suppressed }.flatMap { it.members }.toSet()
        val roles = learned.held.associateWith { lift ->
            when (lift) {
                in learned.everyday -> LiftRole.EVERYDAY
                in memberIds -> LiftRole.MEMBER
                else -> LiftRole.SOLO
            }
        }
        return LearnedModel(
            asOf = inputs.asOf.toString(),
            memoryStart = LocalDate.fromEpochDays(windows.memoryStart).toString(),
            lastArchiveEnd = lastArchiveEnd,
            roles = roles,
            groups = groups.map { withRememberedAlternatives(it, options) },
            dueOrder = order.map { it.key },
            dueGroupKey = order.firstOrNull { !it.isDormant }?.key,
            attribution = attribution,
            lastWorkingDate = lastWorkingDates(history, asOf),
            presence = history.presence,
            actions = actions,
        )
    }
}
