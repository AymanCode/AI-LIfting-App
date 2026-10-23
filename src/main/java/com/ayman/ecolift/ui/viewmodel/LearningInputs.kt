package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.data.WorkoutSet

/*
 * The smallest slice of the set list each Log projection reads, so a weight or reps edit on the
 * shown day (a tape scrub, a keypad entry) does not rebuild the split-learning data, the ticket
 * ledger or the clipboard pages. Each key is compared with data-class equality: O(N), far cheaper
 * than the builders it gates.
 */

/** A row on or after the shown day as the learned programs read it: identity and check state only. */
internal data class SetIdentity(
    val id: Long,
    val syncId: String,
    val exerciseId: Long,
    val date: String,
    val setNumber: Int,
    val completed: Boolean,
    val isWarmup: Boolean,
)

internal fun WorkoutSet.identity() = SetIdentity(id, syncId, exerciseId, date, setNumber, completed, isWarmup)

/**
 * What [buildLearnedPrograms] and [buildEmptyDayLedgerSnapshot] read: every value of the rows
 * before [date] (ticket rows, stakes and ledger history only look back), and for the shown day and
 * later only which rows exist and which are checked (a program's last-session set count and the
 * ledger's empty-day test). Weights and reps on or after the shown day are not part of it.
 */
internal data class LearningSetsKey(
    val date: String,
    val prior: List<WorkoutSet>,
    val shownAndLater: List<SetIdentity>,
)

internal fun learningSetsKey(date: String, sets: List<WorkoutSet>): LearningSetsKey {
    val prior = ArrayList<WorkoutSet>()
    val rest = ArrayList<SetIdentity>()
    for (set in sets) if (set.date < date) prior += set else rest += set.identity()
    return LearningSetsKey(date, prior, rest)
}

/** What [buildClipboardLifts] reads: the lift and date of every checked row. */
internal fun clipboardLiftsKey(sets: List<WorkoutSet>): List<Pair<Long, String>> =
    sets.mapNotNull { set -> if (set.completed) set.exerciseId to set.date else null }

/**
 * Caches a build that reads only the rows dated before the shown day. A new set list whose prior
 * rows are equal (an edit on the shown day) returns the cached value. [tag] is compared by value
 * and [others] by identity, so a new model or catalogue always rebuilds.
 */
internal class PriorHistoryMemo<T : Any> {
    private var date: String? = null
    private var tag: String? = null
    private var sets: List<WorkoutSet>? = null
    private var prior: List<WorkoutSet> = emptyList()
    private var others: List<Any?> = emptyList()
    private var value: T? = null

    fun get(
        date: String,
        tag: String,
        sets: List<WorkoutSet>,
        others: List<Any?>,
        build: (prior: List<WorkoutSet>) -> T,
    ): T {
        val cached = value
        val sameContext = cached != null && date == this.date && tag == this.tag && others.size == this.others.size &&
            others.indices.all { others[it] === this.others[it] }
        if (sameContext && sets === this.sets) return cached!!
        val prior = sets.filter { it.date < date }
        if (sameContext && prior == this.prior) {
            this.sets = sets
            return cached!!
        }
        return build(prior).also {
            this.date = date
            this.tag = tag
            this.sets = sets
            this.prior = prior
            this.others = others
            value = it
        }
    }
}
