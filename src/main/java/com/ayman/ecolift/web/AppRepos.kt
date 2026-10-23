package com.ayman.ecolift.web

import com.ayman.ecolift.web.supabase.AssistantChangeRepository
import com.ayman.ecolift.web.supabase.CardioRepository
import com.ayman.ecolift.web.supabase.CloudStamps
import com.ayman.ecolift.web.supabase.ExerciseRepository
import com.ayman.ecolift.web.supabase.LearnedSplitsSource
import com.ayman.ecolift.web.supabase.PendingReviewRepository
import com.ayman.ecolift.web.supabase.SetRepository
import com.ayman.ecolift.web.supabase.SplitReplacementCoordinator
import com.ayman.ecolift.web.supabase.UserSettingsRepository
import com.ayman.ecolift.web.supabase.WorkoutRepository
import com.ayman.ecolift.web.offline.ConflictChoice
import com.ayman.ecolift.web.offline.Resolution
import com.ayman.ecolift.web.offline.SyncReview
import com.ayman.ecolift.web.supabase.sync.QueuedSet
import com.ayman.ecolift.web.supabase.sync.SyncStatus
import kotlinx.coroutines.flow.StateFlow

interface AppRepos {
    val exerciseSwaps: com.ayman.ecolift.web.supabase.ExerciseSwapRepository
        get() = com.ayman.ecolift.web.supabase.NoExerciseSwapRepository
    val exercises: ExerciseRepository
    val sets: SetRepository
    val settings: UserSettingsRepository
    val workout: WorkoutRepository
    val pendingReviews: PendingReviewRepository
    val cardio: CardioRepository
    val splitReplacement: SplitReplacementCoordinator
    val assistantChanges: AssistantChangeRepository
    /** The learned-splits model every tab reads (spec 2026-09-28). */
    val learnedSplits: LearnedSplitsSource
    /** What the sync dot shows (spec 2026-10-08 §SyncStatus). Demo is always up to date. */
    val syncStatus: StateFlow<SyncStatus>
    /** Last success / last failure stamps for the settings sheet's SYNC block. */
    val cloudStamps: StateFlow<CloudStamps>

    /** Retained sync work IronMind lists for review. Empty unless the durable Log is active. */
    val syncReview: StateFlow<SyncReview> get() = SyncReview.Empty

    /** Resolve a retained conflict after rechecking the cloud. Without a durable Log there is nothing to resolve. */
    suspend fun resolveSyncConflict(id: String, choice: ConflictChoice, reviewedRemote: QueuedSet?): Resolution = Resolution.NotFound

    /** Use the chosen cloud exercise for a local one the cloud reported by the same name. */
    suspend fun useExistingExercise(localId: Long, serverId: Long): Boolean = false

    /** Send an unconfirmed exercise create again with its original bytes. */
    suspend fun retryExerciseCreate(localId: Long): Boolean = false

    suspend fun loadAll()
    fun close()
}
