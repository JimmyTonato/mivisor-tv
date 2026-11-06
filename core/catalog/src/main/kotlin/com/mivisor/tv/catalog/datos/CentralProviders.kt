package com.mivisor.tv.catalog.datos

import com.google.gson.JsonParser
import com.mivisor.tv.catalog.Codes

class InvalidProviderCatalogException(message: String) : Exception(message)

/**
 * 3j · Catálogo central de proveedores (rama `providers` de mivisor-datos, proveedores.json):
 * `{ "schema_version": 1, "providers": [ { "id": "…", "name": "…" } ] }`.
 *
 * Reglas: `id` y `name` obligatorios; el id es texto (si llega como número se toma tal cual); los duplicados se
 * comparan sin mayúsculas (gana el primero); no se exigen más campos. Lo que no cumple se salta y se cuenta.
 */
object CentralProviders {
    const val SUPPORTED_SCHEMA = 1

    class Remote(
        /** Código tal como lo usa la app (normalizado como en la v18, en minúsculas). */
        val code: String,
        val name: String
    )

    class Parsed(val schema: Int, val providers: List<Remote>, val skipped: Int)

    fun parse(json: String): Parsed {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { throw InvalidProviderCatalogException("proveedores.json no es un JSON válido") }
        val schema = root.get("schema_version")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()
            ?: throw InvalidProviderCatalogException("proveedores.json no trae schema_version")
        if (schema > SUPPORTED_SCHEMA) throw InvalidProviderCatalogException("Actualiza MiVisor TV para leer este catálogo (v$schema)")
        val list = root.get("providers")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw InvalidProviderCatalogException("proveedores.json no trae la lista «providers»")
        val seen = HashSet<String>()
        val out = ArrayList<Remote>()
        var skipped = 0
        for (el in list) {
            val o = el.takeIf { it.isJsonObject }?.asJsonObject
            val rawId = o?.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            val name = o?.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            val code = Codes.normalizeOrNull(rawId)
            if (code == null || name.isNullOrEmpty() || !seen.add(code)) { skipped++; continue }
            out += Remote(code, name.take(40))
        }
        return Parsed(schema, out, skipped)
    }

    /** Un proveedor guardado en el aparato. [github] = vino del catálogo central (si no, es «Personal»). */
    class Local(val id: String, val name: String, val github: Boolean)

    enum class Mode {
        /** Al iniciar (interruptor activado): como [FULL], pero no vuelve a añadir los que borraste a mano. */
        AUTO,
        /** «Sincronizar ahora»: reproduce el catálogo: añade, renombra y quita los de GitHub que ya no están. */
        FULL,
        /** «Actualizar sin perder mis proveedores»: añade y renombra; no quita nada. */
        KEEP_MINE
    }

    class Add(val id: String, val code: String, val name: String)

    class Plan(
        val add: List<Add>,
        /** id → nombre nuevo (el central manda en los sincronizados). */
        val rename: Map<String, String>,
        /** Personales con un código que ahora está en GitHub: pasan a «GitHub». */
        val adopt: Set<String>,
        val remove: Set<String>,
        /** Ids que quedan como «GitHub» tras aplicar el plan. */
        val github: Set<String>,
        /** Borrados a mano que siguen en GitHub (para no volver a añadirlos al iniciar). */
        val dismissed: Set<String>
    ) {
        val changes: Int get() = add.size + rename.size + adopt.size + remove.size
    }

    /**
     * Qué cambiar. Los personales no se quitan nunca. [dismissed] = ids de GitHub que el usuario borró a mano
     * (solo los respeta [Mode.AUTO]; sincronizar a mano los vuelve a traer).
     */
    fun plan(remote: List<Remote>, local: List<Local>, mode: Mode, dismissed: Set<String>, idOf: (String) -> String): Plan {
        val byId = local.associateBy { it.id }
        val remoteIds = LinkedHashMap<String, Remote>()
        remote.forEach { r -> remoteIds.putIfAbsent(idOf(r.code), r) }
        val add = ArrayList<Add>()
        val rename = LinkedHashMap<String, String>()
        val adopt = LinkedHashSet<String>()
        for ((id, r) in remoteIds) {
            val l = byId[id]
            when {
                l == null -> if (mode != Mode.AUTO || id !in dismissed) add += Add(id, r.code, r.name)
                else -> {
                    if (!l.github) adopt += id
                    if (l.name != r.name) rename[id] = r.name
                }
            }
        }
        val remove = if (mode == Mode.KEEP_MINE) emptySet()
        else local.filter { it.github && it.id !in remoteIds }.map { it.id }.toSet()
        val github = (local.filter { it.github }.map { it.id }.toSet() - remove) + adopt + add.map { it.id }
        val stillDismissed = if (mode == Mode.AUTO) dismissed.filter { it in remoteIds && it !in byId }.toSet() else emptySet()
        return Plan(add, rename, adopt, remove, github.filter { it in remoteIds || mode == Mode.KEEP_MINE }.toSet(), stillDismissed)
    }
}
