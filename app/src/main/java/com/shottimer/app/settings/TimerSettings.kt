package com.shottimer.app.settings

data class TimerSettings(
    val defaultSensitivity: Float = 0.5f,
    val echoLockoutMs: Long = 100L,
    val minDelaySeconds: Float = 1.0f,
    val maxDelaySeconds: Float = 3.5f,
    /** Multiplier applied via AudioTrack.setVolume(), independent of the system Alarm volume. */
    val beepVolume: Float = 1.0f
)

const val MIN_ECHO_LOCKOUT_MS = 20L
const val MAX_ECHO_LOCKOUT_MS = 500L
const val MIN_RANDOM_DELAY_SECONDS = 0.1f
const val MAX_RANDOM_DELAY_SECONDS = 15.0f
