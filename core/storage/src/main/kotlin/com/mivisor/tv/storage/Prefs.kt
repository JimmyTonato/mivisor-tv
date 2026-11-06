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

    /** 3f · Posición del menú: "top" (barra arriba, por defecto) o "side" (rail lateral). En el móvil siempre lateral. */
    var navPosition: String
        get() = sp.getString("nav_position", "top") ?: "top"
        set(v) = sp.edit().putString("nav_position", v).apply()

    /** 3f · Tipo de pantalla: "auto" (se detecta), "tv", "tablet" o "phone". */
    var deviceClass: String
        get() = sp.getString("device_class", "auto") ?: "auto"
        set(v) = sp.edit().putString("device_class", v).apply()

    /** 3f · Competiciones seguidas (nombre tal como lo trae la agenda), en orden de alta. */
    var followedCompetitions: List<String>
        get() = (sp.getString("followed_competitions", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("followed_competitions", v.distinct().joinToString("\n")).apply()

    /** 3g · Competiciones ocultas (ids "raw:…" de [com.mivisor.tv.model.CompetitionNames]): no salen en ninguna pantalla. */
    var hiddenCompetitions: List<String>
        get() = (sp.getString("hidden_competitions", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("hidden_competitions", v.distinct().joinToString("\n")).apply()

    /** 3i · Preparar el canal enfocado (y la siguiente fuente si la primera tarda). */
    var prewarm: Boolean
        get() = sp.getBoolean("prewarm", true)
        set(v) = sp.edit().putBoolean("prewarm", v).apply()

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

    /** 3e · Con la imagen cortada, pasar sola a otra fuente tras: "60", "120" segundos o "never" (solo a mano). */
    var autoSwitch: String
        get() = sp.getString("auto_switch", "60") ?: "60"
        set(v) = sp.edit().putString("auto_switch", v).apply()

    /** 3e · Volver solo al directo cuando la imagen se queda atrás. */
    var autoLive: Boolean
        get() = sp.getBoolean("auto_live", true)
        set(v) = sp.edit().putBoolean("auto_live", v).apply()

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
        "agenda_url" to agendaUrl, "show_source_info" to showSourceInfo.toString(),
        // 3f (el tipo de pantalla NO: depende de cada aparato).
        "nav_position" to navPosition, "followed_competitions" to followedCompetitions.joinToString("|"),
        "hidden_competitions" to hiddenCompetitions.joinToString("|"),
        "auto_switch" to autoSwitch, "auto_live" to autoLive.toString()
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
        settings["nav_position"]?.takeIf { it in setOf("top", "side") }?.let { navPosition = it }
        // 3g · Fijas (LALIGA…) o reales ("raw:copa libertadores"); separadas por «|» (las copias de la 3f, por comas).
        val compId = Regex("[A-Z0-9_]{1,40}|raw:[a-z0-9 :]{1,80}")
        settings["followed_competitions"]?.let { v -> followedCompetitions = v.split('|', ',').map { it.trim() }.filter { it.matches(compId) } }
        settings["hidden_competitions"]?.let { v -> hiddenCompetitions = v.split('|').map { it.trim() }.filter { it.matches(compId) && it.startsWith("raw:") } }
        settings["auto_switch"]?.takeIf { it in setOf("60", "120", "never") }?.let { autoSwitch = it }
        settings["auto_live"]?.let { autoLive = it == "true" }
    }

    // ------------------------------------------------------------------ 3j: catálogo central de proveedores

    /** Actualizar los proveedores desde GitHub (rama providers) al abrir la app. */
    var providersAutoSync: Boolean
        get() = sp.getBoolean("providers_auto_sync", true)
        set(v) = sp.edit().putBoolean("providers_auto_sync", v).apply()

    /** Ya se hizo la primera carga del catálogo central (se hace una vez aunque el interruptor esté apagado). */
    var providersSeeded: Boolean
        get() = sp.getBoolean("providers_seeded", false)
        set(v) = sp.edit().putBoolean("providers_seeded", v).apply()

    /** Ids de los proveedores que vienen de GitHub (el resto son «Personal»). */
    var githubProviders: Set<String>
        get() = sp.getStringSet("github_providers", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("github_providers", v).apply()

    /** De GitHub pero borrados a mano: al abrir la app no se vuelven a añadir (sincronizar a mano sí). */
    var dismissedProviders: Set<String>
        get() = sp.getStringSet("dismissed_providers", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("dismissed_providers", v).apply()

    var providersSyncedAt: Long
        get() = sp.getLong("providers_synced_at", 0L)
        set(v) = sp.edit().putLong("providers_synced_at", v).apply()

    var providersSyncNote: String
        get() = sp.getString("providers_sync_note", "") ?: ""
        set(v) = sp.edit().putString("providers_sync_note", v).apply()

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
