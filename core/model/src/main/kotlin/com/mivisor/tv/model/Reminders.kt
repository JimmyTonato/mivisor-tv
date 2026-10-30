package com.mivisor.tv.model

/**
 * Recordatorio de un evento de la agenda (3c). Se guarda en la tele; NO hay avisos en segundo plano:
 * MiVisor TV avisa mientras está abierta (unos minutos antes) y, al abrirla, de los que ya han empezado.
 */
data class Reminder(
    val eventId: String,
    val title: String,
    val startMillis: Long,
    val sport: String,
    val competition: String?,
    /** Nombres de canal anunciados, para buscar el tuyo cuando llegue la hora. */
    val channels: List<String>,
    val notified: Boolean = false
)

object ReminderPolicy {
    /** Se avisa con esta antelación. */
    const val LEAD_MS = 5 * 60_000L
    /** Un recordatorio se borra solo cuando el evento ya habrá terminado (3 h tras empezar). */
    const val KEEP_AFTER_START_MS = 3 * 3600_000L

    /** Los que toca avisar ahora (entran en la ventana de aviso y aún no se avisaron), del más próximo al último. */
    fun due(reminders: List<Reminder>, now: Long): List<Reminder> =
        reminders.filter { !it.notified && now >= it.startMillis - LEAD_MS && now < it.startMillis + KEEP_AFTER_START_MS }
            .sortedBy { it.startMillis }

    /** Los que ya no sirven (evento terminado). */
    fun expired(reminders: List<Reminder>, now: Long): List<Reminder> =
        reminders.filter { now >= it.startMillis + KEEP_AFTER_START_MS }

    fun of(event: AgendaEvent): Reminder =
        Reminder(event.id, event.title, event.startMillis, event.sport, event.competition, event.channels.map { it.name })

    /** «Empieza en 4 min», «Ha empezado hace 12 min». */
    fun label(r: Reminder, now: Long): String {
        val min = (r.startMillis - now) / 60_000
        return when {
            min > 0 -> "Empieza en $min min"
            min == 0L -> "Empieza ahora"
            else -> "Empezó hace ${-min} min"
        }
    }
}
