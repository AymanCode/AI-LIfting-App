package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun get(): UserSettings?

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun observe(): Flow<UserSettings?>

    @Upsert
    suspend fun upsert(settings: UserSettings)

    @Query("DELETE FROM user_settings")
    suspend fun deleteAll()
}
