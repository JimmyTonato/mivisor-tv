package com.mivisor.tv.catalog.datos

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.text.Normalizer

/**
 * 3j · Banners de competiciones (rama `banners` de mivisor-datos): `banners/<deporte>/<deporte>-<competicion>.webp`.
 *
 * El manifest.json se lee de forma flexible: vale cualquier JSON que nombre los ficheros (lista de rutas, objetos con
 * `path`/`file`/`url`…) y, si trae `sha256`/`sha`/`hash`/`size`/`updated`…, eso es su versión (cambia → se vuelve a
 * descargar; igual → no). Sin versión en el manifest, decide el ETag de GitHub.
 *
 * Emparejado competición → banner, prudente (mejor el fondo de siempre que el banner de otra competición):
 *  1. mismas palabras clave («Champions League» = `futbol-champions-league`);
 *  2. el banner contenido en el nombre («UFC 310» → `combate-ufc`; «Nitto ATP Finals» → `tenis-atp-finals`), salvo
 *     que lo que sobra cambie la competición («AFC Champions League», «Champions League femenina», «Sub-19»…);
 *  3. si no, el genérico del deporte (`futbol/futbol.webp`, `…-generico.webp`) si existe;
 *  4. si no, null: la app usa su fondo de siempre.
 */
class BannerCatalog(val entries: List<Entry>) {
    class Entry(
        /** Ruta dentro de la rama (`banners/futbol/futbol-champions-league.webp`) o URL completa. */
        val path: String,
        /** Carpeta de deporte (`futbol`, `motor`, `combate`, `tenis`…). */
        val folder: String,
        /** Nombre sin carpeta ni deporte: `champions-league`. Vacío = genérico del deporte. */
        val slug: String,
        /** Versión según el manifest (huella, tamaño, fecha…); null = no trae. */
        val version: String?
    ) {
        val generic: Boolean get() = slug.isEmpty() || slug in GENERIC
        internal val tokens: Set<String> = BannerCatalog.tokens(slug.replace('-', ' '))
    }

    val isEmpty: Boolean get() = entries.isEmpty()

    /** Banner para un evento de [sport] (id de la agenda: futbol, formula1, ufc…) y [competition] (texto de la agenda). */
    fun find(sport: String?, competition: String?): Entry? {
        val folders = FOLDERS[sport]
        val pool = entries.filter { folders == null || it.folder in folders }.ifEmpty { if (folders == null) entries else emptyList() }
        if (pool.isEmpty()) return null
        match(pool, competition)?.let { return it }
        SPORT_TEXT[sport]?.let { t -> match(pool, t)?.let { return it } }
        return pool.firstOrNull { it.generic && (folders == null || it.folder == folders.first()) } ?: pool.firstOrNull { it.generic }
    }

    private fun match(pool: List<Entry>, text: String?): Entry? {
        val comp = tokens(alias(text ?: return null))
        if (comp.isEmpty()) return null
        pool.firstOrNull { !it.generic && it.tokens == comp }?.let { return it }
        return pool.asSequence()
            .filter { !it.generic && it.tokens.isNotEmpty() }
            .filter { e ->
                val extraComp = comp - e.tokens
                val extraBanner = e.tokens - comp
                extraBanner.all { it in QUALIFIERS } && (e.tokens - QUALIFIERS).isNotEmpty() &&
                    (e.tokens - QUALIFIERS).all { it in comp } && extraComp.none { it in CHANGES_COMPETITION || it.matches(AGE) }
            }
            .maxWithOrNull(compareBy<Entry>({ (it.tokens intersect comp).size }, { -it.tokens.size }))
    }

    companion object {
        private val IMAGE = Regex("""(?i)\.(webp|jpe?g|png)$""")
        private val VERSION_KEYS = listOf("sha256", "sha", "sha1", "hash", "md5", "etag", "version", "updated", "updated_at", "updatedAt", "modified", "mtime", "size", "bytes")
        private val GENERIC = setOf("generico", "generica", "general", "default", "deporte", "fondo")
        /** Palabras que no cambian la competición (aparecen o no según quién escriba el nombre). */
        private val QUALIFIERS = setOf("uefa", "fia", "ea", "sports", "tennis", "italia", "espana", "spain", "de", "del", "la", "el", "the", "of", "y", "and",
            "fifa", "nitto", "rolex", "championship", "world", "mundial", "grand", "prix", "gp", "temporada", "season", "jornada", "round", "liga")
        /** Palabras que hacen que sea OTRA competición aunque compartan nombre. */
        private val CHANGES_COMPETITION = setOf("afc", "caf", "concacaf", "conmebol", "ofc", "women", "womens", "woman", "femenina", "femenino",
            "fem", "f", "youth", "juvenil", "u17", "u19", "u20", "u21", "u23", "sub", "junior", "juniors", "wta", "challenger", "qualifying",
            "qualifiers", "previa", "clasificacion", "legends", "futsal", "sala", "playa", "beach", "esports", "efootball", "amateur", "rfef", "2", "segunda")
        private val AGE = Regex("""^(u|sub)\d{2}$""")
        /** Carpetas de banners de cada deporte de la agenda (la primera es la suya). */
        private val FOLDERS = mapOf(
            "futbol" to listOf("futbol"), "tenis" to listOf("tenis"), "baloncesto" to listOf("baloncesto"),
            "formula1" to listOf("motor"), "motogp" to listOf("motor"), "motor" to listOf("motor"),
            "ufc" to listOf("combate"), "boxeo" to listOf("combate"), "combate" to listOf("combate")
        )
        /** Si el evento no trae competición (F1, MotoGP, UFC…), el propio deporte. */
        private val SPORT_TEXT = mapOf("formula1" to "formula 1", "motogp" to "motogp", "ufc" to "ufc")
        private val ALIASES = listOf(
            "liga de campeones" to "champions league", "champions" to "champions league",
            "liga europa" to "europa league", "conference" to "conference league",
            "primera division" to "laliga", "la liga" to "laliga", "segunda division" to "laliga hypermotion",
            "formula one" to "formula 1", "f1" to "formula 1", "moto gp" to "motogp",
            "abierto de australia" to "australian open", "open de australia" to "australian open",
            "abierto de estados unidos" to "us open", "u s open" to "us open",
            "liga de naciones" to "nations league", "supercopa de espana" to "supercopa de espana", "serie a tim" to "serie a",
            "ligue 1 mcdonald s" to "ligue 1", "ligue 1 uber eats" to "ligue 1"
        )

        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "").lowercase()
                .replace(Regex("""[^a-z0-9]+"""), " ").trim()

