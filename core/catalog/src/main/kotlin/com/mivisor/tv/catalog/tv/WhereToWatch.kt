package com.mivisor.tv.catalog.tv

import com.mivisor.tv.model.AgendaChannel

/**
 * «Dónde verlo» (3c): cada canal que anuncia la agenda (o TheSportsDB) y los canales de TUS proveedores que lo emiten.
 * Primero los que puedes ver; dentro, en el orden que diga [rank] (la app pone la fiabilidad); después los que no
 * están en tus proveedores, para que sepas dónde se emite aunque no lo tengas.
 */
class WatchOption(val announced: AgendaChannel, val yours: List<TvChannel>) {
    val available: Boolean get() = yours.isNotEmpty()
}

object WhereToWatch {
    fun build(
        announced: List<AgendaChannel>,
        library: TvLibrary,
        rank: (TvChannel) -> Double = { 0.0 }
    ): List<WatchOption> {
        val seen = HashSet<String>()
        val options = announced.distinctBy { it.name.trim().lowercase() + "|" + it.note.orEmpty().lowercase() }.map { ch ->
            // Un mismo canal tuyo solo aparece una vez (el primer anuncio que lo nombra).
            val mine = library.forAgenda(ch.name, 3).filter { seen.add(it.id) }.sortedByDescending(rank)
            WatchOption(ch, mine)
        }
        return options.filter { it.available }.sortedByDescending { o -> o.yours.maxOfOrNull(rank) ?: 0.0 } +
            options.filter { !it.available }
    }

    /** Tus canales para el evento, el mejor primero (para «Ver ahora»). */
    fun best(options: List<WatchOption>): TvChannel? = options.firstOrNull { it.available }?.yours?.firstOrNull()
}
