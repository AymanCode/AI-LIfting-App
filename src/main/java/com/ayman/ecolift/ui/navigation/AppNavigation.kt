package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import com.ayman.ecolift.ui.theme.DawnBackground
import com.ayman.ecolift.ui.theme.DawnPalette
import com.ayman.ecolift.ui.theme.QuietDepth
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.espressoTabColor
import com.ayman.ecolift.ui.theme.QuietDepthBackground
import com.ayman.ecolift.ui.theme.QuietIcon
import com.ayman.ecolift.ui.theme.QuietIconName
import com.ayman.ecolift.ui.theme.QuietUiFontFamily
import com.ayman.ecolift.ui.theme.rememberReducedMotion
import com.ayman.ecolift.ui.theme.TAB_SHIFT_DP
import com.ayman.ecolift.ui.theme.TAB_TRANSITION_MS
import com.ayman.ecolift.ui.theme.TabTransitionEasing
import com.ayman.ecolift.ui.theme.watchTouchesForWarmup
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ayman.ecolift.ui.theme.GlassPalette
import com.ayman.ecolift.ui.theme.GlassTheme
import com.ayman.ecolift.ui.theme.palette
import com.ayman.ecolift.ui.viewmodel.GlassSettingsViewModel
import com.ayman.ecolift.ui.viewmodel.HubViewModel
import com.ayman.ecolift.web.LocalRepos
import com.ayman.ecolift.ui.cardio.CardioHighlightsScreen
import com.ayman.ecolift.ui.cardio.CardioLogScreen
import com.ayman.ecolift.ui.cardio.CardioMachineScreen
import com.ayman.ecolift.ui.cardio.CardioScanScreen
import com.ayman.ecolift.ui.cardio.CardioSessionsScreen
import com.ayman.ecolift.ui.cardio.CardioTab
import com.ayman.ecolift.ui.cardio.CardioTabWarmup
import com.ayman.ecolift.ui.theme.DawnTheme
import com.ayman.ecolift.ui.viewmodel.CardioViewModel
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.web.LocalAppServices


private data class AppDestination(
    val route: String,
    val icon: QuietIconName,
    val label: String,
)

/**
 * Where a Smart Suggest flip returns after the activity restarts. Held in memory for this
 * process, read once at startup, then cleared. The settings sheet is included only when the
 * flip was made from that sheet.
 */
internal sealed interface ResumeLanding {
    val route: String
    val opensSettings: Boolean

    data object Split : ResumeLanding {
        override val route = "split"
        override val opensSettings = false
    }

    data class IronMind(val settingsOpen: Boolean) : ResumeLanding {
        override val route = "ai"
        override val opensSettings = settingsOpen
    }
}

internal fun resumeLandingFor(route: String?, settingsOpen: Boolean): ResumeLanding? = when (route) {
    "split" -> ResumeLanding.Split
    "ai" -> ResumeLanding.IronMind(settingsOpen = settingsOpen)
    else -> null
}

internal fun encodeResumeLanding(landing: ResumeLanding): String = when (landing) {
    ResumeLanding.Split -> "split"
    is ResumeLanding.IronMind -> if (landing.settingsOpen) "ai|settings" else "ai"
}

internal fun decodeResumeLanding(raw: String?): ResumeLanding? = when (raw) {
    "split" -> ResumeLanding.Split
    "ai" -> ResumeLanding.IronMind(settingsOpen = false)
    "ai|settings" -> ResumeLanding.IronMind(settingsOpen = true)
    else -> null
}

internal object ResumeRouteStore {
    private var saved: String? = null

    fun save(route: String?, settingsOpen: Boolean) {
        val encoded = resumeLandingFor(route, settingsOpen)?.let(::encodeResumeLanding) ?: return
        saved = encoded
    }

    fun take(): ResumeLanding? = decodeResumeLanding(saved).also { saved = null }
}

internal fun buildLogRouteForSplit(splitId: Long): String = "log/$splitId"

internal const val LOG_SESSION_ROUTE = "log/session/{date}/{exerciseId}"
private const val REPORT_WORKOUT_ROUTE = "progress/workout/{date}/{exerciseId}"

