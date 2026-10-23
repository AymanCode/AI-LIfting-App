package com.ayman.ecolift.data.hub

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet

sealed interface HubItem {
    val id: String          // stable, unique; used later as a UI key
}

/** One exercise on one day that has unchecked sets. */
data class UncheckedSetsItem(
    override val id: String,      // "unchecked:<date>:<exerciseId>"
    val date: String,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroups: String,
    val isBodyweight: Boolean,
    val openSets: List<WorkoutSet>,      // unchecked, ordered by setNumber
    val referenceSets: List<WorkoutSet>, // checked sets for the SAME exercise+date, ordered by setNumber
) : HubItem

/** Unchecked sets too old to recall, collapsed into one bulk decision. */
data class BacklogItem(
    override val id: String,      // "backlog"
    val fromDate: String,
    val toDate: String,
    val setCount: Int,
    val dayCount: Int,
) : HubItem

enum class HubDayKind { NeverVerified, Trained }

data class HubDay(
    val date: String,
    val kind: HubDayKind,
    val items: List<HubItem>,
)

data class HubBuild(
    val days: List<HubDay>,   // newest date first
    val backlog: BacklogItem?,
    val count: Int,           // total resolvable notifications = all items across days, + 1 if backlog != null
)

object HubItems {
    fun build(
        sets: List<WorkoutSet>,
        exercisesById: Map<Long, Exercise>,
        today: String,
        individualReviewFrom: String,
    ): HubBuild {
        // 1. Ignore today entirely. Sets dated today (or future) produce no items.
        val pastSets = sets.filter { it.date < today }

        // 2. Split by recency. Older than individualReviewFrom aggregates into BacklogItem.
        val oldUncheckedSets = pastSets.filter { it.date < individualReviewFrom && !it.completed }
        val backlog = if (oldUncheckedSets.isNotEmpty()) {
            BacklogItem(
                id = "backlog",
                fromDate = oldUncheckedSets.minOf { it.date },
                toDate = oldUncheckedSets.maxOf { it.date },
                setCount = oldUncheckedSets.size,
                dayCount = oldUncheckedSets.map { it.date }.distinct().size,
            )
        } else {
            null
        }

        val recentSets = pastSets.filter { it.date >= individualReviewFrom }
        val recentUncheckedSets = recentSets.filter { !it.completed }
        val recentCheckedSets = recentSets.filter { it.completed }

        // 3 & 4. One item per (date, exercise) for recent dates. Skip missing/merged exercises.
        val uncheckedByDateAndExercise = recentUncheckedSets.groupBy { it.date to it.exerciseId }
        val uncheckedItemsByDate = mutableMapOf<String, MutableList<UncheckedSetsItem>>()

        for ((key, openSets) in uncheckedByDateAndExercise) {
            val (date, exerciseId) = key
            val exercise = exercisesById[exerciseId]
            if (exercise == null || exercise.mergedIntoId != null) {
                continue
            }
            val sortedOpenSets = openSets.sortedBy { it.setNumber }
            val referenceSets = recentCheckedSets
                .filter { it.date == date && it.exerciseId == exerciseId }
                .sortedBy { it.setNumber }

            val item = UncheckedSetsItem(
                id = "unchecked:$date:$exerciseId",
                date = date,
                exerciseId = exerciseId,
                exerciseName = exercise.name,
                muscleGroups = exercise.muscleGroups,
                isBodyweight = exercise.isBodyweight,
                openSets = sortedOpenSets,
                referenceSets = referenceSets,
            )
            uncheckedItemsByDate.getOrPut(date) { mutableListOf() }.add(item)
        }

        // 5. Grouping and ordering into HubDay. Split attribution deliberately has no bearing on
        // the hub: this surface exists to resolve unchecked exercises, not to classify every
        // completed workout as a split or a missing split.
        val hubDays = uncheckedItemsByDate.keys.mapNotNull { date ->
            val uncheckedList = uncheckedItemsByDate[date].orEmpty().sortedWith(
                compareBy<UncheckedSetsItem> { it.openSets.firstOrNull()?.setNumber ?: 0 }
                    .thenBy { it.exerciseName }
            )

            if (uncheckedList.isEmpty()) {
                null
            } else {
                val hasChecked = recentCheckedSets.any { it.date == date }
                val kind = if (hasChecked) HubDayKind.Trained else HubDayKind.NeverVerified
                HubDay(
                    date = date,
                    kind = kind,
                    items = uncheckedList,
                )
            }
        }.sortedByDescending { it.date }

        // 6. Total count
        val count = hubDays.sumOf { it.items.size } + (if (backlog != null) 1 else 0)

        return HubBuild(
            days = hubDays,
            backlog = backlog,
            count = count,
        )
    }
}

/**
 * Optimistically removes resolved notifications from the visible stack. Repository writes are
 * already optimistic in the signed-in app, but this keeps the notification interaction immediate
 * even when several set writes are needed or a read-only/demo repository is in use.
 */
fun HubBuild.withoutItems(itemIds: Set<String>): HubBuild {
    if (itemIds.isEmpty()) return this

    val visibleDays = days.mapNotNull { day ->
        val visibleItems = day.items.filterNot { it.id in itemIds }
        if (visibleItems.isEmpty()) null else day.copy(items = visibleItems)
    }
    val visibleBacklog = backlog?.takeUnless { it.id in itemIds }

    return copy(
        days = visibleDays,
        backlog = visibleBacklog,
        count = visibleDays.sumOf { it.items.size } + (if (visibleBacklog != null) 1 else 0),
    )
}

fun HubBuild.itemIds(): Set<String> = buildSet {
    days.forEach { day -> day.items.forEach { item -> add(item.id) } }
    backlog?.let { add(it.id) }
}
