package com.ayman.ecolift.android.services

import android.content.Context
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.ayman.ecolift.android.platform.AndroidHost
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.data.CardioSessionSource
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * True the first time [uid] is claimed. A repeat call with the same uid is false,
 * which is how a second import avoids counting a session it already stored.
 * Blank uids are never claimed.
 */
fun claimNewHealthConnectUid(seen: MutableSet<String>, uid: String?): Boolean {
    if (uid.isNullOrBlank()) return false
    return seen.add(uid)
}

interface DismissedHealthConnectUids {
    fun all(): Set<String>
    fun dismiss(uid: String)
}

fun rememberDeletedHealthConnectUid(hcUid: String?, dismissed: DismissedHealthConnectUids) {
    if (hcUid.isNullOrBlank()) return
    dismissed.dismiss(hcUid)
}

fun seenHealthConnectUids(storedUids: Iterable<String?>, dismissed: DismissedHealthConnectUids): MutableSet<String> {
    val seen = mutableSetOf<String>()
    for (uid in storedUids) {
        if (!uid.isNullOrBlank()) seen.add(uid)
    }
    seen.addAll(dismissed.all())
    return seen
}

object AndroidDismissedHealthConnectUids : DismissedHealthConnectUids {
    private const val PREFS_NAME = "ecolift-health-connect"
    private const val KEY = "dismissed-uids"

    override fun all(): Set<String> {
        val stored = preferences()?.getStringSet(KEY, emptySet()) ?: return emptySet()
        return stored.toSet()
    }

    override fun dismiss(uid: String) {
        if (uid.isBlank()) return
        val preferences = preferences() ?: return
        val next = preferences.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!next.add(uid)) return
        preferences.edit().putStringSet(KEY, next).apply()
    }

    private fun preferences() = AndroidHost.current()?.applicationContext?.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )
}

fun cardioActivityName(exerciseType: Int): String = when (exerciseType) {
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> CardioActivityType.TREADMILL.name
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> CardioActivityType.RUN.name
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> CardioActivityType.WALK.name
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
    -> CardioActivityType.BIKE.name
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
    -> CardioActivityType.ROW.name
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    -> CardioActivityType.SWIM.name
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> CardioActivityType.ELLIPTICAL.name
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
    -> CardioActivityType.STAIR_CLIMBER.name
    else -> CardioActivityType.OTHER.name
}

fun sessionFromHealthConnect(
    activityType: String,
    startEpochMilli: Long,
    endEpochMilli: Long,
    zoneOffsetSeconds: Int?,
    hcUid: String,
    dataOriginPackage: String?,
    lastModifiedEpochMilli: Long?,
    nowEpochMilli: Long,
    localUuid: String,
): CardioSession {
    val zone = zoneOffsetSeconds?.let { ZoneOffset.ofTotalSeconds(it) } ?: ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(startEpochMilli).atZone(zone).toLocalDate().toString()
    val durationSec = ((endEpochMilli - startEpochMilli) / 1000L).toInt().coerceAtLeast(0)
    return CardioSession(
        localUuid = localUuid,
        date = date,
        activityType = activityType,
        durationSec = durationSec,
        source = CardioSessionSource.HEALTH_CONNECT,
        hcUid = hcUid,
        hcDataOriginPackage = dataOriginPackage,
        hcLastModifiedTime = lastModifiedEpochMilli,
        startTime = startEpochMilli,
        endTime = endEpochMilli,
        zoneOffsetSeconds = zoneOffsetSeconds,
        createdAt = nowEpochMilli,
        updatedAt = nowEpochMilli,
    )
}
