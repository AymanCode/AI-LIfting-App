package com.ayman.ecolift.cardio

object CardioEntryParser {
    private const val METERS_PER_MILE = 1609.344
    private const val METERS_PER_SECOND_PER_MPH = 0.44704

    fun parseDurationSeconds(value: String): Int? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val parts = trimmed.split(":").map { it.toIntOrNull() ?: return null }
        return when (parts.size) {
            1 -> parts[0] * 60
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    fun parsePaceSeconds(value: String): Int? = parseDurationSeconds(value)

    fun parseMilesToMeters(value: String): Double? =
        value.trim().takeIf(String::isNotBlank)?.toDoubleOrNull()?.let { it * METERS_PER_MILE }

    fun parseMphToMetersPerSecond(value: String): Double? =
        value.trim().takeIf(String::isNotBlank)?.toDoubleOrNull()?.let { it * METERS_PER_SECOND_PER_MPH }

    fun parseInt(value: String): Int? =
        value.trim().takeIf(String::isNotBlank)?.toIntOrNull()

    fun parseDouble(value: String): Double? =
        value.trim().takeIf(String::isNotBlank)?.toDoubleOrNull()

    fun hasSaveableMetric(
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
    ): Boolean =
        listOf(
            durationSec,
            distanceM,
            calories,
            avgHeartRate,
            maxHeartRate,
            avgSpeed,
            avgInclinePercent,
            cadenceRpm,
            resistanceLevel,
            avgPowerWatts,
            strokeRateSpm,
            paceSecPer500m,
            floors,
            steps,
        ).any { it != null } ||
        notes.trim().isNotEmpty()
}
