package com.shottimer.app.audio

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MicTestViewModel : ViewModel() {

    private val audioSource = AudioSource()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var captureJob: Job? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening() {
        if (captureJob?.isActive == true) return
        _isListening.value = true
        captureJob = viewModelScope.launch {
            audioSource.chunks().collect { chunk ->
                _level.value = normalizedLevel(chunk.samples)
            }
        }
    }

    fun stopListening() {
        captureJob?.cancel()
        captureJob = null
        _isListening.value = false
        _level.value = 0f
    }

    override fun onCleared() {
        stopListening()
    }
}
