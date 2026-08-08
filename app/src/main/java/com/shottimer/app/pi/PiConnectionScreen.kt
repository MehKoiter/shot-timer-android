package com.shottimer.app.pi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shottimer.app.permission.OpenAppSettingsButton
import com.shottimer.app.permission.rememberBlePermissionState
import com.shottimer.app.settings.TimerSource

/** Settings -> Pi Companion sub-screen: LOCAL/PI mode toggle plus scan/connect/disconnect.
 * Reached the same way HistoryScreen.kt pushes to its run-detail view - local mutable state in
 * SettingsScreen, not a navigation library, for one additional screen. */
@Composable
fun PiConnectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PiConnectionViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val devices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val blePermission = rememberBlePermissionState(onGranted = viewModel::startScan)

    // Leaving this screen mid-scan (back button, navigating away) shouldn't leave the radio
    // scanning in the background - stopScan() is a no-op if nothing is in progress.
    DisposableEffect(Unit) {
        onDispose { viewModel.stopScan() }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("< Back") }
        Spacer(Modifier.height(8.dp))
        Text(text = "Pi Companion", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Timer source", style = MaterialTheme.typography.titleSmall)
                SourceModeRow(
                    label = "Phone microphone",
                    selected = settings.sourceMode == TimerSource.LOCAL,
                    onSelect = { viewModel.setSourceMode(TimerSource.LOCAL) }
                )
                SourceModeRow(
                    label = "Pi companion",
                    selected = settings.sourceMode == TimerSource.PI,
                    onSelect = { viewModel.setSourceMode(TimerSource.PI) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Status: ${statusText(connectionState)}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                when {
                    !blePermission.isGranted && blePermission.isPermanentlyDenied -> OpenAppSettingsButton(
                        label = "Open app settings to enable Bluetooth"
                    )
                    !blePermission.isGranted -> Button(onClick = blePermission.request) {
                        Text("Grant Bluetooth permission to scan")
                    }
                    connectionState is PiConnectionState.Connected -> Button(
                        onClick = viewModel::disconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Disconnect") }
                    connectionState is PiConnectionState.Scanning -> Button(
                        onClick = viewModel::stopScan,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Stop scanning") }
                    else -> Button(onClick = viewModel::startScan) { Text("Scan for Pi") }
                }
            }
        }

        if (devices.isNotEmpty() && connectionState !is PiConnectionState.Connected) {
            Spacer(Modifier.height(16.dp))
            Text(text = "Devices found", style = MaterialTheme.typography.titleSmall)
            LazyColumn {
                items(devices, key = { it.address }) { device ->
                    DeviceRow(device = device, onClick = { viewModel.connect(device) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SourceModeRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label)
    }
}

// BLUETOOTH_CONNECT (needed for BluetoothDevice.getName() on API 31+) is already verified by
// the blePermission gate before this row can ever be shown - see the when{} above, which only
// reaches the device list once blePermission.isGranted.
@SuppressLint("MissingPermission")
@Composable
private fun DeviceRow(device: BluetoothDevice, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = { Text(text = device.name ?: device.address) },
        supportingContent = { Text(text = device.address) }
    )
}

private fun statusText(state: PiConnectionState): String = when (state) {
    is PiConnectionState.Disconnected -> "Not connected"
    is PiConnectionState.Scanning -> "Scanning…"
    is PiConnectionState.NotFound -> "No Pi found - check it's powered on and in range"
    is PiConnectionState.Connecting -> "Connecting…"
    is PiConnectionState.Connected -> "Connected"
    is PiConnectionState.Error -> "Error: ${state.message}"
}
