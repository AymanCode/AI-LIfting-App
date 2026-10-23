package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.CycleSlot

/**
 * A proposal unit before attribution: a cluster (matched to [slot], or new with a negative
 * [slotId]) or a slot-backed group that is not a cluster. [members] are canonical ids, ascending.
 */
internal data class GroupUnit(
    val key: String,
    val slotId: Long,
    val slot: CycleSlot?,
    val members: List<Long>,
    val coOccurrenceDays: List<String>,
    val isCluster: Boolean,
    val suppressed: Boolean,
    val colorIndex: Int,
)

internal fun epochDayOfMillis(millis: Long): Int = millis.floorDiv(86_400_000L).toInt()

/**
 * The owner of one training day: each unit gets one vote per member trained that day (everyday
 * lifts never vote); most votes wins. Tie: the unit whose voting member has the lowest persisted
 * card position that day, then the lowest canonical id of that member, then the smaller key.
 * Zero votes: null.
 */
internal fun ownerOf(
    date: String,
    lifts: Set<Long>,
    units: List<GroupUnit>,
    everyday: Set<Long>,
    history: LiftHistory,
): GroupUnit? {
    var best: GroupUnit? = null
    var bestVotes = 0
    var bestPosition = Int.MAX_VALUE
    var bestLead = Long.MAX_VALUE
    for (unit in units) {
        val voting = unit.members.filter { it in lifts && it !in everyday }
        if (voting.isEmpty()) continue
        val lead = voting.minWith(compareBy<Long>({ history.position(date, it) }, { it }))
        val position = history.position(date, lead)
        val current = best
        val better = current == null ||
            voting.size > bestVotes ||
            (voting.size == bestVotes && position < bestPosition) ||
            (voting.size == bestVotes && position == bestPosition && lead < bestLead) ||
            (voting.size == bestVotes && position == bestPosition && lead == bestLead && unit.key < current.key)
        if (better) {
            best = unit
            bestVotes = voting.size
            bestPosition = position
            bestLead = lead
        }
    }
    return best
}

/** Computed attribution of every training day in [memoryStart, D] (today included), with tails. */
internal fun attributeDays(
    history: LiftHistory,
    units: List<GroupUnit>,
    everyday: Set<Long>,
    asOf: Int,
    windows: LearnedWindows,
): Map<String, DayAttribution> {
    val result = LinkedHashMap<String, DayAttribution>()
    for (date in history.dates) {
        val day = epochDay(date)
        if (day < windows.memoryStart || day > asOf) continue
        val lifts = history.lifts(date)
        val owner = ownerOf(date, lifts, units, everyday, history)
        val members = owner?.members?.toSet().orEmpty()
        result[date] = DayAttribution(date, owner?.key, lifts.filterNot { it in members }.sorted())
    }
    return result
}

/**
 * Sessions (attributed days before D), faded members (absent from the FADE_SESSIONS most recent
 * sessions), the learned flag after this pass (a seed with two sessions since its creation or last
 * edit becomes learned), fresh (a seed created in the last FRESH_SPLIT_DAYS with no session) and
 * dormant (any other group with no session: listed in the picker, never due).
 */
internal fun finishGroups(
    units: List<GroupUnit>,
    attribution: Map<String, DayAttribution>,
    history: LiftHistory,
    asOf: Int,
    tunables: LearnedTunables,
): List<LearnedGroup> {
    val sessionsByKey = HashMap<String, MutableList<String>>()
    for (day in attribution.values) {
        val key = day.groupKey ?: continue
        if (epochDay(day.date) >= asOf) continue
        sessionsByKey.getOrPut(key) { mutableListOf() }.add(day.date)
    }
    return units.map { unit ->
        val sessions = sessionsByKey[unit.key].orEmpty().sorted()
        val faded = if (sessions.size >= tunables.fadeSessions) {
            val recent = sessions.takeLast(tunables.fadeSessions)
            unit.members.filter { member -> recent.none { member in history.lifts(it) } }.toSet()
        } else {
            emptySet()
        }
        val slot = unit.slot
        val evidenceStart = slot?.let { epochDayOfMillis(maxOf(it.createdAt, it.anchorUpdatedAt ?: 0L)) } ?: 0
        val evidence = sessions.count { epochDay(it) >= evidenceStart }
        val learned = slot == null || slot.learned || evidence >= 2
        val seed = slot != null && !learned
        val createdAt = slot?.createdAt ?: 0L
        val fresh = seed && sessions.isEmpty() && createdAt > 0L &&
            epochDayOfMillis(createdAt) >= asOf - tunables.freshSplitDays
        LearnedGroup(
            key = unit.key,
            slotId = unit.slotId,
            name = slot?.name.orEmpty(),
            colorIndex = unit.colorIndex,
            members = unit.members,
            ticketOrder = ticketOrder(unit.members, sessions.lastOrNull(), faded, history),
            coOccurrenceDays = unit.coOccurrenceDays,
            sessions = sessions,
            fadedMembers = faded,
            isCluster = unit.isCluster,
            learned = learned,
            createdAt = createdAt,
            isFresh = fresh,
            isDormant = sessions.isEmpty() && !fresh,
            suppressed = unit.suppressed,
        )
    }
}

/**
 * Due order: fresh seeds (created_at, then slot id), then groups with a session in the recent
 * window oldest-last-session first, then the stale groups the same way (when no group is alive
 * they are the ones competing), then dormant seeds. Tie: more members, then lower slot id. The due
 * group is the first entry that is not dormant.
 */
internal fun dueOrder(groups: List<LearnedGroup>, asOf: Int, windows: LearnedWindows): List<LearnedGroup> {
    val visible = groups.filterNot { it.suppressed }
    val bySeedAge = compareBy<LearnedGroup>({ it.createdAt }, { it.slotId })
    val byStaleness = compareBy<LearnedGroup>({ it.lastSession ?: "" }, { -it.members.size }, { it.slotId })
    val fresh = visible.filter { it.isFresh }.sortedWith(bySeedAge)
    val dormant = visible.filter { it.isDormant }.sortedWith(bySeedAge)
    val competing = visible.filterNot { it.isFresh || it.isDormant }
    val alive = competing.filter { group ->
        group.lastSession?.let { inWindow(epochDay(it), windows.recentStart, asOf) } == true
    }
    val stale = competing.filterNot { it in alive }
    return fresh + alive.sortedWith(byStaleness) + stale.sortedWith(byStaleness) + dormant
}

/**
 * A group's lifts in ticket order: members in the order of the group's last session (lowest card
 * position first), then members missing from that session, then faded members.
 */
internal fun ticketOrder(members: List<Long>, lastSession: String?, faded: Set<Long>, history: LiftHistory): List<Long> {
    val lastLifts = lastSession?.let { history.lifts(it) }.orEmpty()
    val present = members.filter { it in lastLifts && it !in faded }
        .sortedWith(compareBy<Long>({ history.position(lastSession!!, it) }, { it }))
    val missing = members.filter { it !in lastLifts && it !in faded }
    return present + missing + members.filter { it in faded }
}
