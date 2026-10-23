package com.ayman.ecolift.android.platform

import com.ayman.ecolift.data.LocalBackupInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.OutputStream

class BackupActionsTest {
    @Test
    fun failedOutputShowsErrorClearsBusyAndAllowsRetry() = runTest {
        val actions = BackupActions()
        actions.run("Export failed. Choose another destination.") {
            object : OutputStream() {
                override fun write(value: Int): Unit = throw IOException("full destination")
            }.write(1)
            "Exported 1 records."
        }
        assertEquals("Export failed. Choose another destination.", actions.state.value.message)
        assertFalse(actions.state.value.busy)
        actions.run("unexpected") { "Exported 2 records." }
        assertEquals("Exported 2 records.", actions.state.value.message)
    }

    @Test
    fun cancellationEscapesClearsBusyAndAllowsRetry() = runTest {
        val actions = BackupActions()
        val result = runCatching {
            actions.run("must not appear") { throw CancellationException("cancelled") }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(null, actions.state.value.message)
        assertFalse(actions.state.value.busy)
        actions.run("unexpected") { "Retry succeeded." }
        assertEquals("Retry succeeded.", actions.state.value.message)
    }

    @Test
    fun selectedFileDoesNothingUntilConfirmedAndCancelKeepsData() = runTest {
        val actions = BackupActions()
        var history = "current history"
        actions.requestRestore(RestoreSource.Document("content://backup"))
        assertEquals("current history", history)
        assertEquals(RestoreSource.Document("content://backup"), actions.state.value.pendingRestore)
        actions.cancelRestore()
        actions.confirmRestore { history = "wrong"; "Imported." }
        assertEquals("current history", history)
        actions.requestRestore(RestoreSource.Local("saved.json"))
        actions.confirmRestore { source ->
            assertEquals(RestoreSource.Local("saved.json"), source)
            history = "saved history"
            "Restored 10 records."
        }
        assertEquals("saved history", history)
        assertEquals("Restored 10 records.", actions.state.value.message)
        assertEquals(null, actions.state.value.pendingRestore)
    }

    @Test
    fun failedImportDoesNotPublishSuccessAndCanBeRetried() = runTest {
        val actions = BackupActions()
        var reloads = 0
        for (input in listOf("{truncated", "this is not JSON", "")) {
            actions.requestRestore(RestoreSource.Document("broken"))
            actions.confirmRestore {
                kotlinx.serialization.json.Json.parseToJsonElement(input)
                reloads++
                "Imported."
            }
            assertEquals(0, reloads)
            assertEquals("Import failed. Choose a valid EcoLift backup.", actions.state.value.message)
            assertFalse(actions.state.value.busy)
        }
        actions.requestRestore(RestoreSource.Document("valid"))
        actions.confirmRestore { reloads++; "Imported 12 records." }
        assertEquals(1, reloads)
        assertEquals("Imported 12 records.", actions.state.value.message)
    }

    @Test
    fun busyActionRejectsConflictingWriteAndRestoreSelection() = runTest {
        val actions = BackupActions()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            actions.run("unexpected") {
                entered.complete(Unit)
                release.await()
                "First finished."
            }
        }
        entered.await()
        assertTrue(actions.state.value.busy)
        actions.requestRestore(RestoreSource.Local("conflicting.json"))
        actions.run("unexpected") { error("must not start") }
        assertEquals(null, actions.state.value.pendingRestore)
        release.complete(Unit)
        first.await()
        assertEquals("First finished.", actions.state.value.message)
        assertFalse(actions.state.value.busy)
    }

    @Test
    fun localBackupsAreListedNewestFirstWithDateAndSize() {
        val actions = BackupActions()
        actions.updateLocalBackups(listOf(LocalBackupInfo("older", 10, 50), LocalBackupInfo("newer", 20, 100)))
        assertEquals(listOf(LocalBackupInfo("newer", 20, 100), LocalBackupInfo("older", 10, 50)), actions.state.value.localBackups)
    }
}
