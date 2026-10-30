package com.mivisor.tv.reliability

/**
 * Cuándo se da por fallido un intento con una fuente y de qué tipo es el fallo (los mismos umbrales que la v18,
 * traducidos a los tipos de [FailureKind] de la fiabilidad).
 */
object StartPolicy {
    /** Sin primera imagen en este tiempo: fallo. */
    const val TIMEOUT_MS = 30_000L
    /** El motor resolvió la fuente pero no hay emisores: se abandona antes. */
    const val NO_PEERS_MS = 15_000L
    /** Más de esto cargando mientras se veía: la fuente se ha caído. */
    const val STALL_MS = 20_000L
    /** Si se cae antes de este tiempo viéndose, cuenta como arranque fallido ("stalled_early"); después, congelación. */
    const val EARLY_PLAY_MS = 60_000L

    /**
     * ¿Abandonar ya el intento? [sawStatus] = el motor llegó a resolver la fuente; [maxPeers] = máximo de emisores.
     * null = seguir esperando.
     */
    fun earlyFailure(elapsedMs: Long, sawStatus: Boolean, maxPeers: Int): String? = when {
        elapsedMs >= TIMEOUT_MS -> when {
            !sawStatus -> FailureKind.NOT_FOUND
            maxPeers == 0 -> FailureKind.NO_PEERS
            else -> FailureKind.NO_VIDEO
        }
        elapsedMs >= NO_PEERS_MS && sawStatus && maxPeers == 0 -> FailureKind.NO_PEERS
        else -> null
    }

    /** Respuesta HTTP del motor local. null = no es culpa de la fuente (el motor falla). */
    fun fromHttpStatus(code: Int): String? = when (code) {
        400, 404, 410, 422 -> FailureKind.NOT_FOUND
        503 -> FailureKind.NO_PROGRESS       // HLS: la lista no llegó a tener su primer segmento a tiempo
        in 500..599 -> null
        else -> FailureKind.CLIENT_ERROR
    }

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

    /** Elegida a mano: se prueba ya aunque se hubiera probado antes. */
    fun choose(cid: String): String? = cid.takeIf { it in order }?.also { tried += it; current = it }

    fun indexOf(cid: String?): Int = order.indexOf(cid)
    fun wasTried(cid: String): Boolean = cid in tried
}
