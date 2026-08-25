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
    val drillName: String? = null,
    /** Null when no shooter was tagged for this run - lets one phone time multiple people
     * (e.g. a group of friends at the range) and later sort/filter History by who was shooting. */
    val shooterName: String? = null,
    /** Null until this run has been uploaded to Firestore (`users/{uid}/runs/{remoteId}`);
     * non-null forever after - a run is uploaded at most once, never re-uploaded or edited in
     * place. See SyncRepository. */
    val remoteId: String? = null
)
