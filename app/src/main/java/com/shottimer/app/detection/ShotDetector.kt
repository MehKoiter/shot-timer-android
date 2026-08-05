package com.shottimer.app.detection

import com.shottimer.app.audio.AUDIO_SAMPLE_RATE_HZ
import com.shottimer.app.audio.AudioChunk
import kotlin.math.abs

/** A detected shot, timestamped on the same clock as [android.os.SystemClock.elapsedRealtimeNanos]. */
data class ShotEvent(val timestampNanos: Long)

const val DEFAULT_THRESHOLD_AMPLITUDE = 0.35f
const val DEFAULT_ECHO_LOCKOUT_MS = 100L

/**
 * Pure sample-domain threshold detector: no Android/AudioRecord dependency, so it can be unit
 * tested against synthetic (or recorded) waveforms without a live mic. Deliberately dumb - a
 * single amplitude threshold plus an echo-lockout window, matching how real shot timers describe
 * their own detection (see docs/DESIGN.md). Sensitivity tuning happens by adjusting
 * [thresholdAmplitude], not by adding smarter signal processing.
 */
class ShotDetector(
    private val thresholdAmplitude: Float = DEFAULT_THRESHOLD_AMPLITUDE,
    private val lockoutNanos: Long = DEFAULT_ECHO_LOCKOUT_MS * 1_000_000L,
    private val sampleRateHz: Int = AUDIO_SAMPLE_RATE_HZ
) {
    private var lastEventNanos: Long = Long.MIN_VALUE / 2

    /** Feeds one chunk through the detector; returns any shots found in it (usually 0 or 1). */
    fun process(chunk: AudioChunk): List<ShotEvent> {
        val samples = chunk.samples
        if (samples.isEmpty()) return emptyList()

        val nanosPerSample = 1_000_000_000L / sampleRateHz
        val chunkStartNanos = chunk.captureEndNanos - samples.size.toLong() * nanosPerSample

        val events = mutableListOf<ShotEvent>()
        for (i in samples.indices) {
            val amplitude = abs(samples[i].toFloat()) / Short.MAX_VALUE
            if (amplitude < thresholdAmplitude) continue

            val sampleTimeNanos = chunkStartNanos + i * nanosPerSample
            if (sampleTimeNanos - lastEventNanos < lockoutNanos) continue

            events += ShotEvent(sampleTimeNanos)
            lastEventNanos = sampleTimeNanos
        }
        return events
    }

    /** Call at the start of each run so a previous run's lockout state can't suppress shot #1. */
    fun reset() {
        lastEventNanos = Long.MIN_VALUE / 2
    }
}
