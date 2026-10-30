package com.mivisor.tv.data

import android.content.Context
import android.os.SystemClock
import com.google.gson.Gson
import com.mivisor.tv.outpace.EngineManager
import com.mivisor.tv.playback.PlaySource
import com.mivisor.tv.playback.SourceBook
import com.mivisor.tv.reliability.FailureContext
import com.mivisor.tv.reliability.Health
import com.mivisor.tv.reliability.ReliabilityStore
import com.mivisor.tv.reliability.SourceState
import com.mivisor.tv.services.WarpPhase
import com.mivisor.tv.storage.MvDatabase
import com.mivisor.tv.warp.WarpController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.mivisor.tv.storage.Prefs

/**
 * Fiabilidad de las fuentes con la app abierta (fase 2): [ReliabilityStore] en memoria, guardado en SQLite tras cada
 * cambio. Aprende solo de las reproducciones reales; no hay verificador ni comprobaciones en segundo plano.
 * Los fallos generales (sin Internet, WARP, motor, poca memoria, varios canales a la vez) no se apuntan a la fuente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
/** Una fuente que descansa: hasta cuándo y por qué (último motivo apuntado). */
class RestingSource(val contentId: String, val state: SourceState, val untilMs: Long?, val reason: String?, val trips: Int)

/** Eventos que no son el motivo de un fallo (para «Fuentes que descansan»). */
private val NOT_REASONS = setOf("excused", "pause", "quarantine", "released", "ok", "freeze", "incompatible")

