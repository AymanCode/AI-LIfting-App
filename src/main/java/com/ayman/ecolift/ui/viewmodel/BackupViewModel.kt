package com.ayman.ecolift.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.DataBackupManager
import com.ayman.ecolift.data.LocalBackupInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val localBackups: List<LocalBackupInfo> = emptyList(),
    val isWorking: Boolean = false,
    val suggestedExportFileName: String = DataBackupManager.suggestedExportFileName(),
)

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val db = AppDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState = _uiState

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refreshBackups()
    }

    fun refreshBackups() {
        _uiState.update {
            it.copy(
                localBackups = DataBackupManager.listAutomaticBackups(appContext),
                suggestedExportFileName = DataBackupManager.suggestedExportFileName(),
            )
        }
    }

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            runBusyAction {
                val result = DataBackupManager.exportToUri(appContext, db, uri)
                _messages.tryEmit("Exported ${result.entryCount} records.")
            }
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            runBusyAction {
                val result = DataBackupManager.importFromUri(appContext, db, uri)
                refreshBackups()
                _messages.tryEmit("Imported ${result.entryCount} records.")
            }
        }
    }

    fun createLocalBackupNow() {
        viewModelScope.launch {
            runBusyAction {
                val backup = DataBackupManager.createAutomaticBackup(
                    context = appContext,
                    db = db,
                    reason = "manual",
                    force = true,
                )
                refreshBackups()
                val message = if (backup != null) {
                    "Created local backup ${backup.fileName}."
                } else {
                    "No local backup created."
                }
                _messages.tryEmit(message)
            }
        }
    }

    fun restoreLocalBackup(fileName: String) {
        viewModelScope.launch {
            runBusyAction {
                val result = DataBackupManager.restoreAutomaticBackup(appContext, db, fileName)
                refreshBackups()
                _messages.tryEmit("Restored ${result.entryCount} records from $fileName.")
            }
        }
    }

    private suspend fun runBusyAction(block: suspend () -> Unit) {
        _uiState.update { it.copy(isWorking = true) }
        runCatching { block() }
            .onFailure { error ->
                _messages.tryEmit(error.message ?: "Backup action failed.")
            }
        _uiState.update { state ->
            state.copy(
                isWorking = false,
                suggestedExportFileName = DataBackupManager.suggestedExportFileName(),
            )
        }
    }
}
