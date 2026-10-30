package com.mivisor.tv.playback

import com.mivisor.tv.reliability.SourceState

/** Una fuente reproducible de un canal (sin URLs: el Content ID solo se usa en memoria y enmascarado en registros). */
class PlaySource(
    val contentId: String,
    val providerId: String,
    val providerName: String,
    /** 0 desconocida, 1 SD, 2 HD, 3 FHD, 4 4K. */
    val quality: Int,
    val qualityLabel: String?
)

/** Lo que se pide reproducir. [channelId] es estable entre lecturas de los proveedores. */
class PlayRequest(val channelId: String, val channelName: String, val sources: List<PlaySource>)

/**
 * Puente con la fiabilidad (lo implementa la app con [com.mivisor.tv.reliability.ReliabilityStore] y la base de
 * datos). Aprende SOLO de lo que pasa aquí, con la app abierta; nada en segundo plano.
 */
interface SourceBook {
    fun order(channelId: String, sources: List<PlaySource>, chosen: String?): List<String>
    fun state(contentId: String): SourceState
    fun onSuccess(channelId: String, source: PlaySource, startMs: Long)
    /** Devuelve "source" si se culpa a la fuente, o la causa general ("offline", "warp", "engine", "global"…). */
    fun onFailure(channelId: String, source: PlaySource, kind: String): String
    fun onFreeze(source: PlaySource, step: Int)
    fun onSessionEnd(channelId: String, channelName: String, source: PlaySource?, playedMs: Long, stalls: Int)
    fun log(category: String, text: String)

    /**
     * Antes de probar una fuente: prepara la red que necesita su proveedor (p. ej. conecta WARP y espera).
     * null = se puede probar; si no, la causa ("warp") y la fuente se salta SIN penalizarla.
     */
    suspend fun prepareNetwork(source: PlaySource): String? = null

    /** Causa general en este momento (sin Internet, WARP caída…) o null. Se llama fuera del hilo principal. */
    fun generalCause(source: PlaySource): String? = null

    /** Idioma de audio elegido la última vez en este canal (multiaudio), o null. */
    fun preferredAudio(channelId: String): String? = null
    fun rememberAudio(channelId: String, language: String?) {}
}
