package com.ayman.ecolift.agent

import com.ayman.ecolift.agent.model.DbPatch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DbPatchTest {

    private val json = Json { prettyPrint = false }

    // ── Serialization round-trips ───────────────────────────────────

    @Test
    fun `LogSet serializes and deserializes`() {
        val patch = DbPatch.LogSet(
            exerciseId = 1, date = "2026-04-16", setNumber = 1,
            weightLbs = 135, reps = 8
        )
        val encoded = json.encodeToString<DbPatch>(patch)
        val decoded = json.decodeFromString<DbPatch>(encoded)
        assertEquals(patch, decoded)
    }

    @Test
    fun `EditSet serializes and deserializes`() {
        val patch = DbPatch.EditSet(setId = 42, weightLbs = 185, reps = 5)
        val encoded = json.encodeToString<DbPatch>(patch)
        val decoded = json.decodeFromString<DbPatch>(encoded)
        assertEquals(patch, decoded)
    }

    @Test
    fun `DeleteSet serializes and deserializes`() {
        val patch = DbPatch.DeleteSet(setId = 7)
        val encoded = json.encodeToString<DbPatch>(patch)
        val decoded = json.decodeFromString<DbPatch>(encoded)
        assertEquals(patch, decoded)
    }

    @Test
    fun `MoveWorkoutDay serializes and deserializes`() {
        val patch = DbPatch.MoveWorkoutDay(currentDate = "2026-04-15", newDate = "2026-04-16")
        val encoded = json.encodeToString<DbPatch>(patch)
        val decoded = json.decodeFromString<DbPatch>(encoded)
        assertEquals(patch, decoded)
    }

    @Test
    fun `RenameExercise serializes and deserializes`() {
        val patch = DbPatch.RenameExercise(exerciseId = 3, newName = "Incline Bench Press")
        val encoded = json.encodeToString<DbPatch>(patch)
        val decoded = json.decodeFromString<DbPatch>(encoded)
        assertEquals(patch, decoded)
    }

    // ── Destructive flag ────────────────────────────────────────────

    @Test
    fun `DeleteSet is destructive`() {
        assertTrue(DbPatch.isDestructive(DbPatch.DeleteSet(setId = 1)))
    }

    @Test
    fun `RenameExercise is destructive`() {
        assertTrue(DbPatch.isDestructive(DbPatch.RenameExercise(exerciseId = 1, newName = "X")))
    }

    @Test
    fun `LogSet is not destructive`() {
        assertFalse(DbPatch.isDestructive(
            DbPatch.LogSet(exerciseId = 1, date = "2026-04-16", setNumber = 1, weightLbs = 100, reps = 10)
        ))
    }

    @Test
    fun `EditSet is not destructive`() {
        assertFalse(DbPatch.isDestructive(DbPatch.EditSet(setId = 1, reps = 12)))
    }

    @Test
    fun `MoveWorkoutDay is not destructive`() {
        assertFalse(DbPatch.isDestructive(
            DbPatch.MoveWorkoutDay(currentDate = "2026-04-15", newDate = "2026-04-16")
        ))
    }

    // ── Polymorphic list round-trip ─────────────────────────────────

    @Test
    fun `list of mixed patches round-trips`() {
        val patches: List<DbPatch> = listOf(
            DbPatch.LogSet(exerciseId = 1, date = "2026-04-16", setNumber = 1, weightLbs = 135, reps = 8),
            DbPatch.DeleteSet(setId = 7),
            DbPatch.RenameExercise(exerciseId = 2, newName = "Close-Grip Bench")
        )
        val encoded = json.encodeToString(patches)
        val decoded = json.decodeFromString<List<DbPatch>>(encoded)
        assertEquals(patches, decoded)
    }
}
