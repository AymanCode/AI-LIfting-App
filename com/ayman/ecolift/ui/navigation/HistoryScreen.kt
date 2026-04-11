package com.ayman.ecolift.ui.navigation

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem

@Composable
fun HistoryScreen(navController: NavController = rememberNavController()) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    label = { Text("Today") },
                    selected = navController.currentDestination?.route == "today",
                    onClick = { navController.navigate("today") },
                    icon = { Text("T") }
                )
                NavigationBarItem(
                    label = { Text("History") },
                    selected = navController.currentDestination?.route == "history",
                    onClick = { navController.navigate("history") },
                    icon = { Text("H") }
                )
                NavigationBarItem(
                    label = { Text("Progress") },
                    selected = navController.currentDestination?.route == "progress",
                    onClick = { navController.navigate("progress") },
                    icon = { Text("P") }
                )
            }
        }
    ) {
        Text(text = "History")
    }
}
