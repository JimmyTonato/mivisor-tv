package com.mivisor.tv.engine

/**
 * 3h/3q · Vuelta al directo ADAPTATIVA (sin ajustes): lo más cerca del directo que la red permita, sin cortes.
 *
 *  1. Un corte no mueve al usuario: la imagen sigue donde se paró y el retraso crece lo que haga falta.
 *  2. Se revisa todo el rato si la descarga va bien: ¿está bajado todo hasta (casi) el directo? ¿crece lo descargado?
 *  3. Si va bien, se PRUEBA a acercarse 10 s (salto dentro de lo ya descargado, sin negro). Se espera 30 s a ver qué
 *     tal: si no hay cortes y sigue yendo bien, otros 10 s… hasta quedarse a ~10 s del directo (o el colchón aprendido).
 *  4. Si tras acercarse hay un corte, la prueba ha salido mal: el colchón crece y se espera el doble antes de volver a
 *     probar (30 s → 1 min → 2 min → 4 min). Cada prueba buena relaja esa espera.
 *  5. Si la descarga va justa, no se prueba nada: se mantiene el retraso y la imagen fluida.
 *  6. Nunca se sale de lo que guarda el motor: si el retraso se acerca a su ventana, se adelanta lo justo.
 */
object CatchUpPolicy {
    /** Sin cortes este tiempo antes de acercarse… */
    const val STABLE_MS = 30_000L
    /** …más esto por cada corte reciente (red inestable = más prudencia)… */
    const val STABLE_PER_STALL_MS = 10_000L
    /** …y como mucho esto. */
    const val STABLE_MAX_MS = 240_000L
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
     * 3p · Con tantos cortes recientes (red a rachas) NO se intenta volver al directo: se mantiene el retraso que haya.
     * Volver al directo con la red así es lo que provocaba el bucle «corte → directo → corte».
     */
    const val BURSTY_STALLS = 3
    /** 3q · Retraso al que se quiere llegar cuando la red va bien (más cerca, poco margen ante un bache). */
    const val PREFERRED_DELAY_MS = 10_000L
    /** 3q · Cada prueba de acercamiento: como mucho esto… */
    const val PROBE_STEP_MS = 10_000L
    /** …y después se espera esto, sin cortes, antes de la siguiente (y para dar la prueba por buena). */
    const val PROBE_EVAL_MS = 30_000L
    /** 3q · «Va bien»: lo descargado llega hasta el directo menos esto (la red sigue el ritmo del directo)… */
    const val KEEP_PACE_GAP_MS = 6_000L
    /** …o lo descargado crece al menos esto por segundo visto (5 %). */
    const val MARGIN_OK = 0.05
    /** 3p · Margen que se deja siempre respecto al final de la ventana que guarda el motor. */
    const val WINDOW_GUARD_MS = 8_000L
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
    /** 3q · Pruebas que han salido mal seguidas (cada una dobla la espera antes de volver a probar). */
    private var backoff = 0
    /** 3q · Hay una prueba de acercamiento pendiente de evaluar. */
    private var probing = false
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
            // 3q · Un corte poco después de acercarse = la prueba salió mal: más espera antes de la siguiente.
            if (probing && nowMs - lastStepAt <= CatchUpPolicy.PROBE_EVAL_MS) backoff = minOf(backoff + 1, 4)
            probing = false
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

    /**
     * 3p · Retraso máximo que admite la ventana HLS del motor (lo que guarda hacia atrás menos un margen). Si se pasa,
     * el reproductor se sale de la ventana (BEHIND_LIVE_WINDOW / 404) y hay que recargar: se evita ANTES.
     */
    var maxDelayMs: Long = Long.MAX_VALUE

    /** Canal o fuente nueva: se olvida todo (los cortes de otra fuente no dicen nada de esta). */
    fun reset(nowMs: Long) {
        stalls.clear()
        stallStart = -1
        stableSince = nowMs
        lastStepAt = Long.MIN_VALUE / 2
        speedGoal = -1
        margin = null
        marginSamples = 0
        backoff = 0
        probing = false
        resetMargin()
        stats = Stats()
    }

