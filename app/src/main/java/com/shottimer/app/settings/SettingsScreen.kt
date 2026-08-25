package com.shottimer.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.audio.MicTestScreen

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    // Mic Test used to be its own bottom-nav tab - moved here as a sub-screen (same list/detail
    // push-pop pattern HistoryScreen uses) to make room in the nav bar as more tabs were added.
    var showMicTest by remember { mutableStateOf(false) }
    if (showMicTest) {
        MicTestScreen(onBack = { showMicTest = false }, modifier = modifier)
        return
    }

    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "These are defaults for new runs - changes don't affect a run already in progress.",
            style = MaterialTheme.typography.bodySmall
        )

        var showSensitivityHelp by remember { mutableStateOf(false) }
        SettingSection(
            title = "Default sensitivity: ${(settings.defaultSensitivity * 100).toInt()}%",
            titleTrailing = {
                IconButton(
                    onClick = { showSensitivityHelp = !showSensitivityHelp },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = "How does sensitivity work?")
                }
            }
        ) {
            if (showSensitivityHelp) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Higher sensitivity picks up quieter sounds - turn it up if shots " +
                            "aren't being detected. Lower sensitivity needs a louder sound to " +
                            "trigger - turn it down if background noise or handling the gun/phone " +
                            "is causing false detections.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Slider(
                value = settings.defaultSensitivity,
                onValueChange = viewModel::setDefaultSensitivity,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingSection(title = "Echo lockout") {
            Text(
                text = "How long after a detected shot to ignore further sound - filters out echoes/reverb without missing a genuinely fast next shot. $MIN_ECHO_LOCKOUT_MS-$MAX_ECHO_LOCKOUT_MS ms.",
                style = MaterialTheme.typography.bodySmall
            )
            IntegerField(
                label = "Lockout (ms)",
                value = settings.echoLockoutMs,
                onValueChange = viewModel::setEchoLockoutMs
            )
        }

        SettingSection(title = "Random delay") {
            Text(
                text = "Range for the random pause between pressing Start and the beep. " +
                    "$MIN_RANDOM_DELAY_SECONDS-$MAX_RANDOM_DELAY_SECONDS seconds.",
                style = MaterialTheme.typography.bodySmall
            )
            FloatField(
                label = "Min (s)",
                value = settings.minDelaySeconds,
                onValueChange = { viewModel.setDelayRange(it, settings.maxDelaySeconds) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            FloatField(
                label = "Max (s)",
                value = settings.maxDelaySeconds,
                onValueChange = { viewModel.setDelayRange(settings.minDelaySeconds, it) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingSection(title = "Beep volume: ${(settings.beepVolume * 100).toInt()}%") {
            Text(
                text = "Relative to your phone's Alarm volume, which the beep also depends on.",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = settings.beepVolume,
                onValueChange = viewModel::setBeepVolume,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            ListItem(
                modifier = Modifier.clickable { showMicTest = true },
                headlineContent = { Text("Mic Test") },
                supportingContent = { Text("Check microphone capture levels") }
            )
        }
    }
}

/** Local text mirrors [value] until the field loses focus, so clamping doesn't fight the user mid-keystroke. */
@Composable
private fun IntegerField(
    label: String,
    value: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { text.toLongOrNull()?.let(onValueChange) }),
        modifier = modifier.onFocusChanged { focus ->
            if (!focus.isFocused) text.toLongOrNull()?.let(onValueChange)
        }
    )
}

@Composable
private fun FloatField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf("%.1f".format(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { text.toFloatOrNull()?.let(onValueChange) }),
        modifier = modifier.onFocusChanged { focus ->
            if (!focus.isFocused) text.toFloatOrNull()?.let(onValueChange)
        }
    )
}

@Composable
private fun SettingSection(
    title: String,
    titleTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                titleTrailing?.invoke()
            }
            content()
        }
    }
}
