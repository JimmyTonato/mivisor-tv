package com.mivisor.tv.catalog.datos

/**
 * 3n · «Abrir un ID»: un enlace o ID de Ace Stream que te pasa alguien y que no está en tus canales.
 *
 * Acepta lo que se suele copiar: el ID de 40 caracteres tal cual, `acestream://ID`, enlaces de reproductores con
 * `?id=ID`, `content_id=ID` o `/getstream?id=ID`, y texto con el ID en medio («mira este: acestream://… 👍»).
 * Nunca guarda nada de proveedores: es solo un ID suelto.
 */
object DirectStream {
    const val PREFIX = "direct:"
    private val HEX40 = Regex("""(?i)(?<![0-9a-f])([0-9a-f]{40})(?![0-9a-f])""")

    /** El ID (40 hex, en minúsculas) o null si el texto no trae ninguno. */
    fun parse(text: String?): String? = text?.let { HEX40.find(it)?.groupValues?.get(1)?.lowercase() }

    fun channelId(contentId: String) = PREFIX + contentId
    fun contentIdOf(channelId: String): String? = channelId.removePrefix(PREFIX).takeIf { channelId.startsWith(PREFIX) && parse(it) == it }

    /**
     * 4h · Historial de «Abrir un ID» (líneas «id\tnombre»): pone [name] al ID [contentId] si aún no tenía nombre (el
     * que escribió el usuario manda). Si no estaba, se añade el primero. Devuelve la lista nueva, o null si no cambia.
     */
    fun nameRecent(recent: List<String>, contentId: String, name: String): List<String>? {
        val clean = name.replace('\t', ' ').trim().take(40).takeIf { it.isNotEmpty() && it != "-" } ?: return null
        val i = recent.indexOfFirst { it.substringBefore('\t') == contentId }
        if (i < 0) return listOf(contentId + "\t" + clean) + recent
        if (recent[i].substringAfter('\t', "").isNotBlank()) return null
        return recent.toMutableList().also { it[i] = contentId + "\t" + clean }
    }

    /** Título del descriptor en la línea del motor «infohash=… firmado=… pieza=… titulo=…» (parche 0003). */
    fun titleOfDescriptorLine(line: String?): String? =
        line?.substringAfter("titulo=", "")?.trim()?.takeIf { it.isNotEmpty() && it != "-" }

    /** «…a1b2c3» para mostrarlo sin ocupar media pantalla. */
    fun short(contentId: String) = "…" + contentId.takeLast(6)
}
