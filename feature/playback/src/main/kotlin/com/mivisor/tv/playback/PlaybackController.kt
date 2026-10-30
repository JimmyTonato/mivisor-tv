package com.mivisor.tv.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.mivisor.tv.catalog.AceStreamLink
import com.mivisor.tv.engine.AdaptiveBuffer
import com.mivisor.tv.engine.EngineApi
import com.mivisor.tv.engine.EngineDiagnostics
import com.mivisor.tv.engine.EngineStatus
import com.mivisor.tv.engine.EngineSupervisor
import com.mivisor.tv.engine.LiveDelayController
import com.mivisor.tv.outpace.EngineManager
import com.mivisor.tv.reliability.FailureKind
import com.mivisor.tv.reliability.SourceCycle
import com.mivisor.tv.reliability.SourceState
import com.mivisor.tv.reliability.StartPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.util.Locale

/**
 * Reproducción: Content ID → Outpace (127.0.0.1) → HLS → Media3. Basado en el PlaybackController de la v18 (que
 * funciona), con la elección de fuentes de la fase 2 ([SourceBook] + fiabilidad) en lugar del selector antiguo.
 *
 *  - UN reproductor para toda la app y UNA fuente activa: antes de probar otra se para el reproductor y se cierra la
 *    sesión en el motor.
 *  - Las fuentes se prueban en el orden de la fiabilidad; cada una como mucho una vez por ciclo. Gana la primera que
 *    pinta imagen.
 *  - Fallo temprano con causa (sin resolver, sin emisores, sin imagen) en lugar de esperar a ciegas.
 *  - Si la que se ve se cae (atasco de 20 s), se sigue con la siguiente.
 *  - Un fallo general (sin Internet, WARP, motor) no se apunta a la fuente: lo decide [SourceBook.onFailure].
 *  - Retraso respecto al directo estable ([LiveDelayController]) y buffer pequeño y adaptativo.
 *
 * Se usa siempre desde el hilo principal.
 */
@OptIn(UnstableApi::class)
class PlaybackController(context: Context, private val book: SourceBook) {

    enum class Phase { IDLE, STARTING_ENGINE, TRYING, PLAYING, FAILED }

    /** Una fila del menú de fuentes. Sin Content IDs completos. */
    data class SourceRow(
        val index: Int,
        val contentId: String,
        val label: String,
        val provider: String,
        val quality: String?,
        val state: SourceState,
        val current: Boolean,
        val tried: Boolean
    )

    data class TrackOption(
        val type: Int,
        val group: Int,
        val track: Int,
        val label: String,
        val selected: Boolean,
        /** Idioma (código ISO) si la pista lo trae: se recuerda por canal. */
        val language: String? = null,
        /** «AAC · estéreo», «E-AC-3 · 5.1» (3d: panel de audio del diseño). */
        val detail: String? = null,
        /** El aparato no la puede reproducir (se enseña, pero no se puede elegir). */
        val supported: Boolean = true
    )

