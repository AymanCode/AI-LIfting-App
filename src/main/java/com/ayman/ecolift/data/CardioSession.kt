package com.ayman.ecolift.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "cardio_sessions",
    indices = [
        Index(value = ["local_uuid"], unique = true),
        Index(value = ["hc_uid"], unique = true),
        Index(value = ["date"]),
        Index(value = ["start_time"]),
    ],
)
@Serializable
data class CardioSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "local_uuid") val localUuid: String,
    val date: String,
    @ColumnInfo(name = "activity_type") val activityType: String,
    @ColumnInfo(name = "activity_label") val activityLabel: String? = null,
    @ColumnInfo(name = "duration_sec") val durationSec: Int? = null,
    @ColumnInfo(name = "distance_m") val distanceM: Double? = null,
    val calories: Int? = null,
    @ColumnInfo(name = "avg_heart_rate") val avgHeartRate: Int? = null,
    @ColumnInfo(name = "max_heart_rate") val maxHeartRate: Int? = null,
    @ColumnInfo(name = "avg_speed") val avgSpeed: Double? = null,
    val source: String,
    @ColumnInfo(name = "hc_uid") val hcUid: String? = null,
    @ColumnInfo(name = "hc_data_origin_package") val hcDataOriginPackage: String? = null,
    @ColumnInfo(name = "hc_last_modified_time") val hcLastModifiedTime: Long? = null,
    @ColumnInfo(name = "start_time") val startTime: Long? = null,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "zone_offset_seconds") val zoneOffsetSeconds: Int? = null,
    @ColumnInfo(name = "machine_type") val machineType: String? = null,
    @ColumnInfo(name = "ocr_confidence") val ocrConfidence: Double? = null,
    @ColumnInfo(name = "ocr_engine_version") val ocrEngineVersion: String? = null,
    val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

enum class CardioActivityType {
    RUN,
    BIKE,
    ROW,
    SWIM,
    WALK,
    ELLIPTICAL,
    STAIR_CLIMBER,
    OTHER,
}

object CardioSessionSource {
    const val MANUAL = "manual"
    const val OCR = "ocr"
    const val HEALTH_CONNECT = "health_connect"
}
