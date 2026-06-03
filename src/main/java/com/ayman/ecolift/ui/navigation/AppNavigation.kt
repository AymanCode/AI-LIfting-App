package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ayman.ecolift.ui.theme.GlassAmbientBackground
import com.ayman.ecolift.ui.theme.GlassPalette
import com.ayman.ecolift.ui.theme.GlassTheme
import com.ayman.ecolift.ui.theme.glassPanel
import com.ayman.ecolift.ui.theme.palette
import com.ayman.ecolift.ui.viewmodel.GlassSettingsViewModel

/**
 * Drives the scroll-linked fade of the log screen's chrome (top bar + bottom nav).
 *
 * `reveal` is 1f when the bars are fully shown and 0f when fully hidden. It is read
 * ONLY inside `graphicsLayer { }` lambdas so updates stay in the draw phase and never
 * trigger recomposition while the user is scrolling. `applyScrollDelta` is fed raw
 * nested-scroll deltas; `lock`/`animateTo` handle discrete forces (sheets, keyboard).
 */
@Stable
class ChromeRevealState(initial: Float = 1f) {
    var reveal by mutableFloatStateOf(initial)
        private set

    /** Distance in px over which a full hide/show happens while scrolling. */
    var hideDistancePx: Float = 200f

    /** When locked, scroll deltas are ignored (an overlay owns the chrome). */
    var locked by mutableStateOf(false)

    fun applyScrollDelta(deltaY: Float) {
        if (locked) return
        reveal = (reveal + deltaY / hideDistancePx).coerceIn(0f, 1f)
    }

    fun snap(value: Float) {
        reveal = value.coerceIn(0f, 1f)
    }

    suspend fun animateTo(target: Float, durationMillis: Int = 180) {
        animate(reveal, target.coerceIn(0f, 1f), animationSpec = tween(durationMillis)) { v, _ ->
            reveal = v
        }
    }
}

private data class AppDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

internal fun buildLogRouteForSplit(splitId: Long): String = "log/$splitId"

internal fun buildProgressRouteForExercise(exerciseId: Long): String = "progress/$exerciseId"

internal fun buildCycleArchiveRoute(archiveId: Long): String = "cycleArchive/$archiveId"

