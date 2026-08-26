package com.shottimer.app.history

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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

// Room's real background executor thread trips the default legacy SQLite shadow's per-thread
// connection tracking ("Illegal connection pointer") - NATIVE mode uses a real SQLite library
// instead of a Java reimplementation, so it has no such limitation.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var application: Application
    private lateinit var repository: RunRepository
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        // Robolectric does not reset app-defined singletons between tests, and ShotTimerDatabase
        // is a real named (not in-memory) file - without this, rows from every other ViewModel
        // test in the same JVM run would still be sitting in it.
        ShotTimerDatabase.resetForTesting(application)
        repository = RunRepository(application)
        viewModel = HistoryViewModel(application)
    }

    private fun run(shooterName: String? = null, remoteId: String? = null) = RunEntity(
        timestampEpochMillis = 1_000L,
        totalElapsedMillis = 4_270L,
        shotTimestampsMillis = listOf(2_000L),
        parTimeSeconds = null,
        drillName = null,
        shooterName = shooterName,
        remoteId = remoteId
    )

    @Test
    fun `deleteRun removes the row from the database`() = runTest(mainDispatcherRule.dispatcher) {
        val id = repository.saveRun(run())
        val saved = repository.observeRuns().first().single()

        viewModel.deleteRun(saved.copy(id = id))
        testScheduler.advanceUntilIdle()

        val remaining = pollUntil(
            predicate = { it.isEmpty() },
            probe = { runBlocking { repository.observeRuns().first() } }
        )
        assertEquals(emptyList<RunEntity>(), remaining)
    }

    @Test
    fun `restoreRun re-inserts the exact same run, remoteId included`() = runTest(mainDispatcherRule.dispatcher) {
        val id = repository.saveRun(run(shooterName = "Kyle", remoteId = "cloud-1"))
        val saved = repository.observeRuns().first().single().copy(id = id)

        viewModel.deleteRun(saved)
        testScheduler.advanceUntilIdle()
        viewModel.restoreRun(saved)
        testScheduler.advanceUntilIdle()

        val restored = pollUntil(
            predicate = { it.isNotEmpty() },
            probe = { runBlocking { repository.observeRuns().first() } }
        ).single()
        assertEquals("Kyle", restored.shooterName)
        assertEquals("cloud-1", restored.remoteId)
        // The freed id gets reused since restoreRun reinserts the entity with its original
        // (non-zero) primary key still set - this is what makes it a true "undo" rather than a
        // new row that happens to look the same.
        assertEquals(id, restored.id)
    }
}
