package com.ayman.ecolift.agent.model

class InMemoryAuditStore {
    private val entries = mutableListOf<AuditEntity>()
    private var nextId = 1L

    @Synchronized
    fun insert(entity: AuditEntity): Long {
        val id = nextId++
        entries += entity.copy(id = id)
        return id
    }

    @Synchronized
    fun getById(id: Long): AuditEntity? = entries.firstOrNull { it.id == id }

    @Synchronized
    fun getRecent(limit: Int): List<AuditEntity> = entries.takeLast(limit).reversed()

    @Synchronized
    fun all(): List<AuditEntity> = entries.toList()
}

class InMemoryTurnLogStore {
    private val turns = mutableListOf<AgentTurnLog>()
    private var nextId = 1L

    fun insert(log: AgentTurnLog): Long {
        val id = nextId++
        turns += log.copy(id = id)
        return id
    }

    fun latest(limit: Int): List<AgentTurnLog> = turns.takeLast(limit).reversed()

    fun getAll(): List<AgentTurnLog> = turns.toList()
}
