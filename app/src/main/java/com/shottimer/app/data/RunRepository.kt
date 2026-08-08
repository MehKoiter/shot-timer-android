package com.shottimer.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class RunRepository(context: Context) {
    private val dao = ShotTimerDatabase.getInstance(context).runDao()

    suspend fun saveRun(run: RunEntity): Long = dao.insert(run)

    fun observeRuns(): Flow<List<RunEntity>> = dao.observeAll()

    /** Saves a run synced from the Pi's offline backlog, skipping it if [RunEntity.piRunId] was
     * already saved - see RunDao.existsByPiRunId's doc. Returns true if it was actually saved. */
    suspend fun saveSyncedRunIfNew(run: RunEntity): Boolean {
        val piRunId = run.piRunId ?: error("saveSyncedRunIfNew requires a non-null piRunId")
        if (dao.existsByPiRunId(piRunId)) return false
        dao.insert(run)
        return true
    }
}
