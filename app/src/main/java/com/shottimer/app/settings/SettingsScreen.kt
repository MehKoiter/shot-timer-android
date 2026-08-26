package com.shottimer.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.shottimer.app.BuildConfig
import com.shottimer.app.R
import com.shottimer.app.audio.MicTestScreen
import com.shottimer.app.auth.AuthState
import com.shottimer.app.sync.BackupScreen
import com.shottimer.app.sync.SyncViewModel
import com.shottimer.app.ui.ScreenScaffold

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    // Mic Test used to be its own bottom-nav tab - moved here as a sub-screen (same push-pop
    // pattern HistoryScreen uses) to make room in the nav bar as more tabs were added.
    // rememberSaveable so rotation doesn't dump the user back to the Settings list.
    var showMicTest by rememberSaveable { mutableStateOf(false) }
    if (showMicTest) {
        MicTestScreen(onBack = { showMicTest = false }, modifier = modifier)
        return
    }

    // Same push/pop pattern as showMicTest above.
    var showBackup by rememberSaveable { mutableStateOf(false) }
    if (showBackup) {
        BackupScreen(onBack = { showBackup = false }, modifier = modifier)
        return
    }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val syncViewModel: SyncViewModel = viewModel()
    val authState by syncViewModel.authState.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.tab_settings), modifier = modifier) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_desc),
            style = MaterialTheme.typography.bodySmall
        )

        var showSensitivityHelp by remember { mutableStateOf(false) }
        SettingSection(
            title = stringResource(R.string.default_sensitivity_pct, (settings.defaultSensitivity * 100).toInt()),
            titleTrailing = {
                // Default IconButton size (48dp) - a previous size(24.dp) shrank the touch
                // target below the accessibility minimum.
                IconButton(onClick = { showSensitivityHelp = !showSensitivityHelp }) {
                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.sensitivity_help_cd))
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
                        text = stringResource(R.string.sensitivity_help_body),
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

        SettingSection(title = stringResource(R.string.echo_lockout_title)) {
            Text(
                text = stringResource(R.string.echo_lockout_desc, MIN_ECHO_LOCKOUT_MS, MAX_ECHO_LOCKOUT_MS),
                style = MaterialTheme.typography.bodySmall
            )
            IntegerField(
                label = stringResource(R.string.lockout_ms_label),
                value = settings.echoLockoutMs,
                onValueChange = viewModel::setEchoLockoutMs
            )
        }

        SettingSection(
            title = stringResource(R.string.random_delay_title, settings.minDelaySeconds, settings.maxDelaySeconds)
        ) {
            Text(
                text = stringResource(R.string.random_delay_desc),
                style = MaterialTheme.typography.bodySmall
            )
            // One two-thumb slider instead of the old Min/Max decimal text fields - expresses
            // the range in a single gesture and makes min > max impossible by construction.
            RangeSlider(
                value = settings.minDelaySeconds..settings.maxDelaySeconds,
                onValueChange = { range -> viewModel.setDelayRange(range.start, range.endInclusive) },
                valueRange = MIN_RANDOM_DELAY_SECONDS..MAX_RANDOM_DELAY_SECONDS,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingSection(title = stringResource(R.string.beep_volume_pct, (settings.beepVolume * 100).toInt())) {
            Text(
                text = stringResource(R.string.beep_volume_desc),
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
                headlineContent = { Text(stringResource(R.string.mic_test)) },
                supportingContent = { Text(stringResource(R.string.mic_test_desc)) }
            )
        }

        Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            ListItem(
                modifier = Modifier.clickable { showBackup = true },
                headlineContent = { Text(stringResource(R.string.backup_title)) },
                supportingContent = {
                    Text(
                        when (val state = authState) {
                            is AuthState.SignedOut -> stringResource(R.string.not_signed_in)
                            is AuthState.SignedIn -> stringResource(
                                R.string.signed_in_as,
                                state.email ?: state.displayName ?: state.uid
                            )
                        }
                    )
                }
            )
        }

        // Test-build only: the one entry point into App Distribution's feedback flow (the
        // shake-to-report gesture was removed - this button is it now).
        if (BuildConfig.DEBUG) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                ListItem(
                    modifier = Modifier.clickable {
                        FirebaseAppDistribution.getInstance().startFeedback(R.string.feedback_prompt)
                    },
                    headlineContent = { Text(stringResource(R.string.send_feedback)) },
                    supportingContent = { Text(stringResource(R.string.send_feedback_desc)) }
                )
            }
        }
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
