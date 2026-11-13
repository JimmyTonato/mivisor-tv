package com.mivisor.tv.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * 4f · Con qué clave se abre cada Content ID en el motor.
 *
 * Por defecto «cid:<id>» (el motor pide el descriptor al catálogo). Si MiVisor Gold publicó el descriptor del ID, se
 * abre como «tf-<descriptor en base64url>» (parche 0010 de Outpace): el motor no tiene que pedirlo, solo pregunta la
 * emisión actual. Si esa forma falla al arrancar, se marca y desde entonces ese ID vuelve a «cid:» (el camino de
 * siempre), así la app sigue funcionando aunque Gold o el descriptor fallen.
 */
object EngineKeys {
    /** Un descriptor de Ace Stream ocupa 1–3 KB; por encima de esto no se mete en la URL. */
    const val MAX_INLINE_CHARS = 16 * 1024
    private val VALID = Regex("^[A-Za-z0-9_-]+$")

    private val inline = ConcurrentHashMap<String, String>()
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Sustituye todos los descriptores conocidos (Content ID → base64 estándar o url-safe). */
    fun replaceAll(descriptors: Map<String, String>) {
        val next = HashMap<String, String>()
        for ((cid, b64) in descriptors) toInlineKey(b64)?.let { next[norm(cid)] = it }
        // Un descriptor que falló sigue apartado mientras sea el mismo; uno distinto merece otra oportunidad.
        val before = HashMap(inline)
        failed.retainAll { c -> next[c] != null && next[c] == before[c] }
        inline.keys.retainAll(next.keys)
        inline.putAll(next)
    }

    fun clear() {
        inline.clear()
        failed.clear()
    }

    /** Clave del motor para este Content ID. */
    fun keyFor(contentId: String): String {
        val c = norm(contentId)
        return inline[c]?.takeIf { c !in failed } ?: "cid:$c"
    }

    /** ¿Se está abriendo con el descriptor publicado? */
    fun usingInline(contentId: String): Boolean = norm(contentId).let { inline.containsKey(it) && it !in failed }

    /** El descriptor publicado no sirvió: desde ahora, «cid:». true si se cambió algo. */
    fun dropInline(contentId: String): Boolean {
        val c = norm(contentId)
        return inline.containsKey(c) && failed.add(c)
    }

    /** base64 (estándar o url-safe, con o sin relleno) → «tf-<base64url sin relleno>»; null si no vale. */
    fun toInlineKey(base64: String): String? {
        val url = base64.filterNot { it.isWhitespace() || it == '=' }.replace('+', '-').replace('/', '_')
        if (url.length < 16 || url.length > MAX_INLINE_CHARS || !VALID.matches(url) || url.length % 4 == 1) return null
        return "tf-$url"
    }

    private fun norm(cid: String) = cid.trim().lowercase()
}
