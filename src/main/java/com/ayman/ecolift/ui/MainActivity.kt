package com.ayman.ecolift.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ayman.ecolift.ui.navigation.AppNavigation
import com.ayman.ecolift.ui.theme.loadAppFonts
import com.ayman.ecolift.web.LocalAppServices
import com.ayman.ecolift.web.LocalRepos
import com.ayman.ecolift.web.demo.DemoAppServices
import com.ayman.ecolift.web.demo.DemoRepos

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var fontsReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                loadAppFonts()
                fontsReady = true
            }
            if (!fontsReady) return@setContent
            val repos = remember { DemoRepos() }
            val services = remember { DemoAppServices() }
            CompositionLocalProvider(
                LocalRepos provides repos,
                LocalAppServices provides services,
            ) {
                AppNavigation()
            }
        }
    }
}
