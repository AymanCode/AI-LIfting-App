package com.ayman.ecolift.agent.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentTurnLogDao {

    @Insert
    suspend fun insert(entry: AgentTurnLog): Long

    /** Latest [limit] turns, newest first — observed as a Flow for the debug UI. */
    @Query("SELECT * FROM agent_turn_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<AgentTurnLog>>

    /** One-shot fetch for tests. */
    @Query("SELECT * FROM agent_turn_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<AgentTurnLog>

    @Query("DELETE FROM agent_turn_log")
    suspend fun clearAll()
}
