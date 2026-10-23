package com.ayman.ecolift.cardio

object CardioEntryParser {
    private const val METERS_PER_MILE = 1609.344
    private const val METERS_PER_SECOND_PER_MPH = 0.44704

    fun parseDurationSeconds(value: String): Int? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        val parts = trimmed.split(":").map { it.toIntOrNull() ?: return null }
        return when (parts.size) {
            1 -> parts[0].takeIf { it > 0 }?.let { it * 60 }
            2 -> {
                val minutes = parts[0]
                val seconds = parts[1]
                if (minutes < 0 || seconds !in 0..59) null else minutes * 60 + seconds
            }
            3 -> {
                val hours = parts[0]
                val minutes = parts[1]
                val seconds = parts[2]
                if (hours < 0 || minutes !in 0..59 || seconds !in 0..59) {
                    null
                } else {
                    hours * 3600 + minutes * 60 + seconds
                }
            }
            else -> null
        }?.takeIf { it > 0 }
    }

    fun parsePaceSeconds(value: String): Int? = parseDurationSeconds(value)

    fun parseMilesToMeters(value: String): Double? =
        value.trim().takeIf(String::isNotBlank)?.toDoubleOrNull()
            ?.takeIf { it.isFiniteValue() && it > 0.0 }
            ?.let { it * METERS_PER_MILE }

    fun parseMphToMetersPerSecond(value: String): Double? =
        value.trim().takeIf(String::isNotBlank)?.toDoubleOrNull()
            ?.takeIf { it.isFiniteValue() && it > 0.0 }
            ?.let { it * METERS_PER_SECOND_PER_MPH }

    fun parseInt(value: String): Int? =
        value.trim().takeIf(String::isNotBlank)?.toIntOrNull()?.takeIf { it > 0 }

    fun parseDouble(value: String): Double? =
        value.trim().takeIf(String::isNotBlank)?.toDoubleOrNull()?.takeIf { it.isFiniteValue() }

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
        ).any { it != null } || notes.trim().isNotEmpty()

    private fun Double.isFiniteValue(): Boolean = !isNaN() && !isInfinite()
}
