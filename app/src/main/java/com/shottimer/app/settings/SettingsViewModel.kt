package com.shottimer.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val settings: StateFlow<TimerSettings> = repository.settings

    fun setDefaultSensitivity(value: Float) {
        repository.update { it.copy(defaultSensitivity = value.coerceIn(0f, 1f)) }
    }

    fun setEchoLockoutMs(value: Long) {
        repository.update { it.copy(echoLockoutMs = value.coerceIn(MIN_ECHO_LOCKOUT_MS, MAX_ECHO_LOCKOUT_MS)) }
    }

    fun setDelayRange(minSeconds: Float, maxSeconds: Float) {
        repository.update {
            it.copy(
                minDelaySeconds = minSeconds.coerceIn(MIN_RANDOM_DELAY_SECONDS, maxSeconds),
                maxDelaySeconds = maxSeconds.coerceIn(minSeconds, MAX_RANDOM_DELAY_SECONDS)
            )
        }
    }

    fun setBeepVolume(value: Float) {
        repository.update { it.copy(beepVolume = value.coerceIn(0f, 1f)) }
    }
}
