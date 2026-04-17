package com.ayman.ecolift.agent.model

import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy of all mutations the agent can propose.
 * No mutation reaches Room except through PatchService.applyPatches.
 */
@Serializable
sealed interface DbPatch {

    /** Log a new set for an exercise on a given date. */
    @Serializable
    data class LogSet(
        val exerciseId: Long,
        val date: String,           // "YYYY-MM-DD"
        val setNumber: Int,
        val weightLbs: Int?,        // null for bodyweight
        val reps: Int,
        val isBodyweight: Boolean = false,
        val restTimeSeconds: Int? = null
    ) : DbPatch

    /** Edit fields on an existing set. Only non-null fields are updated. */
    @Serializable
    data class EditSet(
        val setId: Long,
        val weightLbs: Int? = null,
        val reps: Int? = null,
        val restTimeSeconds: Int? = null
    ) : DbPatch

    /** Delete a set by ID. Destructive — requires user confirmation. */
    @Serializable
    data class DeleteSet(val setId: Long) : DbPatch

    /** Move a workout day to a new date. */
    @Serializable
    data class MoveWorkoutDay(
        val currentDate: String,    // existing WorkoutDay.date PK
        val newDate: String         // "YYYY-MM-DD"
    ) : DbPatch

    /** Rename an exercise. Destructive — requires user confirmation. */
    @Serializable
    data class RenameExercise(
        val exerciseId: Long,
        val newName: String
    ) : DbPatch

    companion object {
        /** Patches that mutate or destroy data in a way that is hard to undo from the user's perspective. */
        fun isDestructive(patch: DbPatch): Boolean = when (patch) {
            is DeleteSet -> true
            is RenameExercise -> true
            is LogSet, is EditSet, is MoveWorkoutDay -> false
        }
    }
}
