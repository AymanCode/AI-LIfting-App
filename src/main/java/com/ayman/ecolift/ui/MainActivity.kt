package com.ayman.ecolift.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ayman.ecolift.R
import com.ayman.ecolift.android.platform.AndroidBackupUi
import com.ayman.ecolift.android.platform.AndroidDeviceActions
import com.ayman.ecolift.android.platform.AndroidHost
import com.ayman.ecolift.android.platform.LocalAndroidDeviceActions
import com.ayman.ecolift.android.platform.RestoreSource
import com.ayman.ecolift.android.room.LoadedRepositories
import com.ayman.ecolift.android.room.RepositoryOwner
import com.ayman.ecolift.android.room.SEED_DEMO_EXTRA
import com.ayman.ecolift.android.services.AndroidAppServices
import com.ayman.ecolift.android.services.AndroidDismissedHealthConnectUids
import com.ayman.ecolift.android.services.HealthConnectImport
import com.ayman.ecolift.android.services.seenHealthConnectUids
import com.ayman.ecolift.cardio.health.CardioHealthConnectReader
import com.ayman.ecolift.data.CardioRepository
import com.ayman.ecolift.ui.navigation.AppNavigation
import com.ayman.ecolift.web.LocalAppServices
import com.ayman.ecolift.web.LocalRepos
import com.ayman.ecolift.web.PhotoPickerBridge
import com.ayman.ecolift.web.util.WebHapticFeedback
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    internal val repositoryOwner: RepositoryOwner by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidHost.install(this)
        PhotoPickerBridge.register(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val seedRequested = intent.getBooleanExtra(SEED_DEMO_EXTRA, false)
        setContentView(View(this).apply { setBackgroundColor(getColor(R.color.startup_espresso)) })
        val initialization = repositoryOwner.initialize(seedRequested)
        lifecycleScope.launch {
            val loaded = initialization.await()
            setContent {
                LoadedApp(owner = repositoryOwner, loaded = loaded)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AndroidHost.install(this)
    }
}

@Composable
private fun LoadedApp(owner: RepositoryOwner, loaded: LoadedRepositories) {
    val context = LocalContext.current.applicationContext
    val repos = loaded.repos
    val database = loaded.database
    val services = remember { AndroidAppServices(context) }
    val haptics = remember { WebHapticFeedback() }
    val reader = remember { CardioHealthConnectReader(context) }
    val cardioStore = remember { CardioRepository(database) }
    val importer = remember {
        HealthConnectImport(
            reader = reader,
            existingHealthConnectUids = {
                seenHealthConnectUids(
                    cardioStore.getAll().map { it.hcUid },
                    AndroidDismissedHealthConnectUids,
                )
            },
            upsert = { session ->
                cardioStore.upsertHealthConnect(session)
            },
        )
    }
    val healthSessionsAdded by owner.healthSessionsAdded.collectAsStateWithLifecycle()
    val backupState by owner.backups.state.collectAsStateWithLifecycle()
    var showLocalBackups by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        owner.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        owner.backups.requestRestore(RestoreSource.Document(uri.toString()))
    }
    val healthPermission = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        owner.runDeviceAction("Health Connect import failed. Try again.") {
            val added = if (importer.exerciseSessionReadPermission() in granted) {
                importer.importRecentSessions()
            } else {
                0
            }
            repos.loadAll()
            owner.healthSessionsAdded.value = added
            null
        }
    }
    CompositionLocalProvider(
        LocalRepos provides repos,
        LocalAppServices provides services,
        LocalHapticFeedback provides haptics,
        LocalAndroidDeviceActions provides AndroidDeviceActions(
            backup = { exportLauncher.launch("ecolift-backup.json") },
            restore = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            localBackups = {
                owner.refreshBackups()
                showLocalBackups = true
            },
            busy = backupState.busy,
            importHealth = {
                if (!reader.isAvailable()) {
                    owner.healthSessionsAdded.value = 0
                } else {
                    healthPermission.launch(setOf(importer.exerciseSessionReadPermission()))
                }
            },
            healthSessionsAdded = healthSessionsAdded,
        ),
    ) {
        AppNavigation()
        AndroidBackupUi(
            state = backupState,
            showLocalBackups = showLocalBackups,
            onCloseLocalBackups = { showLocalBackups = false },
            onCreateLocalBackup = owner::createLocalBackup,
            onRequestRestore = owner.backups::requestRestore,
            onConfirmRestore = owner::confirmRestore,
            onCancelRestore = owner.backups::cancelRestore,
            onDismissMessage = owner.backups::dismissMessage,
        )
    }
}
