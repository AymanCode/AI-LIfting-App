package com.ayman.ecolift.web.offline

import com.ayman.ecolift.web.supabase.sync.QueuedSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What IronMind shows for retained sync work, derived from the account's stored log. A view of it
 * can be dismissed; the stored evidence stays until a resolution is stored.
 */
data class SyncReview(
    val conflicts: List<ConflictReviewItem> = emptyList(),
    val exercises: List<ExerciseReviewItem> = emptyList(),
) {
    val count: Int get() = conflicts.size + exercises.size

    companion object {
        private val none = MutableStateFlow(SyncReview()).asStateFlow()

        /** For an app with no durable log: nothing to review, ever. */
        val Empty: StateFlow<SyncReview> get() = none
    }
}

/** Why a conflict's two choices are not offered yet. The person still sees both versions. */
enum class ReviewHold {
    /** A card's display or split change was refused. There is no set to compare, and no card choice exists yet. */
    CARD_ONLY,

    /** The cloud's version arrives with the next refresh. */
    WAITING_FOR_CLOUD_VERSION,

    /** This device moved the set to another date. Resolving a move is not supported yet. */
    MOVED_TO_ANOTHER_DATE,

    /** Later changes on the same dates were made on top of this one, so they are kept and reviewed together later. */
    LATER_CHANGES_ON_THESE_DATES,
}

data class ConflictReviewItem(
    val id: String,
    val dates: List<String>,
    val code: String,
    val local: QueuedSet?,
    val remote: QueuedSet?,
    val cardId: String?,
    val hold: ReviewHold?,
)

enum class ExerciseReviewKind {
    /** The cloud has an exercise with this name and the evidence does not prove it is the same lift. */
    SAME_NAME_IN_CLOUD,

    /** The cloud refused the new exercise with a proved answer. */
    CLOUD_REFUSED,

    /** The cloud may have created it, but its reply proved nothing. The exact request is kept to send again. */
    UNCONFIRMED,
}

data class ExerciseReviewItem(
    val localId: Long,
    val name: String,
    val kind: ExerciseReviewKind,
    val candidates: List<ExerciseCandidate>,
    val code: String?,
)
