package com.shottimer.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomDrillDao {
    @Insert
    suspend fun insert(drill: CustomDrillEntity): Long

    @Delete
    suspend fun delete(drill: CustomDrillEntity)

    @Query("SELECT * FROM custom_drills ORDER BY name")
    fun observeAll(): Flow<List<CustomDrillEntity>>
}
