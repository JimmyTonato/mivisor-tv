package com.mivisor.tv.data

import android.content.Context
import com.mivisor.tv.catalog.datos.CachedJson
import com.mivisor.tv.catalog.datos.CatalogHosts
import com.mivisor.tv.catalog.datos.GitHubData
import com.mivisor.tv.engine.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 3o · Servidores del catálogo de Ace Stream (donde un ID se convierte en su descriptor) desde la rama `catalogo` de
 * mivisor-datos, igual que la agenda: se consulta al abrir la app (como mucho cada 6 h, con ETag), se valida y se
 * guarda en la carpeta del motor. Outpace los lee AL ARRANCAR, por delante de los suyos de serie.
 * Sin red o con un fichero roto, se sigue con la última lista buena (o con la de serie de MiVisor).
 */
class CatalogHostsStore(app: Context, private val scope: CoroutineScope, private val agendaUrl: () -> String, private val log: LogBook) {
    /** [hosts]: los de GitHub (o de serie). [manual]: los añadidos en Ajustes, que el motor prueba primero. */
    data class State(val hosts: List<String>, val updated: String? = null, val note: String = "", val manual: List<String> = emptyList())

    private val engineDir = File(app.filesDir, "engine")
    private val cache = CachedJson(File(app.filesDir, "catalogo"), "catalog-hosts.json", ttlMs = 6 * 3600_000L)
    private val _state = MutableStateFlow(State(EngineConfig.downloadedCatalogHosts(engineDir), manual = EngineConfig.manualCatalogHosts(engineDir)))
    val state: StateFlow<State> = _state.asStateFlow()

    fun start() = refresh(force = false)

    fun refresh(force: Boolean = true, onDone: (String) -> Unit = {}) {
        scope.launch {
            val note = withContext(Dispatchers.IO) {
                try {
                    val r = cache.load(GitHubData.catalogHosts(agendaUrl()), force) { CatalogHosts.parse(it) }
                    val parsed = CatalogHosts.parse(r.text)
                    val before = EngineConfig.downloadedCatalogHosts(engineDir)
                    if (parsed.hosts != before) {
                        engineDir.mkdirs()
                        val tmp = File(engineDir, EngineConfig.CATALOG_HOSTS_FILE + ".tmp")
                        tmp.writeText(parsed.hosts.joinToString("\n"))
                        val dst = File(engineDir, EngineConfig.CATALOG_HOSTS_FILE)
                        if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst) }
                        log.add("Motor", "Servidores del catálogo actualizados: ${parsed.hosts.joinToString(", ")} (se usan al arrancar el motor)")
                    }
                    _state.value = State(parsed.hosts, parsed.updated, if (r.offline) "Sin conexión: se usa la última lista" else "", _state.value.manual)
                    when {
                        r.offline -> "Sin conexión con GitHub: se usa la última lista"
                        parsed.hosts != before -> "Lista nueva: se usará la próxima vez que arranque el motor"
                        else -> "La lista está al día"
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Rama aún no creada (404) o fichero roto: se sigue con lo que hay.
                    val msg = "Lista del catálogo no disponible (${e.message ?: e.javaClass.simpleName}): se usa la guardada"
                    _state.value = _state.value.copy(note = msg)
                    msg
                }
            }
            onDone(note)
        }
    }

    /**
     * 4t · Añade un servidor a mano («ip» o «ip:puerto»). Devuelve el aviso para el usuario. Se guarda en su propio
     * fichero, así que una lista nueva de GitHub no lo borra. El motor lo usa al volver a arrancar.
     */
    fun addManual(text: String): Result<String> {
        val entry = CatalogHosts.parseManual(text)
            ?: return Result.failure(IllegalArgumentException("Escribe una IP pública, por ejemplo 51.158.152.5 o 51.158.152.5:8081"))
        val now = _state.value.manual
        if (entry in now) return Result.failure(IllegalArgumentException("$entry ya está en la lista"))
        if (now.size >= MAX_MANUAL) return Result.failure(IllegalArgumentException("Como mucho $MAX_MANUAL servidores a mano: quita alguno"))
        return saveManual(now + entry).map { entry }.onSuccess { log.add("Motor", "Servidor del catálogo añadido a mano: $it") }
    }

    fun removeManual(entry: String): Result<String> =
        saveManual(_state.value.manual - entry).map { entry }.onSuccess { log.add("Motor", "Servidor del catálogo quitado: $it") }

    private fun saveManual(list: List<String>): Result<List<String>> = runCatching {
        EngineConfig.saveManualCatalogHosts(engineDir, list)
        EngineConfig.manualCatalogHosts(engineDir).also { _state.value = _state.value.copy(manual = it) }
    }

    companion object { const val MAX_MANUAL = 8 }
}
