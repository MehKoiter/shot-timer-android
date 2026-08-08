package com.shottimer.app.pi

import org.json.JSONException
import org.json.JSONObject

/**
 * Mirrors the JSON payloads the Pi sends over BLE - see pi-companion/shot_timer_pi/ble_service.py
 * and pi-companion/README.md's "BLE GATT protocol reference" table. [Sync] and [RunComplete]
 * share the same wire shape ({"type":"run_complete", ...}) - they're told apart by the presence
 * of "run_id", not by which characteristic (Event vs Sync) the notification arrived on, since a
 * re-delivered Sync payload should parse the same way regardless of transport.
 */
sealed interface PiEvent {
    data object Beep : PiEvent
    data class Shot(val elapsedMs: Long) : PiEvent
    data class RunComplete(val totalMs: Long, val shotsMs: List<Long>) : PiEvent
    data class Sync(val totalMs: Long, val shotsMs: List<Long>, val runId: Long) : PiEvent
}

/** Parses one BLE notification payload; returns null for anything malformed or unrecognized
 * rather than throwing, since a single bad packet shouldn't crash the collecting coroutine. */
fun parsePiEvent(bytes: ByteArray): PiEvent? {
    return try {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        when (json.optString("type")) {
            "beep" -> PiEvent.Beep
            "shot" -> PiEvent.Shot(elapsedMs = json.getLong("elapsed_ms"))
            "run_complete" -> {
                val totalMs = json.getLong("total_ms")
                val shotsMs = json.getJSONArray("shots").let { array ->
                    List(array.length()) { i -> array.getLong(i) }
                }
                if (json.has("run_id")) {
                    PiEvent.Sync(totalMs = totalMs, shotsMs = shotsMs, runId = json.getLong("run_id"))
                } else {
                    PiEvent.RunComplete(totalMs = totalMs, shotsMs = shotsMs)
                }
            }
            else -> null
        }
    } catch (e: JSONException) {
        null
    }
}
