package com.ayman.ecolift.ui.navigation

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ayman.ecolift.data.LocalBackupInfo
import com.ayman.ecolift.ui.viewmodel.BackupViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingRestore by remember { mutableStateOf<LocalBackupInfo?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportToUri(uri)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        pendingImport = uri
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import Backup") },
            text = { Text("This will replace your current workout history and split data with the selected backup.") },
            confirmButton = {
                Button(onClick = {
                    val uri = pendingImport
                    pendingImport = null
                    if (uri != null) viewModel.importFromUri(uri)
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingImport = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore Local Backup") },
            text = { Text("This will replace your current workout history with the selected automatic backup.") },
            confirmButton = {
                Button(onClick = {
                    val backup = pendingRestore
                    pendingRestore = null
                    if (backup != null) viewModel.restoreLocalBackup(backup.fileName)
                }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRestore = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backups", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Protect your history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Manual export survives app updates and app-data clears. Automatic local backups are created on startup and before restore/import actions.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { exportLauncher.launch(uiState.suggestedExportFileName) },
                                enabled = !uiState.isWorking,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Export")
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                                enabled = !uiState.isWorking,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Import")
                            }
                        }
                        OutlinedButton(
                            onClick = viewModel::createLocalBackupNow,
                            enabled = !uiState.isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Create Local Backup Now")
                        }
                        if (uiState.isWorking) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                Text("Working…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            item {
                Text("Automatic local backups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (uiState.localBackups.isEmpty()) {
                item {
                    Text("No automatic backups yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(uiState.localBackups, key = { it.fileName }) { backup ->
                    AutomaticBackupCard(
                        backup = backup,
                        onRestore = { pendingRestore = backup },
                        enabled = !uiState.isWorking,
                        formattedSize = Formatter.formatShortFileSize(context, backup.sizeBytes),
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomaticBackupCard(
    backup: LocalBackupInfo,
    onRestore: () -> Unit,
    enabled: Boolean,
    formattedSize: String,
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a").withZone(ZoneId.systemDefault())
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(backup.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${formatter.format(Instant.ofEpochMilli(backup.createdAtEpochMs))} • $formattedSize",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(2.dp))
            OutlinedButton(onClick = onRestore, enabled = enabled) {
                Text("Restore This Backup")
            }
        }
    }
}
