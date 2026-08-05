package com.shottimer.app.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.ui.theme.ShotTimerTheme

@Composable
fun TimerScreen(
    modifier: Modifier = Modifier,
    viewModel: ShotTimerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = statusText(uiState.runState), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Text(text = formatElapsed(uiState.elapsedMillis), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(48.dp))
        when (uiState.runState) {
            RunState.IDLE, RunState.STOPPED -> Button(onClick = viewModel::start) { Text("Start") }
            RunState.ARMED_WAITING, RunState.RUNNING -> Button(onClick = viewModel::stop) { Text("Stop") }
        }
    }
}

private fun statusText(state: RunState): String = when (state) {
    RunState.IDLE -> "Ready"
    RunState.ARMED_WAITING -> "Stand by…"
    RunState.RUNNING -> "GO"
    RunState.STOPPED -> "Stopped"
}

private fun formatElapsed(elapsedMillis: Long): String {
    val totalCentis = elapsedMillis / 10
    val minutes = totalCentis / 6000
    val seconds = (totalCentis / 100) % 60
    val centis = totalCentis % 100
    return "%02d:%02d.%02d".format(minutes, seconds, centis)
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
