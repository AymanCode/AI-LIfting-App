package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ayman.ecolift.ui.viewmodel.ai.AiViewModel

private data class AppDestination(
    val route: String,
    val icon: ImageVector,
    val label: String,
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    
    // Check if keyboard is visible to hide the BottomBar
    val isKeyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    val destinations = listOf(
        AppDestination("log", Icons.Filled.DateRange, "Log"),
        AppDestination("progress", Icons.AutoMirrored.Filled.ShowChart, "Progress"),
        AppDestination("ai", Icons.Filled.AutoAwesome, "IronMind"),
        AppDestination("split", Icons.Filled.Refresh, "Split"),
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!isKeyboardVisible) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "log",
            modifier = Modifier
                .padding(padding)
                .padding(top = statusBarPadding.calculateTopPadding()),
        ) {
            composable("log") { TodayScreen() }
            composable("progress") { ProgressScreen() }
            composable("ai") { AiScreen() }
            composable("split") { SplitScreen() }
        }
    }
}
