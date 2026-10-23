package com.ayman.ecolift.android.room

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.Exercise
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStoreThreadingTest {
    @Test
    fun nested_writes_share_the_draft_and_other_readers_see_only_committed_rows() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val store = RoomStore(db)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val writer = async(Dispatchers.Main) {
                store.write {
                    assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                    upsertExercise(Exercise(id = 77, name = "Bench Press"))
                    entered.complete(Unit)
                    release.await()
                    store.write {
                        assertEquals(listOf("Bench Press"), store.exercisesNow().map { it.name })
                        upsertExercise(Exercise(id = 78, name = "Row"))
                    }
                }
            }
            entered.await()
            assertEquals(emptyList<Exercise>(), store.exercisesNow())
            release.complete(Unit)
            writer.await()
            assertEquals(listOf("Bench Press", "Row"), store.exercises.value.map { it.name })
            assertEquals(listOf("Bench Press", "Row"), db.exerciseDao().getAll().map { it.name }.sorted())
        } finally {
            db.close()
        }
    }

    @Test
    fun rollback_discards_the_draft_and_a_later_write_can_commit() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val store = RoomStore(db)
            try {
                store.write {
                    upsertExercise(Exercise(id = 77, name = "Rolled back"))
                    error("rollback fixture")
                }
            } catch (failure: IllegalStateException) {
                assertEquals("rollback fixture", failure.message)
            }
            assertEquals(emptyList<Exercise>(), store.exercisesNow())
            assertEquals(emptyList<String>(), db.exerciseDao().getAll().map { it.name })
            store.write { upsertExercise(Exercise(id = 78, name = "Committed")) }
            assertEquals(listOf("Committed"), store.exercisesNow().map { it.name })
            assertEquals(listOf("Committed"), db.exerciseDao().getAll().map { it.name })
        } finally {
            db.close()
        }
    }

    @Test
    fun canonical_lookup_tracks_committed_merge_changes() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val store = RoomStore(db)
            val repository = RoomExerciseRepository(store)
            store.write {
                upsertExercise(Exercise(id = 78, name = "Bench Press"))
                upsertExercise(Exercise(id = 77, name = "Old name", mergedIntoId = 78))
            }
            assertEquals(78L, repository.canonicalId(77))
            assertEquals(123L, repository.canonicalId(123))
            store.write {
                upsertExercise(Exercise(id = 79, name = "New name"))
                upsertExercise(Exercise(id = 77, name = "Old name", mergedIntoId = 79))
            }
            assertEquals(79L, repository.canonicalId(77))
            assertEquals(78L, repository.canonicalId(78))
        } finally {
            db.close()
        }
    }
}
