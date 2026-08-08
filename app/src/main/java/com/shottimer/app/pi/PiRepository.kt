package com.shottimer.app.pi

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import no.nordicsemi.android.ble.observer.ConnectionObserver

sealed interface PiConnectionState {
    data object Disconnected : PiConnectionState
    data object Scanning : PiConnectionState
    // Scan ran to completion (see SCAN_TIMEOUT_MS) without finding a matching device - distinct
    // from Error since it's an expected outcome, not a failure, and from Disconnected so the UI
    // can tell "never tried" apart from "tried, found nothing".
    data object NotFound : PiConnectionState
    data class Connecting(val device: BluetoothDevice) : PiConnectionState
    data class Connected(val device: BluetoothDevice) : PiConnectionState
    data class Error(val message: String) : PiConnectionState
}

/**
 * Singleton (matches [com.shottimer.app.settings.SettingsRepository]'s `getInstance` pattern)
 * wrapping [PiConnectionManager] with scan/connect/disconnect/arm plus the live event stream,
 * so both PiConnectionScreen and ShotTimerViewModel talk to the same underlying BLE connection
 * rather than each opening their own.
 *
 * There's no ble-ktx here (dropped - see build.gradle.kts's comment on the Kotlin metadata
 * version mismatch): [connectionState] is a hand-written StateFlow driven by a
 * [ConnectionObserver], and [events] a hand-written SharedFlow driven by
 * [PiConnectionManager]'s onEvent callback, instead of using Nordic's coroutine extensions.
 */
class PiRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _connectionState = MutableStateFlow<PiConnectionState>(PiConnectionState.Disconnected)
    val connectionState: StateFlow<PiConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    // extraBufferCapacity, no replay: only a run actively in progress cares about Beep/Shot/
    // RunComplete events - a late subscriber should wait for the next event, not replay a stale
    // one from before it started collecting.
    private val _events = MutableSharedFlow<PiEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val connectionManager: PiConnectionManager = PiConnectionManager(appContext) { event ->
        _events.tryEmit(event)
    }.also { manager ->
        manager.setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) {
                _connectionState.value = PiConnectionState.Connecting(device)
            }
            override fun onDeviceConnected(device: BluetoothDevice) = Unit
            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                _connectionState.value = PiConnectionState.Error("Connection failed (reason $reason)")
            }
            // onDeviceReady, not onDeviceConnected: this fires after service/characteristic
            // discovery and notification setup complete, i.e. the moment arm() will actually work.
            override fun onDeviceReady(device: BluetoothDevice) {
                _connectionState.value = PiConnectionState.Connected(device)
            }
            override fun onDeviceDisconnecting(device: BluetoothDevice) = Unit
            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                _connectionState.value = PiConnectionState.Disconnected
            }
        })
    }

    // Main-thread Handler rather than a CoroutineScope: this is a plain singleton (not a
    // ViewModel), and a single postDelayed/removeCallbacks pair is simpler to reason about than
    // owning a Job here, consistent with the rest of this class talking to the Android BLE APIs
    // directly rather than through coroutine wrappers (see class doc above on dropping ble-ktx).
    private val timeoutHandler = Handler(Looper.getMainLooper())
    // Explicit types on this pair: scanTimeoutRunnable and scanCallback reference each other
    // (timeout stops the scanner; the scanner's result callback cancels the timeout), and without
    // explicit types the compiler can't resolve that mutual reference ("recursive problem" error).
    private val scanTimeoutRunnable: Runnable = Runnable {
        // Stop the scanner directly (not via stopScan()) so we can land on NotFound instead of
        // the Disconnected that stopScan() would otherwise set.
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _connectionState.value = PiConnectionState.NotFound
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // A device showed up before the timeout fired - cancel it so NotFound doesn't
            // clobber the in-progress/successful scan once the user has something to connect to.
            timeoutHandler.removeCallbacks(scanTimeoutRunnable)
            val device = result.device
            _discoveredDevices.update { current ->
                if (current.any { it.address == device.address }) current else current + device
            }
        }

        override fun onScanFailed(errorCode: Int) {
            timeoutHandler.removeCallbacks(scanTimeoutRunnable)
            _connectionState.value = PiConnectionState.Error("Scan failed (code $errorCode)")
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION])
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = PiConnectionState.Error("Bluetooth is off or unavailable")
            return
        }
        _discoveredDevices.value = emptyList()
        _connectionState.value = PiConnectionState.Scanning
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(PI_SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        timeoutHandler.removeCallbacks(scanTimeoutRunnable)
        timeoutHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
    }

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION])
    fun stopScan() {
        timeoutHandler.removeCallbacks(scanTimeoutRunnable)
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_connectionState.value is PiConnectionState.Scanning) {
            _connectionState.value = PiConnectionState.Disconnected
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
    fun connect(device: BluetoothDevice) {
        stopScan()
        connectionManager.connect(device)
            .retry(3, 100)
            .useAutoConnect(false)
            .enqueue()
    }

    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH])
    fun disconnect() {
        connectionManager.disconnect().enqueue()
    }

    /** No-op if not connected/ready - callers should check [connectionState] first (see
     * ShotTimerViewModel's PI-mode branch, which surfaces "not connected" as a user-facing
     * error rather than silently falling back to local mic detection). */
    fun arm() {
        connectionManager.arm()
    }

    val isReady: Boolean
        get() = connectionManager.isArmReady

    companion object {
        // How long startScan() waits for a matching advertisement before giving up and
        // transitioning to NotFound.
        private const val SCAN_TIMEOUT_MS = 10_000L

        @Volatile
        private var instance: PiRepository? = null

        fun getInstance(context: Context): PiRepository =
            instance ?: synchronized(this) {
                instance ?: PiRepository(context.applicationContext).also { instance = it }
            }
    }
}
