package com.ayman.ecolift.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

class CardioRepository(private val db: AppDatabase) {
    fun observeSessions(): Flow<List<CardioSession>> = db.cardioSessionDao().domain().observeAll()

    fun observeSessionsForDate(date: String): Flow<List<CardioSession>> = db.cardioSessionDao().domain().observeForDate(date)

    fun observeCaloriesForDate(date: String): Flow<Int> = db.cardioSessionDao().domain().observeCaloriesForDate(date)

    suspend fun getAll(): List<CardioSession> = db.cardioSessionDao().domain().getAll()

    suspend fun getForDate(date: String): List<CardioSession> = db.cardioSessionDao().domain().getForDate(date)

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
        val id = db.cardioSessionDao().domain().insert(session)
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
        val id = db.cardioSessionDao().domain().insert(session)
        return session.copy(id = id)
    }

    suspend fun upsertHealthConnect(session: CardioSession): CardioSession {
        val hcUid = session.hcUid
        if (hcUid.isNullOrBlank()) {
            val id = db.cardioSessionDao().domain().insert(session)
            return session.copy(id = id)
        }

        val existing = db.cardioSessionDao().domain().getByHealthConnectUid(hcUid)
        if (existing == null) {
            val id = db.cardioSessionDao().domain().insert(session)
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

        val edited = healthConnectEditWins(existing)
        fun <T> field(saved: T?, incoming: T?): T? = if (edited) saved ?: incoming else incoming ?: saved
        val stamp = session.hcLastModifiedTime ?: existing.hcLastModifiedTime
        val merged = existing.copy(
            date = if (edited) existing.date else session.date,
            activityType = if (edited) existing.activityType else session.activityType,
            activityLabel = field(existing.activityLabel, session.activityLabel),
            durationSec = field(existing.durationSec, session.durationSec),
            distanceM = field(existing.distanceM, session.distanceM),
            calories = field(existing.calories, session.calories),
            avgHeartRate = field(existing.avgHeartRate, session.avgHeartRate),
            maxHeartRate = field(existing.maxHeartRate, session.maxHeartRate),
            avgSpeed = field(existing.avgSpeed, session.avgSpeed),
            avgInclinePercent = field(existing.avgInclinePercent, session.avgInclinePercent),
            cadenceRpm = field(existing.cadenceRpm, session.cadenceRpm),
            resistanceLevel = field(existing.resistanceLevel, session.resistanceLevel),
            avgPowerWatts = field(existing.avgPowerWatts, session.avgPowerWatts),
            strokeRateSpm = field(existing.strokeRateSpm, session.strokeRateSpm),
            paceSecPer500m = field(existing.paceSecPer500m, session.paceSecPer500m),
            floors = field(existing.floors, session.floors),
            steps = field(existing.steps, session.steps),
            source = CardioSessionSource.HEALTH_CONNECT,
            hcDataOriginPackage = field(existing.hcDataOriginPackage, session.hcDataOriginPackage),
            hcLastModifiedTime = stamp,
            startTime = field(existing.startTime, session.startTime),
            endTime = field(existing.endTime, session.endTime),
            zoneOffsetSeconds = field(existing.zoneOffsetSeconds, session.zoneOffsetSeconds),
            notes = if (edited) {
                existing.notes.ifBlank { session.notes }
            } else if (session.notes.isNotBlank()) {
                session.notes
            } else {
                existing.notes
            },
            updatedAt = if (edited) {
                val floor = if (stamp == null) existing.updatedAt else stamp + 1
                maxOf(System.currentTimeMillis(), existing.updatedAt, floor)
            } else {
                stamp ?: existing.updatedAt
            },
        )
        db.cardioSessionDao().domain().update(merged)
        return merged
    }

    suspend fun getById(id: Long): CardioSession? = db.cardioSessionDao().domain().getById(id)

    suspend fun update(session: CardioSession): CardioSession {
        val updated = session.copy(updatedAt = System.currentTimeMillis())
        db.cardioSessionDao().domain().update(updated)
        return updated
    }

    suspend fun delete(id: Long) {
        db.cardioSessionDao().domain().deleteById(id)
    }

    private fun healthConnectEditWins(existing: CardioSession): Boolean {
        val stamp = existing.hcLastModifiedTime
        return existing.updatedAt > existing.createdAt && (stamp == null || existing.updatedAt > stamp)
    }
}
