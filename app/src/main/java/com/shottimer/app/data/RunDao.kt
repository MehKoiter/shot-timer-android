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
}
