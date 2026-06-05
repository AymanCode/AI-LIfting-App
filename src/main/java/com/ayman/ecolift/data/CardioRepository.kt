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
            (existing.durationSec == null && session.durationSec != null)

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

    suspend fun delete(id: Long) {
        db.cardioSessionDao().deleteById(id)
    }
}
