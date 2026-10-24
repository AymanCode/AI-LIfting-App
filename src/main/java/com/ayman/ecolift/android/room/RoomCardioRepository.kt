package com.ayman.ecolift.android.room

import com.ayman.ecolift.android.services.AndroidDismissedHealthConnectUids
import com.ayman.ecolift.android.services.DismissedHealthConnectUids
import com.ayman.ecolift.android.services.rememberDeletedHealthConnectUid
import com.ayman.ecolift.data.CardioSession
import com.ayman.ecolift.data.CardioSessionSource
import com.ayman.ecolift.data.room.toDomain
import com.ayman.ecolift.web.supabase.CardioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomCardioRepository(
    private val store: RoomStore,
    private val dismissedHealthConnectUids: DismissedHealthConnectUids = AndroidDismissedHealthConnectUids,
) : CardioRepository {
    override val sessions: StateFlow<List<CardioSession>> = store.cardio

    override fun observeSessions(): Flow<List<CardioSession>> = sessions

    override fun observeCaloriesForDate(date: String): Flow<Int> =
        sessions.map { rows -> rows.filter { it.date == date }.sumOf { it.calories ?: 0 } }

    override suspend fun refresh() {
        store.write { cardio = db.cardioSessionDao().getAll().map { it.toDomain() } }
    }

    override suspend fun saveManual(
        date: String,
        activityType: String,
        activityLabel: String?,
        durationSec: Int?,
        distanceM: Double?,
        calories: Int?,
        avgHeartRate: Int?,
        maxHeartRate: Int?,
        avgSpeed: Double?,
        avgInclinePercent: Double?,
        cadenceRpm: Int?,
        resistanceLevel: Double?,
        avgPowerWatts: Int?,
        strokeRateSpm: Int?,
        paceSecPer500m: Int?,
        floors: Int?,
        steps: Int?,
        notes: String,
    ): CardioSession = insert(
        date = date,
        activityType = activityType,
        activityLabel = activityLabel,
        durationSec = durationSec,
        distanceM = distanceM,
        calories = calories,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        avgSpeed = avgSpeed,
        avgInclinePercent = avgInclinePercent,
        cadenceRpm = cadenceRpm,
        resistanceLevel = resistanceLevel,
        avgPowerWatts = avgPowerWatts,
        strokeRateSpm = strokeRateSpm,
        paceSecPer500m = paceSecPer500m,
        floors = floors,
        steps = steps,
        source = CardioSessionSource.MANUAL,
        notes = notes,
    )

    override suspend fun saveOcrConfirmed(
        date: String,
        activityType: String,
        activityLabel: String?,
        durationSec: Int?,
        distanceM: Double?,
        calories: Int?,
        avgHeartRate: Int?,
        maxHeartRate: Int?,
        avgSpeed: Double?,
        avgInclinePercent: Double?,
        cadenceRpm: Int?,
        resistanceLevel: Double?,
        avgPowerWatts: Int?,
        strokeRateSpm: Int?,
        paceSecPer500m: Int?,
        floors: Int?,
        steps: Int?,
        machineType: String?,
        ocrConfidence: Double?,
        notes: String,
    ): CardioSession = insert(
        date = date,
        activityType = activityType,
        activityLabel = activityLabel,
        durationSec = durationSec,
        distanceM = distanceM,
        calories = calories,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        avgSpeed = avgSpeed,
        avgInclinePercent = avgInclinePercent,
        cadenceRpm = cadenceRpm,
        resistanceLevel = resistanceLevel,
        avgPowerWatts = avgPowerWatts,
        strokeRateSpm = strokeRateSpm,
        paceSecPer500m = paceSecPer500m,
        floors = floors,
        steps = steps,
        source = CardioSessionSource.OCR,
        machineType = machineType,
        ocrConfidence = ocrConfidence,
        notes = notes,
    )

    override suspend fun getById(id: Long): CardioSession? = store.cardioNow().firstOrNull { it.id == id }

    override suspend fun update(session: CardioSession): CardioSession = store.write {
        updateCardio(session.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun delete(id: Long) {
        val hcUid = store.cardioNow().firstOrNull { it.id == id }?.hcUid
        store.write { deleteCardio(id) }
        rememberDeletedHealthConnectUid(hcUid, dismissedHealthConnectUids)
    }

    private suspend fun insert(
        date: String,
        activityType: String,
        activityLabel: String?,
        durationSec: Int?,
        distanceM: Double?,
        calories: Int?,
        avgHeartRate: Int?,
        maxHeartRate: Int?,
        avgSpeed: Double?,
        avgInclinePercent: Double?,
        cadenceRpm: Int?,
        resistanceLevel: Double?,
        avgPowerWatts: Int?,
        strokeRateSpm: Int?,
        paceSecPer500m: Int?,
        floors: Int?,
        steps: Int?,
        source: String,
        machineType: String? = null,
        ocrConfidence: Double? = null,
        notes: String,
    ): CardioSession {
        val now = System.currentTimeMillis()
        return store.write {
            insertCardio(
                CardioSession(
                    localUuid = UUID.randomUUID().toString(),
                    date = date,
                    activityType = activityType,
                    activityLabel = activityLabel,
                    durationSec = durationSec,
                    distanceM = distanceM,
                    calories = calories,
                    avgHeartRate = avgHeartRate,
                    maxHeartRate = maxHeartRate,
                    avgSpeed = avgSpeed,
                    avgInclinePercent = avgInclinePercent,
                    cadenceRpm = cadenceRpm,
                    resistanceLevel = resistanceLevel,
                    avgPowerWatts = avgPowerWatts,
                    strokeRateSpm = strokeRateSpm,
                    paceSecPer500m = paceSecPer500m,
                    floors = floors,
                    steps = steps,
                    source = source,
                    machineType = machineType,
                    ocrConfidence = ocrConfidence,
                    notes = notes,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }
}
