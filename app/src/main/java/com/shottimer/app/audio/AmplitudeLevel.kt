package com.shottimer.app.audio

import kotlin.math.sqrt

fun rms(samples: ShortArray): Double {
    if (samples.isEmpty()) return 0.0
    var sumSquares = 0.0
    for (sample in samples) {
        sumSquares += sample.toDouble() * sample.toDouble()
    }
    return sqrt(sumSquares / samples.size)
}

/** RMS amplitude of [samples] normalized against full 16-bit scale, clamped to 0f..1f. */
fun normalizedLevel(samples: ShortArray): Float =
    (rms(samples) / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
