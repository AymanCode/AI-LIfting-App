package com.ayman.ecolift.web.supabase

import com.ayman.ecolift.data.splits.EffectiveSplitMembers
import com.ayman.ecolift.data.splits.SplitChoicesState
import com.ayman.ecolift.data.splits.SplitMemberChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The repository's saved boards: what the server confirmed, highest revision per slot, overlaid
 * with boards still being saved. A refresh that read before a save landed carries a lower revision
 * and never replaces the newer board; rows are never deleted, so a slot missing from a refresh
 * keeps its board.
 */
internal class SplitChoiceBook {
    private var loaded = false
    private var confirmed: Map<Long, SplitMemberChoice> = emptyMap()
    private var pending: Map<Long, Pair<Long, EffectiveSplitMembers>> = emptyMap()
    private var nextToken = 0L
    private val _state = MutableStateFlow<SplitChoicesState>(SplitChoicesState.Loading)
    val state: StateFlow<SplitChoicesState> = _state.asStateFlow()

    /** A finished read; an absent table reads as no rows. */
    fun loaded(rows: List<SplitMemberChoice>) {
        loaded = true
        confirmed = rows.fold(confirmed) { book, row -> book.withNewer(row) }
        publish()
    }

    /**
     * Reads every board. A failure [isMissing] accepts (the table is not migrated yet) reads as no
     * boards; any other failure is rethrown and leaves the state as it was.
     */
    suspend fun refresh(isMissing: (Throwable) -> Boolean, read: suspend () -> List<SplitMemberChoice>) {
        val rows = try {
            read()
        } catch (failure: Throwable) {
            if (failure is CancellationException || !isMissing(failure)) throw failure
            emptyList()
        }
        loaded(rows)
    }

    /** Shows [board] at once; the token ends it with [confirm] or [drop]. */
    fun stage(slotId: Long, board: EffectiveSplitMembers): Long {
        val token = ++nextToken
        pending = pending + (slotId to (token to board))
        publish()
        return token
    }

    fun confirm(token: Long, row: SplitMemberChoice) {
        confirmed = confirmed.withNewer(row)
        drop(token)
    }

    fun drop(token: Long) {
        pending = pending.filterValues { it.first != token }
        publish()
    }

    private fun Map<Long, SplitMemberChoice>.withNewer(row: SplitMemberChoice): Map<Long, SplitMemberChoice> {
        val held = this[row.splitId]
        return if (held != null && held.revision > row.revision) this else this + (row.splitId to row)
    }

    private fun publish() {
        if (!loaded) return
        val shown = confirmed + pending.mapValues { (slotId, staged) ->
            SplitMemberChoice(slotId, confirmed[slotId]?.revision ?: 0L, staged.second)
        }
        _state.value = SplitChoicesState.Ready(shown, pending.keys, confirmed)
    }
}
