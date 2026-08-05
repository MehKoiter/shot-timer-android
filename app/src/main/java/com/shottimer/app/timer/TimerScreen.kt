package com.shottimer.app.timer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.results.RunSummaryView
import com.shottimer.app.ui.theme.ShotTimerTheme
import com.shottimer.app.util.formatElapsed

@Composable
fun TimerScreen(
    modifier: Modifier = Modifier,
    viewModel: ShotTimerViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.start()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = statusText(uiState.runState), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Text(text = formatElapsed(uiState.elapsedMillis), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(24.dp))

        Text(text = "Sensitivity: ${(uiState.sensitivity * 100).toInt()}%")
        Slider(
            value = uiState.sensitivity,
            onValueChange = viewModel::setSensitivity,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Par time")
            Switch(checked = uiState.parTimeEnabled, onCheckedChange = viewModel::setParTimeEnabled)
        }
        if (uiState.parTimeEnabled) {
            Text(text = "Par: %.1fs".format(uiState.parTimeSeconds))
            Slider(
                value = uiState.parTimeSeconds,
                onValueChange = viewModel::setParTimeSeconds,
                valueRange = MIN_PAR_TIME_SECONDS..MAX_PAR_TIME_SECONDS,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))

        when {
            !hasMicPermission -> Button(onClick = {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text("Grant microphone permission to start") }
            uiState.runState == RunState.IDLE || uiState.runState == RunState.STOPPED ->
                Button(onClick = viewModel::start) { Text("Start") }
            else -> Button(onClick = viewModel::stop) { Text("Stop") }
        }

        Spacer(Modifier.height(32.dp))
        RunSummaryView(
            totalElapsedMillis = uiState.elapsedMillis,
            shotTimestampsMillis = uiState.shotSplitsMillis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun statusText(state: RunState): String = when (state) {
    RunState.IDLE -> "Ready"
    RunState.ARMED_WAITING -> "Stand by…"
    RunState.RUNNING -> "GO"
    RunState.STOPPED -> "Stopped"
}

@Preview(showBackground = true)
@Composable
private fun TimerScreenIdlePreview() {
    ShotTimerTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = statusText(RunState.IDLE), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(text = formatElapsed(0L), style = MaterialTheme.typography.displayLarge)
        }
    }
}
