package com.shottimer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.auth.AuthState
import com.shottimer.app.drills.DrillsScreen
import com.shottimer.app.history.HistoryScreen
import com.shottimer.app.settings.SettingsRepository
import com.shottimer.app.settings.SettingsScreen
import com.shottimer.app.shooters.ShootersScreen
import com.shottimer.app.sync.SyncViewModel
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
        // The app is a fixed dark theme regardless of system setting (see ShotTimerTheme), so the
        // system bars must be told explicitly - the no-arg overload follows the system theme and
        // draws light-mode scrims/icons over our dark background when the OS is in light mode.
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(transparent),
            navigationBarStyle = SystemBarStyle.dark(transparent)
        )
        setContent {
            ShotTimerTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    // rememberSaveable, not remember: rotation (or any configuration change) recreates the
    // Activity, and plain remember snapped the user back to the Timer tab every time.
    var screen by rememberSaveable { mutableStateOf(AppScreen.TIMER) }
    // Set when a shooter card on the Shooters tab is tapped, so History opens pre-filtered to
    // them. Cleared when History is opened directly from the nav bar - a direct visit should
    // start unfiltered, not resurrect the last tapped shooter.
    var pendingShooterFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val shotTimerViewModel: ShotTimerViewModel = viewModel()

    FirstLaunchSignInPrompt()

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
                    onClick = {
                        pendingShooterFilter = null
                        screen = AppScreen.HISTORY
                    },
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
            AppScreen.SHOOTERS -> ShootersScreen(
                modifier = contentModifier,
                onShooterClick = { name ->
                    pendingShooterFilter = name
                    screen = AppScreen.HISTORY
                }
            )
            AppScreen.HISTORY -> HistoryScreen(
                modifier = contentModifier,
                initialShooterFilter = pendingShooterFilter
            )
            AppScreen.SETTINGS -> SettingsScreen(modifier = contentModifier)
        }
    }
}

/**
 * One-time "back up your runs?" dialog on first launch. Backup is optional by design, so this is
 * a single ask with a clear decline path - never shown again after either choice (tracked via
 * [SettingsRepository.markSignInPromptShown]); Settings > Back up to Google remains the way in.
 * Installs already signed in (or upgrading with a restored session) are marked shown silently.
 */
@Composable
private fun FirstLaunchSignInPrompt(syncViewModel: SyncViewModel = viewModel()) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository.getInstance(context) }
    val authState by syncViewModel.authState.collectAsStateWithLifecycle()

    // Decided once per composition-lifetime, not observed: the prompt should never pop later in
    // the session because some state changed - only ever on a fresh launch. authState is safe to
    // read here because AuthRepository seeds it synchronously from FirebaseAuth's disk cache.
    var showPrompt by rememberSaveable {
        val alreadyHandled = settingsRepository.wasSignInPromptShown() || authState is AuthState.SignedIn
        if (alreadyHandled) settingsRepository.markSignInPromptShown()
        mutableStateOf(!alreadyHandled)
    }
    if (!showPrompt) return

    AlertDialog(
        onDismissRequest = {
            settingsRepository.markSignInPromptShown()
            showPrompt = false
        },
        title = { Text("Back up your runs?") },
        text = {
            Text(
                "Sign in with Google to keep your run history safe if you switch or lose your " +
                    "phone. Entirely optional - the timer works fully offline, and you can " +
                    "always sign in later from Settings."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                settingsRepository.markSignInPromptShown()
                showPrompt = false
                syncViewModel.signIn(context)
            }) { Text("Sign in") }
        },
        dismissButton = {
            TextButton(onClick = {
                settingsRepository.markSignInPromptShown()
                showPrompt = false
            }) { Text("Not now") }
        }
    )
}
