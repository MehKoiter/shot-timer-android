package com.shottimer.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Insert
    suspend fun insert(run: RunEntity): Long

    @Query("SELECT * FROM runs ORDER BY timestampEpochMillis DESC")
    fun observeAll(): Flow<List<RunEntity>>

    /** Dedupes Pi Sync replays - see RunEntity.piRunId's doc for why delivery isn't exactly-once. */
    @Query("SELECT EXISTS(SELECT 1 FROM runs WHERE piRunId = :piRunId)")
    suspend fun existsByPiRunId(piRunId: Long): Boolean
}
