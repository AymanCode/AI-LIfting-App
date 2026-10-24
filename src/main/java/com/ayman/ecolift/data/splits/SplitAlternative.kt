package com.ayman.ecolift.data.splits

import kotlinx.serialization.Serializable

/** Explicit choices survive the engine's changing frequency window; these are not extra required lifts. */
@Serializable
data class SplitAlternative(
    val splitId: Long,
    val sourceId: Long,
    val alternativeId: Long,
    val replacesSlot: Boolean = true,
)

internal fun alternativeResolver(options: List<SplitAlternative>): (Long) -> Long {
    val parents = mutableMapOf<Long, Long>()
    fun root(id: Long): Long {
        var current = id
        while (parents[current] != null && parents[current] != current) current = parents.getValue(current)
        return current
    }
    options.filter { it.replacesSlot }.forEach { option ->
        val a = root(option.sourceId)
        val b = root(option.alternativeId)
        if (a != b) parents[maxOf(a, b)] = minOf(a, b)
    }
    return { root(it) }
}

/** Expand the displayed catalogue only. Learning, day attribution and progress still use real checked sets. */
internal fun withRememberedAlternatives(group: LearnedGroup, options: List<SplitAlternative>): LearnedGroup {
    val choices = options.filter { it.splitId == group.slotId }
    val remembered = choices
        .flatMap { listOf(it.sourceId, it.alternativeId) }.distinct()
    val additional = remembered.filterNot { it in group.members }
    val resolve = alternativeResolver(choices)
    val selectedFamilies = mutableSetOf<Long>()
    // Both lifts can have checked sets after a mid-exercise swap. Remember both, but
    // never turn that one choice into two required lifts when learning catches up.
    val optional = additional.toMutableSet()
    group.ticketOrder.forEach { id ->
        if (id !in group.fadedMembers && !selectedFamilies.add(resolve(id))) optional += id
    }
    return group.copy(
        members = (group.members + additional).sorted(),
        ticketOrder = group.ticketOrder + additional,
        optionalMembers = optional,
    )
}
