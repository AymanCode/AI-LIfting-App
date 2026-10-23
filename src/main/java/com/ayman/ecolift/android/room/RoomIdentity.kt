package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.WorkoutSet
import java.util.UUID

/** Keeper rule from merge_exercises: more checked sets, then the later session, then the older row, then the lower id. */
fun chooseMergeKeeper(a: Exercise, b: Exercise, sets: List<WorkoutSet>): Long {
    val history = sets.filter { it.completed }
    val aSets = history.filter { it.exerciseId == a.id }
    val bSets = history.filter { it.exerciseId == b.id }
    val aLast = aSets.maxOfOrNull { it.date }.orEmpty()
    val bLast = bSets.maxOfOrNull { it.date }.orEmpty()
    val aWins = when {
        aSets.size != bSets.size -> aSets.size > bSets.size
        aLast != bLast -> aLast > bLast
        a.createdAt != b.createdAt -> a.createdAt < b.createdAt
        else -> a.id < b.id
    }
    return if (aWins) a.id else b.id
}

/** Same ranking as migration 18 to 19: lowest set number, then lowest id, then 1..n. */
fun renumberSets(sets: List<WorkoutSet>): List<WorkoutSet> =
    sets.sortedWith(compareBy({ it.setNumber }, { it.id }))
        .mapIndexed { index, set -> set.copy(setNumber = index + 1) }

/** Distinct pairs are stored with the smaller id first. The same exercise is not a pair. */
fun orderDistinctPair(first: Long, second: Long): Pair<Long, Long>? {
    if (first == second) return null
    return if (first < second) first to second else second to first
}

/**
 * Apply [requested] onto [current], keeping any field the caller did not change from [observed].
 * Identity stays on [current]. [rowRevision] advances by one.
 */
fun mergeSetFields(observed: WorkoutSet?, current: WorkoutSet, requested: WorkoutSet): WorkoutSet {
    if (observed == null) {
        return requested.copy(
            id = current.id,
            syncId = current.syncId.ifBlank { requested.syncId },
            rowRevision = current.rowRevision + 1,
        )
    }
    fun <T> pick(field: WorkoutSet.() -> T): T {
        val base = observed.field()
        val theirs = current.field()
        val mine = requested.field()
        return if (mine == base) theirs else mine
    }
    return current.copy(
        exerciseId = pick { exerciseId },
        date = pick { date },
        setNumber = pick { setNumber },
        weightLbs = pick { weightLbs },
        reps = pick { reps },
        isBodyweight = pick { isBodyweight },
        completed = pick { completed },
        restTimeSeconds = pick { restTimeSeconds },
        cardId = pick { cardId },
        isWarmup = pick { isWarmup },
        syncId = current.syncId.ifBlank { requested.syncId },
        rowRevision = current.rowRevision + 1,
    )
}

fun newSyncId(): String = UUID.randomUUID().toString()

fun newCardId(): String = UUID.randomUUID().toString()
