package com.ayman.ecolift.agent.patches

import com.ayman.ecolift.agent.model.DbPatch
import java.time.LocalDate
import java.time.format.DateTimeParseException

sealed interface ValidationResult {
    data object Ok : ValidationResult
    data class Rejected(val reason: String) : ValidationResult
}

/**
 * Stateless business-rule validation for DbPatch instances.
 *
 * Schema-level type safety is handled by kotlinx.serialization.
 * This validator catches logical issues: negative weights, blank names,
 * unparseable dates, etc.
 *
 * It does NOT verify that referenced IDs exist in the database —
 * that is PatchService's responsibility at apply-time.
 */
class PatchValidator {

    fun validate(patch: DbPatch): ValidationResult = when (patch) {
        is DbPatch.LogSet -> validateLogSet(patch)
        is DbPatch.EditSet -> validateEditSet(patch)
        is DbPatch.DeleteSet -> validateDeleteSet(patch)
        is DbPatch.MoveWorkoutDay -> validateMoveWorkoutDay(patch)
        is DbPatch.RenameExercise -> validateRenameExercise(patch)
    }

    fun validateAll(patches: List<DbPatch>): ValidationResult {
        for (patch in patches) {
            val result = validate(patch)
            if (result is ValidationResult.Rejected) return result
        }
        return ValidationResult.Ok
    }

    // ── Individual validators ───────────────────────────────────────

    private fun validateLogSet(p: DbPatch.LogSet): ValidationResult {
        if (p.exerciseId <= 0) return rejected("exerciseId must be positive")
        if (!isValidDate(p.date)) return rejected("date must be valid YYYY-MM-DD, got '${p.date}'")
        if (p.setNumber <= 0) return rejected("setNumber must be positive")
        if (p.reps <= 0) return rejected("reps must be positive")
        if (!p.isBodyweight && p.weightLbs != null && p.weightLbs <= 0) {
            return rejected("weightLbs must be positive when provided")
        }
        if (p.isBodyweight && p.weightLbs != null && p.weightLbs != 0) {
            return rejected("weightLbs should be null or 0 for bodyweight exercises")
        }
        if (p.restTimeSeconds != null && p.restTimeSeconds < 0) {
            return rejected("restTimeSeconds cannot be negative")
        }
        return ValidationResult.Ok
    }

    private fun validateEditSet(p: DbPatch.EditSet): ValidationResult {
        if (p.setId <= 0) return rejected("setId must be positive")
        if (p.weightLbs != null && p.weightLbs <= 0) return rejected("weightLbs must be positive")
        if (p.reps != null && p.reps <= 0) return rejected("reps must be positive")
        if (p.restTimeSeconds != null && p.restTimeSeconds < 0) {
            return rejected("restTimeSeconds cannot be negative")
        }
        if (p.weightLbs == null && p.reps == null && p.restTimeSeconds == null) {
            return rejected("EditSet must change at least one field")
        }
        return ValidationResult.Ok
    }

    private fun validateDeleteSet(p: DbPatch.DeleteSet): ValidationResult {
        if (p.setId <= 0) return rejected("setId must be positive")
        return ValidationResult.Ok
    }

    private fun validateMoveWorkoutDay(p: DbPatch.MoveWorkoutDay): ValidationResult {
        if (!isValidDate(p.currentDate)) return rejected("currentDate must be valid YYYY-MM-DD")
        if (!isValidDate(p.newDate)) return rejected("newDate must be valid YYYY-MM-DD")
        if (p.currentDate == p.newDate) return rejected("newDate must differ from currentDate")
        return ValidationResult.Ok
    }

    private fun validateRenameExercise(p: DbPatch.RenameExercise): ValidationResult {
        if (p.exerciseId <= 0) return rejected("exerciseId must be positive")
        if (p.newName.isBlank()) return rejected("newName must not be blank")
        if (p.newName.length > 100) return rejected("newName exceeds 100 characters")
        return ValidationResult.Ok
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun isValidDate(date: String): Boolean = try {
        LocalDate.parse(date)
        true
    } catch (_: DateTimeParseException) {
        false
    }

    private fun rejected(reason: String) = ValidationResult.Rejected(reason)
}
