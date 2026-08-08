package com.shottimer.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMillis: Long,
    val totalElapsedMillis: Long,
    val shotTimestampsMillis: List<Long>,
    /** Null when the run wasn't a par-time drill. */
    val parTimeSeconds: Float?,
    /** Null for phone-detected runs. Set for runs that originated on the Pi companion (both
     * live-armed runs and backlog entries delivered via its Sync characteristic) - the Pi's
     * local SQLite row id, so a re-delivered Sync payload (delivery there is at-least-once,
     * not exactly-once - see pi-companion/shot_timer_pi/storage.py) can be recognized and
     * skipped instead of creating a duplicate row. */
    val piRunId: Long? = null
)
