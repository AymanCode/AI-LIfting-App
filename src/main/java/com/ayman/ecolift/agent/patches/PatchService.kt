package com.ayman.ecolift.agent.patches

import com.ayman.ecolift.web.supabase.sync.orThrow
import com.ayman.ecolift.agent.model.AuditEntity
import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.model.InMemoryAuditStore
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.normalizedBodyweightLoad
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.supabase.ExerciseRenameResult
import com.ayman.ecolift.web.util.nowMillis
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface PatchResult {
    data class Applied(val auditId: Long, val patchCount: Int) : PatchResult
    data class Rejected(val reason: String) : PatchResult
    data class Failed(val error: String) : PatchResult
}

/** Testable interface over patch application and undo. */
interface PatchApplier {
    suspend fun applyPatches(requestId: String, patches: List<DbPatch>, userConfirmed: Boolean): PatchResult
    suspend fun undo(auditId: Long): PatchResult
}

/**
 * Single entry point for all agent-proposed mutations.
 *
 * Flow: validate -> (read pre-state -> compute inverse -> apply) per patch
 *       -> write audit -> commit. Sequential writes; no DB transaction needed
 *       for single-user browser context.
 */
class PatchService(
    private val repos: AppRepos,
    private val validator: PatchValidator,
    private val auditStore: InMemoryAuditStore
) : PatchApplier {
    private val json = Json { prettyPrint = false }
    private val inverseComputer by lazy {
        InverseComputer(repos.sets, repos.exercises)
    }

    override suspend fun applyPatches(
        requestId: String,
        patches: List<DbPatch>,
        userConfirmed: Boolean
    ): PatchResult {
        // 1. Validate all patches up front - no DB access yet
        val validation = validator.validateAll(patches)
        if (validation is ValidationResult.Rejected) {
            return PatchResult.Rejected(validation.reason)
        }

        // 2. Gate destructive ops on user confirmation
        if (!userConfirmed && patches.any { DbPatch.isDestructive(it) }) {
            return PatchResult.Rejected("Destructive patches require user confirmation")
        }

        // 3. Apply sequentially (single-user, no real transaction needed for web v1)
        return try {
            val inversePatches = mutableListOf<DbPatch>()

            for (patch in patches) {
                if (patch is DbPatch.LogSet) {
                    // LogSet: apply first to get inserted ID, then build inverse
                    val insertedId = applyPatch(patch)
                    inversePatches.add(inverseComputer.computeInverse(patch, insertedId))
                } else {
                    // All others: read pre-state for inverse BEFORE applying
                    val inverse = inverseComputer.computeInverse(patch)
                    applyPatch(patch)
                    inversePatches.add(inverse)
                }
            }

            val audit = AuditEntity(
                requestId = requestId,
                timestamp = nowMillis(),
                serializedPatches = json.encodeToString(patches),
                serializedInverse = json.encodeToString(inversePatches),
                userConfirmed = userConfirmed
            )
            val auditId = auditStore.insert(audit)
            PatchResult.Applied(auditId = auditId, patchCount = patches.size)
        } catch (e: Exception) {
            PatchResult.Failed(e.message ?: "Unknown error during patch application")
        }
    }

    /** Undo a previously applied set of patches by applying the stored inverse. */
    override suspend fun undo(auditId: Long): PatchResult {
        val audit = auditStore.getById(auditId)
            ?: return PatchResult.Failed("Audit entry $auditId not found")

        val inversePatches = json.decodeFromString<List<DbPatch>>(audit.serializedInverse)
        return applyPatches(
            requestId = "undo-${audit.requestId}",
            patches = inversePatches,
            userConfirmed = true  // undo is always user-initiated
        )
    }

    // Per-patch application

    /** Applies a single patch. Returns inserted row ID for LogSet, null otherwise. */
    private suspend fun applyPatch(patch: DbPatch): Long? {
        return when (patch) {
            is DbPatch.LogSet -> {
                val canonicalExerciseId = repos.exercises.canonicalId(patch.exerciseId)
                val inserted = repos.sets.insert(
                    WorkoutSet(
                        exerciseId = canonicalExerciseId,
                        date = patch.date,
                        setNumber = patch.setNumber,
                        weightLbs = if (patch.isBodyweight) normalizedBodyweightLoad(patch.weightLbs) else patch.weightLbs,
                        reps = patch.reps,
                        isBodyweight = patch.isBodyweight,
                        restTimeSeconds = patch.restTimeSeconds,
                        completed = patch.completed,
                    )
                ).orThrow()
                inserted.id
            }

            is DbPatch.EditSet -> {
                val current = repos.sets.getById(patch.setId)
                    ?: error("WorkoutSet ${patch.setId} not found")
                repos.sets.update(
                    current.copy(
                        weightLbs = patch.weightLbs ?: current.weightLbs,
                        reps = patch.reps ?: current.reps,
                        restTimeSeconds = patch.restTimeSeconds ?: current.restTimeSeconds
                    )
                ).orThrow()
                null
            }

            is DbPatch.DeleteSet -> {
                repos.sets.delete(patch.setId)
                null
            }

            is DbPatch.MoveWorkoutDay -> {
                repos.workout.moveWorkoutDay(patch.currentDate, patch.newDate)
                repos.sets.updateDate(patch.currentDate, patch.newDate)
                null
            }

            is DbPatch.RenameExercise -> {
                repos.exercises.getById(patch.exerciseId)
                    ?: error("Exercise ${patch.exerciseId} not found")
                when (val rename = repos.exercises.updateName(patch.exerciseId, patch.newName)) {
                    is ExerciseRenameResult.Success -> Unit
                    is ExerciseRenameResult.NameCollision -> error(
                        "Exercise name collides with ${rename.existing.name}; confirmation is required.",
                    )
                }
                null
            }
        }
    }
}

/** Test runner - executes block directly, no transaction wrapper needed for mock stores. */
fun noOpTransactionRunner(): suspend (() -> Unit) -> Unit = { block -> block() }
