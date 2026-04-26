package com.ayman.ecolift.agent.patches

import androidx.room.withTransaction
import com.ayman.ecolift.agent.model.AuditEntity
import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.ExerciseDao
import com.ayman.ecolift.data.WorkoutDay
import com.ayman.ecolift.data.WorkoutDayDao
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.WorkoutSetDao
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
 * Flow: validate -> open transaction -> (read pre-state -> compute inverse -> apply) per patch
 *       -> write audit -> commit. On any exception the transaction rolls back automatically.
 *
 * [transactionRunner] is injectable for testing - production code passes [roomTransactionRunner].
 */
class PatchService(
    private val db: AppDatabase,
    private val validator: PatchValidator,
    private val transactionRunner: TransactionRunner = roomTransactionRunner(db)
) : PatchApplier {
    private val json = Json { prettyPrint = false }
    private val inverseComputer by lazy {
        InverseComputer(db.workoutSetDao(), db.exerciseDao())
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

        // 3. Apply inside a single transaction
        return try {
            transactionRunner.run {
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
                    timestamp = System.currentTimeMillis(),
                    serializedPatches = json.encodeToString(patches),
                    serializedInverse = json.encodeToString(inversePatches),
                    userConfirmed = userConfirmed
                )
                val auditId = db.auditDao().insert(audit)
                PatchResult.Applied(auditId = auditId, patchCount = patches.size)
            }
        } catch (e: Exception) {
            PatchResult.Failed(e.message ?: "Unknown error during patch application")
        }
    }

    /** Undo a previously applied set of patches by applying the stored inverse. */
    override suspend fun undo(auditId: Long): PatchResult {
        val audit = db.auditDao().getById(auditId)
            ?: return PatchResult.Failed("Audit entry $auditId not found")

        val inversePatches = json.decodeFromString<List<DbPatch>>(audit.serializedInverse)
        return applyPatches(
            requestId = "undo-${audit.requestId}",
            patches = inversePatches,
            userConfirmed = true  // undo is always user-initiated
        )
    }

    // Per-patch application

    /** Applies a single patch via the appropriate DAO. Returns inserted row ID for LogSet, null otherwise. */
    private suspend fun applyPatch(patch: DbPatch): Long? {
        val setDao: WorkoutSetDao = db.workoutSetDao()
        val dayDao: WorkoutDayDao = db.workoutDayDao()
        val exerciseDao: ExerciseDao = db.exerciseDao()

        return when (patch) {
            is DbPatch.LogSet -> {
                setDao.insert(
                    WorkoutSet(
                        exerciseId = patch.exerciseId,
                        date = patch.date,
                        setNumber = patch.setNumber,
                        weightLbs = patch.weightLbs,
                        reps = patch.reps,
                        isBodyweight = patch.isBodyweight,
                        restTimeSeconds = patch.restTimeSeconds
                    )
                )
            }

            is DbPatch.EditSet -> {
                val current = setDao.getById(patch.setId)
                    ?: error("WorkoutSet ${patch.setId} not found")
                setDao.update(
                    current.copy(
                        weightLbs = patch.weightLbs ?: current.weightLbs,
                        reps = patch.reps ?: current.reps,
                        restTimeSeconds = patch.restTimeSeconds ?: current.restTimeSeconds
                    )
                )
                null
            }

            is DbPatch.DeleteSet -> {
                setDao.deleteById(patch.setId)
                null
            }

            is DbPatch.MoveWorkoutDay -> {
                val currentDay = dayDao.getByDate(patch.currentDate)
                    ?: error("WorkoutDay ${patch.currentDate} not found")
                dayDao.upsert(currentDay.copy(date = patch.newDate))
                dayDao.deleteByDate(patch.currentDate)
                setDao.updateDate(patch.currentDate, patch.newDate)
                null
            }

            is DbPatch.RenameExercise -> {
                exerciseDao.getById(patch.exerciseId)
                    ?: error("Exercise ${patch.exerciseId} not found")
                exerciseDao.updateName(patch.exerciseId, patch.newName)
                null
            }
        }
    }
}

// TransactionRunner

/** Abstraction over Room's withTransaction so PatchService is testable without a real DB. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

/** Production runner - delegates to Room's withTransaction for full rollback-on-exception. */
fun roomTransactionRunner(db: AppDatabase): TransactionRunner = object : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}

/** Test runner - executes block directly, no transaction wrapper needed for mock DBs. */
fun noOpTransactionRunner(): TransactionRunner = object : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}
