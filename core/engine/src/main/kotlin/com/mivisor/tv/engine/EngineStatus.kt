package com.mivisor.tv.engine

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Estado de una sesión del motor (`GET /streams/ace/<id>/status`). */
data class EngineStatus(
    val peers: Int,
    val bitrateBps: Long,
    val engineBufferMs: Long,
    val upstreamReconnects: Long,
    val staleTeardowns: Long,
    val skippedGaps: Long,
    val activeUpstreams: Int,
    val title: String?,
    /** Piezas entre el cursor del motor y el borde en directo del enjambre (livepos.live_last - pos), si se sabe. */
    val piecesBehindLive: Long? = null
) {
    val recoveries: Long get() = upstreamReconnects + staleTeardowns

    companion object {
        fun parse(body: String?): EngineStatus? = runCatching {
            val o = JsonParser.parseString(body.orEmpty()).asJsonObject
            val r = o.getAsJsonObject("recovery") ?: JsonObject()
            val meta = o.get("metadata")?.takeIf { it.isJsonObject }?.asJsonObject
            EngineStatus(
                peers = o.num("peers").toInt(),
                bitrateBps = o.num("bitrate"),
                engineBufferMs = o.num("buffer_ms"),
                upstreamReconnects = r.num("upstream_reconnects"),
                staleTeardowns = r.num("stale_teardowns"),
                skippedGaps = r.num("skipped_evicted_gaps"),
                activeUpstreams = r.num("active_upstreams").toInt(),
                title = meta?.get("title")?.takeIf { it.isJsonPrimitive }?.asString,
                piecesBehindLive = o.get("livepos")?.takeIf { it.isJsonObject }?.asJsonObject?.let { lp ->
                    val pos = lp.get("pos")?.takeIf { it.isJsonPrimitive }?.asLong
                    val last = lp.get("live_last")?.takeIf { it.isJsonPrimitive }?.asLong
                    if (pos != null && last != null) (last - pos).coerceAtLeast(0) else null
                }
            )
        }.getOrNull()

        private fun JsonObject.num(k: String): Long = get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
    }
}