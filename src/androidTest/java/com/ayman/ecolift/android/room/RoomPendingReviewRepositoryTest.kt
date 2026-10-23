package com.ayman.ecolift.android.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.WorkoutSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPendingReviewRepositoryTest {
    @Test
    fun failedReviewUpdateRollsBackTheSetAndCardTogether() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val store = RoomStore(db)
            val row = store.write {
                upsertExercise(Exercise(1, "Bench Press", "Chest"))
                insertReview(PendingReview(rawInput = "leg thing", dateLogged = "2026-10-20"))
            }
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_review_update BEFORE UPDATE ON pending_review BEGIN SELECT RAISE(ABORT, 'review write fixture'); END",
            )
            val reviews = RoomPendingReviewRepository(store)
            try {
                reviews.resolve(row.id, PendingReviewSet(1, 1350, 12))
                throw AssertionError("Fixture did not reject the review update")
            } catch (_: android.database.sqlite.SQLiteException) {
                assertEquals(listOf(row), reviews.unresolved.first())
                assertEquals(emptyList<WorkoutSet>(), store.sets.value)
                assertEquals(0, db.workoutSetDao().getAll().size)
                assertEquals(0, db.workoutExerciseCardDao().getAll().size)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun resolutionSavesOnTheReviewDateAndCannotApplyTwiceAfterReload() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val store = RoomStore(db)
            val row = store.write {
                upsertExercise(Exercise(1, "Bench Press", "Chest"))
                insertSet(WorkoutSet(exerciseId = 1, date = "2026-10-20", setNumber = 1, weightLbs = 1300, reps = 8, completed = true))
                insertReview(PendingReview(rawInput = "leg thing", dateLogged = "2026-10-20"))
            }
            val reviews = RoomPendingReviewRepository(store)
            assertEquals(listOf(row), reviews.unresolved.first())
            assertTrue(reviews.resolve(row.id, PendingReviewSet(1, 1355, 12)))
            assertEquals(listOf(1300 to 8, 1355 to 12), store.sets.value.map { it.weightLbs to it.reps })
            assertEquals(listOf(1, 2), store.sets.value.map { it.setNumber })
            assertEquals(listOf("2026-10-20", "2026-10-20"), db.workoutSetDao().getAll().map { it.date })
            assertEquals(true, db.pendingReviewDao().getAll().single().resolved)

            val reloaded = RoomStore(db)
            reloaded.write { reloadAll() }
            assertFalse(RoomPendingReviewRepository(reloaded).resolve(row.id, PendingReviewSet(1, 1400, 6)))
            assertEquals(2, db.workoutSetDao().getAll().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun invalidResolutionKeepsTheDraftAndDismissPreservesOtherRows() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val store = RoomStore(db)
            val first = store.write {
                upsertExercise(Exercise(1, "Bench Press", "Chest"))
                insertReview(PendingReview(rawInput = "first draft", dateLogged = "2026-10-20"))
            }
            val second = store.write { insertReview(PendingReview(rawInput = "second draft", dateLogged = "2026-10-19")) }
            val reviews = RoomPendingReviewRepository(store)
            try {
                reviews.resolve(first.id, PendingReviewSet(1, 0, 12))
                throw AssertionError("Invalid weighted set was saved")
            } catch (_: IllegalArgumentException) {
                assertEquals(listOf(first, second), reviews.unresolved.first())
                assertEquals(emptyList<WorkoutSet>(), store.sets.value)
                assertEquals(0, db.workoutSetDao().getAll().size)
            }
            reviews.markResolved(first.id)
            assertEquals(listOf(second), reviews.unresolved.first())
            assertEquals(listOf("first draft", "second draft"), db.pendingReviewDao().getAll().map { it.rawInput })
        } finally {
            db.close()
        }
    }
}
