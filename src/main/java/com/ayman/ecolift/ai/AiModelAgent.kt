package com.ayman.ecolift.ai

import android.net.Uri

interface AiModelAgent : AutoCloseable {
    fun getStatus(): AiModelStatus

    suspend fun respond(
        userMessage: String,
        history: List<AiConversationTurn>,
        runtimeContext: AiRuntimeContext,
        imageUri: Uri? = null,
    ): Result<AiModelOutput>

    override fun close() = Unit
}
