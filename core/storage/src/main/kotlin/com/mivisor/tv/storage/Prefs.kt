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
