package com.mivisor.tv.catalog

import com.mivisor.tv.model.Channel
import java.text.Normalizer

/**
 * Relaciona los canales de la agenda ("DAZN LaLiga", "M+ LaLiga TV") con los canales
 * de TUS categorías ("DAZN LALIGA FHD", "Movistar LaLiga 1080"), para poder ofrecer
 * "Abrir canal" directamente desde un partido.
 *
 * Reglas (conservadoras: mejor no enlazar que enlazar mal):
 *  - Se normaliza: minúsculas, sin acentos ni símbolos, alias (M+ → movistar),
 *    y se quitan etiquetas de calidad (HD, FHD, 4K, 1080p…).
 *  - Coincidencia exacta tras normalizar: puntuación máxima.
 *  - Tus canales pueden tener palabras EXTRA sin números ("dazn laliga multi" ⊇ "dazn laliga"),
 *    pero no números distintos: "DAZN LaLiga" no enlaza con "DAZN LaLiga 2".
 */
class ChannelMatcher(channels: List<Channel>) {

    private class Entry(val tokens: List<String>, val channel: Channel) {
        val set: Set<String> = tokens.toHashSet()
    }

    private val entries: List<Entry> = channels.mapNotNull { ch ->
        tokens(ch.name).takeIf { it.isNotEmpty() }?.let { Entry(it, ch) }
    }

    /**
     * 3g · Índice palabra → canales que la contienen. Un canal solo puede casar si tiene TODAS las palabras buscadas,
     * así que basta con mirar los canales de la palabra más rara (antes se recorrían todos por cada nombre de la agenda,
     * millones de comparaciones con una agenda grande en una tele modesta).
     */
    private val index: Map<String, List<Entry>> = HashMap<String, MutableList<Entry>>().also { m ->
        for (e in entries) for (t in e.set) m.getOrPut(t) { ArrayList() } += e
    }

    /** 3g · Resultados ya calculados (la misma agenda se pinta muchas veces). */
    private val memo = Memo<String, List<Channel>>(4_000)

    val isEmpty: Boolean get() = entries.isEmpty()

    /**
     * Todas tus versiones de [agendaName] (de cualquier código/categoría), mejores primero.
     * Mismo enlace repetido en varios códigos = una sola opción.
     */
    fun match(agendaName: String, limit: Int = 12): List<Channel> = memo.get("$limit\u0000$agendaName") {
        val wanted = tokens(agendaName)
        if (wanted.isEmpty()) return@get emptyList()
        val wantedSet = wanted.toHashSet()
        val candidates = wantedSet.map { index[it] ?: return@get emptyList() }.minByOrNull { it.size } ?: return@get emptyList()
        candidates.asSequence()
            .mapNotNull { e -> score(wanted, wantedSet, e)?.let { it to e.channel } }
            .sortedByDescending { it.first }
            .distinctBy { it.second.url }
            .take(limit)
            .map { it.second }
            .toList()
    }

    private fun score(wanted: List<String>, wantedSet: Set<String>, e: Entry): Int? {
        val have = e.tokens
        if (have == wanted) return 100
        // Todas las palabras buscadas, en cualquier orden ("LaLiga DAZN" = "DAZN LaLiga")…
        if (!e.set.containsAll(wantedSet)) return null
        // …y lo que sobra sin números: "DAZN LaLiga 2" no es "DAZN LaLiga".
        val extra = have.filter { it !in wantedSet }
        if (extra.any { t -> t.any(Char::isDigit) }) return null
        // Un nombre de una sola palabra genérica ("dazn") no debe enlazar con todo.
        if (wantedSet.size == 1 && extra.isNotEmpty()) return null
        val sameOrder = have.filter { it in wantedSet } == wanted
        return (if (sameOrder) 90 else 85) - extra.size
    }

    companion object {
        private val QUALITY = setOf(
            "hd", "fhd", "uhd", "sd", "4k", "2k", "8k", "1080", "1080p", "720", "720p", "2160p",
            "hevc", "h265", "x265", "50fps", "60fps", "hdr", "multi", "multiaudio", "backup", "alt", "es", "spain", "tv", "canal"
        )
        private val ALIASES = listOf(
            "movistar plus+" to "movistar",
            "movistar plus" to "movistar",
            "movistar+" to "movistar",
            "m+" to "movistar",
            "la liga" to "laliga",
            "liga de campeones" to "champions",
            "champions league" to "champions",
            "#vamos" to "vamos",
            "teledeporte" to "tdp",
            "eurosport 1" to "eurosport1",
            "eurosport 2" to "eurosport2",
            "dazn f1" to "daznf1"
        )

        fun tokens(name: String): List<String> {
            // Primero la normalización GENERAL (Unicode decorativo, invisibles, emojis…); después los alias.
            var text = Normalizer.normalize(ChannelNameNormalizer.display(name).lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
            ALIASES.forEach { (from, to) -> text = text.replace(from, " $to ") }
            return text.replace(Regex("[^a-z0-9]+"), " ")
                .split(' ')
                .filter { it.isNotEmpty() && it !in QUALITY }
        }
    }
}
