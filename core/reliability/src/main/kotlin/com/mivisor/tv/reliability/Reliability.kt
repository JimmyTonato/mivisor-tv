package com.mivisor.tv.reliability

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Fiabilidad de fuentes de MiVisor TV (especificación de la fase 2).
 *
 * Traducción literal del modelo de referencia `fiabilidad/modelo.py`: debe dar los mismos estados, el mismo orden y
 * las mismas puntuaciones con `vectores.json` (prueba [ReliabilityVectorsTest]).
 *
 *  - Aprende SOLO de lo que pasa con la app abierta. No hay temporizadores ni trabajo en segundo plano: los estados
 *    que dependen del tiempo (fin de una pausa, revisión de la cuarentena) se evalúan al necesitarlos.
 *  - El tiempo con la tele apagada solo hace que lo antiguo pese menos (olvido con vida media de 72 h).
 *  - Un fallo general (red, Internet, WARP, motor, aparato, segundo plano o muchas fuentes a la vez) no penaliza.
 *  - Límites: pausa máxima de 12 h, cuarentena revisable como mucho cada 7 días, un canal nunca sin candidata.
 *
 * Los tiempos van en segundos (epoch). La clase no es segura entre hilos: el repositorio la usa desde un solo hilo.
 */
object Policy {
    const val HALF_LIFE_S = 72.0 * 3600
    const val NEAR = 0.08
    const val BREAKER_STREAK = 3
    val PAUSE_MIN = intArrayOf(10, 30, 120, 360, 720)
    const val LQ_FAILS = 3
    const val LQ_SPAN_S = 72.0 * 3600
    val LQ_RETRY_D = intArrayOf(1, 2, 4, 7)
    const val CONFIRMED_WINDOW_S = 20.0 * 3600
    const val GLOBAL_WINDOW_S = 5.0 * 60
    const val GLOBAL_MIN = 3
    const val GLOBAL_MIN_CH = 2
    const val NET_GRACE_S = 30.0
    const val EVENTS_KEPT = 12

    val WEIGHT = mapOf(
        "frozen" to 0.7, "not_found" to 1.0, "no_video" to 1.0, "no_progress" to 0.8, "client_error" to 0.8,
        "no_peers" to 0.6, "stalled_early" to 0.5, "unknown" to 0.8
    )
}

/** Tipos de fallo que culpan a la fuente (los mismos que la Web 7.7.0). */
object FailureKind {
    const val NOT_FOUND = "not_found"
    const val NO_PEERS = "no_peers"
    const val NO_PROGRESS = "no_progress"
    const val NO_VIDEO = "no_video"
    const val CLIENT_ERROR = "client_error"
    const val STALLED_EARLY = "stalled_early"
    const val FROZEN = "frozen"
    const val UNKNOWN = "unknown"
}

/** Lo que la app sabe del momento del fallo. Se rellena SOLO cuando hay un fallo (sin sondeos continuos). */
data class FailureContext(
    val online: Boolean = true,
    val internetOk: Boolean = true,
    val warpNeeded: Boolean = false,
    val warpOk: Boolean = true,
    /** Segundos desde el último cambio de red (null si no se sabe). */
    val netChangedS: Double? = null,
    /** Segundos desde el último cambio de estado de WARP (null si no se sabe). */
    val warpChangedS: Double? = null,
    val engineOk: Boolean = true,
    val lowMemory: Boolean = false,
    val foreground: Boolean = true
)

data class Event(val k: String, val t: Long, val extra: Map<String, String> = emptyMap())

/** Lo aprendido de una fuente. Se guarda tal cual (JSON) entre sesiones. */
data class Health(
    var ok: Double = 0.0,
    var n: Double = 0.0,
    var at: Double = 0.0,
    var streak: Int = 0,
    var pauseUntil: Double? = null,
    var trips: Int = 0,
    var probation: Boolean = false,
    var startMs: Double? = null,
    var recent: Double? = null,
    var stalls: Double = 0.0,
    var freezes: Double = 0.0,
    var playMin: Double = 0.0,
    var fsg: Int = 0,
    var fsgSince: Double? = null,
    var lastConfirmed: Double? = null,
    var lqSince: Double? = null,
    var lqNext: Double? = null,
    var lqTries: Int = 0,
    var incompatible: Boolean = false,
    var events: MutableList<Event> = mutableListOf()
) {
    fun deepCopy(): Health = copy(events = events.toMutableList())
}

