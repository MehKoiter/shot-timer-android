package com.shottimer.app.timer

import android.app.Application
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.shottimer.app.MainDispatcherRule
import com.shottimer.app.audio.AUDIO_SAMPLE_RATE_HZ
import com.shottimer.app.audio.AudioChunk
import com.shottimer.app.data.RunEntity
import com.shottimer.app.data.RunRepository
import com.shottimer.app.data.ShotTimerDatabase
import com.shottimer.app.drills.Drill
import com.shottimer.app.pollUntil
import com.shottimer.app.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * The most concurrency-sensitive class in the app (see the shot-loss race fixed earlier this
 * project) and, per the original review, the least-tested. Uses the injectable [AudioChunk]
 * source seam (see ShotTimerViewModel's constructor) to feed synthetic audio without a real mic,
 * and StandardTestDispatcher + explicit runCurrent() calls to drive the ViewModel's coroutines
 * deterministically step by step rather than racing real background threads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ShotTimerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var application: Application
    private lateinit var runRepository: RunRepository

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        // Robolectric does not reset app-defined singletons between test methods, and
        // ShotTimerDatabase is a real named (not in-memory) file - without this, runs saved by
        // earlier tests in the same JVM run would still be sitting in it.
        ShotTimerDatabase.resetForTesting(application)
        runRepository = RunRepository(application)
        // Zero delay makes start() -> RUNNING deterministic in one runCurrent() pass instead of
        // waiting out a random 1-3.5s window on virtual time.
        SettingsRepository.getInstance(application).update {
            it.copy(minDelaySeconds = 0f, maxDelaySeconds = 0f)
        }
    }

    /** A chunk loud enough to clear the detector's default threshold (sensitivity 0.5 -> 0.6). */
    private fun spikeChunk(captureEndNanos: Long): AudioChunk {
        val samples = ShortArray(AUDIO_SAMPLE_RATE_HZ / 10) { i ->
            if (i == 0) Short.MAX_VALUE else 0
        }
        return AudioChunk(samples, captureEndNanos)
    }

    private fun awaitRunCount(expected: Int) = pollUntil(
        predicate = { it.size == expected },
        probe = { kotlinx.coroutines.runBlocking { runRepository.observeRuns().first() } }
    )

    @Test
    fun `selectShooter trims whitespace and collapses blank input to null`() {
        val viewModel = ShotTimerViewModel(application, audioChunks = { MutableSharedFlow() }, playTone = { _, _ -> })

        viewModel.selectShooter("  Kyle  ")
        assertEquals("Kyle", viewModel.uiState.value.selectedShooter)

        viewModel.selectShooter("   ")
        assertNull(viewModel.uiState.value.selectedShooter)
    }

    @Test
    fun `setSensitivity and setParTimeSeconds clamp to their documented ranges`() {
        val viewModel = ShotTimerViewModel(application, audioChunks = { MutableSharedFlow() }, playTone = { _, _ -> })

        viewModel.setSensitivity(5f)
        assertEquals(1f, viewModel.uiState.value.sensitivity)
        viewModel.setSensitivity(-5f)
        assertEquals(0f, viewModel.uiState.value.sensitivity)

        viewModel.setParTimeSeconds(999f)
        assertEquals(MAX_PAR_TIME_SECONDS, viewModel.uiState.value.parTimeSeconds)
        viewModel.setParTimeSeconds(-999f)
        assertEquals(MIN_PAR_TIME_SECONDS, viewModel.uiState.value.parTimeSeconds)
    }

    @Test
    fun `stop during the armed wait resets to idle without saving a run`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = ShotTimerViewModel(application, audioChunks = { MutableSharedFlow() }, playTone = { _, _ -> })
            val drill = Drill(id = "d", name = "Test Drill", summary = "", instructions = "", roundCount = 3)
            viewModel.selectDrill(drill)
            viewModel.selectShooter("Kyle")

            // A non-zero delay here (unlike the other tests) so the run is still ARMED_WAITING,
            // not RUNNING, when stop() is called.
            SettingsRepository.getInstance(application).update {
                it.copy(minDelaySeconds = 10f, maxDelaySeconds = 10f)
            }
            viewModel.start()
            testScheduler.runCurrent()
            assertEquals(RunState.ARMED_WAITING, viewModel.uiState.value.runState)

            viewModel.stop()
            testScheduler.runCurrent()

            assertEquals(RunState.IDLE, viewModel.uiState.value.runState)
            // Drill/shooter selections survive the reset - only the run itself is discarded.
            assertEquals(drill, viewModel.uiState.value.selectedDrill)
            assertEquals("Kyle", viewModel.uiState.value.selectedShooter)
            // Cancelled before the beep ever fired - stop() never calls saveRun on this path at
            // all, so there's no async write to race against here.
            assertEquals(emptyList<RunEntity>(), runRepository.observeRuns().first())
        }

    @Test
    fun `stop while running saves a run tagged with the selected drill and shooter`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = ShotTimerViewModel(application, audioChunks = { MutableSharedFlow() }, playTone = { _, _ -> })
            val drill = Drill(id = "d", name = "Bill Drill", summary = "", instructions = "", roundCount = 6)
            viewModel.selectDrill(drill)
            viewModel.selectShooter("Kyle")

            viewModel.start()
            // A zero-second random delay still schedules a delay(0) task at the current virtual
            // instant - runCurrent() alone only drains what's *already* due when called, not a
            // task newly (re)scheduled at that same instant, so nudge the clock forward first.
            testScheduler.advanceTimeBy(1)
            testScheduler.runCurrent()
            assertEquals(RunState.RUNNING, viewModel.uiState.value.runState)

            viewModel.stop()
            testScheduler.advanceUntilIdle()

            val saved = awaitRunCount(1).single()
            assertEquals("Bill Drill", saved.drillName)
            assertEquals("Kyle", saved.shooterName)
            assertNull(saved.parTimeSeconds)
        }

    @Test
    fun `a shot detected before the start beep is dropped, not counted`() =
        runTest(mainDispatcherRule.dispatcher) {
            val chunks = MutableSharedFlow<AudioChunk>(extraBufferCapacity = 4)
            val viewModel = ShotTimerViewModel(application, audioChunks = { chunks }, playTone = { _, _ -> })

            viewModel.start()
            testScheduler.advanceTimeBy(1)
            testScheduler.runCurrent()
            assertEquals(RunState.RUNNING, viewModel.uiState.value.runState)

            // suppressUntilNanos is startMark + 200ms; "now" (captured right after start) is
            // always well under that, so this chunk must be gated out regardless of its samples.
            chunks.tryEmit(spikeChunk(SystemClock.elapsedRealtimeNanos()))
            testScheduler.runCurrent()

            assertTrue(viewModel.uiState.value.shotSplitsMillis.isEmpty())

            viewModel.stop()
            testScheduler.advanceUntilIdle()
            // Settle the fire-and-forget save before the test returns, so the next test's
            // ShotTimerDatabase.resetForTesting() in @Before doesn't close the connection out
            // from under a write still in flight on Room's real executor thread.
            awaitRunCount(1)
        }

    @Test
    fun `a shot detected after the beep window registers`() = runTest(mainDispatcherRule.dispatcher) {
        val chunks = MutableSharedFlow<AudioChunk>(extraBufferCapacity = 4)
        val viewModel = ShotTimerViewModel(application, audioChunks = { chunks }, playTone = { _, _ -> })

        viewModel.start()
        testScheduler.advanceTimeBy(1)
        testScheduler.runCurrent()

        // 300ms comfortably clears the 200ms post-beep suppression window.
        chunks.tryEmit(spikeChunk(SystemClock.elapsedRealtimeNanos() + 300_000_000L))
        testScheduler.runCurrent()

        assertEquals(1, viewModel.uiState.value.shotSplitsMillis.size)

        viewModel.stop()
        testScheduler.advanceUntilIdle()
        awaitRunCount(1)
    }

    @Test
    fun `multiple shots in quick succession are all captured, none lost`() =
        runTest(mainDispatcherRule.dispatcher) {
            val chunks = MutableSharedFlow<AudioChunk>(extraBufferCapacity = 8)
            val viewModel = ShotTimerViewModel(application, audioChunks = { chunks }, playTone = { _, _ -> })

            viewModel.start()
            testScheduler.advanceTimeBy(1)
            testScheduler.runCurrent()

            val base = SystemClock.elapsedRealtimeNanos() + 300_000_000L
            // Spaced 50ms apart, past the 100ms default echo lockout, so each is a distinct shot.
            repeat(5) { i ->
                chunks.tryEmit(spikeChunk(base + i * 150_000_000L))
                testScheduler.runCurrent()
            }

            // This is the direct beneficiary of converting every _uiState.value = ...copy(...)
            // to atomic update{} earlier - a lost update here would have shrunk this count.
            assertEquals(5, viewModel.uiState.value.shotSplitsMillis.size)

            viewModel.stop()
            testScheduler.advanceUntilIdle()
            awaitRunCount(1)
        }

    @Test
    fun `par time auto-stops and saves the run with the par time recorded`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = ShotTimerViewModel(application, audioChunks = { MutableSharedFlow() }, playTone = { _, _ -> })
            viewModel.setParTimeEnabled(true)
            viewModel.setParTimeSeconds(MIN_PAR_TIME_SECONDS)

            viewModel.start()
            testScheduler.advanceTimeBy(1)
            testScheduler.advanceUntilIdle()

            // Auto-stop lands in STOPPED, same as a manual stop() while running - the ViewModel
            // never transitions itself back to IDLE (TimerScreen shows STOPPED as "Stopped").
            assertEquals(RunState.STOPPED, viewModel.uiState.value.runState)
            val saved = awaitRunCount(1).single()
            assertEquals(MIN_PAR_TIME_SECONDS, saved.parTimeSeconds)
        }
}
