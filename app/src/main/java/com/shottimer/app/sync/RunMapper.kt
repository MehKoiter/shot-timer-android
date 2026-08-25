package com.shottimer.app.sync

import com.shottimer.app.data.RunEntity

/**
 * Pure mapping between [RunEntity] and the plain `Map<String, Any?>` shape stored in Firestore
 * (`users/{uid}/runs/{remoteId}`). Deliberately kept as free functions over a plain `Map` rather
 * than operating on `DocumentSnapshot` directly - that seam lets [toRunEntity] be exercised with
 * plain JUnit + a hand-built `Map` in [RunMapperTest], with no real/mocked Firestore SDK object
 * needed. [toRunEntity] mirrors the rest of this codebase's convention for parsing
 * loosely-typed external input (e.g. malformed data shouldn't crash a sync loop): return `null`
 * for anything missing or wrong-typed instead of throwing.
 */

private const val FIELD_TIMESTAMP = "timestampEpochMillis"
private const val FIELD_TOTAL_ELAPSED = "totalElapsedMillis"
private const val FIELD_SHOT_TIMESTAMPS = "shotTimestampsMillis"
private const val FIELD_PAR_TIME = "parTimeSeconds"
private const val FIELD_DRILL_NAME = "drillName"
private const val FIELD_SHOOTER_NAME = "shooterName"

/**
 * The fields to sync. Deliberately excludes [RunEntity.id] (a local-only Room auto-generated
 * PK, meaningless on another device) and [RunEntity.remoteId] (derived from the Firestore
 * document's own id, not stored as a field within the doc).
 */
fun RunEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    FIELD_TIMESTAMP to timestampEpochMillis,
    FIELD_TOTAL_ELAPSED to totalElapsedMillis,
    FIELD_SHOT_TIMESTAMPS to shotTimestampsMillis,
    // Firestore has no Float type - store as Double, cast back to Float on the way in.
    FIELD_PAR_TIME to parTimeSeconds?.toDouble(),
    FIELD_DRILL_NAME to drillName,
    FIELD_SHOOTER_NAME to shooterName
)

/**
 * Builds a [RunEntity] from a Firestore document's raw field map plus the doc's own id (passed
 * separately - see the class doc above). [remoteId] is threaded through as-is so the resulting
 * entity is recognized as already-synced. [id] is set to 0 so Room assigns a fresh local PK on
 * insert.
 *
 * Numeric fields may deserialize from Firestore as [Long], [Int], or [Double] depending on how
 * they were originally written, so every numeric read goes through [Number] rather than assuming
 * an exact type. Returns `null` - rather than throwing - if a required field is missing or is
 * not the expected type, so one malformed remote doc can't crash an entire pull.
 */
fun Map<String, Any?>.toRunEntity(remoteId: String): RunEntity? {
    val timestampEpochMillis = (this[FIELD_TIMESTAMP] as? Number)?.toLong() ?: return null
    val totalElapsedMillis = (this[FIELD_TOTAL_ELAPSED] as? Number)?.toLong() ?: return null
    val shotTimestampsMillis = (this[FIELD_SHOT_TIMESTAMPS] as? List<*>)
        ?.map { (it as? Number)?.toLong() ?: return null }
        ?: return null
    val parTimeSeconds = (this[FIELD_PAR_TIME] as? Number)?.toFloat()
    val drillName = this[FIELD_DRILL_NAME] as? String
    val shooterName = this[FIELD_SHOOTER_NAME] as? String

    return RunEntity(
        id = 0,
        timestampEpochMillis = timestampEpochMillis,
        totalElapsedMillis = totalElapsedMillis,
        shotTimestampsMillis = shotTimestampsMillis,
        parTimeSeconds = parTimeSeconds,
        drillName = drillName,
        shooterName = shooterName,
        remoteId = remoteId
    )
}
