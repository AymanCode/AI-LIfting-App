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
    private val reader: CardioHealthConnectReader? = null,
    private val storedHealthConnectUids: suspend () -> Iterable<String?>,
    private val dismissedHealthConnectUids: DismissedHealthConnectUids,
    private val upsert: suspend (CardioSession) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
    private val windowDays: Long = 14,
    private val exerciseSessions: (suspend () -> List<CardioSession>)? = null,
) {
    fun exerciseSessionReadPermission(): String =
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    suspend fun importRecentSessions(): Int {
        val fixture = exerciseSessions
        val source = if (fixture == null) reader ?: return 0 else null
        if (source != null && !source.isAvailable()) return 0
        var added = 0
        return try {
            val stored = storedHealthConnectUids()
                .mapNotNull { uid -> uid?.takeIf(String::isNotBlank) }
                .toSet()
            val dismissed = dismissedHealthConnectUids.all()
            val seen = seenHealthConnectUids(stored, dismissedHealthConnectUids)
            if (fixture != null) {
                for (session in fixture()) {
                    if (importOne(session, stored, dismissed, seen)) added += 1
                }
                return added
            }
            val client = source!!.client()
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
                for (record in page.records) {
                    val session = sessionFrom(record, now) ?: continue
                    if (importOne(session, stored, dismissed, seen)) added += 1
                }
                pageToken = page.pageToken
                pages += 1
            } while (pageToken != null && pages < MAX_PAGES)
            added
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            added
        } catch (error: RemoteException) {
            added
        } catch (error: IllegalStateException) {
            added
        }
    }

    private suspend fun importOne(
        session: CardioSession,
        stored: Set<String>,
        dismissed: Set<String>,
        seen: MutableSet<String>,
    ): Boolean {
        val uid = session.hcUid
        if (!uid.isNullOrBlank() && uid in dismissed && uid !in stored) return false
        val isNew = claimNewHealthConnectUid(seen, uid)
        upsert(session)
        return isNew
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
