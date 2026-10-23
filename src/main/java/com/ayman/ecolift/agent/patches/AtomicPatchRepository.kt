package com.ayman.ecolift.agent.patches

import com.ayman.ecolift.web.supabase.ExerciseRepository

interface AtomicPatchRepository {
    suspend fun applyAtomically(exercises: ExerciseRepository, prepare: suspend () -> (() -> Unit))
    suspend fun deleteWithUndo(setId: Long): PatchUndo
}

fun interface PatchUndo {
    suspend fun restore(): PatchUndo
}
