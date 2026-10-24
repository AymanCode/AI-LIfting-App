package com.ayman.ecolift.web.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class VisionStats(
    @SerialName("activity_type") val activityType: String? = null,
    @SerialName("duration_sec") val durationSec: Int? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    val calories: Int? = null,
    @SerialName("avg_heart_rate") val avgHeartRate: Int? = null,
    @SerialName("max_heart_rate") val maxHeartRate: Int? = null,
    @SerialName("avg_speed") val avgSpeed: Double? = null,
    @SerialName("avg_incline_percent") val avgInclinePercent: Double? = null,
    @SerialName("cadence_rpm") val cadenceRpm: Int? = null,
    @SerialName("resistance_level") val resistanceLevel: Double? = null,
    @SerialName("avg_power_watts") val avgPowerWatts: Int? = null,
    @SerialName("stroke_rate_spm") val strokeRateSpm: Int? = null,
    @SerialName("pace_sec_per_500m") val paceSecPer500m: Int? = null,
    val floors: Int? = null,
    val steps: Int? = null,
    @SerialName("machine_type") val machineType: String? = null,
    val confidence: Double? = null,
)

/** Parsers only. The Supabase groq-chat and groq-vision calls stay on the web client. */
object EdgeAi {
    private val lenientJson = Json { ignoreUnknownKeys = true }

    fun parseChatText(body: String): String? = runCatching {
        lenientJson.parseToJsonElement(body).jsonObject["text"]?.jsonPrimitive?.content
    }.getOrNull()

    fun parseVisionStats(body: String): VisionStats? = runCatching {
        val stats = lenientJson.parseToJsonElement(body).jsonObject["stats"] as? JsonObject ?: return null
        lenientJson.decodeFromJsonElement(VisionStats.serializer(), stats)
    }.getOrNull()
}
