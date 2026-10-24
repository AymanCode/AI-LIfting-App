package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.ExerciseAlias
import com.ayman.ecolift.data.ExerciseMatchInput
import com.ayman.ecolift.data.ExerciseMatcher
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.selectableBodyPartParts
import com.ayman.ecolift.data.splits.SplitAlternative

data class SwapCandidateUi(
    val exerciseId: Long,
    val name: String,
    val detail: String,
    val remembered: Boolean,
    val alreadyToday: Boolean,
)

data class ExerciseSwapUi(
    val date: String,
    val sourceId: Long,
    val sourceName: String,
    val splitId: Long?,
    val splitName: String?,
    val query: String = "",
    val candidates: List<SwapCandidateUi> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val retainedSetCount: Int = 0,
    val context: SwapSheetUi? = null,
)

data class ExerciseSwapUndoUi(val requestId: String, val date: String, val sourceId: Long, val targetId: Long)

/** A day can contain extra lifts. Prefer its chosen split; never guess between tied split owners. */
internal fun swapGroupForDay(
    model: com.ayman.ecolift.data.splits.LearnedModel,
    requestedSlot: Long?,
    dayExerciseIds: Set<Long>,
): com.ayman.ecolift.data.splits.LearnedGroup? {
    model.groupForSlot(requestedSlot)?.takeUnless { it.suppressed }?.let { return it }
    val scored = model.visibleGroups.map { group -> group to group.members.count { it in dayExerciseIds } }
    val best = scored.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: return null
    return scored.filter { it.second == best }.singleOrNull()?.first
}

/** A cached local catalogue gives immediate results, including aliases, partial tokens and common shorthand. */
internal fun swapCandidates(
    sourceId: Long,
    splitId: Long?,
    query: String,
    date: String,
    exercises: List<Exercise>,
    aliases: List<ExerciseAlias>,
    sets: List<WorkoutSet>,
    alternatives: List<SplitAlternative>,
    splitMembers: Set<Long>,
    canonicalId: (Long) -> Long,
): List<SwapCandidateUi> {
    val source = exercises.firstOrNull { it.id == sourceId }
    val sourceGroups = source?.let { selectableBodyPartParts(it.muscleGroups).toSet() }.orEmpty()
    val history = sets.filter { it.completed && it.date < date }.groupBy { canonicalId(it.exerciseId) }
    val usedToday = sets.filter { it.date == date }.map { canonicalId(it.exerciseId) }.toSet()
    val options = alternatives.filter { it.splitId == splitId }.map {
        it.copy(sourceId = canonicalId(it.sourceId), alternativeId = canonicalId(it.alternativeId))
    }
    val family = com.ayman.ecolift.data.splits.alternativeResolver(options)
    val remembered = (options.flatMap { listOf(canonicalId(it.sourceId), canonicalId(it.alternativeId)) }
        .filter { family(it) == family(sourceId) } + exercises.filter {
            it.id in splitMembers && selectableBodyPartParts(it.muscleGroups).any(sourceGroups::contains)
        }.map { it.id }).toSet() - usedToday
    val catalogue = exercises.filter { it.id != sourceId && it.mergedIntoId == null }.distinctBy { it.id }
    val aliasesById = aliases.groupBy { canonicalId(it.exerciseId) }
    val trimmed = query.trim().take(120)
    val ordered = if (trimmed.isEmpty()) {
        catalogue.sortedWith(
            compareByDescending<Exercise> { it.id in remembered }
                .thenBy { it.id in usedToday }
                .thenByDescending { selectableBodyPartParts(it.muscleGroups).any(sourceGroups::contains) }
                .thenByDescending { history[it.id]?.maxOfOrNull { set -> set.date }.orEmpty() }
                .thenBy { it.name.lowercase() },
        ).take(30)
    } else {
        val matches = ExerciseMatcher.rank(catalogue.map {
            ExerciseMatchInput(it, aliasesById[it.id].orEmpty().map { alias -> alias.alias },
                history[it.id].orEmpty().size, history[it.id]?.maxOfOrNull { set -> set.date })
        }, trimmed, limit = 50).map { it.exerciseId }
        // One- and two-letter prefixes are useful while the rest of a word is still being typed.
        val prefixes = catalogue.filter { exercise ->
            (listOf(exercise.name) + aliasesById[exercise.id].orEmpty().map { it.alias })
                .any { name -> name.contains(trimmed, ignoreCase = true) }
        }.sortedBy { it.name.lowercase() }.map { it.id }
        val byId = catalogue.associateBy { it.id }
        (matches + prefixes).distinct().take(50).mapNotNull(byId::get)
    }
    return ordered.map { exercise ->
        val previous = history[exercise.id].orEmpty()
        val lastDate = previous.maxOfOrNull { it.date }
        val last = previous.filter { it.date == lastDate }.sortedBy { it.setNumber }
            .firstOrNull { !it.isWarmup } ?: previous.filter { it.date == lastDate }.minByOrNull { it.setNumber }
        val load = last?.let {
            if (it.isBodyweight) {
                if ((it.weightLbs ?: 0) > 0) "BW + ${WeightLbs.formatStored(it.weightLbs)} lb" else "BW"
            } else {
                it.weightLbs?.let { weight -> "${WeightLbs.formatStored(weight)} lb" } ?: "—"
            }
        }
        val lastLabel = last?.let { "Last $load × ${it.reps?.toString() ?: "—"}" } ?: "No sets"
        val group = selectableBodyPartParts(exercise.muscleGroups).firstOrNull()
        SwapCandidateUi(exercise.id, exercise.name,
            listOfNotNull(group, lastLabel, if (exercise.id in usedToday) "In today" else null).joinToString(" · "),
            exercise.id in remembered, exercise.id in usedToday)
    }
}
