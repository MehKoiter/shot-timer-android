package com.shottimer.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Insert
    suspend fun insert(run: RunEntity): Long

    @Delete
    suspend fun delete(run: RunEntity)

    @Query("SELECT * FROM runs ORDER BY timestampEpochMillis DESC")
    fun observeAll(): Flow<List<RunEntity>>

    /** Feeds the shooter picker's "previously used names" list - lets you re-select a friend you
     * already timed once instead of retyping their name every run. */
    @Query("SELECT DISTINCT shooterName FROM runs WHERE shooterName IS NOT NULL ORDER BY shooterName")
    fun observeDistinctShooterNames(): Flow<List<String>>

    /** One row per shooter with aggregate stats across their tagged runs - this IS "shooter
     * profiles": there's no separate profile table, a shooter's profile is just every run
     * tagged with their name, summarized. */
    @Query(
        "SELECT shooterName, COUNT(*) AS runCount, MIN(totalElapsedMillis) AS bestTimeMillis, " +
            "AVG(totalElapsedMillis) AS avgTimeMillis " +
            "FROM runs WHERE shooterName IS NOT NULL GROUP BY shooterName ORDER BY shooterName"
    )
    fun observeShooterStats(): Flow<List<ShooterStats>>

    /** Runs never uploaded to Firestore yet - see SyncRepository.syncNow(). Ascending so an
     * interrupted sync resumes roughly where it left off rather than re-racing the newest runs
     * first every time. */
    @Query("SELECT * FROM runs WHERE remoteId IS NULL ORDER BY timestampEpochMillis ASC")
    suspend fun getUnsyncedRuns(): List<RunEntity>

    /** The set of remote ids already present locally, so a pull can skip docs already synced -
     * either uploaded from this device or pulled down on a previous sync. */
    @Query("SELECT remoteId FROM runs WHERE remoteId IS NOT NULL")
    suspend fun getSyncedRemoteIds(): List<String>

    /** Deliberately narrow instead of a general @Update - a run is otherwise immutable once
     * saved, and this is the only field sync is ever allowed to change after the fact. */
    @Query("UPDATE runs SET remoteId = :remoteId WHERE id = :id")
    suspend fun markSynced(id: Long, remoteId: String)

    /** Rename a shooter across every run tagged with them. Renaming onto an existing name is the
     * merge operation - both shooters' runs end up under one name, which is exactly the fix for
     * a typo'd duplicate. Note: already-synced cloud copies keep the old name (sync is
     * upload-once and never updates remote docs). */
    @Query("UPDATE runs SET shooterName = :newName WHERE shooterName = :oldName")
    suspend fun renameShooter(oldName: String, newName: String)

    /** Remove a shooter by untagging their runs - the runs themselves are kept as practice runs. */
    @Query("UPDATE runs SET shooterName = NULL WHERE shooterName = :name")
    suspend fun untagShooter(name: String)
}
