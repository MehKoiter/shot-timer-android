package com.shottimer.app.timer

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class RunState { IDLE, ARMED_WAITING, RUNNING, STOPPED }

data class TimerUiState(
    val runState: RunState = RunState.IDLE,
    val elapsedMillis: Long = 0L
)

private const val MIN_DELAY_MS = 1000L
private const val MAX_DELAY_MS = 3500L
private const val BEEP_DURATION_MS = 150
private const val TICK_INTERVAL_MS = 10L

class ShotTimerViewModel : ViewModel() {

    // STREAM_ALARM so the start signal is audible even if the phone's media/ring volume is low or muted.
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null
    private var startMarkNanos: Long = 0L

    fun start() {
        if (_uiState.value.runState == RunState.ARMED_WAITING || _uiState.value.runState == RunState.RUNNING) return
        _uiState.value = TimerUiState(runState = RunState.ARMED_WAITING)
        runJob = viewModelScope.launch {
            delay(Random.nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1))
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, BEEP_DURATION_MS)
            startMarkNanos = SystemClock.elapsedRealtimeNanos()
            _uiState.value = _uiState.value.copy(runState = RunState.RUNNING)
            while (isActive) {
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startMarkNanos) / 1_000_000
                _uiState.value = _uiState.value.copy(elapsedMillis = elapsedMs)
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        val runStateBeforeCancel = _uiState.value.runState
        runJob?.cancel()
        runJob = null
        _uiState.value = when (runStateBeforeCancel) {
            // Cancelled during the random delay, before the beep ever fired - nothing to record.
            RunState.ARMED_WAITING -> TimerUiState()
            RunState.RUNNING -> _uiState.value.copy(runState = RunState.STOPPED)
            else -> _uiState.value
        }
    }

    override fun onCleared() {
        toneGenerator.release()
    }
}
