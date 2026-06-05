package com.ayman.ecolift.cardio.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class HealthConnectDailyCalories(
    val available: Boolean,
    val permissionGranted: Boolean,
    val calories: Int? = null,
    val message: String? = null,
)

class CardioHealthConnectReader(private val context: Context) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(): Boolean {
        if (!isAvailable()) return false
        return client().permissionController.getGrantedPermissions().containsAll(permissions)
    }

    suspend fun readTodayCalories(date: LocalDate = LocalDate.now()): HealthConnectDailyCalories {
        if (!isAvailable()) {
            return HealthConnectDailyCalories(
                available = false,
                permissionGranted = false,
                message = "Health Connect is unavailable on this device.",
            )
        }
        if (!hasPermissions()) {
            return HealthConnectDailyCalories(
                available = true,
                permissionGranted = false,
                message = "Health Connect permission is needed.",
            )
        }

        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return runCatching {
            val aggregate = client().aggregate(
                AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            )
            HealthConnectDailyCalories(
                available = true,
                permissionGranted = true,
                calories = aggregate[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?.roundToInt(),
            )
        }.getOrElse { error ->
            HealthConnectDailyCalories(
                available = true,
                permissionGranted = true,
                message = error.message ?: "Health Connect read failed.",
            )
        }
    }
}
