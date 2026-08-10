package com.ayman.ecolift.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

class CardioRepository(private val db: AppDatabase) {
    fun observeSessions(): Flow<List<CardioSession>> = db.cardioSessionDao().observeAll()

    fun observeSessionsForDate(date: String): Flow<List<CardioSession>> = db.cardioSessionDao().observeForDate(date)

    fun observeCaloriesForDate(date: String): Flow<Int> = db.cardioSessionDao().observeCaloriesForDate(date)

    suspend fun getAll(): List<CardioSession> = db.cardioSessionDao().getAll()

    suspend fun getForDate(date: String): List<CardioSession> = db.cardioSessionDao().getForDate(date)

    suspend fun saveManual(
        date: String = LocalDate.now().toString(),
        activityType: CardioActivityType,
        activityLabel: String? = null,
        durationSec: Int? = null,
        distanceM: Double? = null,
        calories: Int? = null,
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null,
        avgSpeed: Double? = null,
        avgInclinePercent: Double? = null,
        cadenceRpm: Int? = null,
        resistanceLevel: Double? = null,
        avgPowerWatts: Int? = null,
        strokeRateSpm: Int? = null,
        paceSecPer500m: Int? = null,
        floors: Int? = null,
        steps: Int? = null,
        notes: String = "",
    ): CardioSession {
        val now = System.currentTimeMillis()
        val session = CardioSession(
            localUuid = UUID.randomUUID().toString(),
            date = date,
            activityType = activityType.name,
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
            createdAt = now,
            updatedAt = now,
        )
        val id = db.cardioSessionDao().insert(session)
        return session.copy(id = id)
    }

    suspend fun saveOcrConfirmed(
        date: String = LocalDate.now().toString(),
        activityType: CardioActivityType,
        activityLabel: String? = null,
        durationSec: Int? = null,
        distanceM: Double? = null,
        calories: Int? = null,
        avgHeartRate: Int? = null,
        maxHeartRate: Int? = null,
        avgSpeed: Double? = null,
        avgInclinePercent: Double? = null,
        cadenceRpm: Int? = null,
        resistanceLevel: Double? = null,
        avgPowerWatts: Int? = null,
        strokeRateSpm: Int? = null,
        paceSecPer500m: Int? = null,
        floors: Int? = null,
        steps: Int? = null,
        machineType: String? = null,
        ocrConfidence: Double? = null,
        ocrEngineVersion: String? = null,
        notes: String = "",
    ): CardioSession {
        val now = System.currentTimeMillis()
        val session = CardioSession(
            localUuid = UUID.randomUUID().toString(),
            date = date,
            activityType = activityType.name,
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
            ocrEngineVersion = ocrEngineVersion,
            notes = notes,
            createdAt = now,
            updatedAt = now,
        )
        val id = db.cardioSessionDao().insert(session)
        return session.copy(id = id)
    }

    suspend fun upsertHealthConnect(session: CardioSession): CardioSession {
        val hcUid = session.hcUid
        if (hcUid.isNullOrBlank()) {
            val id = db.cardioSessionDao().insert(session)
            return session.copy(id = id)
        }

        val existing = db.cardioSessionDao().getByHealthConnectUid(hcUid)
        if (existing == null) {
            val id = db.cardioSessionDao().insert(session)
            return session.copy(id = id)
        }

        val incomingModified = session.hcLastModifiedTime ?: Long.MIN_VALUE
        val existingModified = existing.hcLastModifiedTime ?: Long.MIN_VALUE
        val fillsMissingValues = (existing.calories == null && session.calories != null) ||
            (existing.distanceM == null && session.distanceM != null) ||
            (existing.avgHeartRate == null && session.avgHeartRate != null) ||
            (existing.maxHeartRate == null && session.maxHeartRate != null) ||
            (existing.durationSec == null && session.durationSec != null) ||
            (existing.avgSpeed == null && session.avgSpeed != null) ||
            (existing.avgInclinePercent == null && session.avgInclinePercent != null) ||
            (existing.cadenceRpm == null && session.cadenceRpm != null) ||
            (existing.resistanceLevel == null && session.resistanceLevel != null) ||
            (existing.avgPowerWatts == null && session.avgPowerWatts != null) ||
            (existing.strokeRateSpm == null && session.strokeRateSpm != null) ||
            (existing.paceSecPer500m == null && session.paceSecPer500m != null) ||
            (existing.floors == null && session.floors != null) ||
            (existing.steps == null && session.steps != null)

        if (incomingModified <= existingModified && !fillsMissingValues) {
            return existing
        }

        val merged = existing.copy(
            date = session.date,
            activityType = session.activityType,
            activityLabel = session.activityLabel ?: existing.activityLabel,
            durationSec = session.durationSec ?: existing.durationSec,
            distanceM = session.distanceM ?: existing.distanceM,
            calories = session.calories ?: existing.calories,
            avgHeartRate = session.avgHeartRate ?: existing.avgHeartRate,
            maxHeartRate = session.maxHeartRate ?: existing.maxHeartRate,
            avgSpeed = session.avgSpeed ?: existing.avgSpeed,
            avgInclinePercent = session.avgInclinePercent ?: existing.avgInclinePercent,
            cadenceRpm = session.cadenceRpm ?: existing.cadenceRpm,
            resistanceLevel = session.resistanceLevel ?: existing.resistanceLevel,
            avgPowerWatts = session.avgPowerWatts ?: existing.avgPowerWatts,
            strokeRateSpm = session.strokeRateSpm ?: existing.strokeRateSpm,
            paceSecPer500m = session.paceSecPer500m ?: existing.paceSecPer500m,
            floors = session.floors ?: existing.floors,
            steps = session.steps ?: existing.steps,
            source = CardioSessionSource.HEALTH_CONNECT,
            hcDataOriginPackage = session.hcDataOriginPackage ?: existing.hcDataOriginPackage,
            hcLastModifiedTime = session.hcLastModifiedTime ?: existing.hcLastModifiedTime,
            startTime = session.startTime ?: existing.startTime,
            endTime = session.endTime ?: existing.endTime,
            zoneOffsetSeconds = session.zoneOffsetSeconds ?: existing.zoneOffsetSeconds,
            notes = if (session.notes.isNotBlank()) session.notes else existing.notes,
            updatedAt = System.currentTimeMillis(),
        )
        db.cardioSessionDao().update(merged)
        return merged
    }

    suspend fun getById(id: Long): CardioSession? = db.cardioSessionDao().getById(id)

    suspend fun update(session: CardioSession): CardioSession {
        val updated = session.copy(updatedAt = System.currentTimeMillis())
        db.cardioSessionDao().update(updated)
        return updated
    }

    suspend fun delete(id: Long) {
        db.cardioSessionDao().deleteById(id)
    }
}
