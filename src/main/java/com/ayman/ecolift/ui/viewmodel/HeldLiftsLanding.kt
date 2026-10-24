package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.splits.LearnedModel
import com.ayman.ecolift.data.splits.LiftRole
import com.ayman.ecolift.data.splits.learnedGroupLabel
import kotlinx.datetime.LocalDate

enum class HeldSectionKind { GROUP, EVERY_SESSION, SOLO }

/** One held lift on the Progress landing: its last top set and the same stake the ticket shows. */
data class HeldLiftRowUi(
    val exerciseId: Long,
    val name: String,
    val primaryGroup: String?,
    val topSetLabel: String?,
    val stake: StakeUi?,
    val faded: Boolean,
)

/** A group (named as its ticket is), then "Every session", then "Solo". */
data class HeldSectionUi(
    val title: String,
    val kind: HeldSectionKind,
    val colorIndex: Int?,
    val rows: List<HeldLiftRowUi>,
    val groupKey: String? = null,
)

/**
 * The Progress landing (spec 2026-09-28): held lifts under their groups in due order (faded
 * members last), then everyday lifts, then solo lifts, each lift once. [query] filters by name,
 * as the library search does; an empty result is an empty list, and the screen then shows the
 * library.
 */
internal fun buildHeldSections(
    model: LearnedModel,
    today: String,
    exercises: List<Exercise>,
    sets: List<WorkoutSet>,
    canonicalId: (Long) -> Long,
    query: String = "",
): List<HeldSectionUi> {
    if (model.roles.isEmpty()) return emptyList()
    val byId = exercises.associateBy { it.id }
    val setsByLift = sets.groupBy { canonicalId(it.exerciseId) }
    val day = LocalDate.parse(today)
    val needle = query.trim().lowercase()
    val shown = HashSet<Long>()
    fun row(lift: Long, faded: Boolean): HeldLiftRowUi? {
        val exercise = byId[lift] ?: return null
        if (needle.isNotEmpty() && needle !in exercise.name.lowercase()) return null
        val report = ticketHistoryBefore(setsByLift[lift].orEmpty(), exercise.isBodyweight, day)
        return HeldLiftRowUi(
            exerciseId = lift,
            name = exercise.name,
            primaryGroup = primaryGroupOf(exercise.muscleGroups),
            topSetLabel = report.topSet?.let { formatTicketTopSet(it, exercise.isBodyweight) },
            stake = report.stake,
            faded = faded,
        )
    }
    val sections = mutableListOf<HeldSectionUi>()
    for (group in model.groupsInDueOrder.filterNot { it.suppressed }) {
        val rows = group.ticketOrder
            .filter { model.roles[it] == LiftRole.MEMBER && shown.add(it) }
            .mapNotNull { row(it, faded = it in group.fadedMembers) }
        if (rows.isNotEmpty()) sections += HeldSectionUi(learnedGroupLabel(group, byId), HeldSectionKind.GROUP, group.colorIndex, rows, group.key)
    }
    fun byName(role: LiftRole) = model.roles.filterValues { it == role }.keys
        .filter { shown.add(it) }
        .sortedBy { byId[it]?.name?.lowercase().orEmpty() }
        .mapNotNull { row(it, faded = false) }
    byName(LiftRole.EVERYDAY).takeIf { it.isNotEmpty() }?.let { sections += HeldSectionUi("Every session", HeldSectionKind.EVERY_SESSION, null, it) }
    byName(LiftRole.SOLO).takeIf { it.isNotEmpty() }?.let { sections += HeldSectionUi("Solo", HeldSectionKind.SOLO, null, it) }
    return sections
}