        private fun alias(text: String): String {
            var t = " " + normalize(text) + " "
            for ((from, to) in ALIASES) t = t.replace(" $from ", " $to ")
            // «champions league league» si ya decía «Champions League»
            return t.replace(" champions league league ", " champions league ").trim()
        }

        internal fun tokens(text: String): Set<String> =
            alias(text).split(' ').filter { it.isNotEmpty() && it !in setOf("de", "del", "la", "el", "the", "of", "y", "and") }.toSet()

        /** Lee el manifest (flexible). Lanza si no es JSON o no nombra ninguna imagen. */
        fun parse(json: String): BannerCatalog {
            val root = runCatching { JsonParser.parseString(json) }.getOrElse { throw IllegalArgumentException("manifest.json no es un JSON válido") }
            val found = LinkedHashMap<String, String?>()
            walk(root, found)
            if (found.isEmpty()) throw IllegalArgumentException("manifest.json no nombra ningún banner")
            return fromPaths(found)
        }

        fun fromPaths(paths: Map<String, String?>): BannerCatalog =
            BannerCatalog(paths.mapNotNull { (p, v) -> entry(p, v) }.distinctBy { it.path })

        private fun walk(e: JsonElement, out: MutableMap<String, String?>) {
            when {
                e.isJsonArray -> e.asJsonArray.forEach { walk(it, out) }
                e.isJsonObject -> {
                    val o = e.asJsonObject
                    val own = o.entrySet().firstOrNull { (_, v) -> v.isJsonPrimitive && v.asJsonPrimitive.isString && IMAGE.containsMatchIn(v.asString) }
                    if (own != null) {
                        val version = VERSION_KEYS.mapNotNull { k -> o.get(k)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }?.let { "$k=$it" } }
                            .joinToString("|").ifEmpty { null }
                        out[own.value.asString] = version
                    }
                    o.entrySet().forEach { (k, v) ->
                        // { "banners/futbol/x.webp": "sha…" } o { "x.webp": { "sha256": … } }
                        if (IMAGE.containsMatchIn(k)) {
                            out[k] = when {
                                v.isJsonPrimitive -> v.asString
                                v.isJsonObject -> VERSION_KEYS.mapNotNull { kk -> v.asJsonObject.get(kk)?.takeIf { it.isJsonPrimitive }?.asString?.let { "$kk=$it" } }.joinToString("|").ifEmpty { null }
                                else -> null
                            }
                        } else if (v !== own?.value) walk(v, out)
                    }
                }
                e.isJsonPrimitive && e.asJsonPrimitive.isString && IMAGE.containsMatchIn(e.asString) -> out.putIfAbsent(e.asString, null)
            }
        }

        private val SPORT_FOLDERS = setOf("futbol", "tenis", "baloncesto", "motor", "combate", "otros", "golf", "ciclismo", "rugby", "balonmano", "voleibol")

        private fun entry(raw: String, version: String?): Entry? {
            var p = raw.trim().replace('\\', '/')
            if (p.startsWith("http://")) return null
            val isUrl = p.startsWith("https://")
            if (!isUrl) {
                p = p.trimStart('.', '/')
                if (p.contains("..")) return null
                // «futbol/x.webp» (relativa a banners/) → «banners/futbol/x.webp»
                if (!p.startsWith("banners/") && p.substringBefore('/') in SPORT_FOLDERS) p = "banners/$p"
            }
            val segs = p.substringAfter("/banners/", p).removePrefix("banners/").split('/').filter { it.isNotEmpty() }
            val file = segs.lastOrNull() ?: return null
            val base = file.replace(IMAGE, "")
            val folder = if (segs.size >= 2) normalize(segs[segs.size - 2]).replace(' ', '-') else normalize(base.substringBefore('-'))
            val slug = normalize(base).replace(' ', '-').let { s -> if (s == folder) "" else s.removePrefix("$folder-") }
            return Entry(p, folder, slug, version)
        }
    }
}