    data class Ui(
        val phase: Phase = Phase.IDLE,
        val channelId: String = "",
        val channelName: String = "",
        val sourceIndex: Int = 0,
        val sourceCount: Int = 0,
        val provider: String? = null,
        val quality: String? = null,
        val peers: Int = 0,
        val message: String? = null,
        /** Causa general cuando no es culpa de las fuentes ("offline", "warp", "engine"…). */
        val cause: String? = null,
        val sources: List<SourceRow> = emptyList(),
        val tracks: List<TrackOption> = emptyList(),
        val paused: Boolean = false,
        val startMs: Long? = null,
        /** Recuperando un corte de la fuente que se veía (escalón 1 a 3) o esperando a que vuelva la red. */
        val recovering: String? = null
    ) {
        val active: Boolean get() = phase == Phase.STARTING_ENGINE || phase == Phase.TRYING || phase == Phase.PLAYING
    }

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    var player: ExoPlayer? = null
        private set
    private var loadControl: AdaptiveLoadControl? = null
    private var job: Job? = null
    private var monitor: Job? = null
    private var request: PlayRequest? = null
    private var cycle: SourceCycle? = null
    private var currentSource: PlaySource? = null
    private var baseUrl: String? = null
    private var generation = 0
    private var attemptStartedAt = 0L
    private var playingSince = 0L
    private var bufferingSince = 0L
    private var stalls = 0
    private var resyncing = false
    private var resyncStartedAt = 0L
    private val liveDelay = LiveDelayController()
    /** Escalón de recuperación ya aplicado en el corte actual (0 = ninguno). */
    private var recoverStep = 0
    /** Fuentes saltadas en este ciclo porque su red (WARP) no estaba disponible: no se penalizan. */
    private var skippedForNetwork = 0
    private var audioApplied = false

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            val src = currentSource ?: return
            val req = request ?: return
            if (_ui.value.phase != Phase.TRYING) return
            val elapsed = SystemClock.elapsedRealtime() - attemptStartedAt
            playingSince = SystemClock.elapsedRealtime()
            stalls = 0
            EngineDiagnostics.log(EngineDiagnostics.Cat.SOURCE, "Imagen con la fuente ${_ui.value.sourceIndex}/${_ui.value.sourceCount} (${mask(src.contentId)}) en $elapsed ms", important = true)
            EngineDiagnostics.update { it.copy(firstFrameMs = elapsed) }
            book.onSuccess(req.channelId, src, elapsed)
            _ui.value = _ui.value.copy(phase = Phase.PLAYING, message = null, cause = null, startMs = elapsed, sources = rows())
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> if (_ui.value.phase == Phase.PLAYING) {
                    liveDelay.onInterruption(SystemClock.elapsedRealtime())
                    // Un corte se mide desde su principio: los saltos y recargas de la recuperación no lo reinician.
                    if (!resyncing && bufferingSince == 0L) {
                        bufferingSince = SystemClock.elapsedRealtime()
                        stalls++
                        loadControl?.policy?.onRebuffer()
                    }
                }
                Player.STATE_READY -> {
                    val src = currentSource
                    val stalledMs = if (bufferingSince > 0) SystemClock.elapsedRealtime() - bufferingSince else 0
                    if (src != null && _ui.value.phase == Phase.PLAYING && stalledMs >= StartPolicy.FREEZE_MIN_MS) {
                        // Corte real recuperado sin cambiar de fuente: se apunta con el escalón que hizo falta.
                        book.onFreeze(src, recoverStep)
                        book.log("Recuperación", "${mask(src.contentId)}: imagen recuperada tras ${stalledMs / 1000} s (escalón $recoverStep)")
                    }
                    bufferingSince = 0
                    recoverStep = 0
                    resyncing = false
                    if (_ui.value.recovering != null) _ui.value = _ui.value.copy(recovering = null)
                }
                Player.STATE_ENDED -> if (_ui.value.active) dropped("El directo terminó")
                else -> Unit
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val options = trackOptions(tracks)
            _ui.value = _ui.value.copy(tracks = options)
            applyPreferredAudio(options)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val p = player ?: return
            _ui.value = _ui.value.copy(paused = !isPlaying && !p.playWhenReady)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!_ui.value.active) return
            val (kind, detail) = classify(error)
            when {
                kind == null -> engineFailed(detail)
                _ui.value.phase == Phase.PLAYING -> dropped(detail)
                else -> failed(kind, detail)
            }
        }
    }

    // ------------------------------------------------------------------ API

    /** Empieza un canal. [chosen]: Content ID elegido a mano en el menú de fuentes (se prueba el primero). */
    fun play(req: PlayRequest, chosen: String? = null) {
        endSession()
        cancelAttempt()
        request = req
        generation++
        val gen = generation
        liveDelay.reset(SystemClock.elapsedRealtime())
        _ui.value = Ui(Phase.STARTING_ENGINE, req.channelId, req.channelName, sourceCount = req.sources.size, message = "Preparando el motor…")
        EngineDiagnostics.update { it.copy(channel = req.channelName, sourceCount = req.sources.size, sourceSwitches = 0, rebuffers = 0, firstFrameMs = null) }
        job = scope.launch {
            val engine = EngineManager.ensureStarted(app)
            if (gen != generation) return@launch
            val base = engine.baseUrl
            if (base == null) {
                _ui.value = _ui.value.copy(phase = Phase.FAILED, cause = "engine", message = engine.message ?: "El motor Outpace no arrancó")
                book.log("Motor", "No arrancó: ${engine.message ?: "sin detalle"}")
                return@launch
            }
            baseUrl = base
            val order = withContext(Dispatchers.Default) { book.order(req.channelId, req.sources, chosen) }
            if (gen != generation) return@launch
            cycle = SourceCycle(order)
            skippedForNetwork = 0
            ensurePlayer()
            loadControl?.policy?.reset()
            book.log("Reproducción", "${req.channelName}: ${order.size} fuente(s)")
            next()
        }
    }

    /** Menú de fuentes: ver otra fuente concreta (o reintentar una que falló). */
    fun selectSource(contentId: String) {
        val c = cycle ?: return
        if (contentId == currentSource?.contentId && _ui.value.phase == Phase.PLAYING) return
        endSession()
        closeCurrent()
        val chosen = c.choose(contentId) ?: return
        attempt(chosen)
    }

    fun retry() {
        val req = request ?: return
        play(req)
    }

    fun togglePause() {
        val p = player ?: return
        if (p.playWhenReady) {
            p.pause()
        } else {
            // Al reanudar se vuelve al directo (borde − objetivo): salto limpio en HLS.
            resyncing = true
            resyncStartedAt = SystemClock.elapsedRealtime()
            runCatching { p.seekToDefaultPosition() }
            liveDelay.onInterruption(SystemClock.elapsedRealtime())
            p.play()
        }
        _ui.value = _ui.value.copy(paused = !p.playWhenReady)
    }

    fun selectTrack(option: TrackOption) {
        val p = player ?: return
        val group = p.currentTracks.groups.getOrNull(option.group) ?: return
        if (option.type == C.TRACK_TYPE_AUDIO) request?.let { book.rememberAudio(it.channelId, option.language ?: option.label) }
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(option.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.track))
            .build()
    }

    fun disableSubtitles() {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }

    /** Para la reproducción y cierra la sesión del motor (el motor sigue vivo para el siguiente canal). */
    fun stop() {
        endSession()
        cancelAttempt()
        player?.stop()
        player?.clearMediaItems()
        request = null
        _ui.value = Ui()
        EngineDiagnostics.update { it.copy(channel = null, sourceId = null, peers = 0, bitrateBps = 0, engineBufferMs = 0, playerBufferMs = 0) }
    }

    /** Libera el reproductor (segundo plano). */
    fun release() {
        stop()
        player?.removeListener(listener)
        player?.release()
        player = null
        loadControl = null
    }

    // ------------------------------------------------------------------ interno

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val lowRam = EngineManager.isLowRam(app)
        val lc = AdaptiveLoadControl(AdaptiveBuffer(lowRam), lowRam)
        loadControl = lc
        // El motor puede tardar en tener el primer segmento (resolver + emisores): lectura larga; conexión corta (local).
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(5_000)
            .setReadTimeoutMs(35_000)
            .setAllowCrossProtocolRedirects(false)
        return ExoPlayer.Builder(app)
            .setLoadControl(lc)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build().also {
                it.addListener(listener)
                it.playWhenReady = true
                player = it
            }
    }

    private fun sourceOf(cid: String): PlaySource? = request?.sources?.firstOrNull { it.contentId == cid }

    private fun next() {
        val c = cycle ?: return
        val cid = c.next()
        if (cid == null) {
            exhausted()
            return
        }
        attempt(cid)
    }

    /** Prepara la red que pide el proveedor de la fuente (WARP) y, si está, la prueba; si no, la salta sin penalizarla. */
    private fun attempt(cid: String) {
        val src = sourceOf(cid) ?: return next()
        val c = cycle ?: return
        val gen = ++generation
        _ui.value = _ui.value.copy(
            phase = Phase.TRYING, sourceIndex = c.indexOf(cid) + 1, sourceCount = c.order.size, provider = src.providerName,
            quality = src.qualityLabel, peers = 0, message = "Preparando la conexión…", cause = null, recovering = null
        )
        scope.launch {
            val blocked = book.prepareNetwork(src)
            if (gen != generation) return@launch
            if (blocked != null) {
                skippedForNetwork++
                book.log("Fuente", "${mask(cid)} saltada sin penalizar: su proveedor necesita $blocked y no está disponible")
                advanceSoon()
            } else start(src, cid)
        }
    }

    private fun start(src: PlaySource, cid: String) {
        val base = baseUrl ?: return
        val c = cycle ?: return
        val p = player ?: return
        generation++
        val gen = generation
        currentSource = src
        recoverStep = 0
        audioApplied = false
        attemptStartedAt = SystemClock.elapsedRealtime()
        playingSince = 0
        bufferingSince = 0
        stalls = 0
        resyncing = false
        _ui.value = _ui.value.copy(
            phase = Phase.TRYING, sourceIndex = c.indexOf(cid) + 1, sourceCount = c.order.size, provider = src.providerName,
            quality = src.qualityLabel, peers = 0, message = "Buscando emisores…", cause = null, sources = rows(),
            tracks = emptyList(), paused = false, startMs = null, recovering = null
        )
        EngineDiagnostics.update { it.copy(sourceIndex = c.indexOf(cid) + 1, sourceCount = c.order.size, sourceId = mask(cid), sourceSwitches = c.triedCount - 1) }
        p.stop()
        p.clearMediaItems()
        liveDelay.reset(SystemClock.elapsedRealtime(), keepStats = true)
        p.setPlaybackSpeed(1f)
        p.setMediaItem(mediaItemFor(base, cid))
        p.volume = 1f
        p.prepare()
        p.play()
        startMonitor(gen, src)
    }

    /** Cada segundo: estado del motor (emisores, buffer) y decisión de fallo temprano o de caída. */
    private fun startMonitor(gen: Int, src: PlaySource) {
        monitor?.cancel()
        monitor = scope.launch {
            var sawStatus = false
            var maxPeers = 0
            var tick = 0
            while (isActive && gen == generation) {
                delay(1_000)
                tick++
                val base = baseUrl ?: break
                val status = withContext(Dispatchers.IO) { EngineApi.status(base, src.contentId) }
                if (gen != generation) break
                if (status == null && sawStatus && tick > 3 && !withContext(Dispatchers.IO) { EngineSupervisor.health(EngineManager.state.value.port) }) {
                    engineFailed("El proceso del motor dejó de responder")
                    break
                }
                if (status != null) {
                    sawStatus = true
                    maxPeers = maxOf(maxPeers, status.peers)
                }
                val p = player
                val policy = loadControl?.policy
                policy?.onTick()
                EngineDiagnostics.update {
                    it.copy(
                        peers = status?.peers ?: it.peers, bitrateBps = status?.bitrateBps ?: it.bitrateBps,
                        engineBufferMs = status?.engineBufferMs ?: 0,
                        playerBufferMs = p?.let { pl -> (pl.bufferedPosition - pl.currentPosition).coerceAtLeast(0) } ?: 0,
                        bufferTargetMs = policy?.targetMs ?: 0, rebuffers = policy?.rebuffers ?: 0
                    )
                }
                _ui.value = _ui.value.copy(peers = status?.peers ?: _ui.value.peers)
                when (_ui.value.phase) {
                    Phase.TRYING -> {
                        val elapsed = SystemClock.elapsedRealtime() - attemptStartedAt
                        StartPolicy.earlyFailure(elapsed, sawStatus, maxPeers)?.let { kind ->
                            failed(kind, "${elapsed / 1000} s sin imagen · emisores máx. $maxPeers")
                            return@launch
                        }
                    }
                    Phase.PLAYING -> {
                        controlLiveDelay(status)
                        val now = SystemClock.elapsedRealtime()
                        if (bufferingSince > 0) {
                            val step = StartPolicy.recoveryStep(now - bufferingSince)
                            if (step > recoverStep) {
                                if (step >= 3) {
                                    dropped("${(now - bufferingSince) / 1000} s sin imagen tras saltar al directo y recargar")
                                    return@launch
                                }
                                recover(step, base, src)
                            }
                        } else if (resyncing && now - resyncStartedAt > StartPolicy.STALL_MS) {
                            dropped("El salto al directo no volvió a dar imagen")
                            return@launch
                        }
                    }
                    else -> break
                }
            }
        }
    }

    /** No arrancó: se apunta (si es culpa de la fuente) y se prueba la siguiente. */
    private fun failed(kind: String, detail: String) {
        val src = currentSource ?: return
        val req = request ?: return
        monitor?.cancel()
        closeCurrent()
        val gen = ++generation
        _ui.value = _ui.value.copy(message = "Probando otra fuente…")
        scope.launch {
            // El contexto del fallo (red, WARP, motor, memoria) se mira ahora, fuera del hilo principal.
            val verdict = withContext(Dispatchers.IO) { book.onFailure(req.channelId, src, kind) }
            book.log("Fuente", "${mask(src.contentId)}: $kind · $detail" + if (verdict != "source") " · no se culpa a la fuente ($verdict)" else "")
            if (gen != generation) return@launch
            when (verdict) {
                "engine" -> engineFailed("El motor no estaba sano durante el intento")
                "offline", "internet", "warp" -> generalFailure(verdict)
                else -> advanceSoon()
            }
        }
    }

    /** Recuperación de un corte sin cambiar de fuente: 1 = salto al directo, 2 = recargar la misma fuente. */
    private fun recover(step: Int, base: String, src: PlaySource) {
        val p = player ?: return
        recoverStep = step
        if (step == 1) {
            _ui.value = _ui.value.copy(recovering = "Recuperando la imagen: saltando al directo…")
            resyncing = true
            resyncStartedAt = SystemClock.elapsedRealtime()
            runCatching { p.seekToDefaultPosition() }
        } else {
            _ui.value = _ui.value.copy(recovering = "Recuperando la imagen: recargando la fuente…")
            resyncing = false
            p.stop()
            p.setMediaItem(mediaItemFor(base, src.contentId))
            p.prepare()
            p.play()
        }
        book.log("Recuperación", "${mask(src.contentId)}: escalón $step")
    }

    /**
     * Se cayó mientras se veía. Antes de culpar a la fuente se mira si es un fallo general (red, WARP): en ese caso
     * no se penaliza ni se cambia de fuente; se espera a que vuelva y se recarga la misma.
     */
    private fun dropped(detail: String) {
        val src = currentSource ?: return
        monitor?.cancel()
        val gen = ++generation
        scope.launch {
            val cause = withContext(Dispatchers.IO) { book.generalCause(src) }
            if (gen != generation) return@launch
            if (cause != null) waitForNetwork(src, cause, gen) else droppedBySource(src, detail)
        }
    }

    /** Fallo general durante la reproducción: se espera (comprobando cada 5 s) y se vuelve a la MISMA fuente. */
    private fun waitForNetwork(src: PlaySource, cause: String, gen: Int) {
        book.log("Recuperación", "Corte por causa general ($cause): no se penaliza la fuente; se espera a que vuelva")
        _ui.value = _ui.value.copy(
            recovering = if (cause == "warp") "WARP se ha desconectado: esperando a que vuelva…" else "Sin conexión: esperando a que vuelva la red…"
        )
        scope.launch {
            while (gen == generation) {
                delay(5_000)
                if (gen != generation) return@launch
                val still = withContext(Dispatchers.IO) { book.generalCause(src) }
                if (still == null) {
                    bufferingSince = 0
                    recoverStep = 0
                    val base = baseUrl ?: return@launch
                    val p = player ?: return@launch
                    _ui.value = _ui.value.copy(recovering = "Conexión recuperada: volviendo al canal…")
                    p.stop()
                    p.setMediaItem(mediaItemFor(base, src.contentId))
                    p.prepare()
                    p.play()
                    startMonitor(gen, src)
                    return@launch
                }
            }
        }
    }

    private fun droppedBySource(src: PlaySource, detail: String) {
        val playedMs = if (playingSince > 0) SystemClock.elapsedRealtime() - playingSince else 0
        val early = StartPolicy.dropKind(playedMs)
        if (early != null) {
            failed(early, detail)
            return
        }
        book.onFreeze(src, 3)
        book.log("Fuente", "${mask(src.contentId)} se cortó tras ${playedMs / 1000} s: $detail · se pasa a otra")
        endSession()
        closeCurrent()
        advanceSoon()
    }

    private fun engineFailed(detail: String) {
        monitor?.cancel()
        closeCurrent()
        _ui.value = _ui.value.copy(phase = Phase.FAILED, cause = "engine", message = "El motor Outpace dejó de responder. Pulsa Reintentar.")
        book.log("Motor", detail)
    }

    private fun generalFailure(cause: String) {
        val text = when (cause) {
            "warp" -> "WARP no está conectada y este proveedor la necesita. No se ha penalizado ninguna fuente."
            else -> "Parece que no hay conexión a Internet. No se ha penalizado ninguna fuente."
        }
        _ui.value = _ui.value.copy(phase = Phase.FAILED, cause = cause, message = text, sources = rows())
    }

    private fun exhausted() {
        currentSource = null
        monitor?.cancel()
        val c = cycle
        if (c != null && skippedForNetwork > 0 && skippedForNetwork >= c.triedCount) {
            generalFailure("warp")
            return
        }
        _ui.value = _ui.value.copy(
            phase = Phase.FAILED, cause = null, sources = rows(),
            message = "Ninguna de las ${cycle?.order?.size ?: 0} fuentes ha dado imagen ahora. Puedes reintentar o elegir una en «Fuentes»."
        )
        book.log("Reproducción", "Fin del ciclo sin imagen")
    }

    private fun advanceSoon() {
        val gen = ++generation
        scope.launch {
            delay(300)                         // deja cerrar la sesión anterior en el motor
            if (gen == generation) next()
        }
    }

    private fun closeCurrent() {
        val src = currentSource
        player?.stop()
        player?.clearMediaItems()
        stopEngineSession(src?.contentId)
    }

    /** Fin de lo que se estaba viendo: minutos y cortes a la fiabilidad e historial. */
    private fun endSession() {
        val req = request ?: return
        val src = currentSource
        val playedMs = if (playingSince > 0) SystemClock.elapsedRealtime() - playingSince else 0
        book.onSessionEnd(req.channelId, req.channelName, src?.takeIf { playedMs > 0 }, playedMs, stalls)
        playingSince = 0
    }

    private fun cancelAttempt() {
        job?.cancel()
        monitor?.cancel()
        stopEngineSession(currentSource?.contentId)
        currentSource = null
        cycle = null
        generation++
    }

    private fun stopEngineSession(contentId: String?) {
        val base = baseUrl ?: return
        contentId ?: return
        scope.launch(Dispatchers.IO) { EngineApi.stop(base, contentId) }
    }

    private fun rows(): List<SourceRow> {
        val req = request ?: return emptyList()
        val c = cycle ?: return emptyList()
        return c.order.mapIndexedNotNull { i, cid ->
            val s = req.sources.firstOrNull { it.contentId == cid } ?: return@mapIndexedNotNull null
            SourceRow(
                i, cid, "Fuente ${i + 1}", s.providerName, s.qualityLabel, book.state(cid),
                current = cid == currentSource?.contentId, tried = c.wasTried(cid)
            )
        }
    }

    private fun classify(error: PlaybackException): Pair<String?, String> {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return StartPolicy.fromHttpStatus(cause.responseCode) to "HTTP ${cause.responseCode} del motor"
            }
            if (cause is ConnectException) return null to "Conexión rechazada en 127.0.0.1"
            cause = cause.cause
        }
        return FailureKind.CLIENT_ERROR to "${error.errorCodeName}: ${error.message.orEmpty().take(120)}"
    }

    /** Multiaudio: se vuelve a poner el idioma elegido la última vez en este canal (una vez por fuente). */
    private fun applyPreferredAudio(options: List<TrackOption>) {
        if (audioApplied) return
        val req = request ?: return
        if (options.isEmpty()) return
        audioApplied = true
        // Subtítulos al empezar según Ajustes › Audio y subtítulos (3d).
        val subs = options.filter { it.type == C.TRACK_TYPE_TEXT && it.supported }
        if (!book.subtitlesByDefault()) {
            if (subs.any { it.selected }) disableSubtitles()
        } else if (subs.isNotEmpty() && subs.none { it.selected }) {
            selectTrack(subs.first())
        }
        val audio = options.filter { it.type == C.TRACK_TYPE_AUDIO && it.supported }
        if (audio.size < 2) return
        val wanted = book.preferredAudio(req.channelId) ?: return
        val match = audio.firstOrNull { sameLanguage(it.language, wanted) } ?: audio.firstOrNull { it.label == wanted } ?: return
        if (!match.selected) {
            val p = player ?: return
            val group = p.currentTracks.groups.getOrNull(match.group) ?: return
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, match.track))
                .build()
        }
    }

    /** «es», «spa» y «es-ES» son el mismo idioma. */
    private fun sameLanguage(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        if (a.equals(b, ignoreCase = true)) return true
        fun iso3(x: String) = runCatching { Locale(x.substringBefore('-')).isO3Language }.getOrDefault(x)
        return iso3(a).equals(iso3(b), ignoreCase = true)
    }

    private fun codecName(mime: String?): String? = when (mime) {
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "AC-3"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
        MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP2/MP3"
        MimeTypes.AUDIO_DTS -> "DTS"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.TEXT_VTT -> "WebVTT"
        MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_CEA708 -> "Teletexto CEA"
        MimeTypes.APPLICATION_DVBSUBS -> "DVB"
        else -> null
    }

    private fun trackOptions(tracks: Tracks): List<TrackOption> {
        val out = ArrayList<TrackOption>()
        val es = Locale("es")
        tracks.groups.forEachIndexed { gi, g ->
            if (g.type != C.TRACK_TYPE_AUDIO && g.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (i in 0 until g.length) {
                val supported = g.isTrackSupported(i)
                val f = g.getTrackFormat(i)
                val number = out.count { it.type == g.type } + 1
                val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { code ->
                    runCatching { Locale(code).getDisplayLanguage(es) }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
                }
                val extra = ""
                val name = listOfNotNull(f.label?.takeIf { it.isNotBlank() }, lang).distinct().joinToString(" · ").ifBlank {
                    if (g.type == C.TRACK_TYPE_AUDIO) "Audio $number" else "Subtítulos $number"
                }
                val layout = when {
                    g.type != C.TRACK_TYPE_AUDIO -> null
                    f.channelCount == 6 -> "5.1"
                    f.channelCount == 8 -> "7.1"
                    f.channelCount == 2 -> "estéreo"
                    f.channelCount == 1 -> "mono"
                    f.channelCount > 0 -> "${f.channelCount} canales"
                    else -> null
                }
                val detail = listOfNotNull(codecName(f.sampleMimeType), layout, if (!supported) "este aparato no la puede reproducir" else null)
                    .joinToString(" · ").ifEmpty { null }
                out += TrackOption(g.type, gi, i, name + extra, g.isTrackSelected(i), f.language?.takeIf { it.isNotBlank() && it != "und" }, detail, supported)
            }
        }
        return out
    }

    private fun mediaItemFor(base: String, contentId: String): MediaItem {
        val target = loadControl?.policy?.targetMs ?: 6_000L
        return MediaItem.Builder()
            .setMediaId(EngineApi.key(contentId))
            .setUri(Uri.parse(EngineApi.hlsUrl(base, contentId)))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            // Arranque cerca del objetivo; la velocidad la gestiona LiveDelayController (no la de Media3).
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(target).setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build())
            .build()
    }

    /** Cada segundo mientras se ve: mide el retraso y lo corrige (nada / acelerar / salto limpio). */
    private fun controlLiveDelay(status: EngineStatus?) {
        val p = player ?: return
        val now = SystemClock.elapsedRealtime()
        val target = loadControl?.policy?.targetMs ?: 6_000L
        val playing = p.playbackState == Player.STATE_READY && p.playWhenReady && !resyncing
        val duration = p.duration
        val lag = if (resyncing || !p.isCurrentMediaItemLive || duration == C.TIME_UNSET || duration <= 0) null
        else (duration - p.currentPosition).coerceAtLeast(0)
        when (val action = liveDelay.onTick(now, lag, target, playing)) {
            is LiveDelayController.Action.SetSpeed -> p.setPlaybackSpeed(action.speed)
            is LiveDelayController.Action.Resync -> {
                resyncing = true
                resyncStartedAt = now
                p.setPlaybackSpeed(1f)
                p.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                p.seekTo((duration - action.targetDelayMs).coerceAtLeast(0))
            }
            LiveDelayController.Action.None -> Unit
        }
        val st = liveDelay.stats
        EngineDiagnostics.update {
            it.copy(liveTargetMs = target, liveDelayMs = st.delayMs, liveResyncs = st.resyncs, liveDiscardedMs = st.discardedMs,
                enginePiecesBehind = status?.piecesBehindLive ?: it.enginePiecesBehind)
        }
    }

    private fun mask(id: String) = AceStreamLink.masked(id)
}
