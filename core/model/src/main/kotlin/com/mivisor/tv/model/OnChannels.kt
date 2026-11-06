package com.mivisor.tv.model

/**
 * 3i · «Qué echan ahora» en cada canal, a partir de la agenda: para cada canal de tus proveedores, el evento que está
 * en directo en él (si hay) y el siguiente de las próximas [horizonMs]. [match] traduce el nombre de canal que anuncia la
 * agenda («M+ LaLiga 2») a ids de canales tuyos (el emparejador de la biblioteca, que ya distingue los numerados).
 */
object OnChannels {
    class NowNext(val now: AgendaEvent?, val next: AgendaEvent?)

    fun build(
        events: List<AgendaEvent>,
        now: Long,
        match: (String) -> List<String>,
        horizonMs: Long = 24 * 3600_000L
    ): Map<String, NowNext> {
        val live = HashMap<String, AgendaEvent>()
        val next = HashMap<String, AgendaEvent>()
        val names = HashMap<String, List<String>>()            // el mismo nombre de canal sale en muchos eventos
        for (e in events) {
            val st = e.status(now)
            if (st == EventStatus.FINISHED || e.timeTbd) continue
            if (st == EventStatus.UPCOMING && e.startMillis - now > horizonMs) continue
            for (c in e.channels) {
                for (id in names.getOrPut(c.name) { match(c.name) }) {
                    if (st == EventStatus.LIVE) {
                        // Si coinciden dos, el que empezó más tarde (el que se está viendo de verdad ahora).
                        val cur = live[id]
                        if (cur == null || e.startMillis > cur.startMillis) live[id] = e
                    } else {
                        val cur = next[id]
                        if (cur == null || e.startMillis < cur.startMillis) next[id] = e
                    }
                }
            }
        }
        val out = HashMap<String, NowNext>(live.size + next.size)
        (live.keys + next.keys).forEach { id -> out[id] = NowNext(live[id], next[id]) }
        return out
    }
}
