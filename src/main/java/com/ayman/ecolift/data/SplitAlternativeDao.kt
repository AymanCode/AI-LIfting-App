package com.ayman.ecolift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ayman.ecolift.data.room.SplitAlternativeRow

@Dao
interface SplitAlternativeDao {
    @Query("SELECT * FROM split_alternative ORDER BY createdAt, sourceId, alternativeId")
    suspend fun getAll(): List<SplitAlternativeRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: SplitAlternativeRow): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<SplitAlternativeRow>)
}
