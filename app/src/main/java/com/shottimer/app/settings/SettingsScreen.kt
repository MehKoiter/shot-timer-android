package com.shottimer.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
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

        SettingSection(title = "Echo lockout: ${settings.echoLockoutMs}ms") {
            Text(
                text = "How long after a detected shot to ignore further sound - filters out echoes/reverb without missing a genuinely fast next shot.",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = settings.echoLockoutMs.toFloat(),
                onValueChange = { viewModel.setEchoLockoutMs(it.toLong()) },
                valueRange = MIN_ECHO_LOCKOUT_MS.toFloat()..MAX_ECHO_LOCKOUT_MS.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingSection(
            title = "Random delay: %.1fs - %.1fs".format(settings.minDelaySeconds, settings.maxDelaySeconds)
        ) {
            Text(
                text = "Range for the random pause between pressing Start and the beep.",
                style = MaterialTheme.typography.bodySmall
            )
            RangeSlider(
                value = settings.minDelaySeconds..settings.maxDelaySeconds,
                onValueChange = { range -> viewModel.setDelayRange(range.start, range.endInclusive) },
                valueRange = MIN_RANDOM_DELAY_SECONDS..MAX_RANDOM_DELAY_SECONDS,
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

@Composable
private fun SettingSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}
