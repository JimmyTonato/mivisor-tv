package com.mivisor.tv.model

/**
 * 3i · Agenda para el mando: el día y el deporte elegidos, agrupados en filas por competición (la competición REAL
 * que trae la agenda: «Champions League», «Copa Libertadores»…). Las ocultas en Ajustes ya llegan filtradas.
 *
 * Orden de las filas: primero las que tienen algo en directo, luego las seguidas (★) y después por la hora del primer
 * evento. Dentro de cada fila: en directo primero y luego por hora. Todo puro (sin Android) para poder probarlo.
 */
object AgendaBoard {
    class Group(
        /** Id de la competición para rutas y preferencias ("raw:…"). */
        val id: String,
        val name: String,
        val events: List<AgendaEvent>,
        val live: Int,
        val followed: Boolean
    ) {
        val later: Int get() = events.size - live
    }

    class Board(
        val groups: List<Group>,
        /** Deportes del día con su número de eventos (sin filtrar por deporte), en el orden de la agenda. */
        val sports: List<Pair<String, Int>>,
        /** Eventos (no terminados) por día, 0 = hoy, para el deporte elegido. */
        val perDay: IntArray
    ) {
        val events: List<AgendaEvent> get() = groups.flatMap { it.events }
    }

    fun build(
        events: List<AgendaEvent>,
        now: Long,
        clock: DayClock,
        day: Int,
        sport: String?,
        followed: Set<String>,
        categories: List<String> = emptyList(),
        days: Int = 7,
        otherName: (String) -> String = { it }
    ): Board {
        // Un solo cálculo de día por evento (DayClock recorre días de calendario).
        val starts = LongArray(days + 1) { clock.startOfDay(now, it) }
        fun dayOf(ms: Long): Int {
            if (ms < starts[0]) return 0                    // empezó ayer y sigue en directo: cuenta como hoy
            for (d in 0 until days) if (ms < starts[d + 1]) return d
            return days
        }
        val open = events.filter { it.status(now) != EventStatus.FINISHED }
        val dayOfEvent = HashMap<String, Int>(open.size * 2)
        open.forEach { dayOfEvent[it.id] = dayOf(it.startMillis) }

        val perDay = IntArray(days)
        open.forEach { e -> val d = dayOfEvent.getValue(e.id); if (d < days && (sport == null || e.sport == sport)) perDay[d]++ }

        val today = open.filter { dayOfEvent.getValue(it.id) == day }
        val sportCount = today.groupingBy { it.sport }.eachCount()
        val order = categories + sportCount.keys.filter { it !in categories }.sorted()
        val sports = order.mapNotNull { s -> sportCount[s]?.let { s to it } }

        val chosen = today.filter { sport == null || it.sport == sport }
        val groups = chosen.groupBy { CompetitionNames.id(it) }.map { (id, list) ->
            val sorted = list.sortedWith(compareBy<AgendaEvent>({ it.status(now) != EventStatus.LIVE }, { it.startMillis }, { it.id }))
            val name = list.firstNotNullOfOrNull { it.competition?.trim()?.takeIf(String::isNotEmpty) } ?: otherName(list.first().sport)
            Group(id, name, sorted, sorted.count { it.status(now) == EventStatus.LIVE }, id in followed)
        }.sortedWith(compareBy<Group>({ it.live == 0 }, { !it.followed }, { it.events.first().startMillis }, { it.name }))
        return Board(groups, sports, perDay)
    }
}
