package com.shottimer.app.timer

import android.Manifest
import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shottimer.app.audio.AudioSource
import com.shottimer.app.data.RunEntity
import com.shottimer.app.data.RunRepository
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
    val sensitivity: Float = DEFAULT_SENSITIVITY,
    val parTimeEnabled: Boolean = false,
    val parTimeSeconds: Float = DEFAULT_PAR_TIME_SECONDS
)

private const val MIN_DELAY_MS = 1000L
private const val MAX_DELAY_MS = 3500L
private const val TICK_INTERVAL_MS = 10L

private const val BEEP_SAMPLE_RATE_HZ = 44100
private const val BEEP_DURATION_MS = 150
private const val START_BEEP_FREQUENCY_HZ = 1800.0
// Lower pitch than the start beep so a dry-fire drill can tell "go" and "par" apart by ear alone.
private const val PAR_BEEP_FREQUENCY_HZ = 1100.0
private const val BEEP_FADE_MS = 5

const val DEFAULT_SENSITIVITY = 0.5f
const val DEFAULT_PAR_TIME_SECONDS = 5.0f
const val MIN_PAR_TIME_SECONDS = 1.0f
const val MAX_PAR_TIME_SECONDS = 15.0f

// Calibrated against on-device peak-amplitude logging: finger snaps at arm's length peaked at
// 0.94-1.00 (clipping), background/handling noise stayed at 0.03-0.56. Range sits between those
// two clusters so the slider has real effect. Still a placeholder for actual gunfire, which will
// be louder still - may need to shift this range up further once tested on the range.
// Higher sensitivity = lower amplitude threshold = quieter sounds trigger.
private const val MIN_THRESHOLD_AMPLITUDE = 0.3f
private const val MAX_THRESHOLD_AMPLITUDE = 0.9f

private fun thresholdFor(sensitivity: Float): Float =
    MAX_THRESHOLD_AMPLITUDE - sensitivity.coerceIn(0f, 1f) * (MAX_THRESHOLD_AMPLITUDE - MIN_THRESHOLD_AMPLITUDE)

class ShotTimerViewModel(application: Application) : AndroidViewModel(application) {

    // Synthesized in-code rather than a ToneGenerator system tone or a bundled asset: ToneGenerator's
    // legacy CDMA tones are silent on some GSM-only devices (no tone table shipped), and this way the
    // exact frequency/duration/volume are ours to control without needing an audio asset in res/raw.
    private val startBeepSamples: ShortArray = buildBeepSamples(START_BEEP_FREQUENCY_HZ)
    private val parBeepSamples: ShortArray = buildBeepSamples(PAR_BEEP_FREQUENCY_HZ)

    private val audioSource = AudioSource()
    private val repository = RunRepository(application)

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null
    private var parJob: Job? = null
    private var startMarkNanos: Long = 0L

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (_uiState.value.runState == RunState.ARMED_WAITING || _uiState.value.runState == RunState.RUNNING) return
        val detector = ShotDetector(thresholdAmplitude = thresholdFor(_uiState.value.sensitivity))
        _uiState.value = freshIdleState().copy(runState = RunState.ARMED_WAITING)
        runJob = viewModelScope.launch {
            delay(Random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1))
            playTone(startBeepSamples)
            startMarkNanos = SystemClock.elapsedRealtimeNanos()
            _uiState.value = _uiState.value.copy(runState = RunState.RUNNING)

            if (_uiState.value.parTimeEnabled) {
                schedulePar(_uiState.value.parTimeSeconds)
            }

            coroutineScope {
                launch { runClock() }
                launch { detectShots(detector) }
            }
        }
    }

    fun stop() {
        parJob?.cancel()
        parJob = null
        val runStateBeforeCancel = _uiState.value.runState
        runJob?.cancel()
        runJob = null
        _uiState.value = when (runStateBeforeCancel) {
            // Cancelled during the random delay, before the beep ever fired - nothing to record.
            RunState.ARMED_WAITING -> freshIdleState()
            RunState.RUNNING -> _uiState.value.copy(runState = RunState.STOPPED).also(::saveRun)
            else -> _uiState.value
        }
    }

    private fun saveRun(finishedState: TimerUiState) {
        viewModelScope.launch {
            repository.saveRun(
                RunEntity(
                    timestampEpochMillis = System.currentTimeMillis(),
                    totalElapsedMillis = finishedState.elapsedMillis,
                    shotTimestampsMillis = finishedState.shotSplitsMillis,
                    parTimeSeconds = if (finishedState.parTimeEnabled) finishedState.parTimeSeconds else null
                )
            )
        }
    }

    fun setSensitivity(sensitivity: Float) {
        _uiState.value = _uiState.value.copy(sensitivity = sensitivity.coerceIn(0f, 1f))
    }

    fun setParTimeEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(parTimeEnabled = enabled)
    }

    fun setParTimeSeconds(seconds: Float) {
        _uiState.value = _uiState.value.copy(
            parTimeSeconds = seconds.coerceIn(MIN_PAR_TIME_SECONDS, MAX_PAR_TIME_SECONDS)
        )
    }

    /** Fields that should survive a fresh start()/stop() reset instead of snapping back to defaults. */
    private fun freshIdleState(): TimerUiState {
        val current = _uiState.value
        return TimerUiState(
            sensitivity = current.sensitivity,
            parTimeEnabled = current.parTimeEnabled,
            parTimeSeconds = current.parTimeSeconds
        )
    }

    private fun schedulePar(parTimeSeconds: Float) {
        parJob = viewModelScope.launch {
            delay((parTimeSeconds * 1000).toLong())
            playTone(parBeepSamples)
            stop()
        }
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

    private fun playTone(samples: ShortArray) {
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
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track.play()
        // Fire-and-forget: release once playback has had time to finish, without blocking run state
        // transitions (the clock should start the instant play() is triggered, not after the beep ends).
        viewModelScope.launch {
            delay(BEEP_DURATION_MS.toLong() + 50)
            track.release()
        }
    }
}

private fun buildBeepSamples(frequencyHz: Double): ShortArray {
    val numSamples = BEEP_SAMPLE_RATE_HZ * BEEP_DURATION_MS / 1000
    val fadeSamples = BEEP_SAMPLE_RATE_HZ * BEEP_FADE_MS / 1000
    return ShortArray(numSamples) { i ->
        val angle = 2.0 * PI * i * frequencyHz / BEEP_SAMPLE_RATE_HZ
        val envelope = min(
            min(i, fadeSamples).toDouble() / fadeSamples,
            min(numSamples - i, fadeSamples).toDouble() / fadeSamples
        )
        (sin(angle) * envelope * Short.MAX_VALUE).toInt().toShort()
    }
}
