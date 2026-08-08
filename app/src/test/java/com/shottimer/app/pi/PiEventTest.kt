package com.shottimer.app.pi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun bytesOf(json: String) = json.toByteArray(Charsets.UTF_8)

class PiEventTest {

    @Test
    fun `parses a beep event`() {
        assertEquals(PiEvent.Beep, parsePiEvent(bytesOf("""{"type":"beep"}""")))
    }

    @Test
    fun `parses a shot event`() {
        assertEquals(
            PiEvent.Shot(elapsedMs = 870L),
            parsePiEvent(bytesOf("""{"type":"shot","elapsed_ms":870}"""))
        )
    }

    @Test
    fun `parses a run_complete event without run_id as RunComplete`() {
        assertEquals(
            PiEvent.RunComplete(totalMs = 2580L, shotsMs = listOf(870L, 1200L, 2500L)),
            parsePiEvent(bytesOf("""{"type":"run_complete","total_ms":2580,"shots":[870,1200,2500]}"""))
        )
    }

    @Test
    fun `parses a run_complete event with run_id as Sync`() {
        assertEquals(
            PiEvent.Sync(totalMs = 2580L, shotsMs = listOf(870L, 1200L, 2500L), runId = 5L),
            parsePiEvent(
                bytesOf("""{"type":"run_complete","total_ms":2580,"shots":[870,1200,2500],"run_id":5}""")
            )
        )
    }

    @Test
    fun `parses an empty shots array`() {
        assertEquals(
            PiEvent.RunComplete(totalMs = 0L, shotsMs = emptyList()),
            parsePiEvent(bytesOf("""{"type":"run_complete","total_ms":0,"shots":[]}"""))
        )
    }

    @Test
    fun `unrecognized type returns null`() {
        assertNull(parsePiEvent(bytesOf("""{"type":"something_else"}""")))
    }

    @Test
    fun `malformed json returns null instead of throwing`() {
        assertNull(parsePiEvent(bytesOf("not json at all")))
    }

    @Test
    fun `missing required field returns null instead of throwing`() {
        assertNull(parsePiEvent(bytesOf("""{"type":"shot"}""")))
    }

    @Test
    fun `empty bytes returns null instead of throwing`() {
        assertNull(parsePiEvent(ByteArray(0)))
    }
}
