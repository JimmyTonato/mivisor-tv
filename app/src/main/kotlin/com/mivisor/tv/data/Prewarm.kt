package com.mivisor.tv.data

import android.content.Context
import com.mivisor.tv.catalog.tv.TvChannel
import com.mivisor.tv.engine.EngineApi
import com.mivisor.tv.engine.WarmPool
import com.mivisor.tv.outpace.EngineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * 3i · Precarga del canal enfocado: si el foco se queda [DWELL_MS] sobre un canal o un partido, se pide al motor su
 * MEJOR fuente (la que el reproductor probaría primero). Al pulsar OK, el motor ya está buscando emisores y la imagen
 * sale antes. Solo con la app abierta (nada en segundo plano); 1 fuente caliente en aparatos con poca memoria, 2 si no;
 * se sueltan a los 60 s si no se usan, y nunca se suelta la que se está viendo.
 */
class Prewarm(
    private val app: Context,
    private val scope: CoroutineScope,
    /** Content ID que el reproductor probaría primero para este canal. */
    private val bestSource: (TvChannel) -> String?,
    /** Content ID que se está viendo ahora (no se toca). */
    private val playing: () -> String?,
    private val enabled: () -> Boolean,
    private val log: (String) -> Unit,
    /** 3k · La fuente de la vista previa de Canales (tampoco se suelta mientras se ve). */
    private val previewing: () -> String? = { null }
) {
    private val lowRam = EngineManager.isLowRam(app)
    private val pool = WarmPool(capacity = if (lowRam) 1 else 2, ttlMs = TTL_MS)
    private var pending: Job? = null
    private var expiry: Job? = null

    /** El foco llega a un canal (o a un partido con canal). null = el foco se fue a otra cosa. */
    fun onFocus(channel: TvChannel?) {
        pending?.cancel()
        if (channel == null || !enabled()) return
        pending = scope.launch {
            delay(DWELL_MS)
            warm(channel)
        }
    }

    /** 3k · La vista previa suelta [contentId]: se queda preparada un rato por si lo abres (y luego se para sola). */
    fun keep(contentId: String) {
        val base = EngineManager.state.value.baseUrl ?: return
        pool.add(contentId, now()).filter { busy(it).not() }.forEach { stop(base, it) }
        scheduleExpiry()
    }

    private fun busy(cid: String) = cid == playing() || cid == previewing()

    /** El reproductor abre [contentId]: ya es suyo. */
    fun taken(contentId: String) {
        pool.take(contentId)
    }

    /** La app pasa a segundo plano: se suelta todo. */
    fun releaseAll() {
        pending?.cancel()
        val base = EngineManager.state.value.baseUrl ?: return
        val now = playing()
        pool.drain().filter { it != now && it != previewing() }.forEach { stop(base, it) }
    }

    private suspend fun warm(channel: TvChannel) {
        val cid = bestSource(channel) ?: return
        if (cid == playing() || pool.contains(cid)) {
            if (pool.contains(cid)) pool.add(cid, now())
            return
        }
        val engine = EngineManager.ensureStarted(app)
        val base = engine.baseUrl ?: return
        val out = pool.add(cid, now())
        log("Precarga: ${channel.name}")
        scope.launch(Dispatchers.IO) { touch(EngineApi.hlsUrl(base, cid)) }
        out.filter { !busy(it) }.forEach { stop(base, it) }
        scheduleExpiry()
    }

    private fun scheduleExpiry() {
        if (expiry?.isActive == true) return
        expiry = scope.launch {
            while (pool.size > 0) {
                delay(10_000)
                val base = EngineManager.state.value.baseUrl ?: continue
                pool.expired(now()).filter { !busy(it) }.forEach { stop(base, it) }
            }
        }
    }

    private fun stop(base: String, cid: String) {
        scope.launch(Dispatchers.IO) { runCatching { EngineApi.stop(base, cid) } }
    }

    private fun now() = android.os.SystemClock.elapsedRealtime()

    companion object {
        const val DWELL_MS = 1_500L
        const val TTL_MS = 60_000L

        /** Pide la lista HLS: el motor arranca la fuente y empieza a buscar emisores. No se reproduce nada. */
        fun touch(url: String) {
            var c: HttpURLConnection? = null
            try {
                c = URL(url).openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
                c.connectTimeout = 3_000
                c.readTimeout = 25_000
                c.responseCode
            } catch (_: Exception) {
            } finally {
                c?.disconnect()
            }
        }
    }
}
