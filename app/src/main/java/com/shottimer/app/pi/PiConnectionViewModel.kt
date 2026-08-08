package com.shottimer.app.pi

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import com.shottimer.app.settings.SettingsRepository
import com.shottimer.app.settings.TimerSettings
import com.shottimer.app.settings.TimerSource
import kotlinx.coroutines.flow.StateFlow

class PiConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val piRepository = PiRepository.getInstance(application)
    private val settingsRepository = SettingsRepository.getInstance(application)

    val connectionState: StateFlow<PiConnectionState> = piRepository.connectionState
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = piRepository.discoveredDevices
    val settings: StateFlow<TimerSettings> = settingsRepository.settings

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION])
    fun startScan() = piRepository.startScan()

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION])
    fun stopScan() = piRepository.stopScan()

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
    fun connect(device: BluetoothDevice) = piRepository.connect(device)

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
    fun disconnect() = piRepository.disconnect()

    fun setSourceMode(mode: TimerSource) {
        settingsRepository.update { it.copy(sourceMode = mode) }
    }
}
