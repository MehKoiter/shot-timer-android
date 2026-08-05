package com.shottimer.app.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

const val AUDIO_SAMPLE_RATE_HZ = 44100

/**
 * Thin wrapper around [AudioRecord]. Emits raw PCM chunks so detection logic (added in a later
 * milestone) can stay a pure function over sample arrays instead of depending on this class.
 */
class AudioSource {

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun chunks(): Flow<ShortArray> = flow {
        val minBufferSize = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBufferSize > 0) minBufferSize else AUDIO_SAMPLE_RATE_HZ / 10

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AUDIO_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        try {
            audioRecord.startRecording()
            val readBuffer = ShortArray(bufferSize / 2)
            while (currentCoroutineContext().isActive) {
                val samplesRead = audioRecord.read(readBuffer, 0, readBuffer.size)
                if (samplesRead > 0) {
                    emit(readBuffer.copyOf(samplesRead))
                }
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
    }.flowOn(Dispatchers.IO)
}
