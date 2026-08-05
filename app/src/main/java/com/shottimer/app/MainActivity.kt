package com.shottimer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shottimer.app.audio.MicTestScreen
import com.shottimer.app.timer.TimerScreen
import com.shottimer.app.ui.theme.ShotTimerTheme

private enum class AppScreen(val label: String) {
    TIMER("Timer"),
    MIC_TEST("Mic Test")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    var screen by remember { mutableStateOf(AppScreen.TIMER) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Button(onClick = {
                        screen = if (screen == AppScreen.TIMER) AppScreen.MIC_TEST else AppScreen.TIMER
                    }) {
                        Text("Switch to ${if (screen == AppScreen.TIMER) AppScreen.MIC_TEST.label else AppScreen.TIMER.label}")
                    }
                }
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (screen) {
            AppScreen.TIMER -> TimerScreen(modifier = contentModifier)
            AppScreen.MIC_TEST -> MicTestScreen(modifier = contentModifier)
        }
    }
}
