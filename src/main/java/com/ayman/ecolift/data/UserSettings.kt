package com.ayman.ecolift.data

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
)
