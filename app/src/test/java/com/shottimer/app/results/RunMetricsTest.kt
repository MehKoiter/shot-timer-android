package com.shottimer.app.results

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunMetricsTest {

    @Test
    fun `no shots produces null first shot and no splits`() {
        val metrics = computeRunMetrics(totalElapsedMillis = 5000L, shotTimestampsMillis = emptyList())
        assertNull(metrics.firstShotMillis)
        assertEquals(5000L, metrics.totalElapsedMillis)
        assertEquals(emptyList<ShotSplit>(), metrics.splits)
    }

    @Test
    fun `single shot splits from zero - the draw time`() {
        val metrics = computeRunMetrics(totalElapsedMillis = 870L, shotTimestampsMillis = listOf(870L))
        assertEquals(870L, metrics.firstShotMillis)
        assertEquals(listOf(ShotSplit(shotNumber = 1, elapsedMillis = 870L, splitMillis = 870L)), metrics.splits)
    }

    @Test
    fun `multiple shots split from the previous shot, not from zero`() {
        val metrics = computeRunMetrics(
            totalElapsedMillis = 2500L,
            shotTimestampsMillis = listOf(870L, 1200L, 2500L)
        )
        assertEquals(870L, metrics.firstShotMillis)
        assertEquals(
            listOf(
                ShotSplit(shotNumber = 1, elapsedMillis = 870L, splitMillis = 870L),
                ShotSplit(shotNumber = 2, elapsedMillis = 1200L, splitMillis = 330L),
                ShotSplit(shotNumber = 3, elapsedMillis = 2500L, splitMillis = 1300L)
            ),
            metrics.splits
        )
    }
}
