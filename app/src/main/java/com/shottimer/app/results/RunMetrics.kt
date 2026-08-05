package com.shottimer.app.results

/** One shot's absolute time since the beep, plus its split (delta) from the previous shot (or from 0 for shot 1 - i.e. the draw/first-shot time). */
data class ShotSplit(val shotNumber: Int, val elapsedMillis: Long, val splitMillis: Long)

data class RunMetrics(
    val firstShotMillis: Long?,
    val totalElapsedMillis: Long,
    val splits: List<ShotSplit>
)

/** Pure derivation of shot timer metrics from raw data - no Android/ViewModel dependency, so it's
 * usable both for a live run and for replaying a saved run from history. */
fun computeRunMetrics(totalElapsedMillis: Long, shotTimestampsMillis: List<Long>): RunMetrics {
    val splits = shotTimestampsMillis.mapIndexed { index, elapsedMillis ->
        val previousElapsedMillis = if (index == 0) 0L else shotTimestampsMillis[index - 1]
        ShotSplit(
            shotNumber = index + 1,
            elapsedMillis = elapsedMillis,
            splitMillis = elapsedMillis - previousElapsedMillis
        )
    }
    return RunMetrics(
        firstShotMillis = shotTimestampsMillis.firstOrNull(),
        totalElapsedMillis = totalElapsedMillis,
        splits = splits
    )
}
