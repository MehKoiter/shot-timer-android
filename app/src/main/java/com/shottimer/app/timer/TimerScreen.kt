package com.shottimer.app.timer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.permission.OpenAppSettingsButton
import com.shottimer.app.permission.rememberMicPermissionState
import com.shottimer.app.results.RunSummaryView
import com.shottimer.app.ui.ScreenScaffold
import com.shottimer.app.util.formatElapsed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    modifier: Modifier = Modifier,
    viewModel: ShotTimerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val knownShooters by viewModel.knownShooters.collectAsStateWithLifecycle()
    val micPermission = rememberMicPermissionState(onGranted = viewModel::start)
    var showShooterPicker by remember { mutableStateOf(false) }
    var showRunOptions by rememberSaveable { mutableStateOf(false) }

    // A range timer that lets the display sleep mid-drill is useless - keep the screen on while
    // a run is armed or live, and release the flag the moment it isn't (or we leave the screen).
    val isRunActive = uiState.runState == RunState.ARMED_WAITING || uiState.runState == RunState.RUNNING
    val view = LocalView.current
    DisposableEffect(isRunActive) {
        view.keepScreenOn = isRunActive
        onDispose { view.keepScreenOn = false }
    }

    ScreenScaffold(title = "Timer", modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
            uiState.micErrorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))

            // Run context and per-run options as one compact chip row - these used to be three
            // stacked full-width cards that pushed the Start button below the fold.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val drill = uiState.selectedDrill
                if (drill != null) {
                    InputChip(
                        selected = true,
                        onClick = { viewModel.selectDrill(null) },
                        label = { Text(drill.name) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Clear drill") }
                    )
                }
                InputChip(
                    selected = uiState.selectedShooter != null,
                    onClick = { showShooterPicker = true },
                    label = { Text(uiState.selectedShooter ?: "Tag shooter") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
                AssistChip(
                    onClick = { showRunOptions = true },
                    label = { Text("Options") },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) }
                )
            }
            Spacer(Modifier.height(16.dp))

            // One oversized target: this gets hit one-handed, possibly gloved, with ear pro on.
            val bigButton = Modifier
                .fillMaxWidth()
                .height(72.dp)
            when {
                !micPermission.isGranted && micPermission.isPermanentlyDenied -> OpenAppSettingsButton()
                !micPermission.isGranted -> Button(onClick = micPermission.request, modifier = bigButton) {
                    Text("Grant microphone permission to start")
                }
                uiState.runState == RunState.IDLE || uiState.runState == RunState.STOPPED ->
                    Button(onClick = viewModel::start, modifier = bigButton) {
                        Text("Start", style = MaterialTheme.typography.titleLarge)
                    }
                else -> Button(
                    onClick = viewModel::stop,
                    modifier = bigButton,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Stop", style = MaterialTheme.typography.titleLarge) }
            }

            Spacer(Modifier.height(24.dp))
            RunSummaryView(
                totalElapsedMillis = uiState.elapsedMillis,
                shotTimestampsMillis = uiState.shotSplitsMillis,
                modifier = Modifier.fillMaxWidth(),
                expectedRoundCount = uiState.selectedDrill?.roundCount
            )
        }
    }

    if (showRunOptions) {
        RunOptionsSheet(
            uiState = uiState,
            onSensitivityChange = viewModel::setSensitivity,
            onParTimeEnabledChange = viewModel::setParTimeEnabled,
            onParTimeSecondsChange = viewModel::setParTimeSeconds,
            onDismiss = { showRunOptions = false }
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

/** Per-run overrides (sensitivity, par time), pulled off the main screen into a sheet so the
 * clock and Start button stay above the fold. Settings still holds the persistent defaults. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunOptionsSheet(
    uiState: TimerUiState,
    onSensitivityChange: (Float) -> Unit,
    onParTimeEnabledChange: (Boolean) -> Unit,
    onParTimeSecondsChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(text = "Run options", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Apply to this run only - defaults live in Settings.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(16.dp))

            Text(text = "Sensitivity: ${(uiState.sensitivity * 100).toInt()}%")
            Slider(
                value = uiState.sensitivity,
                onValueChange = onSensitivityChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Par time")
                Switch(checked = uiState.parTimeEnabled, onCheckedChange = onParTimeEnabledChange)
            }
            if (uiState.parTimeEnabled) {
                Text(text = "Par: %.1fs".format(uiState.parTimeSeconds))
                Slider(
                    value = uiState.parTimeSeconds,
                    onValueChange = onParTimeSecondsChange,
                    valueRange = MIN_PAR_TIME_SECONDS..MAX_PAR_TIME_SECONDS,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(24.dp))
        }
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
                            // Box + heightIn rather than bare Text padding: keeps each row at the
                            // 48dp minimum touch target.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { onSelect(name) },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(text = name)
                            }
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
