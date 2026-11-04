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
    private val CHUNK = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val x = CHUNK.findAll(fold(a)).map { it.value }.toList()
        val y = CHUNK.findAll(fold(b)).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                val byLen = p.trimStart('0').length.compareTo(q.trimStart('0').length)
                if (byLen != 0) byLen else p.trimStart('0').compareTo(q.trimStart('0'))
            } else p.compareTo(q)
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }

    private fun fold(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()
}
