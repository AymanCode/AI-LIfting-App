package com.ayman.ecolift.android.services

import android.os.RemoteException
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.ayman.ecolift.cardio.health.CardioHealthConnectReader
import com.ayman.ecolift.data.CardioSession
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Reads exercise sessions through the existing Health Connect client and upserts each one
 * that is still stored, or that the person has not deleted.
 * The returned count is only the uids that were not already stored or dismissed.
 * A later run still upserts, so a newer record can fill missing fields, and it does not count those rows again.
 * Without [exerciseSessionReadPermission] granted, the count is 0. Today's calorie total is not turned into a session.
 */
class HealthConnectImport(
    private val reader: CardioHealthConnectReader,
    private val storedHealthConnectUids: suspend () -> Iterable<String?>,
    private val dismissedHealthConnectUids: DismissedHealthConnectUids,
    private val upsert: suspend (CardioSession) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
    private val windowDays: Long = 14,
) {
    fun exerciseSessionReadPermission(): String =
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    suspend fun importRecentSessions(): Int {
        if (!reader.isAvailable()) return 0
        var state: ImportState? = null
        return try {
            val stored = storedHealthConnectUids()
                .mapNotNull { uid -> uid?.takeIf(String::isNotBlank) }
                .toSet()
            val dismissed = dismissedHealthConnectUids.all()
            val seen = seenHealthConnectUids(stored, dismissedHealthConnectUids)
            val imported = ImportState(stored, dismissed, seen, upsert)
            state = imported
            val client = reader.client()
            val granted = client.permissionController.getGrantedPermissions()
            if (exerciseSessionReadPermission() !in granted) return 0
            val end = Instant.now(clock)
            val start = end.minus(windowDays, ChronoUnit.DAYS)
            var pageToken: String? = null
            var pages = 0
            do {
                val page = client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageToken = pageToken,
                    ),
                )
                val now = clock.millis()
                val sessions = page.records.mapNotNull { record -> sessionFrom(record, now) }
                importSessions(sessions, imported)
                pageToken = page.pageToken
                pages += 1
            } while (pageToken != null && pages < MAX_PAGES)
            imported.added
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            state?.added ?: 0
        } catch (error: RemoteException) {
            state?.added ?: 0
        } catch (error: IllegalStateException) {
            state?.added ?: 0
        }
    }

    private fun sessionFrom(record: ExerciseSessionRecord, nowEpochMilli: Long): CardioSession? {
        val uid = record.metadata.id
        if (uid.isBlank()) return null
        return sessionFromHealthConnect(
            activityType = cardioActivityName(record.exerciseType),
            startEpochMilli = record.startTime.toEpochMilli(),
            endEpochMilli = record.endTime.toEpochMilli(),
            zoneOffsetSeconds = record.startZoneOffset?.totalSeconds,
            hcUid = uid,
            dataOriginPackage = record.metadata.dataOrigin.packageName,
            lastModifiedEpochMilli = record.metadata.lastModifiedTime.toEpochMilli(),
            nowEpochMilli = nowEpochMilli,
            localUuid = UUID.randomUUID().toString(),
        )
    }

    private companion object {
        const val MAX_PAGES = 20
    }
}

internal class ImportState(
    val stored: Set<String>,
    val dismissed: Set<String>,
    val seen: MutableSet<String>,
    val upsert: suspend (CardioSession) -> Unit,
) {
    var added = 0
}

internal suspend fun importSessions(sessions: List<CardioSession>, state: ImportState) {
    for (session in sessions) {
        val uid = session.hcUid
        if (!uid.isNullOrBlank() && uid in state.dismissed && uid !in state.stored) continue
        val isNew = claimNewHealthConnectUid(state.seen, uid)
        state.upsert(session)
        if (isNew) state.added += 1
    }
}
