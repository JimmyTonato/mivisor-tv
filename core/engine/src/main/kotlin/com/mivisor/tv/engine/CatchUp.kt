package com.mivisor.tv.engine

/**
 * 3h · Vuelta al directo ADAPTATIVA (sin ajustes): números en un sitio.
 *
 *  1. Un corte no mueve al usuario: la imagen sigue donde se paró y el retraso puede crecer (20 s, 40 s, 1 min…).
 *  2. Se mide todo el rato cuánto lleva sin cortes y cuánta imagen hay YA descargada por delante.
 *  3. Solo se acerca al directo cuando ve la oportunidad (estable un rato y con imagen de sobra) y en PROPORCIÓN:
 *     poco retraso → poco recorte; mucho retraso y mucha estabilidad → más recorte. Nunca de golpe hasta el borde.
 *  4. Deja siempre un COLCHÓN aprendido de la red (≈ 2 × lo que duran sus cortes): no vuelve al borde para cortarse.
 *  5. Un corte suspende la vuelta al momento; el colchón crece y se exige más estabilidad antes de volver a bajar.
 *  6. Recortes pequeños: acelerando un poco (inapreciable). Grandes: salto DENTRO de lo ya descargado (sin negro).
 */
object CatchUpPolicy {
    /** Sin cortes este tiempo antes de acercarse… */
    const val STABLE_MS = 30_000L
    /** …más esto por cada corte reciente (red inestable = más prudencia)… */
    const val STABLE_PER_STALL_MS = 10_000L
    /** …y como mucho esto. */
    const val STABLE_MAX_MS = 90_000L
    /** «Reciente» para contar cortes y medir su duración. */
    const val RECENT_MS = 5 * 60_000L
    /** Tras tanto tiempo estable se recorta más (la mitad del exceso en vez de un tercio). */
    const val LONG_STABLE_MS = 3 * 60_000L
    /** Por debajo de esto de exceso sobre el colchón no se hace nada. */
    const val TOLERANCE_MS = 1_500L
    const val MIN_STEP_MS = 2_000L
    const val MAX_STEP_MS = 30_000L
    /** Recortes hasta esto: acelerando (×[SPEED], Media3 mantiene el tono). Más: salto dentro de lo descargado. */
    const val SPEED_UP_MAX_MS = 8_000L
    const val SPEED = 1.1f
    /** Para saltar, lo descargado por delante debe superar el recorte en esto. */
    const val SEEK_MARGIN_MS = 2_000L
    /** Acelerando, se para si lo descargado por delante baja de esto (no se «come» el colchón). */
    const val SPEED_MIN_BUFFER_MS = 4_000L
    /** Entre un recorte y el siguiente, sin cortes por medio. */
    const val STEP_GAP_MS = 20_000L
    /** Colchón = este factor × lo que duran los cortes recientes (el más largo de los 3 últimos)… */
    const val RESERVE_FACTOR = 2.0
    /** …con 3 o más cortes recientes, × esto más… */
    const val RESERVE_BURSTY = 1.5
    /** …y como mucho esto. */
    const val MAX_RESERVE_MS = 45_000L
    /**
     * 3i · Margen de descarga: cuánto crece lo descargado por delante por cada segundo visto a velocidad normal
     * (0,10 = la red baja un 10 % más rápido de lo que se ve). Por debajo de esto la red va justa: solo se recorta si
     * tras el recorte sigue quedando el colchón descargado. Por encima de [MARGIN_GOOD] se recorta más (la mitad).
     */
    const val MARGIN_TIGHT = 0.02
    const val MARGIN_GOOD = 0.25
    /** Muestras (≈ segundos) antes de fiarse del margen medido. */
    const val MARGIN_SAMPLES = 10
}

class CatchUpController {

    sealed class Action {
        object None : Action()
        data class SetSpeed(val speed: Float) : Action()
        /** Adelantar [ms] dentro de lo ya descargado (salto instantáneo, sin pantalla negra). */
        data class SeekForward(val ms: Long) : Action()
    }

    enum class State { ON_TIME, WAITING, CATCHING_UP, SUSPENDED }

