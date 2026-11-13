package com.mivisor.tv.catalog.biblioteca

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 4f · Biblioteca de canales que publica MiVisor Gold (formato «mivisor.biblioteca/2»), ya descifrada.
 *
 * La app NO ordena, puntúa ni filtra: respeta «orden» de canales y categorías y el orden de `ids[]` (ids[0] es el
 * mejor). Cada registro guarda su línea original ([raw]) para volver a escribirla tal cual y para saber si cambió.
 */
class Manifest(
    val formato: String,
    val revision: Long,
    val publicado: String?,
    val cambiosDesde: Long?,
    val iteraciones: Int,
    val canales: Int?,
    val ids: Int?,
    val categorias: Int?
) {
    companion object {
        const val FORMATO = "mivisor.biblioteca/2"

        /** null si no es un manifest válido de este formato. */
        fun parse(text: String): Manifest? = runCatching {
            val o = JsonParser.parseString(text).asJsonObject
            val formato = o.str("formato") ?: return null
            if (formato != FORMATO) return null
            val cifrado = o.obj("cifrado")
            if (cifrado != null && cifrado.str("alg") != null && cifrado.str("alg") != "AES-256-GCM") return null
            val totales = o.obj("totales")
            Manifest(
                formato,
                o.long("revision") ?: return null,
                o.str("publicado"),
                o.long("cambios_desde"),
                cifrado?.long("iteraciones")?.toInt() ?: 200_000,
                totales?.long("canales")?.toInt(), totales?.long("ids")?.toInt(), totales?.long("categorias")?.toInt()
            )
        }.getOrNull()
    }
}

class Categoria(
    val id: String,
    val name: String,
    val parentId: String?,
    val kind: String?,
    val orden: Int,
    val raw: String
)

class Audio(val codec: String?, val idioma: String?, val canales: String?)

class Calidad(
    val etiqueta: String?,
    val clase: String?,
    val resolucion: String?,
    val fps: Double?,
    val codec: String?,
    val bitrateTotal: Long?,
    val audios: List<Audio>,
    val declaradaVsReal: String?
) {
    /** 0 desconocida, 1 SD, 2 HD, 3 Full HD, 4 4K (lo que usa la app para la insignia). */
    val rank: Int get() = when (clase?.trim()?.lowercase()) {
        "4k", "uhd" -> 4
        "full hd", "fullhd", "fhd" -> 3
        "hd" -> 2
        "sd" -> 1
        else -> 0
    }
}

class Descriptor(val base64: String, val sha1: String?, val pedido: String?)

class Verificacion(
    val nota: Int?,
    val banda: String?,
    val comprobado: String?,
    val primeraPiezaS: Double?,
    val avisos: List<String>,
    val info: List<String>,
    val calidad: Calidad?,
    val emision: String?,
    val descriptor: Descriptor?
)

class GoldId(
    val assetId: String,
    /** null = normal; «a_prueba» = acaba de volver tras dar imagen otra vez. */
    val lista: String?,
    val verificacion: Verificacion?
) {
    val onTrial: Boolean get() = lista == "a_prueba"
}

class Canal(
    val channelId: String,
    val name: String,
    val categoryId: String?,
    val orden: Int,
    val ids: List<GoldId>,
    val raw: String
)

/** Registros por id, en el orden en que llegaron (el de pantalla lo da «orden»). */
class Biblioteca(
    val revision: Long,
    val resumenRaw: String?,
    val categorias: Map<String, Categoria>,
    val canales: Map<String, Canal>
) {
    val isEmpty: Boolean get() = canales.isEmpty()

    /** JSON Lines para guardar en el aparato: resumen, categorías y canales, tal como llegaron. */
    fun toJsonLines(): String = buildString {
        append("{\"tipo\":\"local\",\"revision\":").append(revision).append("}\n")
        resumenRaw?.let { append(it).append('\n') }
        categorias.values.forEach { append(it.raw).append('\n') }
        canales.values.forEach { append(it.raw).append('\n') }
    }

    companion object {
        val EMPTY = Biblioteca(0, null, emptyMap(), emptyMap())
    }
}

