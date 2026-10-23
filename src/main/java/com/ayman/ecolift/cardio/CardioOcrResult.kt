package com.ayman.ecolift.cardio

data class CardioOcrResult(
    val recognizedCardioScreen: Boolean,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val calories: Int? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val avgSpeed: Double? = null,
    val avgInclinePercent: Double? = null,
    val cadenceRpm: Int? = null,
    val resistanceLevel: Double? = null,
    val avgPowerWatts: Int? = null,
    val strokeRateSpm: Int? = null,
    val paceSecPer500m: Int? = null,
    val floors: Int? = null,
    val steps: Int? = null,
    val machineType: String? = null,
    val activityType: String? = null,
    val confidence: Double = 0.0,
)
