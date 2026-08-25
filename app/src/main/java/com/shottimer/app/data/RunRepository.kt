package com.shottimer.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class RunRepository(context: Context) {
    private val dao = ShotTimerDatabase.getInstance(context).runDao()

    suspend fun saveRun(run: RunEntity): Long = dao.insert(run)

    suspend fun deleteRun(run: RunEntity) = dao.delete(run)

    fun observeRuns(): Flow<List<RunEntity>> = dao.observeAll()

    fun observeDistinctShooterNames(): Flow<List<String>> = dao.observeDistinctShooterNames()

    fun observeShooterStats(): Flow<List<ShooterStats>> = dao.observeShooterStats()

    suspend fun getUnsyncedRuns(): List<RunEntity> = dao.getUnsyncedRuns()

    suspend fun getSyncedRemoteIds(): List<String> = dao.getSyncedRemoteIds()

    suspend fun markSynced(id: Long, remoteId: String) = dao.markSynced(id, remoteId)
}
