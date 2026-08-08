package com.shottimer.app.pi

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import java.util.UUID
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data

/** Matches SERVICE_UUID in pi-companion/shot_timer_pi/ble_service.py - also used by
 * [PiRepository] to filter BLE scan results down to Pi companions. */
val PI_SERVICE_UUID: UUID = UUID.fromString("28559379-1dfa-497c-ab16-21999fe01d90")
private val COMMAND_CHAR_UUID: UUID = UUID.fromString("28559379-1dfa-497c-ab16-21999fe01d91")
private val EVENT_CHAR_UUID: UUID = UUID.fromString("28559379-1dfa-497c-ab16-21999fe01d92")
private val SYNC_CHAR_UUID: UUID = UUID.fromString("28559379-1dfa-497c-ab16-21999fe01d93")

private const val ARM_COMMAND = "ARM"

/**
 * Nordic `BleManager` subclass owning the actual `BluetoothGatt` connection to the Pi.
 * Discovers the Command/Event/Sync characteristics by the UUIDs the Pi side defines, writes
 * "ARM" to arm a run, and forwards every Event/Sync notification (parsed via [parsePiEvent])
 * to [onEvent]. `connect()`/`disconnect()` are inherited public methods on [BleManager] -
 * callers (see PiRepository) use those directly rather than this class wrapping them.
 *
 * Not unit-testable - needs a real `BluetoothGatt`/adapter, same as every hardware-facing
 * module on the Pi side (see the phone-BLE-client plan's testability table).
 */
class PiConnectionManager(
    context: Context,
    private val onEvent: (PiEvent) -> Unit
) : BleManager(context) {

    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var eventCharacteristic: BluetoothGattCharacteristic? = null
    private var syncCharacteristic: BluetoothGattCharacteristic? = null

    /** True once connected AND the Command characteristic has been discovered - i.e. [arm] will
     * actually do something rather than silently no-op. */
    val isArmReady: Boolean
        get() = isReady && commandCharacteristic != null

    fun arm() {
        val characteristic = commandCharacteristic ?: return
        writeCharacteristic(
            characteristic,
            ARM_COMMAND.toByteArray(Charsets.US_ASCII),
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        ).enqueue()
    }

    // BleManager's own constructor calls getGattCallback() synchronously, before any of this
    // subclass's property initializers run (superclass <init> always completes first) - a
    // stored `private val gattCallback = object : ... {}` would still be null at that point,
    // crashing with an NPE inside BleManager's constructor. Returning a fresh object literal
    // from the override itself sidesteps that: it's only actually built (and only needs `this`
    // to exist, not be fully initialized) the moment the framework calls this function.
    override fun getGattCallback(): BleManagerGattCallback = object : BleManagerGattCallback() {
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service = gatt.getService(PI_SERVICE_UUID) ?: return false
            commandCharacteristic = service.getCharacteristic(COMMAND_CHAR_UUID)
            eventCharacteristic = service.getCharacteristic(EVENT_CHAR_UUID)
            syncCharacteristic = service.getCharacteristic(SYNC_CHAR_UUID)
            return commandCharacteristic != null &&
                eventCharacteristic != null &&
                syncCharacteristic != null
        }

        override fun initialize() {
            setNotificationCallback(eventCharacteristic!!).with { _, data -> handleNotification(data) }
            setNotificationCallback(syncCharacteristic!!).with { _, data -> handleNotification(data) }
            enableNotifications(eventCharacteristic!!).enqueue()
            enableNotifications(syncCharacteristic!!).enqueue()
        }

        override fun onServicesInvalidated() {
            commandCharacteristic = null
            eventCharacteristic = null
            syncCharacteristic = null
        }
    }

    private fun handleNotification(data: Data) {
        val bytes = data.value ?: return
        parsePiEvent(bytes)?.let(onEvent)
    }
}
