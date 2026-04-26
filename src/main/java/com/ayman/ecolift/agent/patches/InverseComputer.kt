package com.ayman.ecolift.agent.patches

import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.data.ExerciseDao
import com.ayman.ecolift.data.WorkoutSetDao

/**
 * Computes the inverse patch for each DbPatch type.
 * Must be called BEFORE applying the forward patch so that
 * pre-mutation state can be captured.
 */
class InverseComputer(
    private val workoutSetDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao
) {
    /**
     * Returns the inverse of [patch]. For patches that need current DB state
     * (EditSet, DeleteSet, RenameExercise), this reads the row first.
     *
     * @param insertedId For LogSet only - the ID of the row that was just inserted.
     *   Pass null for pre-read patches (the read happens inside this method).
     */
    suspend fun computeInverse(patch: DbPatch, insertedId: Long? = null): DbPatch = when (patch) {
        is DbPatch.LogSet -> {
            // Inverse of insert = delete the new row
            DbPatch.DeleteSet(setId = requireNotNull(insertedId) {
                "insertedId required for LogSet inverse"
            })
        }

        is DbPatch.EditSet -> {
            // Read current values before overwrite
            val current = workoutSetDao.getById(patch.setId)
                ?: error("WorkoutSet ${patch.setId} not found for inverse computation")
            DbPatch.EditSet(
                setId = patch.setId,
                weightLbs = if (patch.weightLbs != null) current.weightLbs else null,
                reps = if (patch.reps != null) current.reps else null,
                restTimeSeconds = if (patch.restTimeSeconds != null) current.restTimeSeconds else null
            )
        }

        is DbPatch.DeleteSet -> {
            // Read full row before delete
            val current = workoutSetDao.getById(patch.setId)
                ?: error("WorkoutSet ${patch.setId} not found for inverse computation")
            DbPatch.LogSet(
                exerciseId = current.exerciseId,
                date = current.date,
                setNumber = current.setNumber,
                weightLbs = current.weightLbs,
                reps = current.reps ?: 0,
                isBodyweight = current.isBodyweight,
                restTimeSeconds = current.restTimeSeconds
            )
        }

        is DbPatch.MoveWorkoutDay -> {
            // Symmetric: inverse just swaps the dates back
            DbPatch.MoveWorkoutDay(
                currentDate = patch.newDate,
                newDate = patch.currentDate
            )
        }

        is DbPatch.RenameExercise -> {
            // Read current name before rename
            val current = exerciseDao.getById(patch.exerciseId)
                ?: error("Exercise ${patch.exerciseId} not found for inverse computation")
            DbPatch.RenameExercise(
                exerciseId = patch.exerciseId,
                newName = current.name
            )
        }
    }
}
