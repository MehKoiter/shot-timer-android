package com.shottimer.app.audio

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var captureJob: Job? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening() {
        if (captureJob?.isActive == true) return
        _isListening.value = true
        _errorMessage.value = null
        captureJob = viewModelScope.launch {
            try {
                audioSource.chunks().collect { chunk ->
                    _level.value = normalizedLevel(chunk.samples)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "Microphone unavailable - is another app using it?"
                _isListening.value = false
                _level.value = 0f
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
