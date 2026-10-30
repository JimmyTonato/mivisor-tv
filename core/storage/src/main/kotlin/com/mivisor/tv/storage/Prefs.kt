package com.mivisor.tv.storage

import android.content.Context
import android.content.SharedPreferences

/** Ajustes sencillos (SharedPreferences privadas). */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.applicationContext.getSharedPreferences("mivisor_tv", Context.MODE_PRIVATE)

    var wizardDone: Boolean
        get() = sp.getBoolean("wizard_done", false)
        set(v) = sp.edit().putBoolean("wizard_done", v).apply()

    /** Vacío = la URL de la agenda incluida en la compilación (mivisor-datos). */
    var agendaUrl: String
        get() = sp.getString("agenda_url", "") ?: ""
        set(v) = sp.edit().putString("agenda_url", v.trim()).apply()

    /** Modo ligero: sin animaciones ni vista previa (TVs con 1 GB). */
    var lite: Boolean
        get() = sp.getBoolean("lite", false)
        set(v) = sp.edit().putBoolean("lite", v).apply()

    /** Verificador automático de fuentes: DESACTIVADO por defecto (y sin implementar en 3a). */
    var verifier: Boolean
        get() = sp.getBoolean("verifier", false)
        set(v) = sp.edit().putBoolean("verifier", v).apply()

    /** Mostrar la información de la fuente al empezar a ver un canal. */
    var showSourceInfo: Boolean
        get() = sp.getBoolean("show_source_info", true)
        set(v) = sp.edit().putBoolean("show_source_info", v).apply()

    /** Proveedores que necesitan WARP (ids). Si WARP no está conectada, sus fallos no penalizan a las fuentes. */
    var warpProviders: Set<String>
        get() = sp.getStringSet("warp_providers", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("warp_providers", v).apply()

    /** Idioma de audio elegido por canal (multiaudio). */
    fun audioFor(channelId: String): String? = sp.getString("audio:" + channelId, null)

    fun setAudio(channelId: String, language: String?) {
        sp.edit().apply { if (language == null) remove("audio:" + channelId) else putString("audio:" + channelId, language) }.apply()
    }

    // ------------------------------------------------------------------ 3d: ajustes que faltaban

    /** Menú: "top" (arriba, por defecto) o "side" (lateral que se despliega). */
    var navStyle: String
        get() = sp.getString("nav_style", "top") ?: "top"
        set(v) = sp.edit().putString("nav_style", v).apply()

    /** Texto un 15 % más grande. */
    var bigText: Boolean
        get() = sp.getBoolean("big_text", false)
        set(v) = sp.edit().putBoolean("big_text", v).apply()

    /** Al abrir MiVisor TV: "home", "agenda", "channels" o "last" (último canal). */
    var startScreen: String
        get() = sp.getString("start_screen", "home") ?: "home"
        set(v) = sp.edit().putString("start_screen", v).apply()

    /** Idioma de audio preferido (código ISO, p. ej. "es"); vacío = el de la emisión. */
    var audioLanguage: String
        get() = sp.getString("audio_language", "es") ?: "es"
        set(v) = sp.edit().putString("audio_language", v).apply()

    var subtitles: Boolean
        get() = sp.getBoolean("subtitles", false)
        set(v) = sp.edit().putBoolean("subtitles", v).apply()

    /** Tamaño de los subtítulos: "normal", "grande" o "muy-grande". */
    var subtitleSize: String
        get() = sp.getString("subtitle_size", "grande") ?: "grande"
        set(v) = sp.edit().putString("subtitle_size", v).apply()

    var rememberAudio: Boolean
        get() = sp.getBoolean("remember_audio", true)
        set(v) = sp.edit().putBoolean("remember_audio", v).apply()

    /** Verificador (3d): solo manual y con la app abierta; estas opciones afectan a «Comprobar ahora». */
    var verifierFavoritesOnly: Boolean
        get() = sp.getBoolean("verifier_favs", true)
        set(v) = sp.edit().putBoolean("verifier_favs", v).apply()

    var verifierLast: String
        get() = sp.getString("verifier_last", "") ?: ""
        set(v) = sp.edit().putString("verifier_last", v).apply()

    /** Ajustes copiables a otra tele (copia cifrada): solo preferencias, nunca datos del aparato. */
    fun exportable(): Map<String, String> = mapOf(
        "lite" to lite.toString(), "nav_style" to navStyle, "big_text" to bigText.toString(), "start_screen" to startScreen,
        "audio_language" to audioLanguage, "subtitles" to subtitles.toString(), "subtitle_size" to subtitleSize,
        "remember_audio" to rememberAudio.toString(), "engine" to engine, "ace_fallback" to aceFallback,
        "agenda_url" to agendaUrl, "show_source_info" to showSourceInfo.toString()
    )

    fun import(settings: Map<String, String>) {
        settings["lite"]?.let { lite = it == "true" }
        settings["nav_style"]?.takeIf { it in setOf("top", "side") }?.let { navStyle = it }
        settings["big_text"]?.let { bigText = it == "true" }
        settings["start_screen"]?.takeIf { it in setOf("home", "agenda", "channels", "last") }?.let { startScreen = it }
        settings["audio_language"]?.let { audioLanguage = it.take(8) }
        settings["subtitles"]?.let { subtitles = it == "true" }
        settings["subtitle_size"]?.takeIf { it in setOf("normal", "grande", "muy-grande") }?.let { subtitleSize = it }
        settings["remember_audio"]?.let { rememberAudio = it == "true" }
        settings["engine"]?.takeIf { it in setOf("outpace", "ace") }?.let { engine = it }
        settings["ace_fallback"]?.takeIf { it in setOf("ask", "auto", "never") }?.let { aceFallback = it }
        settings["agenda_url"]?.takeIf { it.isBlank() || it.startsWith("https://") }?.let { agendaUrl = it }
        settings["show_source_info"]?.let { showSourceInfo = it == "true" }
    }

    /** Motor: "outpace" (por defecto) o "ace" (app de Ace Stream). */
    var engine: String
        get() = sp.getString("engine", "outpace") ?: "outpace"
        set(v) = sp.edit().putString("engine", v).apply()

    /** Si Outpace no da imagen: "ask" (preguntar), "auto" (abrir Ace Stream) o "never". */
    var aceFallback: String
        get() = sp.getString("ace_fallback", "ask") ?: "ask"
        set(v) = sp.edit().putString("ace_fallback", v).apply()

    /** Último canal visto y el anterior (para «Canal anterior»). */
    var lastChannel: String?
        get() = sp.getString("last_channel", null)
        set(v) = sp.edit().putString("last_channel", v).apply()

    var previousChannel: String?
        get() = sp.getString("previous_channel", null)
        set(v) = sp.edit().putString("previous_channel", v).apply()
}
