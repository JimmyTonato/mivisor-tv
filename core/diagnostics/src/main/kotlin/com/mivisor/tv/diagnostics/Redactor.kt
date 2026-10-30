package com.mivisor.tv.diagnostics

/**
 * Redacción del registro y del informe de MiVisor TV (especificación de la fase 2). Se aplica ANTES de guardar:
 * en disco nunca hay un dato sensible.
 *
 *  - Content IDs e infohashes (40 hex) → 4 primeros y 4 últimos (`a1b2…f9e0`).
 *  - Tokens, claves y contraseñas en URL, cabeceras o JSON → `[oculto]`.
 *  - `magnet:`, `acestream://` y URL → solo el esquema (las de 127.0.0.1 se dejan: son el motor local).
 *  - IP públicas → `[ip]`; las de la red de casa → `192.168.x.x` (o `10.x.x.x`, `172.x.x.x`).
 *  - Códigos de proveedor y su dominio: los que se registran con [addSecret] se sustituyen por `[código]`.
 *  - Después, las reglas de [LogSanitizer] de la v18 para tokens largos y hexadecimales largos.
 */
class Redactor {
    private val secrets = LinkedHashSet<String>()

    /** Textos que nunca deben aparecer (códigos de proveedor, nombres de host del proveedor). */
    @Synchronized fun addSecret(value: String?) {
        val v = value?.trim().orEmpty()
        if (v.length >= 3) secrets += v
    }

    fun redact(text: String): String {
        var s = text
        val list = synchronized(this) { secrets.sortedByDescending { it.length } }
        for (secret in list) s = s.replace(secret, "[código]", ignoreCase = true)
        s = s.replace(KEY_VALUE) { m -> "${m.groupValues[1]}${m.groupValues[2]}[oculto]" }
        s = s.replace(AUTH) { m -> "${m.groupValues[1]} [oculto]" }
        s = s.replace(MAGNET, "magnet:[…]")
        s = s.replace(URL) { m ->
            val scheme = m.groupValues[1]
            val rest = m.value.substringAfter("://")
            if (rest.startsWith("127.0.0.1") || rest.startsWith("localhost")) maskIds(m.value) else "$scheme://[…]"
        }
        s = maskIds(s)
        s = s.replace(IPV4) { m -> maskIp(m.value) }
        s = s.replace(HEX16, "[oculto]")
        return s.replace(LONG_TOKEN) { m -> if (m.value.count { it.isDigit() } >= 2 && m.value.count { it.isLetter() } >= 2) "[oculto]" else m.value }
    }

    private fun maskIds(s: String) = s.replace(HEX40) { m -> m.value.take(4) + "…" + m.value.takeLast(4) }

    private fun maskIp(ip: String): String {
        val p = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || p.any { it > 255 }) return ip
        return when {
            p[0] == 127 -> ip
            p[0] == 192 && p[1] == 168 -> "192.168.x.x"
            p[0] == 10 -> "10.x.x.x"
            p[0] == 172 && p[1] in 16..31 -> "172.x.x.x"
            else -> "[ip]"
        }
    }

    companion object {
        private val HEX40 = Regex("""(?<![0-9a-fA-F])[0-9a-fA-F]{40}(?![0-9a-fA-F])""")
        private val KEY_VALUE = Regex("""(?i)\b(token|key|apikey|api_key|password|passwd|pass|secret|auth|signature|sig)(["']?\s*[=:]\s*["']?)[^\s"'&,;}]+""")
        private val AUTH = Regex("""(?i)\b(authorization:?\s*(?:bearer|basic)?)\s+\S+""")
        private val MAGNET = Regex("""(?i)magnet:\?[^\s"'<>]+""")
        private val URL = Regex("""\b([a-zA-Z][a-zA-Z0-9+.-]{1,15})://[^\s"'<>)]+""")
        private val HEX16 = Regex("""\b[0-9a-fA-F]{16,}\b""")
        private val LONG_TOKEN = Regex("""\b[A-Za-z0-9_\-+/=]{24,}\b""")
        private val IPV4 = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""")
    }
}
