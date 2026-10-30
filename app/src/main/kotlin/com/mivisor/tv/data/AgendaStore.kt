package com.mivisor.tv.data

import com.mivisor.tv.catalog.AgendaRepository
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
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(AgendaState())
    val state: StateFlow<AgendaState> = _state.asStateFlow()
    private var job: Job? = null

    val url: String get() = prefs.agendaUrl.ifBlank { defaultUrl }

    fun load(force: Boolean = false) {
        if (job?.isActive == true && !force) return
        job?.cancel()
        _state.value = _state.value.copy(loading = true)
        job = scope.launch {
            _state.value = try {
                val r = repo.load(url, force)
                AgendaState(r.agenda, false, r.fetchedAt, r.offline, null)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                log.add("Agenda", "No se pudo cargar: ${e.message ?: e.javaClass.simpleName}")
                _state.value.copy(loading = false, error = e.message ?: "No se pudo cargar la agenda")
            }
        }
    }
}
