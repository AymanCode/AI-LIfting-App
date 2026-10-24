package com.ayman.ecolift.android.room

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayman.ecolift.android.platform.RestoreSource
import com.ayman.ecolift.android.services.AndroidAppServices
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.DataBackupManager
import com.ayman.ecolift.data.IsolatedBackupContext
import com.ayman.ecolift.data.room.CardioSession
import com.ayman.ecolift.ui.MainActivity
import com.ayman.ecolift.ui.viewmodel.CardioViewModel
import com.ayman.ecolift.web.AppRepos
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupLifecycleTest {
    private val isolatedBackups = mutableListOf<IsolatedBackupContext>()

    private fun isolatedBackupContext(): IsolatedBackupContext =
        IsolatedBackupContext.open(ApplicationProvider.getApplicationContext()).also { isolatedBackups += it }

    @After
    fun deleteIsolatedBackups() {
        isolatedBackups.forEach(IsolatedBackupContext::deleteIsolatedFiles)
        isolatedBackups.clear()
        val cache = ApplicationProvider.getApplicationContext<Context>().cacheDir
        listOf("bk-roundtrip.json", "bk-malformed.json", "bk-bad-action.json").forEach { File(cache, it).delete() }
    }

    @Test
    fun recreationThenImportUpdatesRetainedCardioScreen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(isolatedBackupContext())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val original = awaitGraph(scenario)
            var screen: CardioViewModel? = null
            scenario.onActivity {
                screen = ViewModelProvider(it, viewModelFactory {
                    initializer { CardioViewModel(original, AndroidAppServices(context).vision) }
                })[CardioViewModel::class.java]
            }
            scenario.recreate()
            val recreated = awaitGraph(scenario)
            val now = System.currentTimeMillis()
            val row = CardioSession(
                localUuid = "bk-rotation-$now",
                date = java.time.LocalDate.now().toString(),
                activityType = "Running",
                durationSec = 300,
                source = "health_connect",
                hcUid = "bk-rotation-$now",
                createdAt = now,
                updatedAt = now,
            )
            withContext(Dispatchers.IO) { db.cardioSessionDao().insert(row) }
            recreated.loadAll()
            try {
                withTimeout(5_000) {
                    while (screen!!.uiState.value.sessions.none { it.localUuid == row.localUuid }) delay(25)
                }
                assertSame(original, recreated)
                assertEquals(300, screen!!.uiState.value.sessions.single { it.localUuid == row.localUuid }.durationSec)
            } finally {
                withContext(Dispatchers.IO) { db.openHelper.writableDatabase.execSQL("DELETE FROM cardio_sessions WHERE local_uuid = ?", arrayOf(row.localUuid)) }
                recreated.loadAll()
            }
        }
    }

    @Test
    fun malformedImportFailsBeforeReplacingData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backups = isolatedBackupContext()
        val db = AppDatabase.getInstance(backups)
        val before = db.workoutSetDao().getAll()
        for (input in listOf("{truncated", "this is not JSON", "")) {
            val file = File(context.cacheDir, "bk-malformed.json").apply { writeText(input) }
            val failure = runCatching { DataBackupManager.importFromUri(backups, db, Uri.fromFile(file)) }.exceptionOrNull()
            assertEquals("JsonDecodingException", failure?.javaClass?.simpleName)
            assertEquals(before, db.workoutSetDao().getAll())
        }
    }

    @Test
    fun exportOutputFailurePropagatesToActionBoundary() = runBlocking {
        val backups = isolatedBackupContext()
        val failure = runCatching {
            DataBackupManager.exportToUri(backups, AppDatabase.getInstance(backups), Uri.fromFile(File("/dev/full")))
        }.exceptionOrNull()
        org.junit.Assert.assertTrue("The full output stream must fail", failure is java.io.IOException)
    }

    @Test
    fun activityOwnerContainsMalformedImportAndExportFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(isolatedBackupContext())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val graph = awaitGraph(scenario)
            val before = db.workoutSetDao().getAll()
            lateinit var owner: RepositoryOwner
            scenario.onActivity { owner = it.repositoryOwner }
            for (input in listOf("{truncated", "this is not JSON", "")) {
                val file = File(context.cacheDir, "bk-bad-action.json").apply { writeText(input) }
                scenario.onActivity {
                    owner.backups.requestRestore(RestoreSource.Document(Uri.fromFile(file).toString()))
                    owner.confirmRestore()
                }
                awaitMessage(owner, "Import failed. Choose a valid EcoLift backup.")
                assertEquals(before, db.workoutSetDao().getAll())
                assertSame(graph, awaitGraph(scenario))
            }
            scenario.onActivity { owner.export(Uri.fromFile(File("/dev/full"))) }
            awaitMessage(owner, "Export failed. Choose another destination.")
            assertEquals(before, db.workoutSetDao().getAll())
        }
    }

    @Test
    fun localRecoveryNeedsConfirmationAndRestoresTheSelectedSnapshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // RepositoryOwner writes recovery snapshots through the real application context, and retention prunes the oldest.
        val realAutomaticBackups = File(context.filesDir, "automatic-backups")
        val savedBackups = File(context.cacheDir, "bk-saved-${UUID.randomUUID()}").apply { mkdirs() }
        val originals = automaticBackupFiles(realAutomaticBackups).map { file ->
            file.copyTo(File(savedBackups, file.name))
            SavedBackup(file.name, file.readBytes(), file.lastModified())
        }
        val exported = File(context.cacheDir, "bk-roundtrip.json")
        try {
            val backups = isolatedBackupContext()
            val db = AppDatabase.getInstance(backups)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val graph = awaitGraph(scenario)
                lateinit var owner: RepositoryOwner
                scenario.onActivity { owner = it.repositoryOwner }
                val originalSettings = db.userSettingsDao().get()
                val originalSets = db.workoutSetDao().getAll()
                DataBackupManager.exportToUri(backups, db, Uri.fromFile(exported))
                var restoredExport = false
                try {
                    val backup = requireNotNull(DataBackupManager.createAutomaticBackup(context, db, "bk-local-test", force = true))
                    val now = System.currentTimeMillis()
                    val row = CardioSession(
                        localUuid = "bk-local-$now",
                        date = java.time.LocalDate.now().toString(),
                        activityType = "Walking",
                        source = "manual",
                        createdAt = now,
                        updatedAt = now,
                    )
                    db.cardioSessionDao().insert(row)
                    graph.loadAll()
                    scenario.onActivity { owner.backups.requestRestore(RestoreSource.Local(backup.fileName)) }
                    assertEquals(1, graph.cardio.sessions.value.count { it.localUuid == row.localUuid })
                    scenario.onActivity { owner.backups.cancelRestore() }
                    assertEquals(1, db.cardioSessionDao().getAll().count { it.localUuid == row.localUuid })
                    scenario.onActivity {
                        owner.backups.requestRestore(RestoreSource.Local(backup.fileName))
                        owner.confirmRestore()
                    }
                    withTimeout(15_000) {
                        while (owner.backups.state.value.busy || owner.backups.state.value.message == null) delay(25)
                    }
                    org.junit.Assert.assertTrue(owner.backups.state.value.message!!.startsWith("Imported "))
                    assertEquals(0, graph.cardio.sessions.value.count { it.localUuid == row.localUuid })
                    assertEquals(0, db.cardioSessionDao().getAll().count { it.localUuid == row.localUuid })
                    org.junit.Assert.assertTrue(DataBackupManager.listAutomaticBackups(context).any { it.fileName == backup.fileName })
                    scenario.onActivity {
                        owner.backups.requestRestore(RestoreSource.Document(Uri.fromFile(exported).toString()))
                        owner.confirmRestore()
                    }
                    withTimeout(15_000) {
                        while (owner.backups.state.value.busy || owner.backups.state.value.message == null) delay(25)
                    }
                    org.junit.Assert.assertTrue(owner.backups.state.value.message!!.startsWith("Imported "))
                    restoredExport = true
                    assertEquals(originalSets, db.workoutSetDao().getAll())
                    assertEquals(originalSettings, db.userSettingsDao().get())
                } finally {
                    if (!restoredExport) {
                        // A restore still running would snapshot into the real directory after cleanup.
                        withTimeoutOrNull(60_000) { while (owner.backups.state.value.busy) delay(25) }
                        DataBackupManager.importFromUri(backups, db, Uri.fromFile(exported))
                        graph.loadAll()
                    }
                }
            }
        } finally {
            exported.delete()
            val originalNames = originals.map { it.name }.toSet()
            automaticBackupFiles(realAutomaticBackups).filter { it.name !in originalNames }.forEach { it.delete() }
            if (originals.isNotEmpty()) realAutomaticBackups.mkdirs()
            originals.forEach { putBack(realAutomaticBackups, it) }
            val restored = automaticBackupFiles(realAutomaticBackups)
            assertEquals("real automatic-backups names", originals.map { it.name }, restored.map { it.name })
            originals.zip(restored).forEach { (saved, file) ->
                org.junit.Assert.assertArrayEquals("bytes of ${saved.name}", saved.bytes, file.readBytes())
                assertEquals("time of ${saved.name}", saved.lastModified, file.lastModified())
            }
            savedBackups.deleteRecursively()
        }
    }

    private class SavedBackup(val name: String, val bytes: ByteArray, val lastModified: Long)

    private fun putBack(directory: File, saved: SavedBackup) {
        val target = File(directory, saved.name)
        if (target.isFile && target.lastModified() == saved.lastModified && target.readBytes().contentEquals(saved.bytes)) return
        // The temp name has no .json extension, so recovery never lists a half-written copy.
        val temp = File(directory, ".${saved.name}.restoring").apply { writeBytes(saved.bytes) }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        check(target.setLastModified(saved.lastModified)) { "Could not restore the time of ${saved.name}" }
    }

    private fun automaticBackupFiles(directory: File): List<File> =
        directory.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()

    private suspend fun awaitMessage(owner: RepositoryOwner, message: String) = withTimeout(15_000) {
        while (owner.backups.state.value.busy || owner.backups.state.value.message != message) delay(25)
        assertEquals(false, owner.backups.state.value.busy)
        assertEquals(message, owner.backups.state.value.message)
    }

    private suspend fun awaitGraph(scenario: ActivityScenario<MainActivity>): AppRepos = withTimeout(30_000) {
        lateinit var loaded: Deferred<LoadedRepositories>
        scenario.onActivity { loaded = it.repositoryOwner.initialize(false) }
        loaded.await().repos
    }
}