internal fun isRouteSelected(currentRoute: String?, tabRoute: String): Boolean {
    if (tabRoute == "split" && currentRoute?.startsWith("cycleArchive/") == true) {
        return true
    }
    return currentRoute == tabRoute || currentRoute?.startsWith("$tabRoute/") == true
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val glassSettingsViewModel: GlassSettingsViewModel = viewModel()
    val paletteChoice by glassSettingsViewModel.paletteChoice.collectAsStateWithLifecycle()
    val palette = paletteChoice.palette()
    
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isLogRoute = isRouteSelected(currentRoute, "log")
    val chromeReveal = remember { ChromeRevealState() }

    // Check if keyboard is visible to hide the BottomBar
    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    val destinations = listOf(
        AppDestination("log", Icons.Filled.DateRange, "Log"),
        AppDestination("progress", Icons.AutoMirrored.Filled.ShowChart, "Progress"),
        AppDestination("ai", Icons.Filled.AutoAwesome, "IronMind"),
        AppDestination("split", Icons.Filled.Refresh, "Split"),
    )

    fun navigateToTab(route: String) {
        if (isRouteSelected(currentRoute, route)) {
            return
        }

        if (route == "log") {
            chromeReveal.snap(1f)
            chromeReveal.locked = false
        }

        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        }
    }

    LaunchedEffect(isLogRoute) {
        if (!isLogRoute) {
            chromeReveal.snap(1f)
            chromeReveal.locked = false
        }
    }

    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarReservedHeight = 64.dp + navigationBarPadding

    GlassTheme(palette = palette) {
        Box(modifier = Modifier.fillMaxSize()) {
            GlassAmbientBackground(palette = palette)

            NavHost(
                navController = navController,
                startDestination = "log",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (!isLogRoute && !isKeyboardVisible) bottomBarReservedHeight else 0.dp),
            ) {
                composable("log") {
                    TodayScreen(
                        chromeReveal = chromeReveal,
                        paletteChoice = paletteChoice,
                        onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice,
                        palette = palette,
                    )
                }
                composable(
                    route = "log/{splitId}",
                    arguments = listOf(navArgument("splitId") { type = NavType.LongType })
                ) { entry ->
                    TodayScreen(
                        initialSplitId = entry.arguments?.getLong("splitId"),
                        chromeReveal = chromeReveal,
                        paletteChoice = paletteChoice,
                        onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice,
                        palette = palette,
                    )
                }
                composable("progress") {
                    ProgressScreen(
                        onOpenBackups = { navController.navigate("backups") },
                        paletteChoice = paletteChoice,
                        onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice,
                    )
                }
                composable(
                    route = "progress/{exerciseId}",
                    arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
                ) { entry ->
                    ProgressScreen(
                        initialExerciseId = entry.arguments?.getLong("exerciseId"),
                        onOpenBackups = { navController.navigate("backups") },
                        paletteChoice = paletteChoice,
                        onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice,
                    )
                }
                composable("ai") {
                    AiScreen(
                        paletteChoice = paletteChoice,
                        onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice,
                    )
                }
                composable("split") {
                    SplitScreen(
                        onNavigateToLog = { splitId ->
                            navController.navigate(buildLogRouteForSplit(splitId)) {
                                launchSingleTop = true
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                            }
                        },
                        onNavigateToExerciseProgress = { exerciseId ->
                            navController.navigate(buildProgressRouteForExercise(exerciseId)) {
                                launchSingleTop = true
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                            }
                        },
                        onNavigateToArchiveDetail = { archiveId ->
                            navController.navigate(buildCycleArchiveRoute(archiveId))
                        },
                        paletteChoice = paletteChoice,
                        onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice,
                    )
                }
                composable(
                    route = "cycleArchive/{archiveId}",
                    arguments = listOf(navArgument("archiveId") { type = NavType.LongType })
                ) { entry ->
                    CycleArchiveDetailScreen(
                        archiveId = entry.arguments?.getLong("archiveId") ?: -1L,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("backups") {
                    BackupScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // The bar is removed from layout only for discrete, infrequent changes (system
            // IME). The scroll-linked hide/show is a draw-phase fade via graphicsLayer, so it
            // never recomposes or relayouts while the list is being scrolled.
            AnimatedVisibility(
                visible = !isKeyboardVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f),
                enter = fadeIn(animationSpec = tween(120)) +
                    slideInVertically(animationSpec = tween(160)) { it / 2 },
                exit = fadeOut(animationSpec = tween(110)) +
                    slideOutVertically(animationSpec = tween(150)) { it / 2 }
            ) {
                AppBottomBar(
                    destinations = destinations,
                    currentRoute = currentRoute,
                    onNavigate = ::navigateToTab,
                    palette = palette,
                    modifier = Modifier.graphicsLayer {
                        val r = chromeReveal.reveal
                        alpha = r
                        translationY = (1f - r) * size.height
                    }
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    destinations: List<AppDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    palette: GlassPalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(palette, RoundedCornerShape(18.dp), strong = true),
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, palette.glassStrokeStrong),
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { destination ->
                    val selected = isRouteSelected(currentRoute, destination.route)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onNavigate(destination.route) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) palette.accentStrong.copy(alpha = 0.18f) else Color.Transparent,
                        border = if (selected) {
                            BorderStroke(1.dp, palette.glassStrokeStrong)
                        } else {
                            BorderStroke(1.dp, Color.Transparent)
                        },
                        contentColor = if (selected) palette.ink else palette.inkMuted
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                                modifier = Modifier.size(17.dp),
                                tint = if (selected) palette.accentStrong else palette.inkMuted
                            )
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) palette.accentStrong else palette.inkMuted,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