    data class Stats(
        val delayMs: Long? = null,
        val reserveMs: Long = 0,
        val bufferAheadMs: Long = 0,
        val stableForMs: Long = 0,
        val requiredStableMs: Long = CatchUpPolicy.STABLE_MS,
        /** 3i · Margen de descarga medido (null = aún no se sabe). */
        val downloadMargin: Double? = null,
        val recentStalls: Int = 0,
        val steps: Int = 0,
        val recoveredMs: Long = 0,
        val state: State = State.ON_TIME,
        val speeding: Boolean = false
    )

    var stats = Stats()
        private set

    private class Stall(val at: Long, var durationMs: Long)
    private val stalls = ArrayList<Stall>()
    private var stallStart = -1L
    private var stableSince = -1L
    private var lastStepAt = Long.MIN_VALUE / 2
    /** Acelerando: retraso al que se quiere llegar. */
    private var speedGoal = -1L
    // 3i · Medida del margen de descarga (media móvil de lo que crece lo descargado por delante).
    private var lastAheadAt = -1L
    private var lastAhead = 0L
    private var margin: Double? = null
    private var marginSamples = 0

    private fun resetMargin() {
        lastAheadAt = -1L
    }

    private fun sampleMargin(nowMs: Long, aheadMs: Long) {
        if (lastAheadAt >= 0) {
            val dt = nowMs - lastAheadAt
            if (dt in 500..5_000) {
                val m = (aheadMs - lastAhead).toDouble() / dt
                margin = margin?.let { it + 0.15 * (m - it) } ?: m
                marginSamples++
            }
        }
        lastAheadAt = nowMs
        lastAhead = aheadMs
    }

    /** Empieza un corte (buffering con la imagen parada). */
    fun onStall(nowMs: Long): Action {
        if (stallStart < 0) {
            stallStart = nowMs
            stalls += Stall(nowMs, 0)
        }
        stableSince = -1
        resetMargin()
        return stopSpeeding(State.SUSPENDED)
    }

    /** Vuelve la imagen: se apunta lo que duró el corte (para el colchón). */
    fun onResume(nowMs: Long) {
        if (stallStart >= 0) {
            stalls.lastOrNull()?.durationMs = nowMs - stallStart
            stallStart = -1
        }
        if (stableSince < 0) stableSince = nowMs
    }

    /** Salto pedido por el usuario («Ir al directo», «Adelantar»): se vuelve a medir desde cero. */
    fun onUserJump(nowMs: Long): Action {
        stableSince = nowMs
        lastStepAt = nowMs
        resetMargin()
        return stopSpeeding(State.WAITING)
    }

    /** Canal o fuente nueva: se olvida todo (los cortes de otra fuente no dicen nada de esta). */
    fun reset(nowMs: Long) {
        stalls.clear()
        stallStart = -1
        stableSince = nowMs
        lastStepAt = Long.MIN_VALUE / 2
        speedGoal = -1
        margin = null
        marginSamples = 0
        resetMargin()
        stats = Stats()
    }

    /** Colchón que se deja siempre respecto al directo, aprendido de los cortes recientes. */
    fun reserveMs(nowMs: Long, targetMs: Long): Long {
        val recent = stalls.filter { nowMs - it.at <= CatchUpPolicy.RECENT_MS && it.durationMs > 0 }
        if (recent.isEmpty()) return targetMs
        val longest = recent.takeLast(3).maxOf { it.durationMs }
        var r = (longest * CatchUpPolicy.RESERVE_FACTOR).toLong()
        if (recent.size >= 3) r = (r * CatchUpPolicy.RESERVE_BURSTY).toLong()
        return r.coerceIn(targetMs, maxOf(targetMs, CatchUpPolicy.MAX_RESERVE_MS))
    }

