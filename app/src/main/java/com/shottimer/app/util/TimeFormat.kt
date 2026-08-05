package com.shottimer.app.util

/** Formats elapsed time as shot timers conventionally display it: MM:SS.CC (centiseconds). */
fun formatElapsed(elapsedMillis: Long): String {
    val totalCentis = elapsedMillis / 10
    val minutes = totalCentis / 6000
    val seconds = (totalCentis / 100) % 60
    val centis = totalCentis % 100
    return "%02d:%02d.%02d".format(minutes, seconds, centis)
}
