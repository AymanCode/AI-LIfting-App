package com.ayman.ecolift.android.platform

import com.ayman.ecolift.data.LocalBackupInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

sealed interface RestoreSource {
    data class Document(val uri: String) : RestoreSource

    data class Local(val fileName: String) : RestoreSource
}

data class BackupActionState(
    val busy: Boolean = false,
    val pendingRestore: RestoreSource? = null,
    val localBackups: List<LocalBackupInfo> = emptyList(),
    val message: String? = null,
)

class BackupActions {
    private val mutableState = MutableStateFlow(BackupActionState())
    val state = mutableState.asStateFlow()
    private val actionLock = Mutex()

    fun requestRestore(source: RestoreSource) {
        mutableState.update { if (it.busy) it else it.copy(pendingRestore = source, message = null) }
    }

    fun cancelRestore() {
        mutableState.update { it.copy(pendingRestore = null) }
    }

    fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }

    fun updateLocalBackups(backups: List<LocalBackupInfo>) {
        mutableState.update { it.copy(localBackups = backups.sortedByDescending(LocalBackupInfo::createdAtEpochMs)) }
    }

    suspend fun confirmRestore(restore: suspend (RestoreSource) -> String) {
        run("Import failed. Choose a valid EcoLift backup.") {
            val source = state.value.pendingRestore ?: return@run null
            mutableState.update { it.copy(pendingRestore = null) }
            restore(source)
        }
    }

    suspend fun run(failureMessage: String, action: suspend () -> String?) {
        if (!actionLock.tryLock()) return
        mutableState.update { it.copy(busy = true, message = null) }
        try {
            val message = action()
            mutableState.update { it.copy(message = message) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(message = failureMessage) }
        } finally {
            mutableState.update { it.copy(busy = false) }
            actionLock.unlock()
        }
    }
}
