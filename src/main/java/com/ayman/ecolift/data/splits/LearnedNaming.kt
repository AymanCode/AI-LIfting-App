package com.ayman.ecolift.data.splits

import com.ayman.ecolift.data.BodyPartRegion
import com.ayman.ecolift.data.BodyPartRegionMapper
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.selectableBodyPartParts

/**
 * The name a group shows: the name given to its slot, else its majority body regions ("Chest ·
 * Arms": every member's regions counted once each, the two most common, ties in body order),
 * else "Group" when no member has a known region.
 */
fun learnedGroupLabel(group: LearnedGroup, exercisesById: Map<Long, Exercise>): String =
    if (group.isUnnamed) regionLabel(group.members, exercisesById) else group.name.trim()

internal fun regionLabel(members: List<Long>, exercisesById: Map<Long, Exercise>): String {
    val counts = HashMap<BodyPartRegion, Int>()
    for (member in members) {
        val exercise = exercisesById[member] ?: continue
        selectableBodyPartParts(exercise.muscleGroups)
            .map(BodyPartRegionMapper::mapGroupToRegion)
            .filter { it != BodyPartRegion.UNKNOWN }
            .distinct()
            .forEach { counts[it] = (counts[it] ?: 0) + 1 }
    }
    val top = counts.entries
        .sortedWith(compareByDescending<Map.Entry<BodyPartRegion, Int>> { it.value }.thenBy { it.key.ordinal })
        .take(2)
        .map { it.key.displayName }
    return if (top.isEmpty()) "Group" else top.joinToString(" · ")
}
