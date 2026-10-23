package com.ayman.ecolift.web.supabase.sync

import com.ayman.ecolift.web.supabase.CloudStamps
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What the sync dot and the settings row show (spec 2026-10-08 §SyncStatus). */
@OptIn(ExperimentalTime::class)
sealed interface SyncStatus {
    /** Nothing pending and the newest stamp is a success (or there are no stamps yet). */
    data object UpToDate : SyncStatus
    /** Writes are queued, whatever the cause. Outranks Offline: the count at risk is the useful fact. */
    data class Waiting(val pending: Int) : SyncStatus
    /** The browser is offline, or the newest stamp is a failure with no success after it. */
    data class Offline(val since: Instant) : SyncStatus
}

/** The browser's own view of the network (`navigator.onLine` plus the online/offline events). */
@OptIn(ExperimentalTime::class)
sealed interface Connectivity {
    data object Online : Connectivity
    data class Offline(val since: Instant) : Connectivity
}

/** Pure: the same inputs always give the same status. `since` is the earliest sign of the current run. */
@OptIn(ExperimentalTime::class)
fun syncStatus(connectivity: Connectivity, pending: Int, stamps: CloudStamps): SyncStatus = when {
    pending > 0 -> SyncStatus.Waiting(pending)
    connectivity is Connectivity.Offline ->
        SyncStatus.Offline(since = minOf(connectivity.since, stamps.failingSince ?: connectivity.since))
    stamps.failingSince != null -> SyncStatus.Offline(since = stamps.failingSince)
    else -> SyncStatus.UpToDate
}
