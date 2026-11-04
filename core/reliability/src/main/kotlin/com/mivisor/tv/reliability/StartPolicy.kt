package com.mivisor.tv.reliability

/**
 * Cuándo se da por fallido un intento con una fuente y de qué tipo es el fallo (los mismos umbrales que la v18,
 * traducidos a los tipos de [FailureKind] de la fiabilidad).
 */
object StartPolicy {
    /** 3e.1: sin primera imagen en este tiempo (1 min, o lo que diga Ajustes): se pasa a otra fuente. */
    const val TIMEOUT_MS = 60_000L
    /** 3e.1: ya no se abandona antes por falta de emisores; se espera el mismo minuto. */
    const val NO_PEERS_MS = TIMEOUT_MS
    /** Más de esto cargando mientras se veía: la fuente se ha caído. */
    const val STALL_MS = 20_000L
    /** Si se cae antes de este tiempo viéndose, cuenta como arranque fallido ("stalled_early"); después, congelación. */
    const val EARLY_PLAY_MS = 60_000L

    /**
     * ¿Abandonar ya el intento? [sawStatus] = el motor llegó a resolver la fuente; [maxPeers] = máximo de emisores.
     * null = seguir esperando.
     */
    fun earlyFailure(elapsedMs: Long, sawStatus: Boolean, maxPeers: Int, timeoutMs: Long = TIMEOUT_MS): String? = when {
        elapsedMs < timeoutMs -> null
        !sawStatus -> FailureKind.NOT_FOUND
        maxPeers == 0 -> FailureKind.NO_PEERS
        else -> FailureKind.NO_VIDEO
    }

    /** Tiempo hasta pasar SOLA a otra fuente según Ajustes ([settingMs] ≤ 0 = nunca); nunca menos de 1 min. */
    fun switchAfter(settingMs: Long): Long = if (settingMs <= 0) Long.MAX_VALUE else maxOf(settingMs, TIMEOUT_MS)

    /** Respuesta HTTP del motor local. null = no es culpa de la fuente (el motor falla). */
    fun fromHttpStatus(code: Int): String? = when (code) {
        400, 404, 410, 422 -> FailureKind.NOT_FOUND
        503 -> FailureKind.NO_PROGRESS       // HLS: la lista no llegó a tener su primer segmento a tiempo
        in 500..599 -> null
        else -> FailureKind.CLIENT_ERROR
    }

    /**
     * Recuperación de un corte mientras se ve (entrega 3b), por escalones:
     *  0 = esperar (el buffer suele volver solo), 1 = salto al directo, 2 = recargar la misma fuente,
     *  3 = cambiar de fuente. Devuelve el escalón que toca tras [stalledMs] sin imagen.
     */
    const val NUDGE_MS = 8_000L
    const val RELOAD_MS = 16_000L
    const val SWITCH_MS = 26_000L

    fun recoveryStep(stalledMs: Long): Int = when {
        stalledMs >= SWITCH_MS -> 3
        stalledMs >= RELOAD_MS -> 2
        stalledMs >= NUDGE_MS -> 1
        else -> 0
    }

    /** Un corte cuenta como congelación (para la fiabilidad) si duró al menos esto. */
    const val FREEZE_MIN_MS = 4_000L

    /** La fuente se cayó mientras se veía: arranque fallido o congelación según cuánto llevaba. */
    fun dropKind(playedMs: Long): String? = if (playedMs < EARLY_PLAY_MS) FailureKind.STALLED_EARLY else null
}

/**
 * Recorrido de las fuentes de un canal en una reproducción: cada una se prueba como mucho una vez por ciclo,
 * en el orden que da [ReliabilityStore.order]. Elegir una a mano la pone la primera.
 */
class SourceCycle(val order: List<String>) {
    private val tried = LinkedHashSet<String>()
    var current: String? = null
        private set

    val triedCount: Int get() = tried.size
    val exhausted: Boolean get() = order.all { it in tried }

    /** Siguiente sin probar, o null si ya se probaron todas. */
    fun next(): String? = order.firstOrNull { it !in tried }?.also { tried += it; current = it }

    /** La siguiente sin probar, sin marcarla (3e: «Probar otra fuente»). */
    fun peekNext(): String? = order.firstOrNull { it !in tried }

    /** Elegida a mano: se prueba ya aunque se hubiera probado antes. */
    fun choose(cid: String): String? = cid.takeIf { it in order }?.also { tried += it; current = it }

    fun indexOf(cid: String?): Int = order.indexOf(cid)
    fun wasTried(cid: String): Boolean = cid in tried
}