/** Estado de una fuente para la interfaz. */
enum class SourceState(val id: String) {
    NEW("nueva"), NORMAL("normal"), PAUSED("pausa"), PROBATION("a-prueba"),
    QUARANTINE("cuarentena"), QUARANTINE_DUE("cuarentena-revisable"), INCOMPATIBLE("incompatible")
}

object Math2 {
    fun wilsonLower(s: Double, n: Double, z: Double = 1.2816): Double {
        if (n <= 0) return 0.0
        val p = min(1.0, max(0.0, s / n)); val z2 = z * z
        return (p + z2 / (2 * n) - z * sqrt((p * (1 - p) + z2 / (4 * n)) / n)) / (1 + z2 / n)
    }

    /** round(x, 3) y round(x, 6) como en el modelo (redondeo al más cercano). */
    fun round(x: Double, decimals: Int): Double {
        val f = 10.0.pow(decimals)
        return Math.rint(x * f) / f
    }

    fun ewf(old: Double?, new: Double, a: Double = 0.3): Double = round(if (old == null) new else old + a * (new - old), 3)
}

class ReliabilityStore(initial: Map<String, Health> = emptyMap()) {
    private val h = LinkedHashMap<String, Health>(initial)
    private class Recent(val t: Double, val cid: String, val channel: String, val before: Health)
    private val recentFail = ArrayList<Recent>()
    private var generalUntil = 0.0

    /** Copia de todo lo aprendido (para guardar). */
    fun snapshot(): Map<String, Health> = h.mapValues { it.value.deepCopy() }
    fun health(cid: String): Health? = h[cid]
    fun cids(): Set<String> = h.keys

    fun get(cid: String): Health = h.getOrPut(cid) { Health() }

    private fun factor(x: Health, now: Double): Double {
        val at = if (x.at == 0.0) now else x.at
        return 0.5.pow(max(0.0, now - at) / Policy.HALF_LIFE_S)
    }

    private fun decay(x: Health, now: Double) {
        val f = factor(x, now)
        x.ok *= f; x.n *= f; x.stalls *= f; x.freezes *= f; x.playMin *= f
        x.at = now
    }

    private fun event(x: Health, k: String, now: Double, extra: Map<String, String> = emptyMap()) {
        x.events.add(Event(k, Math.round(now), extra))
        while (x.events.size > Policy.EVENTS_KEPT) x.events.removeAt(0)
    }

    private fun paused(x: Health, now: Double) = x.pauseUntil.let { it != null && it != 0.0 && it > now }
    private fun inLq(x: Health) = x.lqSince != null

    // ------------------------------------------------------------------ resultados

    fun success(cid: String, now: Double, startMs: Double? = null) {
        val x = get(cid); decay(x, now)
        x.ok += 1; x.n += 1
        x.streak = 0; x.pauseUntil = null; x.probation = false
        x.trips = max(0, x.trips - 1)
        x.fsg = 0; x.fsgSince = null
        if (x.lqSince != null) {
            x.lqSince = null; x.lqNext = null; x.lqTries = 0; event(x, "released", now)
        }
        if (startMs != null) {
            x.startMs = Math.rint(if (x.startMs == null) startMs else x.startMs!! + 0.35 * (startMs - x.startMs!!))
        }
        val slow = min(0.3, max(0.0, ((startMs ?: 0.0) / 1000 - 8) / 40))
        x.recent = Math2.ewf(x.recent, 1.0 - slow)
        event(x, "ok", now, if (startMs != null) mapOf("ms" to Math.round(startMs).toString()) else emptyMap())
        recentFail.clear()
    }

