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
    val piecesBehindLive: Long? = null,
    /** 4d · Bytes de vídeo recibidos (parche 0009; null con un motor sin el parche). */
    val downloaded: Long? = null,
    /** 4d · Velocidad de bajada en bytes/s (parche 0009). */
    val downloadRate: Long? = null,
    /** 4d · Pieza por la que va el motor y última pieza que anuncian los emisores. */
    val pos: Long? = null,
    val liveLast: Long? = null,
    /** 4d · Piezas que no pasaron la comprobación de firma. */
    val signatureRejects: Long = 0
) {
    val recoveries: Long get() = upstreamReconnects + staleTeardowns

    companion object {
        fun parse(body: String?): EngineStatus? = runCatching {
            val o = JsonParser.parseString(body.orEmpty()).asJsonObject
            val r = o.getAsJsonObject("recovery") ?: JsonObject()
            val meta = o.get("metadata")?.takeIf { it.isJsonObject }?.asJsonObject
            val lp = o.get("livepos")?.takeIf { it.isJsonObject }?.asJsonObject
            EngineStatus(
                peers = o.num("peers").toInt(),
                bitrateBps = o.num("bitrate"),
                engineBufferMs = o.num("buffer_ms"),
                upstreamReconnects = r.num("upstream_reconnects"),
                staleTeardowns = r.num("stale_teardowns"),
                skippedGaps = r.num("skipped_evicted_gaps"),
                activeUpstreams = r.num("active_upstreams").toInt(),
                title = meta?.get("title")?.takeIf { it.isJsonPrimitive }?.asString,
                piecesBehindLive = lp?.let {
                    val pos = it.optLong("pos")
                    val last = it.optLong("live_last")
                    if (pos != null && last != null) (last - pos).coerceAtLeast(0) else null
                },
                downloaded = o.optLong("downloaded"),
                downloadRate = o.optLong("download_rate"),
                pos = lp?.optLong("pos"),
                liveLast = lp?.optLong("live_last"),
                signatureRejects = r.num("signature_rejects")
            )
        }.getOrNull()

        private fun JsonObject.optLong(k: String): Long? =
            get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }

        private fun JsonObject.num(k: String): Long = get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
    }
}