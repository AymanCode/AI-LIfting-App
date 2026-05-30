package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ayman.ecolift.ui.theme.AccentTeal
import com.ayman.ecolift.ui.theme.AccentTeal12
import com.ayman.ecolift.ui.theme.BorderDefault
import com.ayman.ecolift.ui.theme.NavBackground
import com.ayman.ecolift.ui.theme.TextMuted

private data class AppDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

internal fun buildLogRouteForSplit(splitId: Long): String = "log/$splitId"

internal fun buildProgressRouteForExercise(exerciseId: Long): String = "progress/$exerciseId"

internal fun isRouteSelected(currentRoute: String?, tabRoute: String): Boolean {
    return currentRoute == tabRoute || currentRoute?.startsWith("$tabRoute/") == true
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isLogRoute = isRouteSelected(currentRoute, "log")
    var isLogChromeVisible by rememberSaveable { mutableStateOf(true) }
    
    // Check if keyboard is visible to hide the BottomBar
    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    val destinations = listOf(
        AppDestination("log", Icons.Filled.DateRange, "Log"),
        AppDestination("progress", Icons.AutoMirrored.Filled.ShowChart, "Progress"),
        AppDestination("ai", Icons.Filled.AutoAwesome, "IronMind"),
        AppDestination("split", Icons.Filled.Refresh, "Split"),
    )

    fun navigateToTab(route: String) {
        if (!isRouteSelected(currentRoute, route)) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
            }
        }
    }

    LaunchedEffect(isLogRoute) {
        if (!isLogRoute) {
            isLogChromeVisible = true
        }
    }

    val shouldShowBottomBar = !isKeyboardVisible && (!isLogRoute || isLogChromeVisible)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = shouldShowBottomBar,
                enter = fadeIn(animationSpec = tween(120)) +
                    slideInVertically(animationSpec = tween(160)) { it / 2 },
                exit = fadeOut(animationSpec = tween(110)) +
                    slideOutVertically(animationSpec = tween(150)) { it / 2 }
            ) {
                AppBottomBar(
                    destinations = destinations,
                    currentRoute = currentRoute,
                    onNavigate = ::navigateToTab
                )
            }
        }
    )
{ padding ->
        NavHost(
            navController = navController,
            startDestination = "log",
            modifier = Modifier
                .padding(padding),
        ) {
            composable("log") {
                TodayScreen(
                    isChromeVisible = isLogChromeVisible,
                    onChromeVisibilityChange = { isLogChromeVisible = it }
                )
            }
            composable(
                route = "log/{splitId}",
                arguments = listOf(navArgument("splitId") { type = NavType.LongType })
            ) { entry ->
                TodayScreen(
                    initialSplitId = entry.arguments?.getLong("splitId"),
                    isChromeVisible = isLogChromeVisible,
                    onChromeVisibilityChange = { isLogChromeVisible = it }
                )
            }
            composable("progress") {
                ProgressScreen(
                    onOpenBackups = { navController.navigate("backups") }
                )
            }
            composable(
                route = "progress/{exerciseId}",
                arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
            ) { entry ->
                ProgressScreen(
                    initialExerciseId = entry.arguments?.getLong("exerciseId"),
                    onOpenBackups = { navController.navigate("backups") }
                )
            }
            composable("ai") { AiScreen() }
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
                )
            }
            composable("backups") {
                BackupScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    destinations: List<AppDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .background(NavBackground)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        HorizontalDivider(color = BorderDefault, thickness = 1.dp)
        NavigationBar(
            containerColor = NavBackground,
            tonalElevation = 0.dp,
            modifier = Modifier.height(64.dp)
        ) {
            destinations.forEach { destination ->
                val selected = isRouteSelected(currentRoute, destination.route)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(destination.route) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 28.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) AccentTeal12 else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label,
                                modifier = Modifier.size(16.dp),
                                tint = if (selected) AccentTeal else TextMuted
                            )
                        }
                    },
                    label = {
                        Text(
                            text = destination.label.uppercase(),
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = if (selected) FontWeight.W800 else FontWeight.Normal,
                                color = if (selected) AccentTeal else TextMuted,
                                letterSpacing = 0.06.sp
                            )
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}
