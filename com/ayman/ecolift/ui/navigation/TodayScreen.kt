package com.ayman.ecolift.ui.navigation

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem

@Composable
fun TodayScreen(navController: NavController = rememberNavController()) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Text("T") },
                    label = { Text("Today") },
                    selected = navController.currentDestination?.route == "today",
                    onClick = { navController.navigate("today") }
                )
                NavigationBarItem(
                    icon = { Text("H") },
                    label = { Text("History") },
                    selected = navController.currentDestination?.route == "history",
                    onClick = { navController.navigate("history") }
                )
                NavigationBarItem(
                    icon = { Text("P") },
                    label = { Text("Progress") },
                    selected = navController.currentDestination?.route == "progress",
                    onClick = { navController.navigate("progress") }
                )
            }
        }
    ) {
        Text(text = "Today")
    }
}
