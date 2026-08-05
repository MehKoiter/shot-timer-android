package com.shottimer.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "shot_timer_settings"
private const val KEY_SENSITIVITY = "default_sensitivity"
private const val KEY_LOCKOUT_MS = "echo_lockout_ms"
private const val KEY_MIN_DELAY = "min_delay_seconds"
private const val KEY_MAX_DELAY = "max_delay_seconds"
private const val KEY_BEEP_VOLUME = "beep_volume"

/**
 * Plain SharedPreferences rather than DataStore: a handful of scalar settings doesn't need
 * DataStore's async/proto machinery, and this avoids pinning yet another library version.
 * [settings] is a write-through cache - update() writes to disk and the in-memory StateFlow
 * together, so readers never need to suspend to see a freshly-written value.
 */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<TimerSettings> = _settings.asStateFlow()

    private fun loadSettings(): TimerSettings {
        val defaults = TimerSettings()
        return TimerSettings(
            defaultSensitivity = prefs.getFloat(KEY_SENSITIVITY, defaults.defaultSensitivity),
            echoLockoutMs = prefs.getLong(KEY_LOCKOUT_MS, defaults.echoLockoutMs),
            minDelaySeconds = prefs.getFloat(KEY_MIN_DELAY, defaults.minDelaySeconds),
            maxDelaySeconds = prefs.getFloat(KEY_MAX_DELAY, defaults.maxDelaySeconds),
            beepVolume = prefs.getFloat(KEY_BEEP_VOLUME, defaults.beepVolume)
        )
    }

    fun update(transform: (TimerSettings) -> TimerSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit()
            .putFloat(KEY_SENSITIVITY, updated.defaultSensitivity)
            .putLong(KEY_LOCKOUT_MS, updated.echoLockoutMs)
            .putFloat(KEY_MIN_DELAY, updated.minDelaySeconds)
            .putFloat(KEY_MAX_DELAY, updated.maxDelaySeconds)
            .putFloat(KEY_BEEP_VOLUME, updated.beepVolume)
            .apply()
    }
}
