package com.shottimer.app.sync

import com.shottimer.app.data.RunEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunMapperTest {

    private val sampleRun = RunEntity(
        id = 42L,
        timestampEpochMillis = 1_700_000_000_000L,
        totalElapsedMillis = 2500L,
        shotTimestampsMillis = listOf(870L, 1200L, 2500L),
        parTimeSeconds = 3.5f,
        drillName = "Bill Drill",
        shooterName = "Jess",
        remoteId = null
    )

    @Test
    fun `round trips a run through toFirestoreMap and back`() {
        val map = sampleRun.toFirestoreMap()
        val restored = map.toRunEntity(remoteId = "abc123")

        assertEquals(
            sampleRun.copy(id = 0, remoteId = "abc123"),
            restored
        )
    }

    @Test
    fun `toFirestoreMap omits id and remoteId`() {
        val map = sampleRun.toFirestoreMap()

        assertEquals(false, map.containsKey("id"))
        assertEquals(false, map.containsKey("remoteId"))
    }

    @Test
    fun `toFirestoreMap casts parTimeSeconds to Double`() {
        val map = sampleRun.toFirestoreMap()

        assertEquals(3.5, map["parTimeSeconds"])
    }

    @Test
    fun `null parTimeSeconds survives the round trip as null`() {
        val run = sampleRun.copy(parTimeSeconds = null)
        val map = run.toFirestoreMap()
        val restored = map.toRunEntity(remoteId = "abc123")

        assertNull(map["parTimeSeconds"])
        assertNull(restored?.parTimeSeconds)
    }

    @Test
    fun `doc missing a required field returns null instead of throwing`() {
        val map = sampleRun.toFirestoreMap() - "totalElapsedMillis"

        assertNull(map.toRunEntity(remoteId = "abc123"))
    }

    @Test
    fun `doc with wrong-typed required field returns null instead of throwing`() {
        val map = sampleRun.toFirestoreMap() + ("timestampEpochMillis" to "not-a-number")

        assertNull(map.toRunEntity(remoteId = "abc123"))
    }

    @Test
    fun `numeric fields deserialized as Int or Double are handled defensively`() {
        val map = mapOf(
            "timestampEpochMillis" to 1_700_000_000_000L,
            "totalElapsedMillis" to 2500, // Int instead of Long
            "shotTimestampsMillis" to listOf(870.0, 1200.0, 2500.0), // Doubles instead of Longs
            "parTimeSeconds" to 3, // Int instead of Double
            "drillName" to "Bill Drill",
            "shooterName" to "Jess"
        )

        val restored = map.toRunEntity(remoteId = "abc123")

        assertEquals(2500L, restored?.totalElapsedMillis)
        assertEquals(listOf(870L, 1200L, 2500L), restored?.shotTimestampsMillis)
        assertEquals(3.0f, restored?.parTimeSeconds)
    }

    @Test
    fun `sets id to 0 and remoteId to the passed-in doc id`() {
        val map = sampleRun.toFirestoreMap()
        val restored = map.toRunEntity(remoteId = "doc-42")

        assertEquals(0L, restored?.id)
        assertEquals("doc-42", restored?.remoteId)
    }
}
