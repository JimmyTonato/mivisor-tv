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
import com.mivisor.tv.catalog.AceStreamLink
import com.mivisor.tv.engine.AdaptiveBuffer
import com.mivisor.tv.engine.CatchUpController
import com.mivisor.tv.engine.EngineApi
import com.mivisor.tv.engine.EngineDiagnostics
import com.mivisor.tv.engine.EngineStatus
import com.mivisor.tv.engine.EngineSupervisor
import com.mivisor.tv.engine.LiveDelayController
import com.mivisor.tv.engine.LoadInsight
import com.mivisor.tv.engine.EngineKeys
import com.mivisor.tv.engine.TsProbe
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
        val tried: Boolean,
        /** 4f · Banda de MiVisor Gold («verde», «medio», «rojo») para el color; null = sin dato de Gold. */
        val band: String? = null,
        val onTrial: Boolean = false
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
        val recovering: String? = null,
        /** 3p · En qué paso va la carga de la fuente actual, y cuántos segundos lleva en él. */
        val stage: LoadStage = LoadStage.NONE,
        val stageSecs: Int = 0,
        /** 3p · Cuánto tardó en encontrar el canal (descriptor), si ya lo encontró. */
        val resolvedInSecs: Int? = null,
        /** 3p · Cómo acabó la última fuente que falló: dónde se quedó (para explicarlo, no un «sin imagen» genérico). */
        val lastFailStage: LoadStage? = null,
        /** 4d · Con emisores y sin imagen: qué pasa de verdad (llega vídeo, emisión parada, no mandan…). */
        val insight: LoadInsight.Insight? = null,
        /** 4d · El [insight] con el que falló la última fuente, para explicar el final. */
        val lastFailInsight: LoadInsight.Insight? = null,
        /** 4e · La fuente actual va con el vídeo directo del motor (plan B), no con HLS. */
        val direct: Boolean = false
    ) {
        val active: Boolean get() = phase == Phase.STARTING_ENGINE || phase == Phase.TRYING || phase == Phase.PLAYING
    }

    /**
     * 3p · Pasos de la carga, lo que ve el usuario mientras espera:
     *  - [RESOLVING]: buscando el canal (su descriptor) en el catálogo de Ace Stream. Puede tardar; NO es que no vaya.
     *  - [PEERS]: canal encontrado, buscando emisores. Si aquí no aparece ninguno, la emisión puede estar caída.
     *  - [BUFFERING]: ya hay emisores, preparando la imagen.
     */
    enum class LoadStage { NONE, RESOLVING, PEERS, BUFFERING }

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    /**
     * 3h · Retraso respecto al directo y vuelta adaptativa ([CatchUpController]), aparte de [ui] para no redibujar
     * todo el reproductor cada segundo. [delayMs] null = no se sabe; [aheadMs] = imagen ya descargada por delante.
     */
    data class Live(
        val delayMs: Long? = null,
        val aheadMs: Long = 0,
        val reserveMs: Long = 0,
        val state: CatchUpController.State = CatchUpController.State.ON_TIME
    ) {
        /** Va claramente por detrás de lo sano: se ofrece «Ir al directo». */
        val behind: Boolean get() = delayMs != null && delayMs - maxOf(reserveMs, 6_000L) > 8_000L
        /** Hay imagen descargada para adelantar 15 s sin pantalla negra. */
        val canJump15: Boolean get() = behind && aheadMs >= 17_000L && (delayMs ?: 0) - 15_000L >= 6_000L
    }
    private val _live = MutableStateFlow(Live())
    val live: StateFlow<Live> = _live.asStateFlow()
    private val catchUp = CatchUpController().also { it.maxDelayMs = com.mivisor.tv.engine.EngineConfig.maxSafeDelayMs(EngineManager.isLowRam(context.applicationContext)) }
    /**
     * 3p · Colchón con el que se retoma tras un corte (recarga): en vez de volver pegado al directo (y volver a cortarse),
     * se vuelve con el retraso aprendido de los cortes recientes. Crece si los cortes se repiten; 0 = el normal.
     */
    private var cushionMs = 0L
    /**
     * 4e · Plan B: fuentes que se están viendo con el vídeo DIRECTO del motor (`.ts`) en vez de HLS, porque llegaba
     * vídeo y el HLS no daba imagen. [directKnown]: las que ya funcionaron así en esta sesión de la app (arrancan
     * directamente en plan B la próxima vez, sin esperar).
     */
    private val direct = mutableSetOf<String>()
    private val directKnown = mutableSetOf<String>()
    /** 4e · Llega vídeo de la fuente que se está probando (lo dice [LoadInsight]). */
    @Volatile private var receivingVideo = false
    /**
     * 4f · Desde cuándo (elapsedRealtime) se está comprobando en el catálogo el descriptor de un ID de MiVisor Gold que
     * no funcionó con el descriptor publicado. 0 = no se está comprobando.
     */
    private var verifyingDescriptorSince = 0L
    private var recentReloads = ArrayDeque<Long>()
    /** Tras un salto propio dentro de lo descargado, un buffering breve no cuenta como corte. */
    private var jumpGraceUntil = 0L

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
    /** Última recarga de la misma fuente en el corte actual (3e: se recarga cada 15 s mientras se espera). */
    private var lastReloadAt = 0L
    /** Último error del reproductor al arrancar (se reintenta la misma fuente hasta el minuto). */
    private var lastStartError: String? = null
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
            if (src.contentId in direct && directKnown.add(src.contentId)) {
                book.log("Fuente", "${mask(src.contentId)}: imagen con la entrega directa (plan B); la próxima vez arranca así")
            }
            _ui.value = _ui.value.copy(phase = Phase.PLAYING, message = null, cause = null, startMs = elapsed, sources = rows())
            // 3i · Ya hay imagen: la siguiente calentada se suelta (si no es esta) para no gastar red ni memoria.
            if (warmedNext != null && warmedNext != currentSource?.contentId) releaseWarmedNext() else warmedNext = null
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> if (_ui.value.phase == Phase.PLAYING) {
                    liveDelay.onInterruption(SystemClock.elapsedRealtime())
                    // 3h · Un corte suspende la vuelta al directo (y deja de acelerar). Un salto propio no cuenta.
                    val nowB = SystemClock.elapsedRealtime()
                    if (nowB >= jumpGraceUntil) applyCatchUp(catchUp.onStall(nowB))
                    // Un corte se mide desde su principio: los saltos y recargas de la recuperación no lo reinician.
                    if (!resyncing && bufferingSince == 0L) {
                        bufferingSince = SystemClock.elapsedRealtime()
                        stalls++
                        loadControl?.policy?.onRebuffer()
                    }
                }
                Player.STATE_READY -> {
                    catchUp.onResume(SystemClock.elapsedRealtime())
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
                Player.STATE_ENDED -> if (_ui.value.active) stallFromError("El directo se detuvo")
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
            // 3e.1: un error del reproductor NO cambia de fuente al momento. Se reintenta la MISMA fuente y solo se
            // pasa a otra cuando ha pasado el minuto (o lo de Ajustes), o si el usuario pulsa «Probar otra fuente».
            when {
                kind == null -> engineFailed(detail)
                _ui.value.phase == Phase.PLAYING -> stallFromError(detail)
                // 4e · Llega vídeo pero el HLS da error: antes de reintentar lo mismo, plan B (vídeo directo).
                receivingVideo && switchToDirect("el reproductor dio error con HLS ($detail)") -> Unit
                else -> retryStart(kind, detail)
            }
        }
    }

    // ------------------------------------------------------------------ API

    /** Empieza un canal. [chosen]: Content ID elegido a mano en el menú de fuentes (se prueba el primero). */
    fun play(req: PlayRequest, chosen: String? = null) {
        endSession()
        cancelAttempt()
        releaseWarmedNext()
        request = req
        generation++
        val gen = generation
        liveDelay.reset(SystemClock.elapsedRealtime())
        catchUp.reset(SystemClock.elapsedRealtime())
        cushionMs = 0L
        recentReloads.clear()
        _live.value = Live()
        direct.clear()
        receivingVideo = false
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
    /**
     * 3e · «Probar otra fuente» a mano (cargando o con la imagen cortada). No penaliza a la fuente actual: ha sido
     * una decisión del usuario, no un fallo comprobado. Si ya se probaron todas, se vuelve a empezar por la mejor.
     */
    fun nextSource() {
        val src = currentSource ?: return
        val c = cycle ?: return
        monitor?.cancel()
        ++generation
        book.log("Fuente", "${mask(src.contentId)}: el usuario pasa a otra fuente (sin penalizarla)")
        if (_ui.value.phase == Phase.PLAYING) endSession()
        closeCurrent()
        _ui.value = _ui.value.copy(message = "Probando otra fuente…", recovering = null)
        val nextId = c.peekNext()
        if (nextId == null) {
            // Todas probadas: otra vuelta empezando por la siguiente en el orden.
            val order = c.order
            val i = order.indexOf(src.contentId)
            val again = order[(i + 1 + order.size) % order.size]
            c.choose(again)?.let { attempt(it) }
        } else advanceSoon()
    }

    /** 3e · «Ir al directo»: salto limpio al borde (menos el retraso objetivo), descartando lo atrasado. */
    fun goLive() {
        val p = player ?: return
        if (_ui.value.phase != Phase.PLAYING) return
        resyncing = true
        resyncStartedAt = SystemClock.elapsedRealtime()
        if (!p.playWhenReady) p.play()
        runCatching { p.seekToDefaultPosition() }
        liveDelay.onInterruption(SystemClock.elapsedRealtime())
        applyCatchUp(catchUp.onUserJump(SystemClock.elapsedRealtime()))
        _ui.value = _ui.value.copy(paused = false)
        book.log("Directo", "Salto al directo pedido por el usuario")
    }

    /**
     * 3h · «Adelantar 15 s»: salto DENTRO de lo ya descargado (instantáneo, sin pantalla negra), sin pasar del retraso
     * mínimo sano. Si no hay imagen descargada suficiente, adelanta lo que haya.
     */
    fun jumpAhead(ms: Long = 15_000L) {
        val p = player ?: return
        if (_ui.value.phase != Phase.PLAYING) return
        val live = _live.value
        val ahead = (p.bufferedPosition - p.currentPosition).coerceAtLeast(0) - 1_500
        val room = (live.delayMs ?: 0) - 6_000
        val step = minOf(ms, ahead, room)
        if (step < 2_000) return
        jumpGraceUntil = SystemClock.elapsedRealtime() + 4_000
        p.setSeekParameters(SeekParameters.PREVIOUS_SYNC)
        p.seekTo(p.currentPosition + step)
        applyCatchUp(catchUp.onUserJump(SystemClock.elapsedRealtime()))
        book.log("Directo", "Adelantar ${step / 1000} s pedido por el usuario")
    }

    /** Aplica una acción de [CatchUpController] al reproductor. */
    private fun applyCatchUp(action: CatchUpController.Action) {
        val p = player ?: return
        when (action) {
            is CatchUpController.Action.SetSpeed -> p.setPlaybackSpeed(action.speed)
            is CatchUpController.Action.SeekForward -> {
                jumpGraceUntil = SystemClock.elapsedRealtime() + 4_000
                p.setPlaybackSpeed(1f)
                // Hacia atrás al fotograma clave anterior: el destino queda DENTRO de lo descargado.
                p.setSeekParameters(SeekParameters.PREVIOUS_SYNC)
                p.seekTo(p.currentPosition + action.ms)
                book.log("Directo", "Acercándose al directo: −${action.ms / 1000} s (estable ${catchUp.stats.stableForMs / 1000} s, colchón ${catchUp.stats.reserveMs / 1000} s)")
            }
            CatchUpController.Action.None -> Unit
        }
    }

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
        releaseWarmedNext()
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
            .setMediaSourceFactory(EngineMediaSource.factory(http))
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
        verifyingDescriptorSince = 0
        recoverStep = 0
        audioApplied = false
        attemptStartedAt = SystemClock.elapsedRealtime()
        lastStartError = null
        playingSince = 0
        bufferingSince = 0
        stalls = 0
        resyncing = false
        _ui.value = _ui.value.copy(
            phase = Phase.TRYING, sourceIndex = c.indexOf(cid) + 1, sourceCount = c.order.size, provider = src.providerName,
            quality = src.qualityLabel, peers = 0, message = "Buscando emisores…", cause = null, sources = rows(),
            tracks = emptyList(), paused = false, startMs = null, recovering = null,
            stage = LoadStage.RESOLVING, stageSecs = 0, resolvedInSecs = null, insight = null
        )
        EngineDiagnostics.update { it.copy(sourceIndex = c.indexOf(cid) + 1, sourceCount = c.order.size, sourceId = mask(cid), sourceSwitches = c.triedCount - 1) }
        p.stop()
        p.clearMediaItems()
        liveDelay.reset(SystemClock.elapsedRealtime(), keepStats = true)
        catchUp.reset(SystemClock.elapsedRealtime())
        cushionMs = 0L
        recentReloads.clear()
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
            var stageSince = 0
            val insight = LoadInsight()
            val cutInsight = LoadInsight()
            var sampled = false
            var lastInsightKind: LoadInsight.Kind? = null
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
                // 3p · Paso de la carga: sin sesión en el motor = aún buscando el descriptor; con sesión y 0 emisores =
                // encontrado, buscando emisores; con emisores = preparando la imagen.
                if (_ui.value.phase == Phase.TRYING) {
                    val stage = when {
                        !sawStatus -> LoadStage.RESOLVING
                        maxPeers == 0 -> LoadStage.PEERS
                        else -> LoadStage.BUFFERING
                    }
                    val prev = _ui.value.stage
                    if (stage != prev) {
                        stageSince = tick
                        if (prev == LoadStage.RESOLVING && stage != LoadStage.RESOLVING) {
                            book.log("Fuente", "${mask(src.contentId)}: canal encontrado en ${tick} s")
                        }
                    }
                    // 4d · Con emisores, decir qué pasa de verdad en vez de un «preparando la imagen» fijo.
                    val seen = if (stage == LoadStage.BUFFERING) insight.observe(status, status?.peers ?: _ui.value.peers) else null
                    receivingVideo = seen?.kind == LoadInsight.Kind.RECEIVING || seen?.kind == LoadInsight.Kind.NO_PICTURE
                    // 4e · Plan B: llega vídeo y en [DIRECT_AFTER_SECS] no hay imagen → vídeo directo del motor.
                    // 4e · Caja negra: a los [SAMPLE_AFTER_SECS] llegando vídeo sin imagen, se mira qué trae.
                    if (receivingVideo && !sampled && stage == LoadStage.BUFFERING && tick - stageSince >= SAMPLE_AFTER_SECS) {
                        sampled = true
                        sampleVideo(base, src)
                    }
                    if (receivingVideo && stage == LoadStage.BUFFERING && tick - stageSince >= DIRECT_AFTER_SECS) {
                        switchToDirect("llega vídeo pero el HLS no da imagen en ${tick - stageSince} s")
                    }
                    if (seen != null && seen.kind != lastInsightKind) {
                        lastInsightKind = seen.kind
                        if (seen.kind != LoadInsight.Kind.WAITING && seen.kind != LoadInsight.Kind.RECEIVING) {
                            book.log("Fuente", "${mask(src.contentId)}: ${seen.status} (${tick} s)")
                        }
                    }
                    _ui.value = _ui.value.copy(
                        stage = stage, stageSecs = tick - stageSince,
                        resolvedInSecs = if (stage != LoadStage.RESOLVING) (_ui.value.resolvedInSecs ?: tick) else null,
                        insight = seen
                    )
                }
                when (_ui.value.phase) {
                    Phase.TRYING -> {
                        val elapsed = SystemClock.elapsedRealtime() - attemptStartedAt
                        val limit = startLimit(src)
                        // 4f · Comprobando en el catálogo el descriptor de un ID de Gold que no funcionó.
                        if (verifyingDescriptorSince > 0) {
                            val sha = EngineDiagnostics.lastCatalogDescriptorSha1
                            val waited = SystemClock.elapsedRealtime() - verifyingDescriptorSince
                            when {
                                sha != null && src.descriptorSha1 != null && sha == src.descriptorSha1.lowercase() -> {
                                    verifyingDescriptorSince = 0
                                    failed(lastStartError ?: FailureKind.NO_VIDEO, "el catálogo da el MISMO descriptor que publicó MiVisor Gold y no funcionó: se pasa al siguiente")
                                    return@launch
                                }
                                sha != null -> {
                                    verifyingDescriptorSince = 0
                                    book.log("Fuente", "${mask(src.contentId)}: el catálogo da un descriptor DISTINTO al publicado · se prueba con el del catálogo")
                                }
                                waited > DESCRIPTOR_CHECK_MS && maxPeers == 0 -> {
                                    verifyingDescriptorSince = 0
                                    failed(lastStartError ?: FailureKind.NOT_FOUND, "no se pudo comprobar el descriptor en el catálogo: se pasa al siguiente")
                                    return@launch
                                }
                                waited > DESCRIPTOR_CHECK_MS -> verifyingDescriptorSince = 0   // ya hay emisores: se sigue con el catálogo
                            }
                        }
                        val timedOut = StartPolicy.earlyFailure(elapsed, sawStatus, maxPeers, limit)
                        if (timedOut != null) {
                            // 4f · ID de Gold que no dio imagen con el descriptor publicado: antes de pasar al siguiente,
                            // se pide al catálogo y se compara (si es el mismo, al siguiente; si es otro, se prueba).
                            if (!fallbackToCatalog(src, "${elapsed / 1000} s sin imagen con el descriptor publicado")) {
                                failed(lastStartError ?: timedOut, "${elapsed / 1000} s sin imagen · emisores máx. $maxPeers")
                                return@launch
                            }
                        }
                        // 3i · Si la primera fuente tarda, se calienta YA la siguiente en el motor: si hay que cambiar
                        // (sola al minuto o con «Probar otra fuente»), ya lleva un rato buscando emisores.
                        if (elapsed >= NEXT_WARM_AFTER_MS && warmedNext == null && book.prewarmNext()) {
                            cycle?.peekNext()?.let { next ->
                                warmedNext = next
                                val b = baseUrl
                                if (b != null) scope.launch(Dispatchers.IO) { warmTouch(EngineApi.hlsUrl(b, next)) }
                                book.log("Fuente", "Preparando también la siguiente (${mask(next)}) por si hace falta")
                            }
                        }
                        if (elapsed >= 8_000) {
                            _ui.value = _ui.value.copy(
                                message = "Buscando señal · ${elapsed / 1000} s" +
                                    if (limit != Long.MAX_VALUE && (_ui.value.sourceCount) > 1) " (otra fuente a los ${limit / 1000} s)" else ""
                            )
                        }
                    }
                    Phase.PLAYING -> {
                        controlLiveDelay(status)
                        val now = SystemClock.elapsedRealtime()
                        if (bufferingSince <= 0) cutInsight.reset()
                        if (bufferingSince > 0) {
                            // 3e: un corte no cambia de fuente a los 26 s. Se salta al directo (8 s), se recarga la
                            // misma fuente (16 s y luego cada 15 s) y solo se pasa SOLA a otra tras el tiempo de
                            // Ajustes (1 min por defecto; «Nunca» = solo con el botón «Probar otra fuente»).
                            val stalled = now - bufferingSince
                            val switchAfter = StartPolicy.switchAfter(book.autoSwitchAfterMs())
                            if (stalled >= switchAfter) {
                                dropped("${stalled / 1000} s sin imagen tras saltar al directo y recargar")
                                return@launch
                            }
                            val step = StartPolicy.recoveryStep(stalled).coerceAtMost(2)
                            if (step > recoverStep) recover(step, base, src)
                            else if (step == 2 && now - lastReloadAt >= RELOAD_EVERY_MS) recover(2, base, src)
                            // 4d · En un corte, decir también por qué (emisión parada, no nos llega…).
                            val why = cutInsight.observe(status, status?.peers ?: _ui.value.peers).takeIf {
                                it.kind == LoadInsight.Kind.STALLED || it.kind == LoadInsight.Kind.NOT_REACHING ||
                                    it.kind == LoadInsight.Kind.NO_DATA || it.kind == LoadInsight.Kind.SIGNATURE
                            }
                            _ui.value = _ui.value.copy(
                                recovering = (why?.let { it.status + " · " } ?: "") +
                                    (if (recoverStep >= 2) "Recargando la fuente" else "Recuperando la imagen") +
                                    " · ${stalled / 1000} s" + if (switchAfter != Long.MAX_VALUE) " (otra fuente a los ${switchAfter / 1000} s)" else ""
                            )
                        } else if (resyncing && now - resyncStartedAt > StartPolicy.NUDGE_MS) {
                            // 3e.1: el salto al directo no volvió a dar imagen: pasa a ser un corte normal (se recarga
                            // la misma fuente y se espera el minuto), en vez de cambiar de fuente a los 20 s.
                            resyncing = false
                            bufferingSince = resyncStartedAt
                            stalls++
                            recoverStep = 1
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
        _ui.value = _ui.value.copy(lastFailStage = _ui.value.stage.takeIf { it != LoadStage.NONE }, lastFailInsight = _ui.value.insight)
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
            // 3h · Se vuelve a pedir la imagen DESDE DONDE ESTABAS (antes: salto al directo). Así un corte no te
            // mueve; la vuelta al directo la hace luego [CatchUpController], poco a poco y solo si es estable.
            _ui.value = _ui.value.copy(recovering = "Recuperando la imagen…")
            resyncing = true
            resyncStartedAt = SystemClock.elapsedRealtime()
            runCatching { p.seekTo(p.currentPosition) }
        } else {
            lastReloadAt = SystemClock.elapsedRealtime()
            // 3p · Se retoma con colchón, no pegado al directo: el colchón aprendido de los cortes y, si las recargas se
            // repiten (3 en 2 min), un 50 % más cada vez. Nunca más allá de lo que guarda el motor.
            val now = lastReloadAt
            recentReloads.addLast(now)
            while (recentReloads.isNotEmpty() && now - recentReloads.first() > 120_000L) recentReloads.removeFirst()
            val base0 = catchUp.reserveMs(now, loadControl?.policy?.targetMs ?: 6_000L)
            // 4o · Se retoma DONDE IBAS (el retraso que llevabas respecto al directo), no en el directo: antes cada
            // recarga daba un salto hacia delante y, si la emisión seguía mal, otro, y otro.
            val where = (_live.value.delayMs ?: 0L)
            val grown = maxOf(if (recentReloads.size >= 3) (maxOf(cushionMs, base0) * 1.5).toLong() else maxOf(cushionMs, base0), where)
            cushionMs = grown.coerceAtMost(catchUp.maxDelayMs - 6_000L).coerceAtLeast(0)
            _ui.value = _ui.value.copy(recovering = "Recuperando la imagen…")
            resyncing = false
            p.stop()
            p.setMediaItem(mediaItemFor(base, src.contentId))
            p.prepare()
            p.play()
        }
        book.log("Recuperación", "${mask(src.contentId)}: escalón $step" + if (step == 2 && cushionMs > 0) " · se retoma con ${cushionMs / 1000} s de colchón" else "")
    }

    /**
     * 3e.1 · Error del reproductor al ARRANCAR: se vuelve a pedir la misma fuente al motor (cada 3 s como mucho).
     * El monitor decide pasar a otra solo cuando se cumple el minuto ([StartPolicy.switchAfter]).
     */
    private fun retryStart(kind: String, detail: String) {
        val src = currentSource ?: return
        val base = baseUrl ?: return
        // 4f · Si se abría con el descriptor publicado por MiVisor Gold y falla, se vuelve al camino de siempre
        // (el motor pide el descriptor al catálogo). Esa vuelta no cuenta como fallo de la fuente.
        if (fallbackToCatalog(src, detail)) return
        lastStartError = kind
        book.log("Fuente", "${mask(src.contentId)}: $detail al arrancar · se reintenta la misma fuente")
        val gen = generation
        scope.launch {
            delay(3_000)
            if (gen != generation || _ui.value.phase != Phase.TRYING) return@launch
            val p = player ?: return@launch
            p.stop()
            p.setMediaItem(mediaItemFor(base, src.contentId))
            p.prepare()
            p.play()
        }
    }

    /**
     * 3e.1 · Error del reproductor (o fin del directo) mientras se veía: cuenta como un corte. Se recarga la MISMA
     * fuente (el monitor la repite cada 15 s) y solo se pasa a otra al cumplirse el minuto o con el botón.
     */
    private fun stallFromError(detail: String) {
        val src = currentSource ?: return
        val base = baseUrl ?: return
        val now = SystemClock.elapsedRealtime()
        if (bufferingSince == 0L) {
            bufferingSince = now
            stalls++
            liveDelay.onInterruption(now)
        }
        resyncing = false
        book.log("Recuperación", "${mask(src.contentId)}: $detail · se recarga la misma fuente")
        if (now - lastReloadAt >= 3_000) recover(2, base, src)
        else recoverStep = 2
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
            message = when (_ui.value.lastFailStage) {
                // 3p · Decir DÓNDE se quedó: no es lo mismo no encontrar el canal que no encontrar emisores.
                LoadStage.RESOLVING -> "No se ha encontrado el canal en el catálogo de Ace Stream" +
                    (if ((cycle?.order?.size ?: 0) > 1) " en ninguna de sus ${cycle?.order?.size} fuentes" else "") +
                    ". Suele ser el bloqueo de la operadora: conecta WARP y reintenta. Si con WARP tampoco, puede que los servidores del catálogo hayan cambiado (Ajustes › Motor)."
                LoadStage.PEERS -> "El canal existe, pero no ha llegado ningún emisor (0)." +
                    if (!EngineManager.fullP2p) " Si en Ace Stream u otra app sí se ve, activa «Aceptar conexiones de otros (P2P completo)» en Ajustes › Motor y reintenta."
                    else " Puede que la emisión esté caída, o que con WARP conectado nadie pueda llegar hasta ti: prueba sin WARP, más tarde u otra fuente."
                LoadStage.BUFFERING -> _ui.value.lastFailInsight
                    ?.takeIf { it.kind != LoadInsight.Kind.WAITING && it.kind != LoadInsight.Kind.RECEIVING }
                    ?.let { "${it.status}. ${it.hint ?: ""}".trim() }
                    ?: "Había emisores, pero no llegó imagen a tiempo. Reintentar suele bastar."
                else -> "Ninguna de las ${cycle?.order?.size ?: 0} fuentes ha dado imagen ahora. Puedes reintentar o elegir una en «Fuentes»."
            }
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

    /** 3i · Content ID que se está viendo o intentando (la precarga no lo toca). */
    fun currentContentId(): String? = currentSource?.contentId

    private fun stopEngineSession(contentId: String?) {
        val base = baseUrl ?: return
        contentId ?: return
        scope.launch(Dispatchers.IO) { EngineApi.stop(base, contentId) }
    }

    /**
     * 4f · Un ID de MiVisor Gold no funcionó con el descriptor publicado (error al arrancar o sin imagen a tiempo).
     * No se quita el camino de siempre: se vuelve a abrir el MISMO ID con «cid:» (el motor pide el descriptor al
     * catálogo con su lista de servidores) y el monitor compara la huella del descriptor del catálogo con la del
     * publicado. Si es el mismo, no se insiste: al siguiente ID. Si es otro, se prueba con él. false = no aplicaba.
     */
    private fun fallbackToCatalog(src: PlaySource, why: String): Boolean {
        if (!src.fixedOrder || !EngineKeys.usingInline(src.contentId)) return false
        val base = baseUrl ?: return false
        val p = player ?: return false
        val oldKey = EngineApi.key(src.contentId)
        EngineKeys.dropInline(src.contentId)
        EngineDiagnostics.resetCatalogDescriptor()
        book.log("Fuente", "${mask(src.contentId)}: $why · se pide el descriptor al catálogo para compararlo")
        scope.launch(Dispatchers.IO) { EngineApi.stopKey(base, oldKey) }
        val now = SystemClock.elapsedRealtime()
        verifyingDescriptorSince = now
        attemptStartedAt = now          // ventana nueva para el camino del catálogo
        lastStartError = null
        _ui.value = _ui.value.copy(message = "Comprobando el canal en el catálogo…")
        p.stop()
        p.setMediaItem(mediaItemFor(base, src.contentId))
        p.prepare()
        p.play()
        return true
    }

    /**
     * 4f · Tiempo sin imagen hasta pasar SOLA a la siguiente fuente. Las de MiVisor Gold ya vienen verificadas y
     * ordenadas: si ids[0] no da imagen en [GOLD_SWITCH_MS], se pasa a ids[1] sin preguntar (si está llegando vídeo,
     * se deja antes acabar el plan B). Con una sola fuente, o con «nunca» en Ajustes, lo de siempre.
     */
    private fun startLimit(src: PlaySource): Long {
        val setting = book.autoSwitchAfterMs()
        val usual = StartPolicy.switchAfter(setting)
        if (!src.fixedOrder || setting <= 0 || cycle?.peekNext() == null) return usual
        return if (receivingVideo) (DIRECT_AFTER_SECS + 30) * 1000L else GOLD_SWITCH_MS
    }

    private fun rows(): List<SourceRow> {
        val req = request ?: return emptyList()
        val c = cycle ?: return emptyList()
        return c.order.mapIndexedNotNull { i, cid ->
            val s = req.sources.firstOrNull { it.contentId == cid } ?: return@mapIndexedNotNull null
            SourceRow(
                i, cid, "Fuente ${i + 1}", s.providerName, s.qualityLabel, book.state(cid),
                current = cid == currentSource?.contentId, tried = c.wasTried(cid), band = s.band, onTrial = s.onTrial
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

    /**
     * 4e · Caja negra: lee unos segundos del vídeo directo del motor (máx. [SAMPLE_BYTES] u [SAMPLE_MS]) y guarda en el
     * diagnóstico qué trae: formato, audio y puntos de arranque. Solo el resumen, no el vídeo.
     */
    private fun sampleVideo(base: String, src: PlaySource) {
        scope.launch(Dispatchers.IO) {
            val buf = java.io.ByteArrayOutputStream()
            val started = SystemClock.elapsedRealtime()
            var c: java.net.HttpURLConnection? = null
            try {
                c = java.net.URL(EngineApi.streamUrl(base, src.contentId)).openConnection(java.net.Proxy.NO_PROXY) as java.net.HttpURLConnection
                c.connectTimeout = 3_000
                c.readTimeout = SAMPLE_MS.toInt()
                c.inputStream.use { input ->
                    val chunk = ByteArray(64 * 1024)
                    while (buf.size() < SAMPLE_BYTES && SystemClock.elapsedRealtime() - started < SAMPLE_MS) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        buf.write(chunk, 0, n)
                    }
                }
            } catch (_: Exception) {
            } finally {
                c?.disconnect()
            }
            val secs = (SystemClock.elapsedRealtime() - started) / 1000
            val text = if (buf.size() == 0) "la salida directa no dio vídeo en $secs s (el motor no encontró dónde empezar)"
                else TsProbe.analyze(buf.toByteArray()).summary()
            val line = "${mask(src.contentId)}: $text"
            EngineDiagnostics.update { it.copy(videoSample = line) }
            EngineDiagnostics.log(EngineDiagnostics.Cat.SOURCE, "Caja negra · $line", important = true)
            withContext(Dispatchers.Main) { book.log("Fuente", "Vídeo · $line") }
        }
    }

    /** 4e · Pasa la fuente actual al vídeo directo del motor. false si ya estaba en plan B (o no hay fuente). */
    private fun switchToDirect(why: String): Boolean {
        val src = currentSource ?: return false
        val base = baseUrl ?: return false
        val p = player ?: return false
        if (src.contentId in direct || src.contentId in directKnown) return false
        direct.add(src.contentId)
        book.log("Fuente", "${mask(src.contentId)}: $why · plan B: vídeo directo del motor")
        EngineDiagnostics.log(EngineDiagnostics.Cat.SOURCE, "Plan B (vídeo directo): $why", important = true)
        _ui.value = _ui.value.copy(direct = true)
        p.stop()
        p.setMediaItem(mediaItemFor(base, src.contentId))
        p.prepare()
        p.play()
        return true
    }

    private fun mediaItemFor(base: String, contentId: String): MediaItem {
        if (contentId in direct || contentId in directKnown) {
            direct.add(contentId)
            EngineDiagnostics.update { it.copy(transport = "TS directo (plan B)") }
            _ui.value = _ui.value.copy(direct = true)
            return MediaItem.Builder()
                .setMediaId(EngineApi.key(contentId))
                .setUri(Uri.parse(EngineApi.streamUrl(base, contentId)))
                .setMimeType(MimeTypes.VIDEO_MP2T)
                .build()
        }
        EngineDiagnostics.update { it.copy(transport = "HLS") }
        if (_ui.value.direct) _ui.value = _ui.value.copy(direct = false)
        // 3p · Tras cortes se arranca con colchón (ver [cushionMs]); la ventana del motor ya guarda esa imagen.
        val target = maxOf(loadControl?.policy?.targetMs ?: 6_000L, cushionMs)
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
        // 3h · Vuelta al directo adaptativa: los cortes no te mueven; se acerca solo cuando es estable y hay imagen
        // descargada, en proporción al retraso y dejando un colchón aprendido. Desactivada: decide el usuario.
        val auto = book.autoLiveEdge()
        val ahead = (p.bufferedPosition - p.currentPosition).coerceAtLeast(0)
        val action = catchUp.onTick(now, lag, ahead, target, playing && now >= jumpGraceUntil)
        if (auto) applyCatchUp(action)
        else if (p.playbackParameters.speed != 1f) p.setPlaybackSpeed(1f)
        val cs = catchUp.stats
        _live.value = Live(lag, ahead, cs.reserveMs, cs.state)
        val st = liveDelay.stats.copy(resyncs = cs.steps, discardedMs = cs.recoveredMs, delayMs = lag)
        EngineDiagnostics.update {
            it.copy(liveTargetMs = target, liveDelayMs = st.delayMs, liveResyncs = st.resyncs, liveDiscardedMs = st.discardedMs,
                enginePiecesBehind = status?.piecesBehindLive ?: it.enginePiecesBehind)
        }
    }

    private fun mask(id: String) = AceStreamLink.masked(id)

    /** 3i · Fuente siguiente calentada mientras la primera tarda (se suelta si al final no se usa). */
    private var warmedNext: String? = null

    /** Suelta la siguiente calentada si no es la que se acabó viendo. */
    private fun releaseWarmedNext() {
        val w = warmedNext ?: return
        warmedNext = null
        if (w != currentSource?.contentId) stopEngineSession(w)
    }

    private fun warmTouch(url: String) {
        var c: java.net.HttpURLConnection? = null
        try {
            c = java.net.URL(url).openConnection(java.net.Proxy.NO_PROXY) as java.net.HttpURLConnection
            c.connectTimeout = 3_000
            c.readTimeout = 25_000
            c.responseCode
        } catch (_: Exception) {
        } finally {
            c?.disconnect()
        }
    }

    private companion object {
        /** Mientras se espera a que vuelva la imagen, la misma fuente se recarga cada… (4o · 20 s, antes 15). */
        const val RELOAD_EVERY_MS = 20_000L
        /** 3i · Tras este tiempo sin imagen con la primera fuente, se calienta la siguiente. */
        const val NEXT_WARM_AFTER_MS = 5_000L
        /** 4e · Segundos llegando vídeo sin imagen antes de pasar al plan B (vídeo directo). */
        const val DIRECT_AFTER_SECS = 15
        /**
         * 4f · Fuentes de MiVisor Gold: sin imagen en este tiempo, a la siguiente. 4o · 35 s (antes 20): vienen
         * comprobadas, así que se les da más margen antes de cambiar.
         */
        const val GOLD_SWITCH_MS = 35_000L
        /** 4f · Tiempo para conseguir del catálogo la huella del descriptor y compararla con la publicada. */
        const val DESCRIPTOR_CHECK_MS = 12_000L
        /** 4e · Caja negra: cuándo se mira el vídeo y cuánto (como mucho). */
        const val SAMPLE_AFTER_SECS = 8
        const val SAMPLE_BYTES = 3 * 1024 * 1024
        const val SAMPLE_MS = 8_000L
    }
}
