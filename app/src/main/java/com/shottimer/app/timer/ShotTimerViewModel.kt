package com.shottimer.app.timer

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shottimer.app.audio.AudioSource
import com.shottimer.app.detection.ShotDetector
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RunState { IDLE, ARMED_WAITING, RUNNING, STOPPED }

data class TimerUiState(
    val runState: RunState = RunState.IDLE,
    val elapsedMillis: Long = 0L,
    val shotSplitsMillis: List<Long> = emptyList(),
    val sensitivity: Float = DEFAULT_SENSITIVITY
)

private const val MIN_DELAY_MS = 1000L
private const val MAX_DELAY_MS = 3500L
private const val TICK_INTERVAL_MS = 10L

private const val BEEP_SAMPLE_RATE_HZ = 44100
private const val BEEP_DURATION_MS = 150
private const val BEEP_FREQUENCY_HZ = 1800.0
private const val BEEP_FADE_MS = 5

const val DEFAULT_SENSITIVITY = 0.5f

// Placeholder starting range - real values need tuning against an actual gun/range once M3 gets
// tested live, per the plan. Higher sensitivity = lower amplitude threshold = quieter sounds trigger.
private const val MIN_THRESHOLD_AMPLITUDE = 0.05f
private const val MAX_THRESHOLD_AMPLITUDE = 0.6f

private fun thresholdFor(sensitivity: Float): Float =
    MAX_THRESHOLD_AMPLITUDE - sensitivity.coerceIn(0f, 1f) * (MAX_THRESHOLD_AMPLITUDE - MIN_THRESHOLD_AMPLITUDE)

class ShotTimerViewModel : ViewModel() {

    // Synthesized in-code rather than a ToneGenerator system tone or a bundled asset: ToneGenerator's
    // legacy CDMA tones are silent on some GSM-only devices (no tone table shipped), and this way the
    // exact frequency/duration/volume are ours to control without needing an audio asset in res/raw.
    private val beepSamples: ShortArray = buildBeepSamples()

    private val audioSource = AudioSource()

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null
    private var startMarkNanos: Long = 0L

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (_uiState.value.runState == RunState.ARMED_WAITING || _uiState.value.runState == RunState.RUNNING) return
        val detector = ShotDetector(thresholdAmplitude = thresholdFor(_uiState.value.sensitivity))
        _uiState.value = TimerUiState(runState = RunState.ARMED_WAITING, sensitivity = _uiState.value.sensitivity)
        runJob = viewModelScope.launch {
            delay(Random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1))
            beep()
            startMarkNanos = SystemClock.elapsedRealtimeNanos()
            _uiState.value = _uiState.value.copy(runState = RunState.RUNNING)
            coroutineScope {
                launch { runClock() }
                launch { detectShots(detector) }
            }
        }
    }

    fun stop() {
        val runStateBeforeCancel = _uiState.value.runState
        runJob?.cancel()
        runJob = null
        _uiState.value = when (runStateBeforeCancel) {
            // Cancelled during the random delay, before the beep ever fired - nothing to record.
            RunState.ARMED_WAITING -> TimerUiState(sensitivity = _uiState.value.sensitivity)
            RunState.RUNNING -> _uiState.value.copy(runState = RunState.STOPPED)
            else -> _uiState.value
        }
    }

    fun setSensitivity(sensitivity: Float) {
        _uiState.value = _uiState.value.copy(sensitivity = sensitivity.coerceIn(0f, 1f))
    }

    private suspend fun runClock() {
        while (currentCoroutineContext().isActive) {
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startMarkNanos) / 1_000_000
            _uiState.value = _uiState.value.copy(elapsedMillis = elapsedMs)
            delay(TICK_INTERVAL_MS)
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun detectShots(detector: ShotDetector) {
        audioSource.chunks().collect { chunk ->
            val events = detector.process(chunk)
            if (events.isNotEmpty()) {
                val newSplits = events.map { (it.timestampNanos - startMarkNanos) / 1_000_000 }
                _uiState.value = _uiState.value.copy(
                    shotSplitsMillis = _uiState.value.shotSplitsMillis + newSplits
                )
            }
        }
    }

    private fun beep() {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(BEEP_SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(beepSamples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(beepSamples, 0, beepSamples.size)
        track.play()
        // Fire-and-forget: release once playback has had time to finish, without blocking run state
        // transitions (the clock should start the instant play() is triggered, not after the beep ends).
        viewModelScope.launch {
            delay(BEEP_DURATION_MS.toLong() + 50)
            track.release()
        }
    }
}

private fun buildBeepSamples(): ShortArray {
    val numSamples = BEEP_SAMPLE_RATE_HZ * BEEP_DURATION_MS / 1000
    val fadeSamples = BEEP_SAMPLE_RATE_HZ * BEEP_FADE_MS / 1000
    return ShortArray(numSamples) { i ->
        val angle = 2.0 * PI * i * BEEP_FREQUENCY_HZ / BEEP_SAMPLE_RATE_HZ
        val envelope = min(
            min(i, fadeSamples).toDouble() / fadeSamples,
            min(numSamples - i, fadeSamples).toDouble() / fadeSamples
        )
        (sin(angle) * envelope * Short.MAX_VALUE).toInt().toShort()
    }
}
