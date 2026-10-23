package com.ayman.ecolift.data

import com.ayman.ecolift.data.room.UserSettings as RoomUserSettings

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun get(): RoomUserSettings?

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun observe(): Flow<RoomUserSettings?>

    @Upsert
    suspend fun upsert(settings: RoomUserSettings)

    @Query("DELETE FROM user_settings")
    suspend fun deleteAll()
}