internal const val PROGRESS_HIGHLIGHTS_REEL_ROUTE = "progress/highlights/reel"

internal fun buildCycleArchiveRoute(archiveId: Long): String = "cycleArchive/$archiveId"

internal fun isProgressHighlightsReelRoute(currentRoute: String?): Boolean =
    currentRoute == PROGRESS_HIGHLIGHTS_REEL_ROUTE

internal fun isRouteSelected(currentRoute: String?, tabRoute: String): Boolean {
    if (tabRoute == "split" && currentRoute?.startsWith("cycleArchive/") == true) {
        return true
    }
    // A route pattern with a query argument (NavDestination.route keeps it verbatim, e.g.
    // "tab?arg={arg}") would otherwise fail to match its own tab: guard on the "?" boundary
    // too, not only "/". No current tab route has one, but this is cheap to keep correct.
    return currentRoute == tabRoute ||
        currentRoute?.startsWith("$tabRoute/") == true ||
        currentRoute?.startsWith("$tabRoute?") == true
}

internal fun shouldIgnoreBottomTabTap(currentRoute: String?, targetRoute: String): Boolean =
    targetRoute != "log" && isRouteSelected(currentRoute, targetRoute)

/** The bottom nav's height above the system inset; the Log's rest tab rides on its top edge. */
internal val AppBottomBarHeight = 74.dp

internal fun shouldShowGlobalBottomBar(
    currentRoute: String?,
    isKeyboardVisible: Boolean,
): Boolean = !isKeyboardVisible && !isProgressHighlightsReelRoute(currentRoute)

