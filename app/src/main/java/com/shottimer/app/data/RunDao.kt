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
}
