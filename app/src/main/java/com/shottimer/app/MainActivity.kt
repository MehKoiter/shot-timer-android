package com.shottimer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shottimer.app.audio.MicTestScreen
import com.shottimer.app.history.HistoryScreen
import com.shottimer.app.settings.SettingsScreen
import com.shottimer.app.timer.TimerScreen
import com.shottimer.app.ui.theme.ShotTimerTheme

private enum class AppScreen(val label: String) {
    TIMER("Timer"),
    MIC_TEST("Mic Test"),
    HISTORY("History"),
    SETTINGS("Settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShotTimerTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var screen by remember { mutableStateOf(AppScreen.TIMER) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == AppScreen.TIMER,
                    onClick = { screen = AppScreen.TIMER },
                    icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                    label = { Text(AppScreen.TIMER.label) }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.MIC_TEST,
                    onClick = { screen = AppScreen.MIC_TEST },
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    label = { Text(AppScreen.MIC_TEST.label) }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.HISTORY,
                    onClick = { screen = AppScreen.HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text(AppScreen.HISTORY.label) }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.SETTINGS,
                    onClick = { screen = AppScreen.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(AppScreen.SETTINGS.label) }
                )
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (screen) {
            AppScreen.TIMER -> TimerScreen(modifier = contentModifier)
            AppScreen.MIC_TEST -> MicTestScreen(modifier = contentModifier)
            AppScreen.HISTORY -> HistoryScreen(modifier = contentModifier)
            AppScreen.SETTINGS -> SettingsScreen(modifier = contentModifier)
        }
    }
}
