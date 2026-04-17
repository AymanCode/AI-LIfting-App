package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.model.DbPatch
import com.ayman.ecolift.agent.patches.PatchValidator
import com.ayman.ecolift.agent.patches.ValidationResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PatchValidatorTest {

    private lateinit var validator: PatchValidator

    @Before
    fun setUp() {
        validator = PatchValidator()
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun assertOk(result: ValidationResult) {
        assertTrue("Expected Ok but got $result", result is ValidationResult.Ok)
    }

    private fun assertRejected(result: ValidationResult, substring: String = "") {
        assertTrue("Expected Rejected but got $result", result is ValidationResult.Rejected)
        if (substring.isNotEmpty()) {
            val reason = (result as ValidationResult.Rejected).reason
            assertTrue("Expected reason containing '$substring' but got '$reason'", reason.contains(substring))
        }
    }

    // ── LogSet ──────────────────────────────────────────────────────

    @Test
    fun `LogSet valid weighted`() {
        assertOk(validator.validate(DbPatch.LogSet(
            exerciseId = 1, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 8
        )))
    }

    @Test
    fun `LogSet valid bodyweight`() {
        assertOk(validator.validate(DbPatch.LogSet(
            exerciseId = 1, date = "2026-04-16", setNumber = 1,
            weightLbs = null, reps = 12, isBodyweight = true
        )))
    }

    @Test
    fun `LogSet rejects zero exerciseId`() {
        assertRejected(validator.validate(DbPatch.LogSet(
            exerciseId = 0, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 8
        )), "exerciseId")
    }

    @Test
    fun `LogSet rejects negative exerciseId`() {
        assertRejected(validator.validate(DbPatch.LogSet(
            exerciseId = -1, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 8
        )), "exerciseId")
    }

    @Test
    fun `LogSet rejects invalid date`() {
        assertRejected(validator.validate(DbPatch.LogSet(
            exerciseId = 1, date = "not-a-date", setNumber = 1, weightLbs = 135, reps = 8
        )), "date")
    }

    @Test
    fun `LogSet rejects empty date`() {
        assertRejected(validator.validate(DbPatch.LogSet(
            exerciseId = 1, date = "", setNumber = 1, weightLbs = 135, reps = 8
        )), "date")
    }

    @Test
    fun `LogSet rejects zero setNumber`() {
        assertRejected(validator.validate(DbPatch.LogSet(
            exerciseId = 1, date = "2026-04-16", setNumber = 0, weightLbs = 135, reps = 8
        )), "setNumber")
    }

    @Test
    fun `LogSet rejects zero reps`() {
        assertRejected(validator.validate(DbPatch.LogSet(
            exerciseId = 1, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 0
        )), "reps")
    }

    @Test
    fun `LogSet rejects negative weight`() {
        assertRejected(validator.validate(DbPatch.LogSet(
            exerciseId = 1, date = "2026-04-16", setNumber = 1, weightLbs = -10, reps = 8
        )), "weightLbs")
    }

    @Test
    fun `LogSet rejects bodyweight with nonzero weight`() {
        assertRejected(validator.validate(DbPatch.LogSet(
            exerciseId = 1, date = "2026-04-16", setNumber = 1,
            weightLbs = 50, reps = 12, isBodyweight = true
        )), "bodyweight")
    }

    @Test
    fun `LogSet rejects negative restTime`() {
        assertRejected(validator.validate(DbPatch.LogSet(
            exerciseId = 1, date = "2026-04-16", setNumber = 1,
            weightLbs = 135, reps = 8, restTimeSeconds = -1
        )), "restTimeSeconds")
    }

    // ── EditSet ─────────────────────────────────────────────────────

    @Test
    fun `EditSet valid single field`() {
        assertOk(validator.validate(DbPatch.EditSet(setId = 1, reps = 10)))
    }

    @Test
    fun `EditSet valid multiple fields`() {
        assertOk(validator.validate(DbPatch.EditSet(setId = 1, weightLbs = 225, reps = 3)))
    }

    @Test
    fun `EditSet rejects zero setId`() {
        assertRejected(validator.validate(DbPatch.EditSet(setId = 0, reps = 10)), "setId")
    }

    @Test
    fun `EditSet rejects zero weight`() {
        assertRejected(validator.validate(DbPatch.EditSet(setId = 1, weightLbs = 0)), "weightLbs")
    }

    @Test
    fun `EditSet rejects negative reps`() {
        assertRejected(validator.validate(DbPatch.EditSet(setId = 1, reps = -1)), "reps")
    }

    @Test
    fun `EditSet rejects negative restTime`() {
        assertRejected(validator.validate(DbPatch.EditSet(setId = 1, restTimeSeconds = -5)), "restTimeSeconds")
    }

    @Test
    fun `EditSet rejects no fields changed`() {
        assertRejected(validator.validate(DbPatch.EditSet(setId = 1)), "at least one")
    }

    // ── DeleteSet ───────────────────────────────────────────────────

    @Test
    fun `DeleteSet valid`() {
        assertOk(validator.validate(DbPatch.DeleteSet(setId = 42)))
    }

    @Test
    fun `DeleteSet rejects zero setId`() {
        assertRejected(validator.validate(DbPatch.DeleteSet(setId = 0)), "setId")
    }

    // ── MoveWorkoutDay ──────────────────────────────────────────────

    @Test
    fun `MoveWorkoutDay valid`() {
        assertOk(validator.validate(DbPatch.MoveWorkoutDay(
            currentDate = "2026-04-15", newDate = "2026-04-16"
        )))
    }

    @Test
    fun `MoveWorkoutDay rejects invalid currentDate`() {
        assertRejected(validator.validate(DbPatch.MoveWorkoutDay(
            currentDate = "bad", newDate = "2026-04-16"
        )), "currentDate")
    }

    @Test
    fun `MoveWorkoutDay rejects invalid newDate`() {
        assertRejected(validator.validate(DbPatch.MoveWorkoutDay(
            currentDate = "2026-04-15", newDate = "13/01/2026"
        )), "newDate")
    }

    @Test
    fun `MoveWorkoutDay rejects same date`() {
        assertRejected(validator.validate(DbPatch.MoveWorkoutDay(
            currentDate = "2026-04-15", newDate = "2026-04-15"
        )), "differ")
    }

    // ── RenameExercise ──────────────────────────────────────────────

    @Test
    fun `RenameExercise valid`() {
        assertOk(validator.validate(DbPatch.RenameExercise(exerciseId = 1, newName = "Squat")))
    }

    @Test
    fun `RenameExercise rejects zero exerciseId`() {
        assertRejected(validator.validate(DbPatch.RenameExercise(exerciseId = 0, newName = "Squat")), "exerciseId")
    }

    @Test
    fun `RenameExercise rejects blank name`() {
        assertRejected(validator.validate(DbPatch.RenameExercise(exerciseId = 1, newName = "  ")), "blank")
    }

    @Test
    fun `RenameExercise rejects empty name`() {
        assertRejected(validator.validate(DbPatch.RenameExercise(exerciseId = 1, newName = "")), "blank")
    }

    @Test
    fun `RenameExercise rejects name over 100 chars`() {
        assertRejected(validator.validate(DbPatch.RenameExercise(
            exerciseId = 1, newName = "A".repeat(101)
        )), "100")
    }

    // ── validateAll ─────────────────────────────────────────────────

    @Test
    fun `validateAll passes for valid list`() {
        assertOk(validator.validateAll(listOf(
            DbPatch.LogSet(exerciseId = 1, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 8),
            DbPatch.EditSet(setId = 2, reps = 10)
        )))
    }

    @Test
    fun `validateAll rejects on first invalid patch`() {
        assertRejected(validator.validateAll(listOf(
            DbPatch.LogSet(exerciseId = 1, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 8),
            DbPatch.DeleteSet(setId = 0)  // invalid
        )), "setId")
    }

    @Test
    fun `validateAll passes for empty list`() {
        assertOk(validator.validateAll(emptyList()))
    }
}