class SourceBookImpl(
    context: Context,
    private val db: MvDatabase,
    private val warp: WarpController,
    private val providers: ProvidersRepository,
    private val mylist: MyListRepository,
    private val log: LogBook,
    private val prefs: Prefs,
    private val scope: CoroutineScope
) : SourceBook {
    private val app = context.applicationContext
    private val gson = Gson()
    private val io = Dispatchers.IO.limitedParallelism(1)
    @Volatile private var store = ReliabilityStore()
    private val lock = Any()
    @Volatile var foreground = true

    /** Carga lo aprendido en sesiones anteriores (una vez, al arrancar). */
    fun load() {
        scope.launch(io) {
            val rows = runCatching { db.healthJson() }.getOrDefault(emptyMap())
            val map = rows.mapNotNull { (cid, json) -> runCatching { gson.fromJson(json, Health::class.java) }.getOrNull()?.let { cid to it } }.toMap()
            synchronized(lock) { store = ReliabilityStore(map) }
        }
    }

    private fun now(): Double = System.currentTimeMillis() / 1000.0

    private fun persist(vararg cids: String) {
        val snap = synchronized(lock) { cids.mapNotNull { c -> store.health(c)?.deepCopy()?.let { c to it } } }
        if (snap.isEmpty()) return
        scope.launch(io) { runCatching { db.saveHealth(snap.associate { it.first to gson.toJson(it.second) }) } }
    }

    override fun order(channelId: String, sources: List<PlaySource>, chosen: String?): List<String> {
        val remembered = runCatching { db.rememberedSource(channelId) }.getOrNull()
        val cids = sources.map { it.contentId }
        val quality = sources.associate { it.contentId to it.quality }
        return synchronized(lock) { store.order(cids, now(), chosen, remembered, quality) }
    }

    override fun state(contentId: String): SourceState = synchronized(lock) { store.state(contentId, now()) }

    override fun onSuccess(channelId: String, source: PlaySource, startMs: Long) {
        synchronized(lock) { store.success(source.contentId, now(), startMs.toDouble()) }
        persist(source.contentId)
        scope.launch(io) { runCatching { db.rememberSource(channelId, source.contentId) } }
    }

    override fun onFailure(channelId: String, source: PlaySource, kind: String): String {
        val ctx = context(source)
        val verdict = synchronized(lock) { store.failure(source.contentId, now(), kind, ctx, channelId) }
        persist(source.contentId)
        if (verdict == "global") persist(*synchronized(lock) { store.cids().toTypedArray() })
        return verdict
    }

    override fun onFreeze(source: PlaySource, step: Int) {
        synchronized(lock) { store.freeze(source.contentId, now(), step) }
        persist(source.contentId)
    }

    override fun onSessionEnd(channelId: String, channelName: String, source: PlaySource?, playedMs: Long, stalls: Int) {
        if (source != null && playedMs > 0) {
            synchronized(lock) { store.session(source.contentId, now(), playedMs / 1000.0, stalls) }
            persist(source.contentId)
        }
        if (playedMs >= 10_000) mylist.addHistory(channelId, channelName, playedMs / 1000)
    }

    override fun log(category: String, text: String) = log.add(category, text)

    /**
     * WARP por proveedor (3b): si el proveedor de la fuente la necesita, se conecta y se espera (25 s como mucho).
     * Si no se puede (no instalada, otra firma, sin autorizar), la fuente se salta sin penalizarla.
     */
    override suspend fun prepareNetwork(source: PlaySource): String? {
        if (!providers.usesWarp(source.providerId)) return null
        var phase = warp.state.value.phase
        if (phase == WarpPhase.CONNECTED) return null
        if (phase == WarpPhase.CHECKING || phase == WarpPhase.ERROR || phase == WarpPhase.DISCONNECTED) {
            phase = runCatching { warp.refresh().phase }.getOrDefault(phase)
            if (phase == WarpPhase.CONNECTED) return null
        }
        when (phase) {
            WarpPhase.NOT_INSTALLED, WarpPhase.SIGNATURE_MISMATCH, WarpPhase.PERMISSION_REQUIRED, WarpPhase.NEEDS_SETUP -> {
                log.add("WARP", "El proveedor de la fuente necesita WARP y no se puede conectar desde MiVisor TV (${phase.name})")
                return "WARP"
            }
            else -> Unit
        }
        log.add("WARP", "Conectando WARP para un proveedor que la necesita")
        warp.connect()
        val final = withTimeoutOrNull(25_000) {
            warp.state.first { it.phase == WarpPhase.CONNECTED || (!it.transitional && it.phase != WarpPhase.CHECKING) }
        }
        return if (final?.phase == WarpPhase.CONNECTED) null else "WARP"
    }

    override fun generalCause(source: PlaySource): String? {
        val ctx = context(source)
        return synchronized(lock) { store.classify(ctx) }
    }

    /** El idioma elegido en el canal (si se recuerda) o, si no, el preferido en Ajustes › Audio y subtítulos. */
    override fun preferredAudio(channelId: String): String? =
        (if (prefs.rememberAudio) prefs.audioFor(channelId) else null) ?: prefs.audioLanguage.takeIf { it.isNotBlank() }

    override fun rememberAudio(channelId: String, language: String?) {
        if (prefs.rememberAudio) prefs.setAudio(channelId, language)
    }

    override fun subtitlesByDefault(): Boolean = prefs.subtitles

    /** Quitar la pausa o la cuarentena de una fuente (Ajustes › fuentes del canal). */
    fun release(contentId: String) {
        synchronized(lock) { store.release(contentId, now()) }
        persist(contentId)
    }

    /** Olvidar todo lo aprendido. */
    fun forgetAll() {
        synchronized(lock) { store = ReliabilityStore() }
        scope.launch(io) { runCatching { db.clearHealth() } }
    }

    /** Lo mejor que se sabe de un canal (máxima puntuación de sus fuentes), para ordenar «Dónde verlo». */
    fun bestScore(contentIds: List<String>): Double = synchronized(lock) {
        val t = now()
        contentIds.maxOfOrNull { store.score(it, t) } ?: 0.0
    }

    /** Fuentes que descansan ahora (pausa o cuarentena), para Ajustes › Fuentes y el informe. */
    fun resting(): List<RestingSource> = synchronized(lock) {
        val t = now()
        store.cids().mapNotNull { c ->
            val st = store.state(c, t)
            if (st != SourceState.PAUSED && st != SourceState.QUARANTINE && st != SourceState.QUARANTINE_DUE) return@mapNotNull null
            val h = store.health(c) ?: return@mapNotNull null
            val until = if (st == SourceState.PAUSED) h.pauseUntil else h.lqNext
            RestingSource(c, st, until?.let { (it * 1000).toLong() }, h.events.lastOrNull { it.k !in NOT_REASONS }?.k,
                if (st == SourceState.PAUSED) h.trips else h.lqTries + 1)
        }.sortedBy { it.untilMs ?: Long.MAX_VALUE }
    }

    fun releaseAll() {
        val ids = resting().map { it.contentId }
        synchronized(lock) { ids.forEach { store.release(it, now()) } }
        persist(*ids.toTypedArray())
    }

    /** Lo que suele tardar en dar imagen la mejor fuente conocida (para «arranca en ~3 s»), en segundos. */
    fun typicalStart(contentIds: List<String>): Int? = synchronized(lock) {
        contentIds.mapNotNull { store.health(it)?.startMs?.takeIf { ms -> ms > 0 } }.minOrNull()?.let { (it / 1000).toInt().coerceAtLeast(1) }
    }

    /** El verificador manual (3d) solo apunta lo que funciona: un intento fallido sin ver el canal no penaliza. */
    fun verifiedOk(contentId: String, startMs: Long) {
        synchronized(lock) { store.success(contentId, now(), startMs.toDouble()) }
        persist(contentId)
    }

    fun knownSources(): Int = synchronized(lock) { store.cids().size }

    /** Lo que se sabe del momento del fallo: se rellena SOLO al fallar (sin sondeos continuos). */
    private fun context(source: PlaySource): FailureContext {
        val online = NetState.online(app)
        val warpNeeded = providers.usesWarp(source.providerId)
        val warpChangedAt = warp.lastChangeAt
        return FailureContext(
            online = online != false,
            internetOk = online != false,
            warpNeeded = warpNeeded,
            warpOk = !warpNeeded || warp.state.value.phase == WarpPhase.CONNECTED,
            netChangedS = NetState.secondsSinceChange(),
            warpChangedS = if (warpChangedAt > 0) (SystemClock.elapsedRealtime() - warpChangedAt) / 1000.0 else null,
            engineOk = EngineManager.healthy(),
            lowMemory = EngineManager.lowMemoryNow(app),
            foreground = foreground
        )
    }
}
