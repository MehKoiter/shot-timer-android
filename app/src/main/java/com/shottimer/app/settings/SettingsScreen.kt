package com.shottimer.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
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

        SettingSection(title = "Default sensitivity: ${(settings.defaultSensitivity * 100).toInt()}%") {
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
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}
