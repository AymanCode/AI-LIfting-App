package com.ayman.ecolift.web.supabase

import com.ayman.ecolift.data.CardioSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CardioRepository {
    val sessions: StateFlow<List<CardioSession>>
    fun observeSessions(): Flow<List<CardioSession>>
    fun observeCaloriesForDate(date: String): Flow<Int>
    suspend fun saveManual(
        date: String,
        activityType: String,
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
    ): CardioSession
    suspend fun saveOcrConfirmed(
        date: String,
        activityType: String,
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
        notes: String = "",
    ): CardioSession
    suspend fun getById(id: Long): CardioSession?
    suspend fun update(session: CardioSession): CardioSession
    suspend fun delete(id: Long)
    suspend fun refresh()
}
