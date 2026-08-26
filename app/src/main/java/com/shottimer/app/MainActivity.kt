package com.shottimer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.drills.DrillsScreen
import com.shottimer.app.feedback.ShakeFeedbackDetector
import com.shottimer.app.history.HistoryScreen
import com.shottimer.app.settings.SettingsScreen
import com.shottimer.app.shooters.ShootersScreen
import com.shottimer.app.timer.ShotTimerViewModel
import com.shottimer.app.timer.TimerScreen
import com.shottimer.app.ui.theme.ShotTimerTheme

private enum class AppScreen(val label: String) {
    TIMER("Timer"),
    DRILLS("Drills"),
    SHOOTERS("Shooters"),
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
    val shotTimerViewModel: ShotTimerViewModel = viewModel()

    // Shake-to-report: lets a tester shake the phone to pop the Firebase App Distribution
    // feedback UI from wherever they are in the app. No-op in release builds - see
    // ShakeFeedbackDetector's own BuildConfig.DEBUG gating.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(context, lifecycleOwner) {
        val shakeDetector = ShakeFeedbackDetector(context)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> shakeDetector.start()
                Lifecycle.Event.ON_PAUSE -> shakeDetector.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            shakeDetector.stop()
        }
    }

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
                    selected = screen == AppScreen.DRILLS,
                    onClick = { screen = AppScreen.DRILLS },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(AppScreen.DRILLS.label) }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.SHOOTERS,
                    onClick = { screen = AppScreen.SHOOTERS },
                    icon = { Icon(Icons.Default.Groups, contentDescription = null) },
                    label = { Text(AppScreen.SHOOTERS.label) }
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
            AppScreen.TIMER -> TimerScreen(modifier = contentModifier, viewModel = shotTimerViewModel)
            AppScreen.DRILLS -> DrillsScreen(
                modifier = contentModifier,
                onStartDrill = { drill ->
                    shotTimerViewModel.selectDrill(drill)
                    screen = AppScreen.TIMER
                }
            )
            AppScreen.SHOOTERS -> ShootersScreen(modifier = contentModifier)
            AppScreen.HISTORY -> HistoryScreen(modifier = contentModifier)
            AppScreen.SETTINGS -> SettingsScreen(modifier = contentModifier)
        }
    }
}
