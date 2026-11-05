package com.mivisor.tv.catalog.tv

import com.mivisor.tv.catalog.ChannelGrouping
import com.mivisor.tv.catalog.logos.ChannelCategory

/**
 * Familias de Canales del diseño 06 (Proveedor → familia → canal). Se deducen del nombre con listas revisadas
 * (nada de algoritmos agresivos): las familias sin canales no se muestran.
 */
enum class ChannelFamily(val id: String, val label: String) {
    FOOTBALL("futbol", "Fútbol"),
    MOTOR("motor", "Motor"),
    SPORTS("deportes", "Deportes"),
    MOVIES("cine", "Cine y series"),
    GENERAL("general", "Generalistas"),
    NEWS("noticias", "Noticias"),
    DOCS("documentales", "Documentales"),
    KIDS("infantil", "Infantil"),
    MUSIC("musica", "Música"),
    OTHER("otros", "Otros");

    companion object {
        fun of(id: String?): ChannelFamily? = values().firstOrNull { it.id == id }
    }
}

object ChannelFamilies {
    /** Palabras (ya normalizadas: minúsculas, sin tildes) que hacen un canal de fútbol. */
    private val FOOTBALL = listOf(
        "laliga", "liga", "futbol", "football", "soccer", "champions", "campeones", "premier", "bundesliga", "serie a",
        "ligue", "calcio", "uefa", "europa league", "conference", "copa", "fifa", "gol", "goles", "rfef", "federacion",
        "hypermotion", "eredivisie", "real madrid", "barca", "atletico", "athletic", "betis", "sevilla", "valencia cf",
        "villarreal", "real sociedad", "celta", "osasuna", "rayo", "espanyol", "mundial", "supercopa", "mls"
    )
    private val MOTOR = listOf(
        "f1", "formula", "motogp", "moto gp", "moto", "motor", "racing", "rally", "nascar", "indycar", "superbike",
        "wrc", "dakar", "motorsport", "le mans"
    )

    /** Familia de un canal por su nombre y su tipo (deportes, cine…). */
    fun of(name: String, category: ChannelCategory?): ChannelFamily {
        val n = " " + ChannelGrouping.key(name).replace('-', ' ') + " "
        fun has(words: List<String>) = words.any { w -> if (w.length <= 3 || ' ' in w) n.contains(" $w ") else n.contains(w) }
        return when {
            has(MOTOR) -> ChannelFamily.MOTOR
            has(FOOTBALL) -> ChannelFamily.FOOTBALL
            category == ChannelCategory.SPORTS -> ChannelFamily.SPORTS
            category == ChannelCategory.MOVIES -> ChannelFamily.MOVIES
            category == ChannelCategory.GENERAL -> ChannelFamily.GENERAL
            category == ChannelCategory.NEWS -> ChannelFamily.NEWS
            category == ChannelCategory.DOCS -> ChannelFamily.DOCS
            category == ChannelCategory.KIDS -> ChannelFamily.KIDS
            category == ChannelCategory.MUSIC -> ChannelFamily.MUSIC
            else -> ChannelFamily.OTHER
        }
    }
}

/** Orden natural: «DAZN 2» antes que «DAZN 10»; sin distinguir mayúsculas ni tildes. */
object NaturalOrder : Comparator<String> {
    private val CHUNK = Regex("[0-9]+|[^0-9]+")

    // 3f.1 · Orden TOTAL y coherente (si no, Android aborta la ordenación con «Comparison method violates its general
    // contract» con nombres raros: dígitos árabes, superíndices…). Solo cuentan como número los dígitos 0-9 que
    // separa la expresión; el resto se compara como texto y, en caso de empate, decide el texto completo.
    override fun compare(a: String, b: String): Int {
        val fa = fold(a)
        val fb = fold(b)
        val x = CHUNK.findAll(fa).map { it.value }.toList()
        val y = CHUNK.findAll(fb).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val pn = p[0] in '0'..'9'
            val qn = q[0] in '0'..'9'
            val c = when {
                pn && qn -> {
                    val pt = p.trimStart('0')
                    val qt = q.trimStart('0')
                    val byLen = pt.length.compareTo(qt.length)
                    if (byLen != 0) byLen else pt.compareTo(qt)
                }
                pn -> -1          // un número va antes que un texto en la misma posición
                qn -> 1
                else -> p.compareTo(q)
            }
            if (c != 0) return c
        }
        val bySize = x.size.compareTo(y.size)
        if (bySize != 0) return bySize
        val byFold = fa.compareTo(fb)
        return if (byFold != 0) byFold else a.compareTo(b)
    }

    private fun fold(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()
}

/**
 * 3e · Serie de canales numerados dentro de un proveedor: «M+ LaLiga» agrupa M+ LaLiga 1, 2, 3…; «DAZN» agrupa
 * DAZN, DAZN 1, DAZN 2…; «M+ Liga de Campeones» agrupa sus 1, 2… Un canal sin número forma su propia serie.
 */
class ChannelSeries(val key: String, val name: String, val channels: List<TvChannel>)

object ChannelSeriesGrouping {
    private val TRAILING_NUMBER = Regex("""\s+\d{1,3}\s*$""")

    /**
     * Nombre visible de la serie: el del canal sin el número final («M+ LaLiga 2» → «M+ LaLiga»). Vale para CUALQUIER
     * canal numerado (también «Eurosport2», con el número pegado): usa la misma lectura de nombres que separa los canales.
     */
    fun baseName(name: String): String {
        val canon = com.mivisor.tv.catalog.ChannelCanon.parse(name)
        if (canon.number != null && canon.baseName.isNotBlank()) return canon.baseName.trim()
        val clean = com.mivisor.tv.catalog.ChannelGrouping.cleanName(name).trim()
        return clean.replace(TRAILING_NUMBER, "").trim().ifEmpty { clean }
    }

    /** Clave para juntar («M+ LaLiga» y «Movistar LaLiga 2» son la misma serie). */
    fun key(name: String): String {
        val canon = com.mivisor.tv.catalog.ChannelCanon.parse(name)
        val k = if (canon.number != null) canon.baseKey else canon.variantKey
        return k.ifEmpty { com.mivisor.tv.catalog.ChannelGrouping.key(baseName(name)) }.ifEmpty { baseName(name).lowercase() }
    }

    /** Series de una lista, en orden natural; dentro de cada una, los canales en orden natural (2 antes que 10). */
    fun group(list: List<TvChannel>): List<ChannelSeries> =
        list.groupBy { key(it.name) }.map { (k, chs) ->
            val sorted = chs.sortedWith(compareBy(NaturalOrder) { it.name })
            // Nombre: el más corto sin número de la serie (prefiere «M+ LaLiga» a «Movistar LaLiga»).
            val name = sorted.map { baseName(it.name) }.minByOrNull { it.length } ?: k
            ChannelSeries(k, name, sorted)
        }.sortedWith(compareBy(NaturalOrder) { it.name })
}
