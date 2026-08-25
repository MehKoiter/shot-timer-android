package com.shottimer.app.timer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.permission.OpenAppSettingsButton
import com.shottimer.app.permission.rememberMicPermissionState
import com.shottimer.app.results.RunSummaryView
import com.shottimer.app.ui.theme.ShotTimerTheme
import com.shottimer.app.util.formatElapsed

@Composable
fun TimerScreen(
    modifier: Modifier = Modifier,
    viewModel: ShotTimerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val knownShooters by viewModel.knownShooters.collectAsStateWithLifecycle()
    val micPermission = rememberMicPermissionState(onGranted = viewModel::start)
    var showShooterPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = statusText(uiState.runState), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(text = formatElapsed(uiState.elapsedMillis), style = MaterialTheme.typography.displayLarge)
            }
        }
        if (uiState.micErrorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = uiState.micErrorMessage!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))

        val drill = uiState.selectedDrill
        if (drill != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = drill.name, style = MaterialTheme.typography.titleSmall)
                    Text(text = drill.instructions, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { viewModel.selectDrill(null) }) { Text("Clear drill") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.selectedShooter?.let { "Shooter: $it" } ?: "No shooter tagged",
                    style = MaterialTheme.typography.titleSmall
                )
                TextButton(onClick = { showShooterPicker = true }) {
                    Text(if (uiState.selectedShooter != null) "Change" else "Set shooter")
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Sensitivity: ${(uiState.sensitivity * 100).toInt()}%")
                Slider(
                    value = uiState.sensitivity,
                    onValueChange = viewModel::setSensitivity,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
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
            }
        }
        Spacer(Modifier.height(16.dp))

        when {
            !micPermission.isGranted && micPermission.isPermanentlyDenied -> OpenAppSettingsButton()
            !micPermission.isGranted -> Button(onClick = micPermission.request) {
                Text("Grant microphone permission to start")
            }
            uiState.runState == RunState.IDLE || uiState.runState == RunState.STOPPED ->
                Button(onClick = viewModel::start) { Text("Start") }
            else -> Button(
                onClick = viewModel::stop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Stop") }
        }

        Spacer(Modifier.height(32.dp))
        RunSummaryView(
            totalElapsedMillis = uiState.elapsedMillis,
            shotTimestampsMillis = uiState.shotSplitsMillis,
            modifier = Modifier.fillMaxWidth(),
            expectedRoundCount = uiState.selectedDrill?.roundCount
        )
    }

    if (showShooterPicker) {
        ShooterPickerDialog(
            knownShooters = knownShooters,
            onSelect = { name ->
                viewModel.selectShooter(name)
                showShooterPicker = false
            },
            onDismiss = { showShooterPicker = false }
        )
    }
}

/** Lets you type a brand-new name or tap someone you've already timed before - either path just
 * calls [onSelect] with the chosen name, so the caller doesn't need to know which one happened. */
@Composable
private fun ShooterPickerDialog(
    knownShooters: List<String>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set shooter") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (knownShooters.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Or pick someone you've timed before:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        knownShooters.forEach { name ->
                            Text(
                                text = name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(name) }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(text) }) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
