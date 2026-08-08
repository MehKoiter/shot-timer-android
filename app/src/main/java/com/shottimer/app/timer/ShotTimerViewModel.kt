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
import com.shottimer.app.pi.PiEvent
import com.shottimer.app.pi.PiRepository
import com.shottimer.app.settings.SettingsRepository
import com.shottimer.app.settings.TimerSource
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    val parTimeSeconds: Float = DEFAULT_PAR_TIME_SECONDS,
    val micErrorMessage: String? = null,
    val sourceMode: TimerSource = TimerSource.LOCAL,
    val piErrorMessage: String? = null
)

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
    private val runRepository = RunRepository(application)
    private val settingsRepository = SettingsRepository.getInstance(application)
    private val piRepository = PiRepository.getInstance(application)

    private val _uiState = MutableStateFlow(
        TimerUiState(
            sensitivity = settingsRepository.settings.value.defaultSensitivity,
            sourceMode = settingsRepository.settings.value.sourceMode
        )
    )
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null
    private var parJob: Job? = null
    private var startMarkNanos: Long = 0L

    init {
        // Always-on, independent of any run in progress: the Pi replays its offline backlog
        // (runs recorded while no phone was connected) the moment a phone subscribes, over the
        // same event stream a live run's Beep/Shot/RunComplete arrive on - see
        // PiRepository/PiConnectionManager and pi-companion/shot_timer_pi/ble_service.py's
        // _push_unsynced_runs. startPi()'s collector ignores Sync events; this one only acts on
        // them, so the two never race over the same event.
        viewModelScope.launch {
            piRepository.events.collect { event ->
                if (event is PiEvent.Sync) {
                    runRepository.saveSyncedRunIfNew(
                        RunEntity(
                            // The Pi doesn't record a wall-clock timestamp for offline runs (see
                            // ble_service.py's Sync payload shape) - "now" is sync time, not run
                            // time, but it's the best available without extending the protocol.
                            timestampEpochMillis = System.currentTimeMillis(),
                            totalElapsedMillis = event.totalMs,
                            shotTimestampsMillis = event.shotsMs,
                            parTimeSeconds = null,
                            piRunId = event.runId
                        )
                    )
                }
            }
        }

        // The LOCAL/PI toggle lives on the Pi Companion settings sub-screen (a different
        // ViewModel instance - see PiConnectionViewModel.setSourceMode), so this has to observe
        // SettingsRepository rather than read it once at construction, or flipping the toggle
        // wouldn't take effect here until the app restarted.
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.value = _uiState.value.copy(sourceMode = settings.sourceMode)
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (_uiState.value.runState == RunState.ARMED_WAITING || _uiState.value.runState == RunState.RUNNING) return
        when (_uiState.value.sourceMode) {
            TimerSource.LOCAL -> startLocal()
            TimerSource.PI -> startPi()
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startLocal() {
        val settings = settingsRepository.settings.value
        val detector = ShotDetector(
            thresholdAmplitude = thresholdFor(_uiState.value.sensitivity),
            lockoutNanos = settings.echoLockoutMs * 1_000_000L
        )
        _uiState.value = freshIdleState().copy(runState = RunState.ARMED_WAITING, micErrorMessage = null)
        runJob = viewModelScope.launch {
            val minDelayMs = (settings.minDelaySeconds * 1000).toLong()
            val maxDelayMs = (settings.maxDelaySeconds * 1000).toLong()
            delay(Random.nextLong(minDelayMs, maxDelayMs + 1))
            playTone(startBeepSamples, settings.beepVolume)
            startMarkNanos = SystemClock.elapsedRealtimeNanos()
            _uiState.value = _uiState.value.copy(runState = RunState.RUNNING)

            if (_uiState.value.parTimeEnabled) {
                schedulePar(_uiState.value.parTimeSeconds, settings.beepVolume)
            }

            coroutineScope {
                launch { runClock() }
                launch { detectShots(detector) }
            }
        }
    }

    /** Pi mode never silently falls through to local mic detection if it isn't connected - the
     * user picked this mode explicitly, so a missing connection is surfaced as an error rather
     * than guessed around (see docs/PI_COMPANION.md's "companion, not a hard dependency"
     * framing - the app doesn't paper over the difference). [PiRepository.arm] triggers the
     * exact same delay/beep/detect run the Pi's physical button does; the phone just listens. */
    private fun startPi() {
        if (!piRepository.isReady) {
            _uiState.value = _uiState.value.copy(
                piErrorMessage = "Not connected to Pi - connect it in Settings first"
            )
            return
        }
        _uiState.value = freshIdleState().copy(runState = RunState.ARMED_WAITING, piErrorMessage = null)
        piRepository.arm()
        runJob = viewModelScope.launch {
            piRepository.events.collect { event ->
                when (event) {
                    PiEvent.Beep -> {
                        // Marked from when the phone received this notification, not the Pi's
                        // own clock - the known, accepted BLE-latency cosmetic covered in
                        // docs/PI_COMPANION.md. Every timestamp downstream of this is the Pi's
                        // own, self-consistent relative to its own beep - only this one instant
                        // is approximate.
                        startMarkNanos = SystemClock.elapsedRealtimeNanos()
                        _uiState.value = _uiState.value.copy(runState = RunState.RUNNING)
                        launch { runClock() }
                    }
                    is PiEvent.Shot -> {
                        _uiState.value = _uiState.value.copy(
                            shotSplitsMillis = _uiState.value.shotSplitsMillis + event.elapsedMs
                        )
                    }
                    is PiEvent.RunComplete -> {
                        // The Pi's own totalMs/shotsMs are authoritative here, not the phone's
                        // local clock or accumulated splits - it's the whole point of the "Pi
                        // owns the run" design that these numbers need no correlation math.
                        val finished = _uiState.value.copy(
                            runState = RunState.STOPPED,
                            elapsedMillis = event.totalMs,
                            shotSplitsMillis = event.shotsMs
                        )
                        _uiState.value = finished
                        saveRun(finished)
                        cancel()
                    }
                    is PiEvent.Sync -> Unit
                }
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
            runRepository.saveRun(
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
            parTimeSeconds = current.parTimeSeconds,
            sourceMode = current.sourceMode
        )
    }

    private fun schedulePar(parTimeSeconds: Float, beepVolume: Float) {
        parJob = viewModelScope.launch {
            delay((parTimeSeconds * 1000).toLong())
            playTone(parBeepSamples, beepVolume)
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
        try {
            audioSource.chunks().collect { chunk ->
                val events = detector.process(chunk)
                if (events.isNotEmpty()) {
                    val newSplits = events.map { (it.timestampNanos - startMarkNanos) / 1_000_000 }
                    _uiState.value = _uiState.value.copy(
                        shotSplitsMillis = _uiState.value.shotSplitsMillis + newSplits
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // AudioRecord failed to initialize (mic busy/unavailable) - surface it instead of the
            // whole app crashing from an uncaught exception in this coroutine.
            _uiState.value = _uiState.value.copy(
                micErrorMessage = "Microphone unavailable - shot detection stopped for this run"
            )
        }
    }

    private fun playTone(samples: ShortArray, volume: Float) {
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
        track.setVolume(volume.coerceIn(0f, 1f))
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
