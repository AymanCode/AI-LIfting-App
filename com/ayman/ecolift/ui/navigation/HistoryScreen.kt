package com.ayman.ecolift.ui.navigation

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

@Composable
fun HistoryScreen(navController: NavController = rememberNavController()) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Dumbbell, contentDescription = "Today") },
                    label = { Text("Today") },
                    selected = navController.currentDestination?.route == "today",
                    onClick = { navController.navigate("today") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Clock, contentDescription = "History") },
                    label = { Text("History") },
                    selected = navController.currentDestination?.route == "history",
                    onClick = { navController.navigate("history") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.ChartBar, contentDescription = "Progress") },
                    label = { Text("Progress") },
                    selected = navController.currentDestination?.route == "progress",
                    onClick = { navController.navigate("progress") }
                )
            }
        }
    ) {
        Text(text = "History")
    }
}
