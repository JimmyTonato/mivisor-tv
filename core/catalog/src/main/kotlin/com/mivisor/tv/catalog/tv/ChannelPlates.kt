package com.mivisor.tv.catalog.tv

/**
 * Rótulo del canal (el del prototipo de la fase 2): marca arriba y lo que distingue al canal debajo
 * («DAZN | LALIGA», «M+ | DEPORTES», «EURO | SPORT 1»). Es texto, no un logo: no hace falta descargar nada.
 */
data class Plate(val top: String, val bottom: String?, val tone: Tone) {
    /** d = claro (DAZN), l = oscuro, c = azul (Eurosport), r = rojo (LaLiga, GOL). */
    enum class Tone { LIGHT, DARK, BLUE, RED }
}

object ChannelPlates {
    private class Brand(val short: String, val tone: Plate.Tone, val words: Set<String>, val shortBottom: String? = null)

    private val BRANDS = mapOf(
        "dazn" to Brand("DAZN", Plate.Tone.LIGHT, setOf("dazn")),
        "movistar" to Brand("M+", Plate.Tone.DARK, setOf("m+", "movistar", "movistar+", "plus", "plus+")),
        "eurosport" to Brand("EURO", Plate.Tone.BLUE, setOf("eurosport"), shortBottom = "SPORT"),
        "laliga" to Brand("LALIGA", Plate.Tone.RED, setOf("laliga", "la", "liga")),
        "gol" to Brand("GOL", Plate.Tone.RED, setOf("gol")),
        "bein" to Brand("beIN", Plate.Tone.BLUE, setOf("bein", "sports", "sport")),
        "sky" to Brand("SKY", Plate.Tone.BLUE, setOf("sky")),
        "tennischannel" to Brand("TENNIS", Plate.Tone.BLUE, setOf("tennis", "channel"))
    )

    private const val MAX = 12

    fun of(name: String, brandKey: String?): Plate {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val brand = brandKey?.let { BRANDS[it] }
        if (brand != null) {
            val rest = words.dropWhile { it.lowercase() in brand.words }.joinToString(" ").uppercase()
            val bottom = listOfNotNull(brand.shortBottom, rest.ifEmpty { null }).joinToString(" ")
            return Plate(brand.short, shorten(bottom), brand.tone)
        }
        // Sin marca conocida: el propio nombre, en una o dos líneas («LA 1», «ANTENA | 3», «TELE | CINCO»).
        val upper = words.map { it.uppercase() }
        return when {
            upper.isEmpty() -> Plate("?", null, Plate.Tone.DARK)
            upper.joinToString(" ").length <= 6 -> Plate(upper.joinToString(" "), null, Plate.Tone.DARK)
            upper.size == 1 -> Plate(upper[0].take(MAX), null, Plate.Tone.DARK)
            else -> Plate(upper[0].take(MAX), shorten(upper.drop(1).joinToString(" ")), Plate.Tone.DARK)
        }
    }

    /** Hasta 12 letras, cortando por palabras; si la primera ya es larga, se recorta. */
    private fun shorten(text: String): String? {
        if (text.isBlank()) return null
        if (text.length <= MAX) return text
        val out = StringBuilder()
        for (w in text.split(' ')) {
            if (out.isEmpty()) { out.append(w.take(MAX)); continue }
            if (out.length + 1 + w.length > MAX) break
            out.append(' ').append(w)
        }
        return out.toString()
    }

    /** Siglas de un equipo o jugador para los escudos de texto de la agenda («Real Betis» → BET). */
    fun crest(team: String): String {
        val skip = setOf("real", "fc", "cf", "cd", "ud", "sd", "rcd", "club", "de", "del", "la", "el", "afc", "ac", "sc", "c.", "a.")
        val words = team.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val main = words.filter { it.lowercase() !in skip }.ifEmpty { words }
        val pick = main.maxByOrNull { it.length } ?: return "?"
        return pick.filter { it.isLetter() }.take(3).uppercase().ifEmpty { "?" }
    }
}
