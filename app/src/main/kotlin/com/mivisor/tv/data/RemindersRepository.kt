package com.mivisor.tv.data

import com.mivisor.tv.model.AgendaEvent
import com.mivisor.tv.model.Reminder
import com.mivisor.tv.model.ReminderPolicy
import com.mivisor.tv.outpace.EngineManager
import com.mivisor.tv.storage.MvDatabase
import com.mivisor.tv.storage.ReminderRow
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Recordatorios de eventos (3c). Se guardan en la tele y se avisa SOLO con la app abierta (sin trabajo en segundo
 * plano): unos minutos antes aparece un aviso en pantalla con «Ver ahora» y se deja Outpace arrancado para que el
 * canal empiece antes. Si la app estaba cerrada a la hora del evento, se avisa al abrirla (si aún no ha terminado).
 */
class RemindersRepository(
    context: Context,
    private val db: MvDatabase,
    private val log: LogBook,
    private val scope: CoroutineScope
) {
    private val app = context.applicationContext
    private val _list = MutableStateFlow<List<Reminder>>(emptyList())
    val list: StateFlow<List<Reminder>> = _list.asStateFlow()

    /** Aviso pendiente de mostrar (uno a la vez). */
    private val _notice = MutableStateFlow<Reminder?>(null)
    val notice: StateFlow<Reminder?> = _notice.asStateFlow()

    private var ticker: Job? = null

    fun load() {
        scope.launch { reload() }
    }

    private suspend fun reload() {
        _list.value = withContext(Dispatchers.IO) { runCatching { db.reminders() }.getOrDefault(emptyList()) }.map { it.model() }
    }

    fun isSet(eventId: String): Boolean = _list.value.any { it.eventId == eventId }

    /** Pone o quita el recordatorio de un evento. Devuelve true si queda puesto. */
    fun toggle(event: AgendaEvent): Boolean {
        val on = !isSet(event.id)
        scope.launch {
            withContext(Dispatchers.IO) {
                if (on) db.saveReminder(ReminderPolicy.of(event).row()) else db.deleteReminder(event.id)
            }
            reload()
        }
        if (on) log.add("Recordatorios", "Puesto: ${event.title}")
        return on
    }

    fun remove(eventId: String) {
        scope.launch {
            withContext(Dispatchers.IO) { db.deleteReminder(eventId) }
            reload()
        }
    }

    /** Con la app en primer plano: revisa cada 20 s si toca avisar. */
    fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                check()
                delay(20_000)
            }
        }
    }

    fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    fun dismiss() {
        _notice.value = null
        scope.launch { check() }
    }

    private suspend fun check() {
        val now = System.currentTimeMillis()
        val all = _list.value
        val expired = ReminderPolicy.expired(all, now)
        if (expired.isNotEmpty()) {
            withContext(Dispatchers.IO) { expired.forEach { runCatching { db.deleteReminder(it.eventId) } } }
            reload()
        }
        if (_notice.value != null) return
        val next = ReminderPolicy.due(_list.value, now).firstOrNull() ?: return
        withContext(Dispatchers.IO) { runCatching { db.markReminderNotified(next.eventId) } }
        reload()
        _notice.value = next
        log.add("Recordatorios", "Aviso: ${next.title}")
        // «Deja la fuente lista»: el motor arrancado para que el canal empiece en cuanto se pulse «Ver ahora».
        if (EngineManager.isSupported(app)) scope.launch { runCatching { EngineManager.ensureStarted(app) } }
    }

    private fun ReminderRow.model() = Reminder(eventId, title, start, sport, competition, channels.split('\n').filter { it.isNotBlank() }, notified)
    private fun Reminder.row() = ReminderRow(eventId, title, startMillis, sport, competition, channels.joinToString("\n"), notified)
}
