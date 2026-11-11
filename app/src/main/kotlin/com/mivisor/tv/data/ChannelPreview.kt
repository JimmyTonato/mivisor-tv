package com.mivisor.tv.data

import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.mivisor.tv.playback.EngineMediaSource
import com.mivisor.tv.catalog.tv.TvChannel
import com.mivisor.tv.engine.EngineApi
import com.mivisor.tv.outpace.EngineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 3k · Vista previa en vídeo en Canales, sin sonido: con el foco 2,5 s sobre un canal, su mejor fuente se ve dentro de
 * la tarjeta. Un único reproductor pequeño (buffer corto), solo mientras estás en Canales: al salir, al abrir un canal
 * o al pasar la app a segundo plano se suelta. La fuente se queda «preparada» un minuto por si la abres (Prewarm).
 *
 * Todo en el hilo principal (ExoPlayer lo exige); el motor y la red no se tocan aquí.
 */
class ChannelPreview(
    private val app: Context,
    private val scope: CoroutineScope,
    private val bestSource: (TvChannel) -> String?,
    private val enabled: () -> Boolean,
    private val keepWarm: (String) -> Unit,
    private val log: (String) -> Unit = {}
) {
    data class State(val channelId: String? = null, val ready: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var job: Job? = null
    private var cid: String? = null
    /** Canal que espera su turno (los 2,5 s con el foco encima). */
    private var pendingId: String? = null
    private var surface: TextureView? = null

    /** La fuente que se está previsualizando (para no soltarla desde Prewarm). */
    fun contentId(): String? = cid

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            _state.value = _state.value.copy(ready = true)
        }

        override fun onPlayerError(error: PlaybackException) {
            log("Vista previa: sin imagen (${error.errorCodeName})")
            stopPlayback()
        }
    }

    fun onFocus(channel: TvChannel) {
        if (_state.value.channelId == channel.id || pendingId == channel.id) return
        job?.cancel()
        stopPlayback()
        pendingId = null
        if (!enabled()) return
        pendingId = channel.id
        job = scope.launch {
            delay(DWELL_MS)
            if (pendingId != channel.id) return@launch
            pendingId = null
            val source = bestSource(channel) ?: return@launch
            val base = EngineManager.ensureStarted(app).baseUrl ?: return@launch
            val p = ensurePlayer()
            cid = source
            _state.value = State(channel.id, ready = false)
            p.setMediaItem(
                MediaItem.Builder()
                    .setUri(Uri.parse(EngineApi.hlsUrl(base, source)))
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
            )
            p.prepare()
            p.play()
        }
    }

    /** El foco sale de la tarjeta de [channelId]. */
    fun onBlur(channelId: String) {
        // Solo si es la suya: el foco puede llegar a la siguiente tarjeta antes de que esta se entere de que lo perdió.
        if (pendingId == channelId) { job?.cancel(); pendingId = null }
        if (_state.value.channelId == channelId) stopPlayback()
    }

    /** La tarjeta enseña el vídeo en [view]. */
    fun attach(view: TextureView) {
        surface = view
        player?.setVideoTextureView(view)
    }

    fun detach(view: TextureView) {
        if (surface === view) {
            player?.clearVideoTextureView(view)
            surface = null
        }
    }

    /** Salir de Canales, abrir un canal o segundo plano: se suelta el reproductor entero. */
    fun release() {
        job?.cancel()
        pendingId = null
        stopPlayback()
        player?.removeListener(listener)
        player?.release()
        player = null
        surface = null
    }

    private fun stopPlayback() {
        player?.let { it.stop(); it.clearMediaItems() }
        cid?.let(keepWarm)
        cid = null
        if (_state.value != State()) _state.value = State()
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val http = DefaultHttpDataSource.Factory().setConnectTimeoutMs(5_000).setReadTimeoutMs(30_000).setAllowCrossProtocolRedirects(false)
        // Buffer corto: es una ventanita sin sonido, no hace falta guardar mucho.
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(3_000, 10_000, 1_000, 2_000).build()
        return ExoPlayer.Builder(app)
            .setLoadControl(load)
            .setMediaSourceFactory(EngineMediaSource.factory(http))
            .build().also {
                it.volume = 0f
                it.playWhenReady = true
                it.addListener(listener)
                surface?.let { s -> it.setVideoTextureView(s) }
                player = it
            }
    }

    companion object {
        const val DWELL_MS = 2_500L

        /** Por defecto: sí, salvo en aparatos justos de memoria (allí se puede encender a mano). */
        fun defaultOn(app: Context) = !EngineManager.isLowRam(app)
    }
}
