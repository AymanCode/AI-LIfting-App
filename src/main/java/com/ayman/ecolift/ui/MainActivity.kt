package com.ayman.ecolift.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import com.ayman.ecolift.data.DebugDataHelper
import com.ayman.ecolift.ui.navigation.AppNavigation
import com.ayman.ecolift.ui.theme.DarkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Populate mock data for testing (only once or for debug)
        // lifecycleScope.launch {
        //     DebugDataHelper.populateMockData(applicationContext)
        // }

        enableEdgeToEdge()
        setContent {
            DarkTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}
