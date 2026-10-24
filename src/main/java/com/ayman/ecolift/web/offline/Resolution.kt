package com.ayman.ecolift.web.offline

import com.ayman.ecolift.web.supabase.sync.NotCommittedReason

sealed interface Resolution {
    data object Resolved : Resolution

    /** The server or this device changed the set after the person reviewed it. Show it again. */
    data object ChangedSinceReview : Resolution

    /**
     * Later requests share a workout date with the rejected one, directly or through a move, and their
     * revision preconditions were computed as if it had applied. Resolving now could let them pass on a
     * coincidental counter, so the conflict and all of that work stay retained. [dates] is the whole scope.
     */
    data class DependentWork(val dates: List<String>) : Resolution

    /** This conflict needs a choice this client cannot author yet, such as a move or a remote delete. */
    data object Unsupported : Resolution

    data object NotFound : Resolution

    data class NotSaved(val reason: NotCommittedReason) : Resolution
}
