package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet

/** One deferred tape commit: the latest stepped row and the row as it stood before the first step. */
internal data class SetCommit(
    val row: WorkoutSet,
    val beforeFirstStep: WorkoutSet?,
) {
    /** Steps moved weight. Without the pre-scrub row both stepped fields count as moved. */
    val weightMoved: Boolean get() = beforeFirstStep == null || row.weightLbs != beforeFirstStep.weightLbs
    val repsMoved: Boolean get() = beforeFirstStep == null || row.reps != beforeFirstStep.reps

    /** [onto] with only the stepped fields this commit moved. */
    fun stepsOnto(onto: WorkoutSet): WorkoutSet = onto.copy(
        weightLbs = if (weightMoved) row.weightLbs else onto.weightLbs,
        reps = if (repsMoved) row.reps else onto.reps,
    )

    /** [onto] with each moved field put back to its pre-scrub value: the base those steps were taken from. */
    fun baseOn(onto: WorkoutSet): WorkoutSet {
        val before = beforeFirstStep ?: return onto
        return onto.copy(
            weightLbs = if (weightMoved) before.weightLbs else onto.weightLbs,
            reps = if (repsMoved) before.reps else onto.reps,
        )
    }
}

/**
 * Holds tape steps (weight/reps notches) until the scrub settles, so a burst of steps on one set
 * becomes one repository write. Rows are keyed by syncId when they have one, because a sync can
 * remap a temp id between two steps; rows without a syncId fall back to their id, and [rekey]
 * follows them when a legacy sync remaps that id or assigns a syncId.
 *
 * Only weight and reps belong to a step (`adjustWeight` / `adjustReps`); every other field of a
 * staged row is whatever the row held at the step and is never laid over newer repository values.
 *
 * Pure bookkeeping: the caller owns the timer and the actual write.
 */
internal class SetCommitBuffer {
    private var pending = LinkedHashMap<String, SetCommit>()

    /** Old key → new key for every [rekey] since the last [flush], so a timer armed under the old key still finds its commit. */
    private val forwards = mutableMapOf<String, String>()

    val isEmpty: Boolean get() = pending.isEmpty()

    fun has(key: String): Boolean = key in pending

    /** Records [row] as the latest value for its set and returns the set's key. */
    fun stage(row: WorkoutSet, before: WorkoutSet?): String {
        val key = keyOf(row)
        val earlier = pending[key]
        pending[key] = SetCommit(row = row, beforeFirstStep = earlier?.beforeFirstStep ?: before)
        return key
    }

    /** The key a commit staged under [key] lives under now. */
    fun resolve(key: String): String {
        var current = key
        while (true) current = forwards[current]?.takeIf { it != current } ?: return current
    }

    /** Removes and returns the pending commit for [key] (or the key it was moved to), if any. */
    fun take(key: String): SetCommit? = pending.remove(resolve(key))

    /**
     * Moves the pending commit from [oldKey] to [newKey], keeping its place in the step order.
     * Returns false when nothing was pending under [oldKey].
     */
    fun rekey(oldKey: String, newKey: String): Boolean {
        if (oldKey == newKey || oldKey !in pending) return false
        val moved = LinkedHashMap<String, SetCommit>()
        for ((key, commit) in pending) {
            when (key) {
                oldKey -> moved[newKey] = commit
                newKey -> if (newKey !in moved) moved[newKey] = commit
                else -> moved[key] = commit
            }
        }
        pending = moved
        forwards[oldKey] = newKey
        return true
    }

    /** Removes and returns every pending commit, in the order the sets were first stepped. */
    fun flush(): List<SetCommit> {
        val commits = pending.values.toList()
        pending.clear()
        forwards.clear()
        return commits
    }

    /**
     * Lays the stepped weight or reps over a repository emission so an unrelated echo cannot snap
     * a stepped field back to its last committed value. A field the steps never moved, and everything else, comes from the repository,
     * and the pending row adopts the repository's id and syncId (a sync may have remapped them).
     */
    fun overlay(rows: List<WorkoutSet>): List<WorkoutSet> {
        if (pending.isEmpty()) return rows
        return rows.map { row ->
            val key = keyOf(row)
            val commit = pending[key] ?: return@map row
            if (commit.row.id != row.id || commit.row.syncId != row.syncId) {
                pending[key] = commit.copy(row = commit.row.copy(id = row.id, syncId = row.syncId))
            }
            commit.stepsOnto(row)
        }
    }

    companion object {
        fun keyOf(row: WorkoutSet): String =
            if (row.syncId.isNotBlank()) "sync:${row.syncId}" else "id:${row.id}"
    }
}
