package com.mivisor.tv.data

import com.mivisor.tv.catalog.AgendaRepository
import com.mivisor.tv.catalog.CompetitionBook
import com.mivisor.tv.model.CompetitionNames
import kotlinx.coroutines.Dispatchers
import com.mivisor.tv.model.Agenda
import com.mivisor.tv.storage.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AgendaState(
    val agenda: Agenda? = null,
    val loading: Boolean = false,
    val fetchedAt: Long = 0,
    val offline: Boolean = false,
    val error: String? = null
)

/**
 * Agenda deportiva publicada en GitHub (mivisor-datos). MiVisor TV solo la LEE (con ETag y copia local); no depende
 * de la Raspberry ni la modifica. Se descarga al abrir la app y al pedirlo; nunca en segundo plano.
 */
class AgendaStore(
    private val repo: AgendaRepository,
    private val prefs: Prefs,
    private val defaultUrl: String,
    private val log: LogBook,
    private val scope: CoroutineScope,
    /** 3g · Competiciones que han llegado en la agenda (para elegir cuáles ver). */
    private val book: CompetitionBook? = null
) {
    private val _state = MutableStateFlow(AgendaState())
    /** Agenda que ven las pantallas: SIN las competiciones ocultas (Ajustes › Competiciones). */
    val state: StateFlow<AgendaState> = _state.asStateFlow()
    private var job: Job? = null
    /** 3g · La agenda completa tal como llegó (con las ocultas). */
    @Volatile private var full: Agenda? = null
    private val _competitions = MutableStateFlow<List<CompetitionBook.Seen>>(emptyList())
    val competitions: StateFlow<List<CompetitionBook.Seen>> = _competitions.asStateFlow()

    /** Quita los eventos de competiciones ocultas. */
    private fun visible(a: Agenda?): Agenda? {
        a ?: return null
        val hidden = prefs.hiddenCompetitions.toSet()
        if (hidden.isEmpty()) return a
        return Agenda(a.generatedAt, a.source, a.events.filter { CompetitionNames.id(it) !in hidden }, a.health)
    }

    /** Publica [a] (filtrada) y apunta sus competiciones en el catálogo. */
    private fun publish(st: AgendaState, a: Agenda?) {
        full = a
        _state.value = st.copy(agenda = visible(a))
        if (a != null && book != null) scope.launch(Dispatchers.IO) {
            val keep = (prefs.hiddenCompetitions + prefs.followedCompetitions).toSet()
            _competitions.value = runCatching { book.update(a.events, keep) }.getOrDefault(_competitions.value)
        }
    }

    /** 3g · Tras cambiar las competiciones ocultas: vuelve a filtrar sin descargar nada. */
    fun refilter() {
        _state.value = _state.value.copy(agenda = visible(full))
    }

    /** Catálogo guardado (aunque aún no haya llegado agenda en esta sesión). */
    fun loadCompetitions() {
        if (book == null) return
        scope.launch(Dispatchers.IO) { if (_competitions.value.isEmpty()) _competitions.value = runCatching { book.all() }.getOrDefault(emptyList()) }
    }

    val url: String get() = prefs.agendaUrl.ifBlank { defaultUrl }

    fun load(force: Boolean = false) {
        if (job?.isActive == true && !force) return
        job?.cancel()
        _state.value = _state.value.copy(loading = true)
        job = scope.launch {
            // 3f · Primero la copia guardada (al instante, aunque sea de ayer) y luego la de Internet.
            if (_state.value.agenda == null && !force) {
                runCatching { repo.cachedOrNull() }.getOrNull()?.also { PerfLog.once("Agenda guardada en pantalla", "${it.agenda.events.size} eventos") }?.let { c ->
                    publish(AgendaState(null, loading = !repo.isFresh(), fetchedAt = c.fetchedAt, offline = false, error = null), c.agenda)
                }
            }
            try {
                val t0 = PerfLog.now()
                val r = repo.load(url, force)
                PerfLog.record("Agenda de Internet", PerfLog.now() - t0, if (r.offline) "sin red: copia guardada" else "${r.agenda.events.size} eventos")
                publish(AgendaState(null, false, r.fetchedAt, r.offline, null), r.agenda)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                log.add("Agenda", "No se pudo cargar: ${e.message ?: e.javaClass.simpleName}")
                _state.value = _state.value.copy(loading = false, error = e.message ?: "No se pudo cargar la agenda")
            }
        }
    }
}
