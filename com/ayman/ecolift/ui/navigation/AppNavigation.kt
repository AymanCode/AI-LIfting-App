package com.ayman.ecolift.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "today") {
        composable("today") { TodayScreen(navController) }
        composable("history") { HistoryScreen(navController) }
        composable("progress") { ProgressScreen(navController) }
    }
}
