package com.mivisor.tv.playback

import com.mivisor.tv.reliability.SourceState

/** Una fuente reproducible de un canal (sin URLs: el Content ID solo se usa en memoria y enmascarado en registros). */
class PlaySource(
    val contentId: String,
    val providerId: String,
    val providerName: String,
    /** 0 desconocida, 1 SD, 2 HD, 3 FHD, 4 4K. */
    val quality: Int,
    val qualityLabel: String?,
    /**
     * 4f · Viene de la Biblioteca de MiVisor Gold: el orden ya lo decidió Gold (ids[0] es el mejor) y la app lo
     * respeta tal cual; si en ~20 s no hay imagen, pasa sola a la siguiente.
     */
    val fixedOrder: Boolean = false,
    /** 4f · Banda de Gold para el color: «verde», «medio», «rojo»; null = sin comprobar. */
    val band: String? = null,
    /** 4f · «A prueba»: acaba de volver tras dar imagen otra vez (válida; se enseña con un aviso discreto). */
    val onTrial: Boolean = false,
    /** 4f · SHA-1 del descriptor publicado por Gold, para compararlo con el del catálogo si no funciona. */
    val descriptorSha1: String? = null
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

    /** Ajustes › Audio y subtítulos: mostrar subtítulos al empezar si la fuente los trae. */
    fun subtitlesByDefault(): Boolean = false

    /**
     * 3e · Ajustes › Fuentes: cuánto se espera con la imagen cortada (mientras se veía) antes de pasar SOLO a otra
     * fuente. Mientras tanto se sigue recargando la misma; el usuario puede pasar a otra a mano. 0 = nunca sola.
     */
    fun autoSwitchAfterMs(): Long = 60_000L

    /** 3e · Volver solo al directo cuando la imagen se ha quedado atrás (tras un corte). false = el usuario decide. */
    fun autoLiveEdge(): Boolean = true

    /** 3i · ¿Calentar la siguiente fuente mientras la primera tarda? (no en aparatos con poca memoria) */
    fun prewarmNext(): Boolean = false
}
