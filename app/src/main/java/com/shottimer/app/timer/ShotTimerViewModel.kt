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
import com.shottimer.app.R
import com.shottimer.app.audio.AudioChunk
import com.shottimer.app.audio.AudioSource
import com.shottimer.app.data.RunEntity
import com.shottimer.app.data.RunRepository
import com.shottimer.app.detection.ShotDetector
import com.shottimer.app.drills.Drill
import com.shottimer.app.settings.SettingsRepository
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
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
    val selectedDrill: Drill? = null,
    val selectedShooter: String? = null
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

/**
 * [audioChunks] and [playTone] default to the real microphone/speaker but are injectable seams so
 * tests can feed synthetic [AudioChunk]s and skip real AudioTrack playback (which blocks
 * indefinitely under Robolectric's audio shadow) without RECORD_AUDIO or real hardware -
 * @JvmOverloads keeps the single-arg `ShotTimerViewModel(Application)` constructor that
 * ViewModelProvider's reflection looks up, so production construction (`viewModel()`) is
 * unaffected. Losing the compile-time @RequiresPermission check on the audioChunks call site is
 * the traded-off cost; start() still carries it.
 */
class ShotTimerViewModel @JvmOverloads constructor(
    application: Application,
    private val audioChunks: () -> Flow<AudioChunk> = { AudioSource().chunks() },
    // Null (not a lambda default) because a default constructor-parameter expression can't
    // reference an instance member (playToneWithAudioTrack) - resolved to the real
    // implementation lazily in playToneEffect() instead, where `this` is fully available.
    private val playTone: ((samples: ShortArray, volume: Float) -> Unit)? = null
) : AndroidViewModel(application) {

    // Synthesized in-code rather than a ToneGenerator system tone or a bundled asset: ToneGenerator's
    // legacy CDMA tones are silent on some GSM-only devices (no tone table shipped), and this way the
    // exact frequency/duration/volume are ours to control without needing an audio asset in res/raw.
    private val startBeepSamples: ShortArray = buildBeepSamples(START_BEEP_FREQUENCY_HZ)
    private val parBeepSamples: ShortArray = buildBeepSamples(PAR_BEEP_FREQUENCY_HZ)

    private val runRepository = RunRepository(application)
    private val settingsRepository = SettingsRepository.getInstance(application)

    private val _uiState = MutableStateFlow(
        TimerUiState(sensitivity = settingsRepository.settings.value.defaultSensitivity)
    )
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    /** Previously-used shooter names, for the picker's "tap to reuse" list - see docs on
     * [com.shottimer.app.data.RunDao.observeDistinctShooterNames]. */
    val knownShooters: StateFlow<List<String>> = runRepository.observeDistinctShooterNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var runJob: Job? = null
    private var parJob: Job? = null
    private var startMarkNanos: Long = 0L

    // Written on the main dispatcher (start()) but read from detectShots' collector on
    // Dispatchers.IO, so it needs to be volatile for visibility across threads.
    @Volatile private var suppressUntilNanos: Long = Long.MAX_VALUE

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (_uiState.value.runState == RunState.ARMED_WAITING || _uiState.value.runState == RunState.RUNNING) return
        val settings = settingsRepository.settings.value
        val detector = ShotDetector(
            thresholdAmplitude = thresholdFor(_uiState.value.sensitivity),
            lockoutNanos = settings.echoLockoutMs * 1_000_000L
        )
        suppressUntilNanos = Long.MAX_VALUE
        _uiState.value = freshIdleState().copy(runState = RunState.ARMED_WAITING, micErrorMessage = null)
        runJob = viewModelScope.launch {
            // Launch detection immediately so AudioRecord is armed and recording throughout the
            // random delay - otherwise a fast first shot can beat the mic coming up. Detected
            // chunks are dropped (see suppressUntilNanos) until just after the start beep ends,
            // so the detector's internal echo-lockout state stays clean during the wait and the
            // beep itself is never mistaken for shot #1.
            launch { detectShots(detector) }

            val minDelayMs = (settings.minDelaySeconds * 1000).toLong()
            val maxDelayMs = (settings.maxDelaySeconds * 1000).toLong()
            delay(Random.nextLong(minDelayMs, maxDelayMs + 1))
            playToneEffect(startBeepSamples, settings.beepVolume)
            startMarkNanos = SystemClock.elapsedRealtimeNanos()
            suppressUntilNanos = startMarkNanos + (BEEP_DURATION_MS + 50) * 1_000_000L
            _uiState.update { it.copy(runState = RunState.RUNNING) }

            if (_uiState.value.parTimeEnabled) {
                schedulePar(_uiState.value.parTimeSeconds, settings.beepVolume)
            }

            runClock()
        }
    }

    fun stop() {
        parJob?.cancel()
        parJob = null
        val runStateBeforeCancel = _uiState.value.runState
        runJob?.cancel()
        runJob = null
        when (runStateBeforeCancel) {
            // Cancelled during the random delay, before the beep ever fired - nothing to record.
            RunState.ARMED_WAITING -> _uiState.value = freshIdleState()
            RunState.RUNNING -> {
                val finishedState = _uiState.updateAndGet { it.copy(runState = RunState.STOPPED) }
                saveRun(finishedState)
            }
            else -> Unit
        }
    }

    private fun saveRun(finishedState: TimerUiState) {
        viewModelScope.launch {
            runRepository.saveRun(
                RunEntity(
                    timestampEpochMillis = System.currentTimeMillis(),
                    totalElapsedMillis = finishedState.elapsedMillis,
                    shotTimestampsMillis = finishedState.shotSplitsMillis,
                    parTimeSeconds = if (finishedState.parTimeEnabled) finishedState.parTimeSeconds else null,
                    drillName = finishedState.selectedDrill?.name,
                    shooterName = finishedState.selectedShooter
                )
            )
        }
    }

    fun selectDrill(drill: Drill?) {
        _uiState.update { it.copy(selectedDrill = drill) }
    }

    /** Blank/whitespace-only names collapse to null rather than saving as an empty-string tag. */
    fun selectShooter(name: String?) {
        _uiState.update { it.copy(selectedShooter = name?.trim()?.takeIf { name -> name.isNotEmpty() }) }
    }

    fun setSensitivity(sensitivity: Float) {
        _uiState.update { it.copy(sensitivity = sensitivity.coerceIn(0f, 1f)) }
    }

    fun setParTimeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(parTimeEnabled = enabled) }
    }

    fun setParTimeSeconds(seconds: Float) {
        _uiState.update {
            it.copy(parTimeSeconds = seconds.coerceIn(MIN_PAR_TIME_SECONDS, MAX_PAR_TIME_SECONDS))
        }
    }

    /** Fields that should survive a fresh start()/stop() reset instead of snapping back to defaults. */
    private fun freshIdleState(): TimerUiState {
        val current = _uiState.value
        return TimerUiState(
            sensitivity = current.sensitivity,
            parTimeEnabled = current.parTimeEnabled,
            parTimeSeconds = current.parTimeSeconds,
            selectedDrill = current.selectedDrill,
            selectedShooter = current.selectedShooter
        )
    }

    private fun schedulePar(parTimeSeconds: Float, beepVolume: Float) {
        parJob = viewModelScope.launch {
            delay((parTimeSeconds * 1000).toLong())
            playToneEffect(parBeepSamples, beepVolume)
            stop()
        }
    }

    private suspend fun runClock() {
        while (currentCoroutineContext().isActive) {
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startMarkNanos) / 1_000_000
            _uiState.update { it.copy(elapsedMillis = elapsedMs) }
            delay(TICK_INTERVAL_MS)
        }
    }

    // Kept as documentation: audioChunks() defaults to a real mic source that requires this,
    // but the lambda call itself isn't lint-checkable the way audioSource.chunks() was.
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun detectShots(detector: ShotDetector) {
        try {
            audioChunks().collect { chunk ->
                // Drop audio captured while armed-waiting or during the start beep itself, so the
                // detector never sees it - keeps its internal echo-lockout state clean and stops
                // the beep from being mistaken for shot #1 or from tripping the lockout and
                // suppressing a genuinely fast first shot.
                if (chunk.captureEndNanos < suppressUntilNanos) return@collect
                val events = detector.process(chunk)
                if (events.isNotEmpty()) {
                    val newSplits = events.map { (it.timestampNanos - startMarkNanos) / 1_000_000 }
                    _uiState.update { it.copy(shotSplitsMillis = it.shotSplitsMillis + newSplits) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // AudioRecord failed to initialize (mic busy/unavailable) - surface it instead of the
            // whole app crashing from an uncaught exception in this coroutine.
            _uiState.update {
                it.copy(micErrorMessage = getApplication<Application>().getString(R.string.mic_unavailable))
            }
        }
    }

    private fun playToneEffect(samples: ShortArray, volume: Float) {
        (playTone ?: ::playToneWithAudioTrack)(samples, volume)
    }

    private fun playToneWithAudioTrack(samples: ShortArray, volume: Float) {
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
