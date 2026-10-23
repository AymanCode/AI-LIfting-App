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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupLifecycleTest {
    @Test
    fun recreationThenImportUpdatesRetainedCardioScreen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
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
        val db = AppDatabase.getInstance(context)
        val before = db.workoutSetDao().getAll()
        for (input in listOf("{truncated", "this is not JSON", "")) {
            val file = File(context.cacheDir, "bk-malformed.json").apply { writeText(input) }
            val failure = runCatching { DataBackupManager.importFromUri(context, db, Uri.fromFile(file)) }.exceptionOrNull()
            assertEquals("JsonDecodingException", failure?.javaClass?.simpleName)
            assertEquals(before, db.workoutSetDao().getAll())
        }
    }

    @Test
    fun exportOutputFailurePropagatesToActionBoundary() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val failure = runCatching {
            DataBackupManager.exportToUri(context, AppDatabase.getInstance(context), Uri.fromFile(File("/dev/full")))
        }.exceptionOrNull()
        org.junit.Assert.assertTrue("The full output stream must fail", failure is java.io.IOException)
    }

    @Test
    fun activityOwnerContainsMalformedImportAndExportFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
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
        val db = AppDatabase.getInstance(context)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val graph = awaitGraph(scenario)
            lateinit var owner: RepositoryOwner
            scenario.onActivity { owner = it.repositoryOwner }
            val originalSettings = db.userSettingsDao().get()
            val originalSets = db.workoutSetDao().getAll()
            val exported = File(context.cacheDir, "bk-roundtrip.json")
            DataBackupManager.exportToUri(context, db, Uri.fromFile(exported))
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
            assertEquals(originalSets, db.workoutSetDao().getAll())
            assertEquals(originalSettings, db.userSettingsDao().get())
        }
    }

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
