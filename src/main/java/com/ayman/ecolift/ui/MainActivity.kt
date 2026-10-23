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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.ayman.ecolift.BuildConfig
import com.ayman.ecolift.R
import com.ayman.ecolift.android.platform.AndroidDeviceActions
import com.ayman.ecolift.android.platform.AndroidHost
import com.ayman.ecolift.android.platform.LocalAndroidDeviceActions
import com.ayman.ecolift.android.room.RoomRepos
import com.ayman.ecolift.android.room.SEED_DEMO_EXTRA
import com.ayman.ecolift.android.room.shouldSeedDemo
import com.ayman.ecolift.android.services.AndroidAppServices
import com.ayman.ecolift.android.services.HealthConnectImport
import com.ayman.ecolift.cardio.health.CardioHealthConnectReader
import com.ayman.ecolift.data.AppDatabase
import com.ayman.ecolift.data.CardioRepository
import com.ayman.ecolift.data.DataBackupManager
import com.ayman.ecolift.ui.navigation.AppNavigation
import com.ayman.ecolift.ui.theme.loadAppFonts
import com.ayman.ecolift.web.LocalAppServices
import com.ayman.ecolift.web.LocalRepos
import com.ayman.ecolift.web.PhotoPickerBridge
import com.ayman.ecolift.web.demo.DemoData
import com.ayman.ecolift.web.util.WebHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var repos: RoomRepos? = null

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
        lifecycleScope.launch {
            val (database, roomRepos) = withContext(Dispatchers.IO) {
                loadAppFonts()
                val database = AppDatabase.getInstance(this@MainActivity)
                val roomRepos = RoomRepos(db = database, scope = lifecycleScope)
                if (shouldSeedDemo(BuildConfig.DEBUG, seedRequested, database.workoutSetDao().hasAny())) {
                    roomRepos.installDemoSeed(DemoData.generate())
                }
                roomRepos.loadAll()
                database to roomRepos
            }
            repos = roomRepos
            setContent {
                LoadedApp(repos = roomRepos, database = database)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AndroidHost.install(this)
    }

    override fun onDestroy() {
        repos?.close()
        repos = null
        super.onDestroy()
    }
}

@Composable
private fun LoadedApp(repos: RoomRepos, database: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val services = remember { AndroidAppServices(context) }
    val haptics = remember { WebHapticFeedback() }
    val reader = remember { CardioHealthConnectReader(context) }
    val cardioStore = remember { CardioRepository(database) }
    val importer = remember {
        HealthConnectImport(
            reader = reader,
            existingHealthConnectUids = { cardioStore.getAll().mapNotNull { it.hcUid }.toSet() },
            upsert = { session ->
                cardioStore.upsertHealthConnect(session)
            },
        )
    }
    var healthSessionsAdded by remember { mutableStateOf<Int?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            DataBackupManager.exportToUri(context, database, uri)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            DataBackupManager.importFromUri(context, database, uri)
            repos.loadAll()
        }
    }
    val healthPermission = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        scope.launch {
            val added = if (importer.exerciseSessionReadPermission() in granted) {
                importer.importRecentSessions()
            } else {
                0
            }
            repos.loadAll()
            healthSessionsAdded = added
        }
    }
    CompositionLocalProvider(
        LocalRepos provides repos,
        LocalAppServices provides services,
        LocalHapticFeedback provides haptics,
        LocalAndroidDeviceActions provides AndroidDeviceActions(
            backup = { exportLauncher.launch("ecolift-backup.json") },
            restore = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            importHealth = {
                if (!reader.isAvailable()) {
                    healthSessionsAdded = 0
                } else {
                    healthPermission.launch(setOf(importer.exerciseSessionReadPermission()))
                }
            },
            healthSessionsAdded = healthSessionsAdded,
        ),
    ) {
        AppNavigation()
    }
}
