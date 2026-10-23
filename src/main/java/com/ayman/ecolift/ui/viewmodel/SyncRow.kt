package com.ayman.ecolift.ui.viewmodel

import com.ayman.ecolift.web.supabase.CloudStamps
import com.ayman.ecolift.web.supabase.sync.SyncStatus
import com.ayman.ecolift.web.util.clockLabel
import kotlinx.datetime.TimeZone
import kotlin.time.ExperimentalTime

/**
 * The settings sheet's SYNC row (spec 2026-10-08 §The sync block in settings). Data never sits in
 * plain text: numbers, times and class names are data parts; the words around them are not.
 */
@OptIn(ExperimentalTime::class)
fun syncRowParts(
    status: SyncStatus,
    stamps: CloudStamps,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): List<DataPart> = when (status) {
    SyncStatus.UpToDate -> buildList {
        add(DataPart("Up to date", isData = false))
        stamps.lastSuccessAt?.let {
            add(DataPart(" · ", isData = false))
            add(DataPart(clockLabel(it, zone), isData = true))
        }
    }
    is SyncStatus.Waiting -> buildList {
        add(DataPart(status.pending.toString(), isData = true))
        add(DataPart(" waiting", isData = false))
        stamps.failingSince?.let {
            add(DataPart(" · since ", isData = false))
            add(DataPart(clockLabel(it, zone), isData = true))
        }
    }
    is SyncStatus.Offline -> listOf(
        DataPart("Offline", isData = false),
        DataPart(" · since ", isData = false),
        DataPart(clockLabel(status.since, zone), isData = true),
    )
}

/** The LAST FAILURE row, or null when nothing has failed in this page's life. */
@OptIn(ExperimentalTime::class)
fun lastFailureParts(
    stamps: CloudStamps,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): List<DataPart>? = stamps.lastFailure?.let { failure ->
    listOf(
        DataPart(failure.cls, isData = true),
        DataPart(" · ", isData = false),
        DataPart(clockLabel(failure.at, zone), isData = true),
    )
}
