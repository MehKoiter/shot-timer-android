package com.shottimer.app.detection

import com.shottimer.app.audio.AudioChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * sampleRateHz is set to 1000 (1ms/sample) in these tests purely so expected timestamps are easy
 * to compute by hand - the real app uses AUDIO_SAMPLE_RATE_HZ (44100), but the detector's math is
 * sample-rate-agnostic.
 */
private const val TEST_SAMPLE_RATE_HZ = 1000
private const val TEST_LOCKOUT_MS = 10L
private const val TEST_THRESHOLD = 0.5f

private fun silentChunk(sampleCount: Int, captureEndNanos: Long, spikesAtIndices: Set<Int> = emptySet()): AudioChunk {
    val samples = ShortArray(sampleCount) { i ->
        if (i in spikesAtIndices) Short.MAX_VALUE else 0
    }
    return AudioChunk(samples, captureEndNanos)
}

private fun detector(lockoutMs: Long = TEST_LOCKOUT_MS) = ShotDetector(
    thresholdAmplitude = TEST_THRESHOLD,
    lockoutNanos = lockoutMs * 1_000_000L,
    sampleRateHz = TEST_SAMPLE_RATE_HZ
)

class ShotDetectorTest {

    @Test
    fun `silence produces no events`() {
        val chunk = silentChunk(sampleCount = 10, captureEndNanos = 10_000_000L)
        assertEquals(emptyList<ShotEvent>(), detector().process(chunk))
    }

    @Test
    fun `single spike is timestamped at its exact sample position`() {
        // 10 samples at 1ms each, chunk covers [0ms, 10ms), spike at index 5 -> 5ms.
        val chunk = silentChunk(sampleCount = 10, captureEndNanos = 10_000_000L, spikesAtIndices = setOf(5))
        val events = detector().process(chunk)
        assertEquals(listOf(ShotEvent(5_000_000L)), events)
    }

    @Test
    fun `quiet samples below threshold are ignored`() {
        val samples = ShortArray(10) { (Short.MAX_VALUE * 0.1).toInt().toShort() } // amplitude 0.1 < threshold 0.5
        val chunk = AudioChunk(samples, captureEndNanos = 10_000_000L)
        assertEquals(emptyList<ShotEvent>(), detector().process(chunk))
    }

    @Test
    fun `second spike within the lockout window is suppressed`() {
        // Spikes 1ms apart; lockout is 10ms, so only the first should register.
        val chunk = silentChunk(sampleCount = 10, captureEndNanos = 10_000_000L, spikesAtIndices = setOf(5, 6))
        val events = detector().process(chunk)
        assertEquals(listOf(ShotEvent(5_000_000L)), events)
    }

    @Test
    fun `second spike past the lockout window registers as a new shot`() {
        // Spikes 20ms apart with a 10ms lockout - both are real shots.
        val chunk = silentChunk(sampleCount = 30, captureEndNanos = 30_000_000L, spikesAtIndices = setOf(5, 25))
        val events = detector().process(chunk)
        assertEquals(listOf(ShotEvent(5_000_000L), ShotEvent(25_000_000L)), events)
    }

    @Test
    fun `lockout persists across chunk boundaries`() {
        val d = detector()

        // Chunk 1 covers [0ms, 10ms); spike at index 9 -> 9ms.
        val chunk1 = silentChunk(sampleCount = 10, captureEndNanos = 10_000_000L, spikesAtIndices = setOf(9))
        assertEquals(listOf(ShotEvent(9_000_000L)), d.process(chunk1))

        // Chunk 2 covers [10ms, 20ms); spike at index 0 -> 10ms, only 1ms after the last event -
        // still inside the 10ms lockout, so it must be suppressed even though it's a new chunk.
        val chunk2 = silentChunk(sampleCount = 10, captureEndNanos = 20_000_000L, spikesAtIndices = setOf(0))
        assertEquals(emptyList<ShotEvent>(), d.process(chunk2))

        // A later spike in the same chunk, well past the lockout from the chunk1 shot, registers.
        val chunk2WithLateSpike = silentChunk(sampleCount = 10, captureEndNanos = 20_000_000L, spikesAtIndices = setOf(9))
        assertEquals(listOf(ShotEvent(19_000_000L)), d.process(chunk2WithLateSpike))
    }

    @Test
    fun `reset clears lockout state for a new run`() {
        val d = detector()
        val chunk = silentChunk(sampleCount = 10, captureEndNanos = 10_000_000L, spikesAtIndices = setOf(5))

        assertEquals(listOf(ShotEvent(5_000_000L)), d.process(chunk))
        // Without reset, replaying the exact same chunk would be suppressed (0ns since the last event).
        assertTrue(d.process(chunk).isEmpty())

        d.reset()
        assertEquals(listOf(ShotEvent(5_000_000L)), d.process(chunk))
    }
}
