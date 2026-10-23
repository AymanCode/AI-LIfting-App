package com.ayman.ecolift.android.room

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.BuildConfig
import com.ayman.ecolift.android.platform.BackupActions
import com.ayman.ecolift.android.platform.RestoreSource
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.DataBackupManager
import com.ayman.ecolift.ui.theme.loadAppFonts
import com.ayman.ecolift.web.AppRepos
import com.ayman.ecolift.web.DataGeneration
import com.ayman.ecolift.web.demo.DemoData
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LoadedRepositories(val database: AppDatabase, val repos: AppRepos)

class RepositoryOwner(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private var initialization: Deferred<LoadedRepositories>? = null
    private var graph: RoomRepos? = null
    private val generation = DataGeneration()
    val backups = BackupActions()
    val healthSessionsAdded = MutableStateFlow<Int?>(null)

    fun initialize(seedRequested: Boolean): Deferred<LoadedRepositories> {
        return initialization ?: viewModelScope.async(Dispatchers.IO) {
            loadAppFonts()
            val database = AppDatabase.getInstance(context)
            val repos = RoomRepos(database, viewModelScope)
            graph = repos
            if (shouldSeedDemo(BuildConfig.DEBUG, seedRequested, database.workoutSetDao().hasAny())) {
                repos.installDemoSeed(DemoData.generate())
            }
            repos.loadAll()
            refreshLocalBackups()
            LoadedRepositories(database, object : AppRepos by repos {
                override val dataGeneration = generation
            })
        }.also { initialization = it }
    }

    fun export(uri: Uri) = launchAction("Export failed. Choose another destination.") { loaded ->
        val result = DataBackupManager.exportToUri(context, loaded.database, uri)
        "Exported ${result.entryCount} records."
    }

    fun createLocalBackup() = launchAction("Backup action failed.") { loaded ->
        DataBackupManager.createAutomaticBackup(context, loaded.database, reason = "manual", force = true)
        refreshLocalBackups()
        "Created local backup."
    }

    fun confirmRestore() {
        viewModelScope.launch {
            backups.confirmRestore { source ->
                val loaded = requireNotNull(initialization).await()
                val result = withContext(NonCancellable) {
                    generation.replace(reload = { loaded.repos.loadAll() }) {
                        when (source) {
                            is RestoreSource.Document -> DataBackupManager.importFromUri(context, loaded.database, Uri.parse(source.uri))
                            is RestoreSource.Local -> DataBackupManager.restoreAutomaticBackup(context, loaded.database, source.fileName)
                        }
                    }
                }
                refreshLocalBackups()
                "Imported ${result.entryCount} records."
            }
        }
    }

    fun refreshBackups() = launchAction("Unable to list local backups.") {
        refreshLocalBackups()
        null
    }

    fun runDeviceAction(failureMessage: String, action: suspend (LoadedRepositories) -> String?) =
        launchAction(failureMessage, action)

    private fun launchAction(failureMessage: String, action: suspend (LoadedRepositories) -> String?) {
        viewModelScope.launch {
            backups.run(failureMessage) { action(requireNotNull(initialization).await()) }
        }
    }

    private suspend fun refreshLocalBackups() = withContext(Dispatchers.IO) {
        backups.updateLocalBackups(DataBackupManager.listAutomaticBackups(context))
    }

    override fun onCleared() {
        graph?.close()
        super.onCleared()
    }
}
