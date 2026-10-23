package com.ayman.ecolift.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

const val USER_SETTINGS_ID = 1

@Entity(tableName = "user_settings")
@Serializable
data class UserSettings(
    @PrimaryKey val id: Int = USER_SETTINGS_ID,
    @ColumnInfo(name = "user_bodyweight_lbs") val userBodyweightLbs: Int? = null,
    @ColumnInfo(name = "glass_palette_choice") val glassPaletteChoice: String? = null,
    @ColumnInfo(name = "time_zone", defaultValue = "'UTC'") val timeZone: String = "UTC",
    @ColumnInfo(name = "unit_system", defaultValue = "'LB'") val unitSystem: String = "LB",
    @ColumnInfo(name = "smart_suggest", defaultValue = "1") val smartSuggest: Boolean = true,
)
