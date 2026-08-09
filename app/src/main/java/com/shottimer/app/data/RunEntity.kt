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
    /** Null for freeform practice runs not tied to a named drill from DrillLibrary. */
    val drillName: String? = null
)
