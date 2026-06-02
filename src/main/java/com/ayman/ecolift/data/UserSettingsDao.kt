package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun get(): UserSettings?

    @Upsert
    suspend fun upsert(settings: UserSettings)

    @Query("DELETE FROM user_settings")
    suspend fun deleteAll()
}