@Composable
fun AppNavigation(
    onImmersiveRouteChanged: (Boolean) -> Unit = {},
) {
    val repos = LocalRepos.current
    val services = LocalAppServices.current
    val navController = rememberNavController()
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val cardioViewModel: CardioViewModel = viewModel { CardioViewModel(repos, services.vision) }
    val reducedMotion = rememberReducedMotion()
    val motionDuration = if (reducedMotion) 0 else TAB_TRANSITION_MS
    val tabEasing = TabTransitionEasing
    val tabShiftPx = with(androidx.compose.ui.platform.LocalDensity.current) { TAB_SHIFT_DP.dp.toPx() }

    val glassSettingsViewModel: GlassSettingsViewModel = viewModel { GlassSettingsViewModel(repos) }
    val hubViewModel: HubViewModel = viewModel { HubViewModel(repos) }
    val emphasisViewModel: com.ayman.ecolift.ui.viewmodel.EmphasisViewModel = viewModel {
        com.ayman.ecolift.ui.viewmodel.EmphasisViewModel(repos)
    }
    val paletteChoice by glassSettingsViewModel.paletteChoice.collectAsStateWithLifecycle()
    val userBodyweightLbs by glassSettingsViewModel.userBodyweightLbs.collectAsStateWithLifecycle()
    val smartSuggestEnabled by glassSettingsViewModel.smartSuggestEnabled.collectAsStateWithLifecycle()
    val hubCount by hubViewModel.count.collectAsStateWithLifecycle()
    val hasUnseenClaudeChange by hubViewModel.hasUnseenClaudeChange.collectAsStateWithLifecycle()
    val palette = paletteChoice.palette()
    
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isExerciseReport = currentRoute == EXERCISE_REPORT_ROUTE
    val isReportWorkout = currentRoute == REPORT_WORKOUT_ROUTE
    val isReportVisit = isExerciseReport || isReportWorkout
    val originEntry = if (isReportWorkout) navController.previousBackStackEntry else backStackEntry
    val reportOrigin = if (isReportVisit && originEntry?.savedStateHandle?.get<Boolean>("reportPushed") == true) parseExerciseReportOrigin(
        originEntry?.savedStateHandle?.get<String>("origin"),
        originEntry?.savedStateHandle?.get<String>("date"),
        originEntry?.savedStateHandle?.get<String>("ex"),
    ) else ExerciseReportOrigin()
    val selectedTabRoute = if (isExerciseReport) {
        when (reportOrigin.tab) { "month" -> "progress"; "ironmind" -> "ai"; else -> reportOrigin.tab }
    } else currentRoute
    // Set synchronously before navigate: the push pauses the Log before recomposition.
    val reportCoveringLog = remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val reportOriginTab = reportOrigin.tab
    LaunchedEffect(isReportVisit, reportOriginTab) {
        reportCoveringLog.value = isReportVisit && reportOriginTab == "log"
    }
    val isCardioRoute = currentRoute?.startsWith("cardio") == true
    val isLogRoute = isRouteSelected(currentRoute, "log")
    val isHighlightsReelRoute = isProgressHighlightsReelRoute(currentRoute)
    val chromeReveal = remember { ChromeRevealState() }

    // Check if keyboard is visible to hide the BottomBar
    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    val destinations = listOf(
        AppDestination("log", QuietIconName.Calendar, "Log"),
        AppDestination("progress", QuietIconName.Progress, "Progress"),
        AppDestination("ai", QuietIconName.Sparkles, "IronMind"),
        AppDestination("cardio", QuietIconName.Heart, "Cardio"),
        AppDestination("split", QuietIconName.Split, "Split"),
    )

    fun openExerciseReport(
        exerciseId: Long, origin: ExerciseReportOrigin = ExerciseReportOrigin(),
        range: String? = null, end: String? = null,
    ) {
        focusManager.clearFocus()
        reportCoveringLog.value = origin.tab == "log"
        navController.navigate(buildProgressRouteForExercise(exerciseId, origin)) { launchSingleTop = true }
        navController.currentBackStackEntry?.savedStateHandle?.apply {
            set("reportPushed", true)
            set("reportRange", range)
            set("reportEnd", end)
        }
    }

    fun returnFromExerciseReport() {
        val previousRoute = navController.previousBackStackEntry?.destination?.route
        if (isRouteSelected(previousRoute, when (reportOrigin.tab) { "month" -> "progress"; "ironmind" -> "ai"; else -> reportOrigin.tab }) ||
            (reportOrigin.tab == "log" && previousRoute == REPORT_WORKOUT_ROUTE) ||
            (reportOrigin.tab == "progress" && isProgressHighlightsReelRoute(previousRoute)) ||
            (reportOrigin.tab == "split" && previousRoute == "cycleArchive/{archiveId}")) {
            navController.popBackStack()
        } else {
            // A direct URL/reload has no trustworthy origin screen to restore.
            navController.popBackStack()
            navController.navigate("progress") { launchSingleTop = true }
        }
        reportCoveringLog.value = false
    }

    fun navigateToTab(route: String) {
        if (isReportWorkout) {
            navController.popBackStack()
            // The origin tab from a historical workout returns to its report, as before.
            if (shouldIgnoreBottomTabTap(reportOrigin.tab, route)) return
        } else if (isExerciseReport && shouldIgnoreBottomTabTap(selectedTabRoute, route)) {
            return
        }
        if (isReportVisit) {
            // A band on a historical workout can open another report. Tab switches leave the
            // entire temporary visit, so restoreState never resurrects a report on a tab landing.
            while (navController.currentDestination?.route == EXERCISE_REPORT_ROUTE ||
                navController.currentDestination?.route == REPORT_WORKOUT_ROUTE) {
                if (!navController.popBackStack()) break
            }
            reportCoveringLog.value = false
        }
        // A historical workout is a temporary visit from Progress. Do not save it on
        // Progress's tab stack: restoreState would reopen it when Progress is tapped.
        // Pop without saving first, preserving the originating exercise detail below it.
        if (navController.currentDestination?.route == LOG_SESSION_ROUTE) {
            navController.popBackStack()
        }
        val activeRoute = navController.currentDestination?.route
        if (shouldIgnoreBottomTabTap(activeRoute, route)) {
            return
        }

        chromeReveal.snap(1f)
        chromeReveal.locked = false

        if (route == "log") {
            if (isRouteSelected(activeRoute, "log")) {
                if (activeRoute != "log") {
                    navController.popBackStack("log", inclusive = false, saveState = true)
                }
                return
            }

            val returnedToLog = navController.popBackStack("log", inclusive = false, saveState = true)
            if (!returnedToLog) {
                navController.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                }
            }
            return
        }

        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
        }
    }

    // Smart Suggest is read when the screen starts. Restart the activity after a successful
    // save so the open tab comes back through ResumeRouteStore. A failed save does not restart.
    fun changeSmartSuggest(enabled: Boolean, settingsOpen: Boolean = false) {
        glassSettingsViewModel.setSmartSuggestEnabled(enabled) {
            ResumeRouteStore.save(navController.currentDestination?.route, settingsOpen)
            activity?.recreate()
        }
    }
    val resumeLanding = remember { ResumeRouteStore.take() }
    LaunchedEffect(navController) {
        resumeLanding?.let { navigateToTab(it.route) }
    }
    var resumeSettings by remember { mutableStateOf(resumeLanding?.opensSettings == true) }

    var openedHighlights by remember { mutableStateOf(com.ayman.ecolift.data.progress.highlights.HighlightReelSnapshot(emptyList())) }
    var openedHighlightPage by remember { mutableStateOf(0) }

    fun openProgressHighlights(snapshot: com.ayman.ecolift.data.progress.highlights.HighlightReelSnapshot, page: Int) {
        openedHighlights = snapshot
        openedHighlightPage = page
        navController.navigate(PROGRESS_HIGHLIGHTS_REEL_ROUTE) {
            launchSingleTop = true
        }
    }

    fun returnFromHighlightsToProgress() {
        val returnedToProgress = navController.popBackStack(
            route = "progress",
            inclusive = false,
        )
        if (!returnedToProgress) {
            navController.navigate("progress") {
                launchSingleTop = true
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
            }
        }
    }

    LaunchedEffect(isLogRoute) {
        if (!isLogRoute) {
            chromeReveal.snap(1f)
            chromeReveal.locked = false
        }
    }

    LaunchedEffect(isHighlightsReelRoute) {
        onImmersiveRouteChanged(isHighlightsReelRoute)
    }

    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarReservedHeight = AppBottomBarHeight + navigationBarPadding

    val swapOverlay = remember { SwapOverlayHost() }
    GlassTheme(palette = palette) {
        SwapOverlayRoot(swapOverlay, modifier = Modifier.fillMaxSize().watchTouchesForWarmup()) {
            // Drawn first, under the opaque background: nobody sees it, Skia still compiles for it.
            CalendarShaderWarmup()
            LogUndoPillWarmup()
            ProgressTabWarmup(bottomBarReservedHeight)
            SplitTabWarmup(bottomBarReservedHeight)
            IronMindTabWarmup(bottomBarReservedHeight)
            CardioTabWarmup(bottomBarReservedHeight)
            if (!isHighlightsReelRoute) {
                Crossfade(
                    targetState = isCardioRoute,
                    animationSpec = tween(if (reducedMotion) 0 else 200),
                ) { cardio ->
                    if (cardio) {
                        DawnBackground()
                    } else {
                        QuietDepthBackground()
                    }
                }
            }

            // Above the background, which would otherwise swallow it, and under the screens, which
            // take every touch first; see DeckSwipeWarmup.
            DeckSwipeWarmup(palette, bottomBarReservedHeight, enabled = !isCardioRoute && !isHighlightsReelRoute)

            NavHost(
                navController = navController,
                startDestination = "log",
                enterTransition = {
                    val direction = quietTabDirection(initialState.destination.route, targetState.destination.route)
                    fadeIn(tween(motionDuration, easing = tabEasing)) + slideInHorizontally(tween(motionDuration, easing = tabEasing)) { (tabShiftPx * direction).toInt() }
                },
                exitTransition = {
                    val direction = quietTabDirection(initialState.destination.route, targetState.destination.route)
                    fadeOut(tween(motionDuration, easing = tabEasing)) + slideOutHorizontally(tween(motionDuration, easing = tabEasing)) { (-tabShiftPx * direction).toInt() }
                },
                modifier = Modifier
                    .semantics { contentDescription = "App content" }
                    .onPreviewKeyEvent { swapOverlay.content != null }
                    .fillMaxSize()
                    .padding(
                        bottom = if (!isReportVisit && shouldShowGlobalBottomBar(currentRoute, isKeyboardVisible)) {
                            bottomBarReservedHeight
                        } else {
                            0.dp
                        },
                    ),
            ) {
                composable("log") {
                    TodayScreen(
                        isReportOpen = { reportCoveringLog.value },
                        onOpenExerciseProgress = { id, date, number ->
                            openExerciseReport(id, parseExerciseReportOrigin("log", date, number.toString()))
                        },
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
                        isReportOpen = { reportCoveringLog.value },
                        onOpenExerciseProgress = { id, date, number ->
                            openExerciseReport(id, parseExerciseReportOrigin("log", date, number.toString()))
                        },
                        initialSplitId = entry.savedStateHandle.get<Long>("splitId"),
                        chromeReveal = chromeReveal,
                        paletteChoice = paletteChoice,
                        onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice,
                        palette = palette,
                    )
                }
                composable(LOG_SESSION_ROUTE, arguments = listOf(
                    navArgument("date") { type = NavType.StringType }, navArgument("exerciseId") { type = NavType.LongType }
                )) { entry ->
                    TodayScreen(
                        isReportOpen = { reportCoveringLog.value },
                        onOpenExerciseProgress = { id, date, number ->
                            openExerciseReport(id, parseExerciseReportOrigin("log", date, number.toString()))
                        },
                        initialDate = entry.savedStateHandle.get<String>("date"),
                        initialExerciseId = entry.savedStateHandle.get<Long>("exerciseId"),
                        onReturnToProgress = { navController.popBackStack() },
                        chromeReveal = chromeReveal, paletteChoice = paletteChoice,
                        onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice, palette = palette)
                }
                composable("progress") {
                    ProgressScreen(
                        isVisible = currentRoute == "progress",
                        onOpenExerciseReport = { id, range, end -> openExerciseReport(id, range = range, end = end) },
                        onOpenMonthReport = { id, range, end -> openExerciseReport(id, ExerciseReportOrigin("month"), range, end) },
                        onOpenEmphasis = { navController.navigate(EMPHASIS_ROUTE) { launchSingleTop = true } },
                        onOpenBackups = {},
                        onOpenHighlightCard = ::openProgressHighlights,
                        onOpenWorkout = { date, exerciseId -> navController.navigate("log/session/$date/$exerciseId") },
                        paletteChoice = paletteChoice,
                        onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice,
                    )
                }
                composable(EMPHASIS_ROUTE) {
                    val emphasisState by emphasisViewModel.uiState.collectAsStateWithLifecycle()
                    EmphasisScreen(emphasisState, onBack = {
                        if (!navController.popBackStack("progress", inclusive = false)) {
                            navController.navigate("progress") {
                                popUpTo(EMPHASIS_ROUTE) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    })
                }
                composable(PROGRESS_HIGHLIGHTS_REEL_ROUTE) {
                    ProgressHighlightsRoute(
                        snapshot = openedHighlights,
                        initialPage = openedHighlightPage,
                        onOpenReport = { id -> openExerciseReport(id, ExerciseReportOrigin("progress")) },
                        onBack = ::returnFromHighlightsToProgress,
                        palette = palette,
                    )
                }
                // A normal push shares the canvas accessibility mirror with the origin. The
                // origin entry retains its ViewModel and saveable UI state until Back pops here.
                composable(
                    route = EXERCISE_REPORT_ROUTE,
                    arguments = listOf(
                        navArgument("exerciseId") { type = NavType.LongType },
                        navArgument("origin") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("date") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("ex") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { entry ->
                    LaunchedEffect(entry) {
                        // Reloads/direct URLs have no live origin to preserve. Seed Progress below
                        // them so the chip, system Back and browser Back have the same target.
                        if (entry.savedStateHandle.get<Boolean>("reportPushed") != true &&
                            navController.previousBackStackEntry?.destination?.route != "progress") {
                            val id = entry.savedStateHandle.get<Long>("exerciseId") ?: return@LaunchedEffect
                            navController.popBackStack()
                            navController.navigate("progress") { launchSingleTop = true }
                            openExerciseReport(id)
                        }
                    }
                    Column(Modifier.fillMaxSize().background(EspressoPalette.bg)) {
                        ProgressScreen(
                            isVisible = backStackEntry == entry,
                            initialExerciseId = entry.savedStateHandle.get<Long>("exerciseId"),
                            reportOrigin = reportOrigin,
                            initialReportRange = entry.savedStateHandle.get<String>("reportRange"),
                            initialReportEnd = entry.savedStateHandle.get<String>("reportEnd"),
                            onReportBack = ::returnFromExerciseReport,
                            onOpenWorkout = { date, exerciseId ->
                                navController.navigate("progress/workout/$date/$exerciseId")
                            },
                            paletteChoice = paletteChoice,
                            onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice,
                            modifier = Modifier.weight(1f),
                        )
                        AppBottomBar(
                            destinations = destinations,
                            currentRoute = selectedTabRoute,
                            onNavigate = ::navigateToTab,
                            palette = palette,
                            ironMindBadgeCount = hubCount,
                            ironMindHasUnseenChange = hasUnseenClaudeChange,
                        )
                    }
                }
                // A workout pushes above the same report entry; either Back path pops to it.
                composable(
                    route = REPORT_WORKOUT_ROUTE,
                    arguments = listOf(
                        navArgument("date") { type = NavType.StringType },
                        navArgument("exerciseId") { type = NavType.LongType },
                    ),
                ) { entry ->
                    Column(Modifier.fillMaxSize().background(EspressoPalette.bg)) {
                        TodayScreen(
                            initialDate = entry.savedStateHandle.get<String>("date"),
                            initialExerciseId = entry.savedStateHandle.get<Long>("exerciseId"),
                            onReturnToProgress = { navController.popBackStack() },
                            onOpenExerciseProgress = { id, date, number ->
                                openExerciseReport(id, parseExerciseReportOrigin("log", date, number.toString()))
                            },
                            isReportOpen = { navController.currentDestination?.route == EXERCISE_REPORT_ROUTE },
                            chromeReveal = chromeReveal,
                            paletteChoice = paletteChoice,
                            onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice,
                            palette = palette,
                            modifier = Modifier.weight(1f),
                        )
                        AppBottomBar(
                            destinations = destinations, currentRoute = "log", onNavigate = ::navigateToTab,
                            palette = palette, ironMindBadgeCount = hubCount, ironMindHasUnseenChange = hasUnseenClaudeChange,
                            modifier = Modifier.graphicsLayer {
                                val r = chromeReveal.reveal
                                alpha = r
                                translationY = (1f - r) * size.height
                            },
                        )
                    }
                }
                composable("ai") {
                    AiScreen(
                        sharedHubViewModel = hubViewModel,
                        onOpenNoticed = { id -> openExerciseReport(id, ExerciseReportOrigin("ironmind")) },
                        onOpenGroup = { slotId ->
                            DeskSplitDeepLink.request(slotId)
                            navController.navigate("split") { launchSingleTop = true }
                        },
                        paletteChoice = paletteChoice,
                        onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice,
                        userBodyweightLbs = userBodyweightLbs,
                        onUserBodyweightChange = glassSettingsViewModel::setUserBodyweightLbs,
                        smartSuggestEnabled = smartSuggestEnabled,
                        onSmartSuggestChange = { changeSmartSuggest(it, settingsOpen = true) },
                        openSettingsOnStart = resumeSettings,
                        onSettingsOpenedOnStart = { resumeSettings = false },
                    )
                }
                composable("cardio") {
                    DawnTheme {
                        CardioTab(
                            viewModel = cardioViewModel,
                            onLog = {
                                cardioViewModel.startNewDraft()
                                runCatching { navController.navigate("cardio/log") }
                            },
                            onEditSession = { id ->
                                runCatching { navController.navigate("cardio/log/$id") }
                            },
                            onScan = {
                                cardioViewModel.startScan()
                                runCatching { navController.navigate("cardio/scan") }
                            },
                            onHighlights = {
                                runCatching { navController.navigate("cardio/highlights") }
                            },
                            onAllSessions = {
                                runCatching { navController.navigate("cardio/sessions") }
                            },
                            onMachine = { type ->
                                runCatching { navController.navigate("cardio/machine/${type.name}") }
                            },
                        )
                    }
                }
                composable("cardio/log") {
                    DawnTheme {
                        CardioLogScreen(
                            viewModel = cardioViewModel,
                            editingId = null,
                            onDone = { navController.popBackStack() },
                        )
                    }
                }
                composable(
                    route = "cardio/log/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.savedStateHandle.get<Long>("id")
                    DawnTheme {
                        CardioLogScreen(
                            viewModel = cardioViewModel,
                            editingId = id,
                            onDone = { navController.popBackStack() },
                        )
                    }
                }
                composable("cardio/scan") {
                    DawnTheme {
                        CardioScanScreen(
                            viewModel = cardioViewModel,
                            onDone = { navController.popBackStack() },
                            onCancel = { navController.popBackStack() },
                        )
                    }
                }
                composable(
                    route = "cardio/machine/{type}",
                    arguments = listOf(navArgument("type") { type = NavType.StringType }),
                ) { entry ->
                    val typeStr = entry.savedStateHandle.get<String>("type")
                    val type = runCatching { CardioActivityType.valueOf(typeStr ?: "") }.getOrNull()
                    if (type == null) {
                        LaunchedEffect(Unit) {
                            navController.popBackStack()
                        }
                    } else {
                        DawnTheme {
                            CardioMachineScreen(
                                viewModel = cardioViewModel,
                                type = type,
                                onBack = { navController.popBackStack() },
                                onEditSession = { id ->
                                    runCatching { navController.navigate("cardio/log/$id") }
                                },
                            )
                        }
                    }
                }
                composable("cardio/highlights") {
                    DawnTheme {
                        CardioHighlightsScreen(
                            viewModel = cardioViewModel,
                            onBack = { navController.popBackStack() },
                            onMachine = { machineType ->
                                runCatching { navController.navigate("cardio/machine/${machineType.name}") }
                            },
                        )
                    }
                }
                composable("cardio/sessions") {
                    DawnTheme {
                        CardioSessionsScreen(
                            viewModel = cardioViewModel,
                            onBack = { navController.popBackStack() },
                            onEditSession = { id ->
                                runCatching { navController.navigate("cardio/log/$id") }
                            },
                        )
                    }
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
                            openExerciseReport(exerciseId, ExerciseReportOrigin("split"))
                        },
                        onNavigateToArchiveDetail = { archiveId ->
                            navController.navigate(buildCycleArchiveRoute(archiveId))
                        },
                        paletteChoice = paletteChoice,
                        onPaletteChoiceChange = glassSettingsViewModel::setPaletteChoice,
                        smartSuggestEnabled = smartSuggestEnabled,
                        onSmartSuggestChange = { changeSmartSuggest(it) },
                    )
                }
                composable(
                    route = "cycleArchive/{archiveId}",
                    arguments = listOf(navArgument("archiveId") { type = NavType.LongType })
                ) { entry ->
                    CycleArchiveDetailScreen(
                        archiveId = entry.savedStateHandle.get<Long>("archiveId") ?: -1L,
                        onBack = { navController.popBackStack() },
                        onOpenLiftReport = { openExerciseReport(it, ExerciseReportOrigin("split")) },
                    )
                }

            }

            // The bar leaves composition for the immersive reel and is removed from layout for
            // the system IME. Scroll-linked hide/show remains a draw-phase fade on Log, so it
            // never recomposes or relayouts while the list is being scrolled.
            if (!isHighlightsReelRoute && !isReportVisit) {
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
                        ironMindBadgeCount = hubCount,
                        ironMindHasUnseenChange = hasUnseenClaudeChange,
                        dawn = isCardioRoute,
                        modifier = Modifier
                            .semantics { contentDescription = "Main navigation" }
                            .onPreviewKeyEvent { swapOverlay.content != null }
                            .graphicsLayer {
                            val r = if (isLogRoute) chromeReveal.reveal else 1f
                            alpha = r
                            translationY = (1f - r) * size.height
                        }
                    )
                }
            }
        }
    }
}

