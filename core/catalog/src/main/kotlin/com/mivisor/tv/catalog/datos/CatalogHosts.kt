package com.mivisor.tv.catalog.datos

import com.google.gson.JsonParser

/**
 * 3o · Servidores del catálogo de Ace Stream (donde un ID se convierte en su descriptor), publicados en la rama
 * `catalogo` de mivisor-datos (`catalog-hosts.json`) para poder cambiarlos sin sacar otra APK:
 *
 * ```json
 * { "schema_version": 1, "updated": "2026-11-08",
 *   "hosts": [ { "host": "51.158.152.5", "port": 8081, "note": "visto el 08-11-2026" } ] }
 * ```
 *
 * Solo IPv4 públicas (sin nombres: no dependen del DNS de la operadora), puerto 1-65535, sin repetidos, en el orden
 * del fichero (el primero se prueba antes) y como mucho 16. `"enabled": false` deja uno apuntado sin usarlo.
 */
object CatalogHosts {
    const val MAX = 16

    class Parsed(val hosts: List<String>, val skipped: Int, val updated: String?)

    fun parse(json: String): Parsed {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { throw IllegalArgumentException("catalog-hosts.json no es un JSON válido") }
        val schema = root.get("schema_version")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()
            ?: throw IllegalArgumentException("catalog-hosts.json no trae schema_version")
        if (schema > 1) throw IllegalArgumentException("Actualiza MiVisor TV para leer esta lista (v$schema)")
        val list = root.get("hosts")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw IllegalArgumentException("catalog-hosts.json no trae la lista «hosts»")
        val out = ArrayList<String>()
        var skipped = 0
        for (el in list) {
            val o = el.takeIf { it.isJsonObject }?.asJsonObject
            if (o?.get("enabled")?.takeIf { it.isJsonPrimitive }?.asString == "false") continue
            val host = o?.get("host")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            val port = o?.get("port")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull() ?: 8081
            val entry = host?.let { valid(it, port) }
            if (entry == null || entry in out || out.size >= MAX) { skipped++; continue }
            out += entry
        }
        if (out.isEmpty()) throw IllegalArgumentException("catalog-hosts.json no trae ningún servidor válido")
        val updated = root.get("updated")?.takeIf { it.isJsonPrimitive }?.asString
        return Parsed(out, skipped, updated)
    }

    /** «ip:puerto» si es una IPv4 pública y un puerto válido. */
    fun valid(host: String, port: Int): String? {
        if (port !in 1..65535) return null
        val parts = host.split('.')
        if (parts.size != 4) return null
        val b = parts.map { p -> p.toIntOrNull()?.takeIf { it in 0..255 && (p == "0" || !p.startsWith("0")) } ?: return null }
        val private = b[0] == 10 || b[0] == 127 || b[0] == 0 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) ||
            (b[0] == 169 && b[1] == 254) || b[0] >= 224 || (b[0] == 100 && b[1] in 64..127)
        return if (private) null else "${b.joinToString(".")}:$port"
    }
}
