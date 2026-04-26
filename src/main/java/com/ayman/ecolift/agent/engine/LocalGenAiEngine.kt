package com.ayman.ecolift.agent.engine

import kotlinx.coroutines.flow.Flow

/**
 * Stable interface over local on-device LLM inference.
 *
 * The agent layer depends only on this interface, so model backends can be
 * swapped without changing routing or orchestration code.
 */
interface LocalGenAiEngine : AutoCloseable {

    /** True after [warmup] completes successfully. */
    val isReady: Boolean

    /**
     * Load the model weights into memory.
     * Call from a background coroutine on app start, not on first user interaction.
     * Safe to call multiple times; subsequent calls are no-ops if already ready.
     */
    suspend fun warmup()

    /**
     * Stream text tokens as they are generated.
     * MediaPipe implementation emits the full response as a single item (no true streaming).
     * Other implementations may emit individual tokens.
     */
    fun streamText(prompt: String): Flow<String>

    /**
     * Generate and return structured JSON matching [schema].
     * The schema is appended to the prompt as a hint. The model is expected to
     * return valid JSON. Validation of the output is the caller's responsibility.
     */
    suspend fun generateStructured(prompt: String, schema: String): String
}