    /**
     * Cada ~1 s. [delayMs] = retraso respecto al borde (null = no se sabe); [bufferAheadMs] = imagen ya descargada por
     * delante de lo que se ve; [targetMs] = retraso mínimo sano del buffer adaptativo; [playing] = imagen en marcha.
     */
    fun onTick(nowMs: Long, delayMs: Long?, bufferAheadMs: Long, targetMs: Long, playing: Boolean): Action {
        val recentStalls = stalls.count { nowMs - it.at <= CatchUpPolicy.RECENT_MS }
        val required = minOf(CatchUpPolicy.STABLE_MAX_MS, CatchUpPolicy.STABLE_MS + recentStalls * CatchUpPolicy.STABLE_PER_STALL_MS)
        val reserve = reserveMs(nowMs, targetMs)
        if (playing && stableSince < 0) stableSince = nowMs
        val stableFor = if (playing && stableSince >= 0) nowMs - stableSince else 0
        stats = stats.copy(delayMs = delayMs, reserveMs = reserve, bufferAheadMs = bufferAheadMs, stableForMs = stableFor,
            requiredStableMs = required, recentStalls = recentStalls)
        if (!playing || delayMs == null) {
            resetMargin()
            return stopSpeeding(if (stallStart >= 0) State.SUSPENDED else stats.state)
        }
        // A velocidad normal, lo que crece lo descargado por delante es el margen de la red.
        if (speedGoal < 0) sampleMargin(nowMs, bufferAheadMs) else resetMargin()
        val known = margin.takeIf { marginSamples >= CatchUpPolicy.MARGIN_SAMPLES }
        stats = stats.copy(downloadMargin = known)

        // Acelerando: hasta el objetivo, o antes si se acaba lo descargado.
        if (speedGoal >= 0) {
            return if (delayMs <= speedGoal + 250 || bufferAheadMs < CatchUpPolicy.SPEED_MIN_BUFFER_MS) {
                stats = stats.copy(recoveredMs = stats.recoveredMs, state = State.WAITING)
                lastStepAt = nowMs
                stopSpeeding(State.WAITING)
            } else Action.None
        }

        val excess = delayMs - reserve
        if (excess <= CatchUpPolicy.TOLERANCE_MS) {
            stats = stats.copy(state = State.ON_TIME)
            return Action.None
        }
        if (stableFor < required || nowMs - lastStepAt < CatchUpPolicy.STEP_GAP_MS) {
            stats = stats.copy(state = State.WAITING)
            return Action.None
        }
        // Recorte proporcional al exceso: un tercio, o la mitad si lleva mucho estable.
        // 3i · Con la red sobrada (margen alto) también se recorta la mitad.
        val fraction = if (stableFor >= CatchUpPolicy.LONG_STABLE_MS || (known ?: 0.0) >= CatchUpPolicy.MARGIN_GOOD) 0.5 else 1.0 / 3.0
        val step = (excess * fraction).toLong().coerceIn(minOf(CatchUpPolicy.MIN_STEP_MS, excess), CatchUpPolicy.MAX_STEP_MS)
        // 3i · Red justa: solo si tras el recorte sigue quedando el colchón ya descargado (no se vuelve a regenerar).
        if (known != null && known < CatchUpPolicy.MARGIN_TIGHT && bufferAheadMs - step < reserve) {
            stats = stats.copy(state = State.WAITING)
            return Action.None
        }
        return when {
            step <= CatchUpPolicy.SPEED_UP_MAX_MS && bufferAheadMs >= CatchUpPolicy.SPEED_MIN_BUFFER_MS + step / 4 -> {
                speedGoal = delayMs - step
                stats = stats.copy(steps = stats.steps + 1, recoveredMs = stats.recoveredMs + step, state = State.CATCHING_UP, speeding = true)
                Action.SetSpeed(CatchUpPolicy.SPEED)
            }
            bufferAheadMs >= step + CatchUpPolicy.SEEK_MARGIN_MS -> {
                lastStepAt = nowMs
                resetMargin()
                stats = stats.copy(steps = stats.steps + 1, recoveredMs = stats.recoveredMs + step, state = State.CATCHING_UP)
                Action.SeekForward(step)
            }
            // Lo descargado no llega para el recorte entero: se recorta lo que sí hay (si merece la pena).
            bufferAheadMs - CatchUpPolicy.SEEK_MARGIN_MS >= CatchUpPolicy.MIN_STEP_MS * 2 -> {
                val partial = bufferAheadMs - CatchUpPolicy.SEEK_MARGIN_MS
                lastStepAt = nowMs
                resetMargin()
                stats = stats.copy(steps = stats.steps + 1, recoveredMs = stats.recoveredMs + partial, state = State.CATCHING_UP)
                Action.SeekForward(partial)
            }
            else -> {
                stats = stats.copy(state = State.WAITING)
                Action.None
            }
        }
    }

    private fun stopSpeeding(state: State): Action {
        val was = speedGoal >= 0
        speedGoal = -1
        stats = stats.copy(speeding = false, state = state)
        return if (was) Action.SetSpeed(1f) else Action.None
    }
}