    /** Colchón que se deja siempre respecto al directo, aprendido de los cortes recientes. */
    fun reserveMs(nowMs: Long, targetMs: Long): Long {
        val recent = stalls.filter { nowMs - it.at <= CatchUpPolicy.RECENT_MS && it.durationMs > 0 }
        if (recent.isEmpty()) return maxOf(targetMs, CatchUpPolicy.PREFERRED_DELAY_MS).coerceAtMost(maxOf(targetMs, maxDelayMs - CatchUpPolicy.WINDOW_GUARD_MS))
        val longest = recent.takeLast(3).maxOf { it.durationMs }
        var r = (longest * CatchUpPolicy.RESERVE_FACTOR).toLong()
        if (recent.size >= 3) r = (r * CatchUpPolicy.RESERVE_BURSTY).toLong()
        val cap = minOf(CatchUpPolicy.MAX_RESERVE_MS, maxDelayMs - CatchUpPolicy.WINDOW_GUARD_MS)
        val floor = maxOf(targetMs, CatchUpPolicy.PREFERRED_DELAY_MS)
        return r.coerceIn(floor, maxOf(floor, cap))
    }

    /**
     * Cada ~1 s. [delayMs] = retraso respecto al borde (null = no se sabe); [bufferAheadMs] = imagen ya descargada por
     * delante de lo que se ve; [targetMs] = retraso mínimo sano del buffer adaptativo; [playing] = imagen en marcha.
     */
    fun onTick(nowMs: Long, delayMs: Long?, bufferAheadMs: Long, targetMs: Long, playing: Boolean): Action {
        val recentStalls = stalls.count { nowMs - it.at <= CatchUpPolicy.RECENT_MS }
        val required = minOf(
            CatchUpPolicy.STABLE_MAX_MS,
            maxOf(CatchUpPolicy.STABLE_MS + recentStalls * CatchUpPolicy.STABLE_PER_STALL_MS, CatchUpPolicy.STABLE_MS shl backoff)
        )
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

        // 3p · Guarda de la ventana: si el retraso se acerca al final de lo que guarda el motor, se adelanta lo justo
        // AHORA (aunque no sea estable): es eso o salirse de la ventana y tener que recargar (mucho peor).
        if (delayMs > maxDelayMs) {
            val step = delayMs - (maxDelayMs - CatchUpPolicy.WINDOW_GUARD_MS)
            lastStepAt = nowMs
            resetMargin()
            stats = stats.copy(steps = stats.steps + 1, recoveredMs = stats.recoveredMs + step, state = State.CATCHING_UP)
            return Action.SeekForward(step)
        }
        val excess = delayMs - reserve
        // Prueba anterior evaluada: 30 s sin cortes desde que se acercó = salió bien (se relaja la espera).
        if (probing && nowMs - lastStepAt >= CatchUpPolicy.PROBE_EVAL_MS) {
            probing = false
            if (backoff > 0) backoff--
        }
        if (excess <= CatchUpPolicy.TOLERANCE_MS) {
            stats = stats.copy(state = State.ON_TIME)
            return Action.None
        }
        // ¿Va bien la descarga? Lo bajado llega casi al directo, o crece a buen ritmo. Con la red a rachas se pide más.
        val keepsPace = bufferAheadMs >= delayMs - CatchUpPolicy.KEEP_PACE_GAP_MS
        val needMargin = if (recentStalls >= CatchUpPolicy.BURSTY_STALLS) CatchUpPolicy.MARGIN_GOOD else CatchUpPolicy.MARGIN_OK
        val healthy = (keepsPace && recentStalls < CatchUpPolicy.BURSTY_STALLS) || (known != null && known >= needMargin)
        if (!healthy || stableFor < required || nowMs - lastStepAt < CatchUpPolicy.PROBE_EVAL_MS) {
            stats = stats.copy(state = State.WAITING)
            return Action.None
        }
        // Prueba: acercarse 10 s (o lo que falte), sin comerse el colchón.
        val step = minOf(CatchUpPolicy.PROBE_STEP_MS, excess)
        return when {
            bufferAheadMs >= step + CatchUpPolicy.SEEK_MARGIN_MS -> {
                lastStepAt = nowMs
                probing = true
                resetMargin()
                stats = stats.copy(steps = stats.steps + 1, recoveredMs = stats.recoveredMs + step, state = State.CATCHING_UP)
                Action.SeekForward(step)
            }
            // Lo bajado no llega para saltar: si es poco, acelerando un pelín (inapreciable) mientras dure lo descargado.
            step <= CatchUpPolicy.SPEED_UP_MAX_MS && bufferAheadMs >= CatchUpPolicy.SPEED_MIN_BUFFER_MS + step / 4 -> {
                speedGoal = delayMs - step
                lastStepAt = nowMs
                probing = true
                stats = stats.copy(steps = stats.steps + 1, recoveredMs = stats.recoveredMs + step, state = State.CATCHING_UP, speeding = true)
                Action.SetSpeed(CatchUpPolicy.SPEED)
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