    /** Clasifica un fallo: la causa general si no es culpa de la fuente, o null. */
    fun classify(ctx: FailureContext): String? = when {
        !ctx.foreground -> "background"
        !ctx.online -> "offline"
        ctx.netChangedS != null && ctx.netChangedS < Policy.NET_GRACE_S -> "offline"
        ctx.warpNeeded && !ctx.warpOk -> "warp"
        ctx.warpChangedS != null && ctx.warpChangedS < Policy.NET_GRACE_S -> "warp"
        !ctx.engineOk -> "engine"
        ctx.lowMemory -> "device"
        !ctx.internetOk -> "internet"
        else -> null
    }

    /** Registra un fallo. Devuelve "source" si se culpa a la fuente o la causa general. */
    fun failure(cid: String, now: Double, kind: String, ctx: FailureContext = FailureContext(), channel: String = ""): String {
        classify(ctx)?.let { cause ->
            event(get(cid), "excused", now, mapOf("cause" to cause))
            return cause
        }
        if (now < generalUntil) return "global"
        val x = get(cid)
        val before = x.deepCopy()
        recentFail.removeAll { now - it.t > Policy.GLOBAL_WINDOW_S }
        recentFail.add(Recent(now, cid, channel, before))
        if (recentFail.map { it.cid }.toSet().size >= Policy.GLOBAL_MIN &&
            recentFail.map { it.channel }.toSet().size >= Policy.GLOBAL_MIN_CH) {
            for (r in recentFail.asReversed()) h[r.cid] = r.before
            generalUntil = now + Policy.GLOBAL_WINDOW_S
            recentFail.clear()
            return "global"
        }
        penalize(x, now, kind)
        return "source"
    }

    private fun penalize(x: Health, now: Double, kind: String) {
        decay(x, now)
        val w = Policy.WEIGHT[kind] ?: 0.8
        x.n += w
        x.recent = Math2.ewf(x.recent, 1.0 - w)
        x.streak += 1
        val wasProbation = x.probation
        x.probation = false
        if ((x.streak >= Policy.BREAKER_STREAK || wasProbation) && !paused(x, now)) {
            val step = Policy.PAUSE_MIN[min(x.trips, Policy.PAUSE_MIN.size - 1)]
            x.pauseUntil = now + step * 60
            x.trips = min(x.trips + 1, Policy.PAUSE_MIN.size)
            event(x, "pause", now, mapOf("min" to step.toString()))
        }
        val last = x.lastConfirmed
        if (last == null || now - last >= Policy.CONFIRMED_WINDOW_S) {
            x.lastConfirmed = now
            x.fsg += 1
            if (x.fsgSince == null) x.fsgSince = now
            if (x.lqSince == null && x.fsg >= Policy.LQ_FAILS && now - x.fsgSince!! >= Policy.LQ_SPAN_S) {
                x.lqSince = now; x.lqTries = 0; x.lqNext = now + Policy.LQ_RETRY_D[0] * 86400.0
                event(x, "quarantine", now)
            } else if (x.lqSince != null) {
                x.lqTries += 1
                x.lqNext = now + Policy.LQ_RETRY_D[min(x.lqTries, Policy.LQ_RETRY_D.size - 1)] * 86400.0
            }
        }
        event(x, kind, now)
    }

    /** Congelación real mientras se ve. step: 0 sola, 1 empujón, 2 directo/recarga, 3 cambio de fuente. */
    fun freeze(cid: String, now: Double, step: Int) {
        val x = get(cid); decay(x, now)
        val s = step.coerceIn(0, 3)
        x.freezes += 1.0
        x.recent = Math2.ewf(x.recent, doubleArrayOf(0.85, 0.7, 0.5, 0.15)[s])
        event(x, "freeze", now, mapOf("paso" to s.toString()))
    }

    /** Al terminar de ver una fuente: minutos y cortes. */
    fun session(cid: String, now: Double, playedS: Double, stalls: Int) {
        val x = get(cid); decay(x, now)
        x.playMin += max(0.0, playedS) / 60
        x.stalls += stalls
    }

