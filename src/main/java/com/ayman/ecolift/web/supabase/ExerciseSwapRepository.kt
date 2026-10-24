package com.ayman.ecolift.web.supabase

import com.ayman.ecolift.data.splits.SplitAlternative
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class ExerciseSwapRequest(val requestId: String, val date: String, val sourceId: Long, val targetId: Long,
    val splitId: Long?, val expectedRevision: Long? = null)

@Serializable
data class ExerciseSwapResult(val ok: Boolean, val code: String, val requestId: String? = null,
    val sourceId: Long? = null, val targetId: Long? = null)

interface ExerciseSwapRepository {
    val alternatives: StateFlow<List<SplitAlternative>>
    suspend fun refresh()
    suspend fun swap(request: ExerciseSwapRequest): ExerciseSwapResult
    suspend fun undo(requestId: String): ExerciseSwapResult
    fun queueAlternatives(choices: List<SplitAlternative>) = Unit
    suspend fun flushAlternatives() = Unit
    suspend fun recover() = Unit
}

object NoExerciseSwapRepository : ExerciseSwapRepository {
    override val alternatives = MutableStateFlow<List<SplitAlternative>>(emptyList())
    override suspend fun refresh() = Unit
    override suspend fun swap(request: ExerciseSwapRequest) = ExerciseSwapResult(false, "DEMO")
    override suspend fun undo(requestId: String) = ExerciseSwapResult(false, "DEMO")
}

interface ExerciseSwapTransport {
    suspend fun rememberAlternative(choice: SplitAlternative) = Unit
    suspend fun listAlternatives(): List<SplitAlternative>
    suspend fun swap(request: ExerciseSwapRequest): ExerciseSwapResult
    suspend fun undo(requestId: String): ExerciseSwapResult
}

interface ExerciseSwapStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}

fun exerciseSwapError(code: String): String = when (code) {
    "OFFLINE" -> "Connect to finish the swap. Your logged sets stay saved."
    "PENDING_SYNC" -> "Your latest edits are still syncing. Try again in a moment."
    "WORKOUT_CHANGED", "EXERCISE_CHANGED" -> "This workout changed. Review the latest sets, then try again."
    "SPLIT_CHANGED" -> "This split changed. Close this sheet and choose the lift again."
    "DEMO" -> "Demo with sample data — editing is disabled."
    else -> "Couldn't finish the swap. Retry to check its status safely."
}
