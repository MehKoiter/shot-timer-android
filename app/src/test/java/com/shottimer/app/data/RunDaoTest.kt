package com.shottimer.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

/**
 * Exercises the DAO layer directly against an in-memory Room instance - no emulator required, so
 * this runs as part of `testDebugUnitTest`. Complements MigrationTest (androidTest), which checks
 * schema evolution rather than query behavior.
 */
@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RunDaoTest {

    private lateinit var db: ShotTimerDatabase
    private lateinit var dao: RunDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            ShotTimerDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.runDao()
    }

    @After
    fun tearDown() = db.close()

    private fun run(
        timestampEpochMillis: Long = 1_000L,
        totalElapsedMillis: Long = 5_000L,
        shots: List<Long> = listOf(1_000L, 2_500L),
        drillName: String? = null,
        shooterName: String? = null
    ) = RunEntity(
        timestampEpochMillis = timestampEpochMillis,
        totalElapsedMillis = totalElapsedMillis,
        shotTimestampsMillis = shots,
        parTimeSeconds = null,
        drillName = drillName,
        shooterName = shooterName
    )

    @Test
    fun `shot timestamps round-trip through the comma-join Converters`() = runBlocking {
        dao.insert(run(shots = listOf(120L, 480L, 1_010L)))

        val loaded = dao.observeAll().first().single()

        assertEquals(listOf(120L, 480L, 1_010L), loaded.shotTimestampsMillis)
    }

    @Test
    fun `an empty shot list round-trips as an empty list, not a list with one blank entry`() = runBlocking {
        dao.insert(run(shots = emptyList()))

        assertEquals(emptyList<Long>(), dao.observeAll().first().single().shotTimestampsMillis)
    }

    @Test
    fun `observeAll orders newest run first`() = runBlocking {
        dao.insert(run(timestampEpochMillis = 1_000L))
        dao.insert(run(timestampEpochMillis = 3_000L))
        dao.insert(run(timestampEpochMillis = 2_000L))

        val timestamps = dao.observeAll().first().map { it.timestampEpochMillis }

        assertEquals(listOf(3_000L, 2_000L, 1_000L), timestamps)
    }

    @Test
    fun `observeShooterStats aggregates runCount, best, and average per shooter`() = runBlocking {
        dao.insert(run(totalElapsedMillis = 4_000L, shooterName = "Kyle"))
        dao.insert(run(totalElapsedMillis = 6_000L, shooterName = "Kyle"))
        dao.insert(run(totalElapsedMillis = 9_999L, shooterName = null)) // unassigned - excluded

        val stats = dao.observeShooterStats().first().single()

        assertEquals("Kyle", stats.shooterName)
        assertEquals(2, stats.runCount)
        assertEquals(4_000L, stats.bestTimeMillis)
        assertEquals(5_000.0, stats.avgTimeMillis, 0.001)
    }

    @Test
    fun `observeDistinctShooterNames excludes nulls and dedupes`() = runBlocking {
        dao.insert(run(shooterName = "Kyle"))
        dao.insert(run(shooterName = "Kyle"))
        dao.insert(run(shooterName = "George"))
        dao.insert(run(shooterName = null))

        assertEquals(listOf("George", "Kyle"), dao.observeDistinctShooterNames().first())
    }

    @Test
    fun `renameShooter onto a fresh name updates every one of that shooter's runs`() = runBlocking {
        dao.insert(run(shooterName = "Kyel"))
        dao.insert(run(shooterName = "Kyel"))
        dao.insert(run(shooterName = "George"))

        dao.renameShooter("Kyel", "Kyle")

        val names = dao.observeAll().first().map { it.shooterName }
        assertEquals(listOf("George", "Kyle", "Kyle"), names.sortedWith(compareBy { it }))
    }

    @Test
    fun `renameShooter onto an existing name merges both into one shooter`() = runBlocking {
        dao.insert(run(shooterName = "Kyel"))
        dao.insert(run(shooterName = "Kyle"))

        dao.renameShooter("Kyel", "Kyle")

        val stats = dao.observeShooterStats().first()
        assertEquals(1, stats.size)
        assertEquals(2, stats.single().runCount)
    }

    @Test
    fun `untagShooter nulls the shooter but keeps the run row`() = runBlocking {
        dao.insert(run(shooterName = "Kyle"))

        dao.untagShooter("Kyle")

        val remaining = dao.observeAll().first()
        assertEquals(1, remaining.size)
        assertNull(remaining.single().shooterName)
    }

    @Test
    fun `getUnsyncedRuns returns only runs without a remoteId, oldest first`() = runBlocking {
        val syncedId = dao.insert(run(timestampEpochMillis = 1_000L))
        dao.markSynced(syncedId, "remote-1")
        dao.insert(run(timestampEpochMillis = 3_000L))
        dao.insert(run(timestampEpochMillis = 2_000L))

        val unsynced = dao.getUnsyncedRuns()

        assertEquals(listOf(2_000L, 3_000L), unsynced.map { it.timestampEpochMillis })
    }

    @Test
    fun `markSynced sets remoteId without disturbing other fields`() = runBlocking {
        val id = dao.insert(run(shooterName = "Kyle"))

        dao.markSynced(id, "remote-abc")

        val loaded = dao.observeAll().first().single()
        assertEquals("remote-abc", loaded.remoteId)
        assertEquals("Kyle", loaded.shooterName)
        assertTrue(dao.getSyncedRemoteIds().contains("remote-abc"))
    }

    @Test
    fun `delete removes exactly the given run`() = runBlocking {
        val keep = run(timestampEpochMillis = 1_000L)
        val toDelete = run(timestampEpochMillis = 2_000L)
        val keepId = dao.insert(keep)
        val deleteId = dao.insert(toDelete)

        dao.delete(toDelete.copy(id = deleteId))

        val remaining = dao.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals(keepId, remaining.single().id)
    }
}
