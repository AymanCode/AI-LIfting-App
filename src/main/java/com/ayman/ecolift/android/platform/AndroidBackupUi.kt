package com.ayman.ecolift.android.platform

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.EspressoShape
import com.ayman.ecolift.ui.theme.EspressoType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidBackupUi(
    state: BackupActionState,
    showLocalBackups: Boolean,
    onCloseLocalBackups: () -> Unit,
    onCreateLocalBackup: () -> Unit,
    onRequestRestore: (RestoreSource) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val context = LocalContext.current
    if (showLocalBackups) {
        ModalBottomSheet(
            onDismissRequest = onCloseLocalBackups,
            containerColor = EspressoPalette.bg,
            contentColor = EspressoPalette.ink,
            scrimColor = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            tonalElevation = 0.dp,
            dragHandle = {
                Box(
                    Modifier.padding(top = 10.dp, bottom = 12.dp).size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape).background(EspressoPalette.hair2),
                )
            },
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Local backups", style = EspressoType.name.copy(fontSize = 22.sp, lineHeight = 26.sp), color = EspressoPalette.ink)
                TextButton(onClick = onCreateLocalBackup, enabled = !state.busy) {
                    Text("Create local backup", style = EspressoType.body, color = EspressoPalette.arms)
                }
                if (state.localBackups.isEmpty()) Text("No local backups yet.", style = EspressoType.body, color = EspressoPalette.dim)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    items(state.localBackups, key = { it.fileName }) { backup ->
                        val date = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a")
                            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(backup.createdAtEpochMs))
                        Column(
                            Modifier.fillMaxWidth().clip(EspressoShape.card).background(EspressoPalette.panel)
                                .clickable(enabled = !state.busy) { onRequestRestore(RestoreSource.Local(backup.fileName)) }
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(date, color = EspressoPalette.ink, style = EspressoType.body)
                            Text(Formatter.formatFileSize(context, backup.sizeBytes), color = EspressoPalette.dim, style = EspressoType.body)
                            Text("Restore", color = EspressoPalette.arms, style = EspressoType.body)
                        }
                    }
                }
            }
        }
    }
    if (state.pendingRestore != null) {
        AlertDialog(
            onDismissRequest = onCancelRestore,
            containerColor = EspressoPalette.panel,
            titleContentColor = EspressoPalette.ink,
            textContentColor = EspressoPalette.dim,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Import backup", style = EspressoType.name.copy(fontSize = 22.sp, lineHeight = 26.sp)) },
            text = { Text("This will replace your current workout history and split data with the selected backup.", style = EspressoType.body) },
            confirmButton = { TextButton(onClick = onConfirmRestore, enabled = !state.busy) { Text("Import", style = EspressoType.body, color = EspressoPalette.arms) } },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text("Cancel", style = EspressoType.body, color = EspressoPalette.dim) } },
        )
    }
    if (state.busy) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Column(
                Modifier.clip(EspressoShape.card).background(EspressoPalette.panel).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(color = EspressoPalette.arms)
                Text("Working...", style = EspressoType.body, color = EspressoPalette.ink)
            }
        }
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissMessage,
            containerColor = EspressoPalette.panel,
            titleContentColor = EspressoPalette.ink,
            textContentColor = EspressoPalette.dim,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Backup", style = EspressoType.name.copy(fontSize = 22.sp, lineHeight = 26.sp)) },
            text = { Text(message, style = EspressoType.body) },
            confirmButton = { TextButton(onClick = onDismissMessage) { Text("OK", style = EspressoType.body, color = EspressoPalette.arms) } },
        )
    }
}