/** Una línea del fichero de cambios. [datos] = null en las bajas. */
class Cambio(val rev: Long, val op: String, val tipo: String, val id: String, val datos: JsonObject?)

/** Resultado de aplicar cambios: la biblioteca nueva y qué ids tocaron (para repintar solo eso). */
class Aplicado(val biblioteca: Biblioteca, val canales: Set<String>, val categorias: Set<String>)

class BibliotecaFormatException(msg: String) : Exception(msg)

object BibliotecaParser {
    private val HEX40 = Regex("^[0-9a-fA-F]{40}$")

    /** Fichero completo (JSON Lines). La primera línea es el resumen. Las líneas que no se entienden se saltan. */
    fun parseFull(text: String): Biblioteca {
        var revision = -1L
        var local = -1L
        var resumen: String? = null
        val cats = LinkedHashMap<String, Categoria>()
        val chans = LinkedHashMap<String, Canal>()
        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val o = runCatching { JsonParser.parseString(t).asJsonObject }.getOrNull() ?: continue
            when (o.str("tipo")) {
                "resumen" -> { revision = o.long("revision") ?: revision; resumen = t }
                // Copia guardada en el aparato: la revisión que se aplicó por última vez (con cambios, el resumen se queda viejo).
                "local" -> local = o.long("revision") ?: local
                "categoria" -> categoria(o, t)?.let { cats[it.id] = it }
                "canal" -> canal(o, t)?.let { chans[it.channelId] = it }
            }
        }
        if (local >= 0) revision = local
        if (revision < 0) throw BibliotecaFormatException("falta el resumen con la revisión")
        return Biblioteca(revision, resumen, cats, chans)
    }

    /** Fichero de cambios: cada línea {"rev","op","tipo","id","datos"?}. Ordenadas por rev. */
    fun parseChanges(text: String): List<Cambio> = text.lineSequence().mapNotNull { line ->
        val t = line.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val o = runCatching { JsonParser.parseString(t).asJsonObject }.getOrNull() ?: return@mapNotNull null
        val rev = o.long("rev") ?: return@mapNotNull null
        val op = o.str("op") ?: return@mapNotNull null
        val tipo = o.str("tipo") ?: return@mapNotNull null
        val id = o.str("id") ?: return@mapNotNull null
        if (op !in setOf("alta", "cambio", "baja") || tipo !in setOf("canal", "categoria")) return@mapNotNull null
        Cambio(rev, op, tipo, id, o.obj("datos"))
    }.sortedBy { it.rev }.toList()

    /**
     * Aplica, en orden, los cambios con rev > [base].revision y deja la biblioteca en [toRevision]. Un «alta»/«cambio»
     * sin datos válidos es un error (no se guarda nada a medias): lanza [BibliotecaFormatException].
     */
    fun apply(base: Biblioteca, changes: List<Cambio>, toRevision: Long): Aplicado {
        val cats = LinkedHashMap(base.categorias)
        val chans = LinkedHashMap(base.canales)
        val touchedCh = HashSet<String>()
        val touchedCat = HashSet<String>()
        for (c in changes) {
            if (c.rev <= base.revision) continue
            when (c.op) {
                "baja" -> if (c.tipo == "canal") { chans.remove(c.id); touchedCh += c.id } else { cats.remove(c.id); touchedCat += c.id }
                else -> {
                    val d = c.datos ?: throw BibliotecaFormatException("cambio ${c.rev} sin datos")
                    val raw = d.toString()
                    if (c.tipo == "canal") {
                        val ch = canal(d, raw) ?: throw BibliotecaFormatException("canal ${c.id} no válido en ${c.rev}")
                        if (ch.channelId != c.id) chans.remove(c.id)
                        chans[ch.channelId] = ch; touchedCh += ch.channelId
                    } else {
                        val cat = categoria(d, raw) ?: throw BibliotecaFormatException("categoría ${c.id} no válida en ${c.rev}")
                        if (cat.id != c.id) cats.remove(c.id)
                        cats[cat.id] = cat; touchedCat += cat.id
                    }
                }
            }
        }
        return Aplicado(Biblioteca(toRevision, base.resumenRaw, cats, chans), touchedCh, touchedCat)
    }

    private fun categoria(o: JsonObject, raw: String): Categoria? {
        val id = o.str("id") ?: return null
        val name = o.str("name") ?: return null
        return Categoria(id, name, o.str("parent_id"), o.str("kind"), o.long("orden")?.toInt() ?: Int.MAX_VALUE, raw)
    }

    private fun canal(o: JsonObject, raw: String): Canal? {
        val id = o.str("channel_id") ?: return null
        val name = o.str("name") ?: return null
        val ids = o.arr("ids")?.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject?.let(::goldId) }.orEmpty()
            .distinctBy { it.assetId }
        return Canal(id, name, o.str("category_id"), o.long("orden")?.toInt() ?: Int.MAX_VALUE, ids, raw)
    }

    private fun goldId(o: JsonObject): GoldId? {
        val asset = o.str("asset_id")?.trim()?.takeIf { HEX40.matches(it) }?.lowercase() ?: return null
        return GoldId(asset, o.str("lista"), o.obj("verificacion")?.let(::verificacion))
    }

    private fun verificacion(o: JsonObject): Verificacion = Verificacion(
        nota = o.long("nota")?.toInt()?.coerceIn(0, 100),
        banda = o.str("banda"),
        comprobado = o.str("comprobado"),
        primeraPiezaS = o.double("primera_pieza_s"),
        avisos = o.strings("avisos"),
        info = o.strings("info"),
        calidad = o.obj("calidad")?.let(::calidad),
        emision = o.str("emision")?.trim()?.takeIf { HEX40.matches(it) }?.lowercase(),
        descriptor = o.obj("descriptor")?.let { d -> d.str("base64")?.takeIf { it.isNotBlank() }?.let { Descriptor(it, d.str("sha1"), d.str("pedido")) } }
    )

    private fun calidad(o: JsonObject): Calidad = Calidad(
        etiqueta = o.str("etiqueta"),
        clase = o.str("clase"),
        resolucion = o.get("resolucion")?.let { if (it.isJsonPrimitive) it.asString else it.toString() },
        fps = o.double("fps"),
        codec = o.str("codec"),
        bitrateTotal = o.long("bitrate_total"),
        audios = o.arr("audios")?.mapNotNull { el ->
            el.takeIf { it.isJsonObject }?.asJsonObject?.let { a -> Audio(a.str("codec"), a.str("idioma"), a.str("canales")) }
        }.orEmpty(),
        declaradaVsReal = o.get("declarada_vs_real")?.let { if (it.isJsonPrimitive) it.asString else it.toString() }
    )
}

// ------------------------------------------------------------------ lectura tolerante

internal fun JsonObject.prim(k: String): JsonElement? = get(k)?.takeIf { it.isJsonPrimitive }
internal fun JsonObject.str(k: String): String? = prim(k)?.asString?.takeIf { it.isNotEmpty() }
internal fun JsonObject.long(k: String): Long? = prim(k)?.let { runCatching { it.asLong }.getOrNull() ?: runCatching { it.asDouble.toLong() }.getOrNull() }
internal fun JsonObject.double(k: String): Double? = prim(k)?.let { runCatching { it.asDouble }.getOrNull() }
internal fun JsonObject.obj(k: String): JsonObject? = get(k)?.takeIf { it.isJsonObject }?.asJsonObject
internal fun JsonObject.arr(k: String) = get(k)?.takeIf { it.isJsonArray }?.asJsonArray
internal fun JsonObject.strings(k: String): List<String> =
    arr(k)?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } }.orEmpty()