    fun release(cid: String, now: Double) {
        val x = get(cid)
        x.pauseUntil = null; x.streak = 0; x.trips = 0; x.probation = false
        x.lqSince = null; x.lqNext = null; x.lqTries = 0; x.fsg = 0; x.fsgSince = null
        event(x, "released", now, mapOf("manual" to "true"))
    }

    fun markIncompatible(cid: String, now: Double) {
        val x = get(cid); x.incompatible = true; event(x, "incompatible", now)
    }

    // ------------------------------------------------------------------ puntuación y orden

    fun score(cid: String, now: Double): Double {
        val x = h[cid] ?: return Math2.wilsonLower(0.5, 1.0)
        val f = factor(x, now)
        var s = Math2.wilsonLower(x.ok * f + 0.5, x.n * f + 1.0)
        x.startMs?.let { if (it != 0.0) s *= 1 - min(0.15, max(0.0, (it / 1000 - 8) / 17 * 0.15)) }
        x.recent?.let { s = 0.6 * s + 0.4 * it }
        val pm = x.playMin * f
        if (pm > 1) {
            val bad = (x.stalls + 2 * x.freezes) * f
            s *= 1 - min(0.25, bad / (pm / 60) * 0.05)
        }
        return Math2.round(s, 6)
    }

    private class Info(
        val c: String, val i: Int, val sc: Double, val paused: Boolean, val lq: Boolean, val inc: Boolean,
        val exp: Boolean, val mem: Boolean, val back: Double, val q: Int, val start: Double
    )

    /**
     * Orden de prueba. [quality]: 0 desconocida, 1 SD, 2 HD, 3 FHD, 4 4K. Ninguna fuente desaparece: las que
     * descansan van al final y, si todas descansan, se prueba primero la que antes vuelve.
     */
    fun order(cids: List<String>, now: Double, chosen: String? = null, remembered: String? = null,
              quality: Map<String, Int> = emptyMap()): List<String> {
        val info = cids.mapIndexed { i, c ->
            val x = h[c]
            val isPaused = x != null && paused(x, now)
            if (x != null && x.pauseUntil.let { it != null && it != 0.0 } && !isPaused && x.streak != 0) x.probation = true
            val lq = x != null && inLq(x)
            val lqDue = lq && x!!.lqNext != null && now >= x.lqNext!!
            Info(
                c = c, i = i, sc = score(c, now), paused = isPaused, lq = lq && !lqDue,
                inc = x?.incompatible == true, exp = c == chosen,
                mem = c == remembered && !isPaused && (x == null || x.streak == 0),
                back = if (x != null && isPaused) x.pauseUntil!! else 0.0,
                q = quality[c] ?: 0, start = x?.startMs?.takeIf { it != 0.0 } ?: 30000.0
            )
        }
        val pool = info.filter { !it.paused && !it.lq && !it.inc }.map { it.sc }
        val best = if (pool.isEmpty()) 0.0 else pool.max()
        fun near(x: Info) = best - x.sc <= Policy.NEAR
        val cmp = compareBy<Info>(
            { if (it.exp) 0 else 1 }, { if (it.inc) 1 else 0 }, { if (it.lq) 1 else 0 }, { if (it.paused) 1 else 0 },
            { it.back }, { if (it.mem) 0 else 1 }, { if (near(it)) 0 else 1 },
            { if (near(it)) -it.q else 0 }, { if (near(it)) it.start else 0.0 }, { -it.sc }, { it.i }
        )
        return info.sortedWith(cmp).map { it.c }
    }

    fun state(cid: String, now: Double): SourceState {
        val x = h[cid] ?: return SourceState.NEW
        return when {
            x.incompatible -> SourceState.INCOMPATIBLE
            inLq(x) -> if ((x.lqNext ?: 0.0) > now) SourceState.QUARANTINE else SourceState.QUARANTINE_DUE
            paused(x, now) -> SourceState.PAUSED
            x.probation || (x.pauseUntil.let { it != null && it != 0.0 } && x.streak != 0) -> SourceState.PROBATION
            else -> SourceState.NORMAL
        }
    }
}
