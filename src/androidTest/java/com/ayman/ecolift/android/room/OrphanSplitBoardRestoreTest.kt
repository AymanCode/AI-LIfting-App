package com.ayman.ecolift.android.room

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.BackupMetadata
import com.ayman.ecolift.data.DataBackupManager
import com.ayman.ecolift.data.UserDataBackup
import com.ayman.ecolift.data.room.CycleSlot as RoomCycleSlot
import com.ayman.ecolift.data.room.Exercise as RoomExercise
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OrphanSplitBoardRestoreTest {
    @Test
    fun restore_drops_a_missing_split_and_a_missing_exercise() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val backup = UserDataBackup(
                metadata = BackupMetadata(createdAtEpochMs = 1L, source = "test", appDbVersion = 21),
                cycle = null,
                exercises = listOf(RoomExercise(id = 1, name = "Kept lift")),
                workoutDays = emptyList(),
                workoutSets = emptyList(),
                pendingReviews = emptyList(),
                cycleSlots = listOf(
                    RoomCycleSlot(id = 10, name = "Kept mix"),
                    RoomCycleSlot(id = 11, name = "Empty after filter"),
                    RoomCycleSlot(id = 12, name = "Options only"),
                ),
                splitExercises = emptyList(),
                tempSessionSwaps = emptyList(),
                splitMemberChoices = listOf(
                    RoomSplitMemberChoice(splitId = 404, revision = 1, inSplitJson = "[1]", otherOptionsJson = "[]"),
                    RoomSplitMemberChoice(splitId = 10, revision = 3, inSplitJson = "[1,99]", otherOptionsJson = "[77]"),
                    RoomSplitMemberChoice(splitId = 11, revision = 2, inSplitJson = "[99]", otherOptionsJson = "[77]"),
                    RoomSplitMemberChoice(splitId = 12, revision = 4, inSplitJson = "[99]", otherOptionsJson = "[1]"),
                ),
            )
            val file = File(context.cacheDir, "orphan-split-board.json")
            file.writeText(Json { prettyPrint = true; ignoreUnknownKeys = true }.encodeToString(backup))
            DataBackupManager.importFromUri(context, db, Uri.fromFile(file))

            val keptMix = db.splitMemberChoiceDao().getBySplitId(10)!!.toChoice()
            assertEquals(listOf(1L), keptMix.board.inSplit)
            assertEquals(emptyList<Long>(), keptMix.board.otherOptions)
            assertEquals(3L, keptMix.revision)
            assertNull(db.splitMemberChoiceDao().getBySplitId(11))
            assertNull(db.splitMemberChoiceDao().getBySplitId(404))
            val optionsOnly = db.splitMemberChoiceDao().getBySplitId(12)!!.toChoice()
            assertEquals(emptyList<Long>(), optionsOnly.board.inSplit)
            assertEquals(listOf(1L), optionsOnly.board.otherOptions)
            assertEquals(4L, optionsOnly.revision)
            assertEquals(listOf(1L), db.exerciseDao().getAll().map { it.id })
        } finally {
            db.close()
        }
    }
}
