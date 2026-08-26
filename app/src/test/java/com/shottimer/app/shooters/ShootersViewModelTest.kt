package com.shottimer.app.shooters

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.shottimer.app.MainDispatcherRule
import com.shottimer.app.data.RunEntity
import com.shottimer.app.data.RunRepository
import com.shottimer.app.data.ShotTimerDatabase
import com.shottimer.app.pollUntil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ShootersViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var application: Application
    private lateinit var repository: RunRepository
    private lateinit var viewModel: ShootersViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        ShotTimerDatabase.resetForTesting(application)
        repository = RunRepository(application)
        viewModel = ShootersViewModel(application)
    }

    private fun run(shooterName: String?, totalElapsedMillis: Long = 4_000L) = RunEntity(
        timestampEpochMillis = 1_000L,
        totalElapsedMillis = totalElapsedMillis,
        shotTimestampsMillis = emptyList(),
        parTimeSeconds = null,
        drillName = null,
        shooterName = shooterName
    )

    @Test
    fun `renameShooter merges onto an existing name instead of leaving two shooters`() =
        runTest(mainDispatcherRule.dispatcher) {
            repository.saveRun(run("Kyel"))
            repository.saveRun(run("Kyle"))

            viewModel.renameShooter("Kyel", "Kyle")
            testScheduler.advanceUntilIdle()

            val stats = pollUntil(
                predicate = { it.size == 1 && it.single().runCount == 2 },
                probe = { runBlocking { repository.observeShooterStats().first() } }
            )
            assertEquals(1, stats.size)
            assertEquals(2, stats.single().runCount)
        }

    @Test
    fun `renameShooter to a blank name is a no-op`() = runTest(mainDispatcherRule.dispatcher) {
        repository.saveRun(run("Kyle"))

        viewModel.renameShooter("Kyle", "   ")
        testScheduler.advanceUntilIdle()

        assertEquals("Kyle", repository.observeShooterStats().first().single().shooterName)
    }

    @Test
    fun `removeShooter untags their runs without deleting them`() = runTest(mainDispatcherRule.dispatcher) {
        repository.saveRun(run("Kyle"))

        viewModel.removeShooter("Kyle")
        testScheduler.advanceUntilIdle()

        val runs = pollUntil(
            predicate = { it.size == 1 && it.single().shooterName == null },
            probe = { runBlocking { repository.observeRuns().first() } }
        )
        assertEquals(1, runs.size)
        assertNull(runs.single().shooterName)
    }
}
