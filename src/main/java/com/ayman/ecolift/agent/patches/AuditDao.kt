package com.ayman.ecolift.agent.patches

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ayman.ecolift.agent.model.AuditEntity

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(audit: AuditEntity): Long

    @Query("SELECT * FROM audit_log WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AuditEntity?

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<AuditEntity>
}