internal fun quietTabDirection(from: String?, to: String?): Int {
    fun index(route: String?): Int = when {
        route?.startsWith("cycleArchive/") == true -> 4
        else -> listOf("log", "progress", "ai", "cardio", "split").indexOf(
            route?.substringBefore('/')?.substringBefore('?'),
        )
    }
    val start = index(from)
    val end = index(to)
    return if (start < 0 || end < 0) 0 else end.compareTo(start)
}

@Composable
private fun AppBottomBar(
    destinations: List<AppDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    palette: GlassPalette,
    ironMindBadgeCount: Int = 0,
    ironMindHasUnseenChange: Boolean = false,
    dawn: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Espresso: one flat bar on the masthead tone, no top line, labels dim/ink, an 18x3 pill in the
    // active tab's colour. Cardio routes keep their Dawn colours (spec §Bottom navigation).
    val reducedMotion = rememberReducedMotion()
    val duration = if (reducedMotion) 0 else 200
    val navBgColor by animateColorAsState(if (dawn) DawnPalette.navBackground else EspressoPalette.mast, tween(duration))
    val navLineColor by animateColorAsState(if (dawn) DawnPalette.navLine else Color.Transparent, tween(duration))
    val inactiveTintColor by animateColorAsState(if (dawn) DawnPalette.navInk else EspressoPalette.dim, tween(duration))
    val activeTintColor by animateColorAsState(if (dawn) DawnPalette.accent else EspressoPalette.ink, tween(duration))
    val markerColor by animateColorAsState(if (dawn) DawnPalette.accent else espressoTabColor(currentRoute), tween(duration))

    val selectedIndex = destinations.indexOfFirst { isRouteSelected(currentRoute, it.route) }.coerceAtLeast(0)
    val markerIndex by animateFloatAsState(selectedIndex.toFloat(), tween(if (reducedMotion) 0 else 230, easing = CubicBezierEasing(.22f, 1f, .36f, 1f)))
    Box(modifier.fillMaxWidth().background(navBgColor).windowInsetsPadding(WindowInsets.navigationBars)
        .semantics { contentDescription = "Active tab ${destinations[selectedIndex].label}" }) {
        Row(
            Modifier.fillMaxWidth().height(AppBottomBarHeight)
                .drawBehind {
                    if (dawn) drawLine(navLineColor, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
                    val inset = 10.dp.toPx()
                    val width = (size.width - inset * 2) / destinations.size
                    val centerX = inset + width * (markerIndex + .5f)
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(centerX - 9.dp.toPx(), size.height - 12.dp.toPx()),
                        size = Size(18.dp.toPx(), 3.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                val active = isRouteSelected(currentRoute, destination.route)
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) .94f else 1f, tween(if (reducedMotion) 0 else 100))
                val tint = if (active) activeTintColor else inactiveTintColor
                Box(
                    Modifier.weight(1f).height(54.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (pressed) EspressoPalette.panel2 else Color.Transparent)
                        .semantics { selected = active }
                        .clickable(interactionSource = source, indication = null, role = Role.Tab) { onNavigate(destination.route) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box {
                            QuietIcon(destination.icon, destination.label, Modifier.size(20.dp), tint, active)
                            if (destination.route == "ai" && ironMindBadgeCount == 0 && ironMindHasUnseenChange) {
                                // A Claude change landed since IronMind was last opened: a quiet dot, no count.
                                Box(Modifier.align(Alignment.TopEnd).offset(4.dp, (-2).dp).size(7.dp).background(EspressoPalette.down, CircleShape).semantics { contentDescription = "New IronMind change" })
                            }
                            if (destination.route == "ai" && ironMindBadgeCount > 0) {
                                Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-4).dp).sizeIn(minWidth = 13.dp, minHeight = 13.dp).background(EspressoPalette.down, CircleShape).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                                    Text(if (ironMindBadgeCount > 9) "9+" else ironMindBadgeCount.toString(), color = EspressoPalette.onColor, fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 9.sp)
                                }
                            }
                        }
                        Text(destination.label, color = tint, fontFamily = QuietUiFontFamily, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.sp, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
