package com.ayman.ecolift.android.room

import com.ayman.ecolift.data.ExerciseAlias as DomainExerciseAlias
import com.ayman.ecolift.data.ExerciseDistinctPair as DomainExerciseDistinctPair
import com.ayman.ecolift.data.UnitSystem
import com.ayman.ecolift.data.UserDataBackup
import com.ayman.ecolift.data.UserSettings as DomainUserSettings
import com.ayman.ecolift.data.WorkoutCardDisplayMode
import com.ayman.ecolift.data.WorkoutCardOrigin
import com.ayman.ecolift.data.WorkoutExerciseCard as DomainWorkoutExerciseCard
import com.ayman.ecolift.data.room.CycleSlot
import com.ayman.ecolift.data.room.Exercise
import com.ayman.ecolift.data.room.USER_SETTINGS_ID
import com.ayman.ecolift.data.room.WorkoutSet
import com.ayman.ecolift.data.room.toDomain
import com.ayman.ecolift.data.room.toRoom
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMappersTest {
    @Test
    fun exerciseRoundTripKeepsMergedIntoId() {
        val room = Exercise(
            id = 4,
            name = "Row",
            muscleGroups = "BACK",
            isBodyweight = false,
            createdAt = 50,
            mergedIntoId = 9,
        )

        val domain = room.toDomain()

        assertEquals(9L, domain.mergedIntoId)
        assertEquals(room, domain.toRoom())
    }

    @Test
    fun setRoundTripKeepsSyncCardAndWarmup() {
        val room = WorkoutSet(
            id = 12,
            exerciseId = 4,
            date = "2026-05-17",
            setNumber = 2,
            weightLbs = 1000,
            reps = 8,
            isBodyweight = false,
            completed = true,
            restTimeSeconds = 90,
            syncId = "sync-12",
            rowRevision = 3,
            cardId = "card-12",
            isWarmup = true,
        )

        assertEquals(room, room.toDomain().toRoom())
    }

    @Test
    fun cycleSlotRoundTripKeepsLearnedFields() {
        val room = CycleSlot(
            id = 7,
            name = "Push",
            orderIndex = 1,
            archivedAt = null,
            learned = true,
            createdAt = 80,
            seedRevision = 2,
            anchorUpdatedAt = 90,
            suppressedAt = null,
            colorIndex = 3,
        )

        assertEquals(room, room.toDomain().toRoom())
    }

    @Test
    fun settingsRoundTripKeepsZoneUnitAndSmartSuggest() {
        val domain = DomainUserSettings(
            userBodyweightLbs = 185,
            glassPaletteChoice = "sage",
            timeZone = "America/New_York",
            unitSystem = UnitSystem.KG,
            smartSuggest = false,
        )

        val room = domain.toRoom()

        assertEquals(USER_SETTINGS_ID, room.id)
        assertEquals("America/New_York", room.timeZone)
        assertEquals("KG", room.unitSystem)
        assertEquals(false, room.smartSuggest)
        assertEquals(domain, room.toDomain())
    }

    @Test
    fun cardRoundTripUsesEnumNames() {
        val domain = DomainWorkoutExerciseCard(
            id = "card-1",
            date = "2026-05-17",
            exerciseId = 4,
            position = 0,
            displayMode = WorkoutCardDisplayMode.COMPACT,
            origin = WorkoutCardOrigin.LEGACY,
        )

        val room = domain.toRoom()

        assertEquals("COMPACT", room.displayMode)
        assertEquals("LEGACY", room.origin)
        assertEquals(1L, room.rowRevision)
        assertEquals(domain, room.toDomain())
    }

    @Test
    fun aliasAndPairRoundTripDropUserId() {
        val alias = DomainExerciseAlias(
            id = 3,
            userId = "remote-user",
            exerciseId = 4,
            alias = "BP",
            normalizedKey = "bp",
        )
        val storedAlias = alias.toRoom().toDomain()
        assertEquals("", storedAlias.userId)
        assertEquals(4L, storedAlias.exerciseId)
        assertEquals("bp", storedAlias.normalizedKey)

        val pair = DomainExerciseDistinctPair(
            id = 8,
            userId = "remote-user",
            exerciseIdA = 4,
            exerciseIdB = 9,
        )
        val storedPair = pair.toRoom().toDomain()
        assertEquals("", storedPair.userId)
        assertEquals(4L, storedPair.exerciseIdA)
        assertEquals(9L, storedPair.exerciseIdB)
    }

    @Test
    fun v19BackupJsonDecodesWithEmptyNewListsAndDefaultSetFields() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<UserDataBackup>(
            """
            {
              "metadata": {"createdAtEpochMs": 1, "source": "v19", "appDbVersion": 19},
              "cycle": null,
              "exercises": [{"id": 1, "name": "Bench Press", "createdAt": 10}],
              "workoutDays": [],
              "workoutSets": [{
                "id": 9,
                "exerciseId": 1,
                "date": "2026-05-17",
                "setNumber": 1,
                "weightLbs": 100,
                "reps": 8,
                "completed": true
              }],
              "pendingReviews": [],
              "cycleSlots": [],
              "splitExercises": [],
              "tempSessionSwaps": []
            }
            """.trimIndent(),
        )

        assertTrue(decoded.workoutExerciseCards.isEmpty())
        assertTrue(decoded.exerciseAliases.isEmpty())
        assertTrue(decoded.exerciseDistinctPairs.isEmpty())
        val set = decoded.workoutSets.single()
        assertEquals("", set.syncId)
        assertEquals(0L, set.rowRevision)
        assertEquals("", set.cardId)
        assertEquals(false, set.isWarmup)
        assertNull(decoded.exercises.single().mergedIntoId)
    }
}
