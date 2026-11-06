package com.mivisor.tv.data

import android.content.Context
import com.mivisor.tv.catalog.datos.BannerCatalog
import com.mivisor.tv.catalog.datos.BannerRepository
import com.mivisor.tv.catalog.datos.GitHubData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 3j · Banners de competiciones de mivisor-datos (rama banners), igual que la agenda: se leen al abrir la app (nunca
 * en segundo plano), se guardan en la memoria de la app y solo se vuelven a bajar si cambian en GitHub.
 */
class BannerStore(
    app: Context,
    private val scope: CoroutineScope,
    private val agendaUrl: () -> String,
    private val log: LogBook
) {
    private val repo = BannerRepository(File(app.filesDir, "banners"))
    private val _catalog = MutableStateFlow<BannerCatalog?>(null)
    val catalog: StateFlow<BannerCatalog?> = _catalog.asStateFlow()
    /** Sube cada vez que llegan imágenes nuevas o se borran (para volver a pintar). */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()
    private var job: Job? = null

    val baseUrl: String get() = GitHubData.bannersBase(agendaUrl())

    /** Al abrir: lo guardado al instante y después GitHub (si toca) y la primera pasada de descargas. */
    fun start() {
        scope.launch(Dispatchers.IO) { runCatching { repo.loadCached() }.getOrNull()?.let { _catalog.value = it } }
        refresh(force = false, delayMs = 4_000)
    }

    fun refresh(force: Boolean = true, delayMs: Long = 0, onDone: (String) -> Unit = {}) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            if (delayMs > 0) delay(delayMs)
            val c = try {
                repo.refresh(GitHubData.bannersManifest(agendaUrl()), force)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                log.add("Banners", "No se pudo leer el catálogo de banners: ${e.message ?: e.javaClass.simpleName}")
                withContext(Dispatchers.Main) { onDone("No se pudo leer el catálogo de banners") }
                return@launch
            }
            _catalog.value = c
            // Primera pasada: se bajan los que falten o hayan cambiado, uno a uno (sin competir con lo demás).
            var got = 0
            for (e in c.entries) {
                if (!repo.needsCheck(e)) continue
                if (runCatching { repo.ensure(e, baseUrl) }.getOrNull() != null) got++
                if (got > 0 && got % 4 == 0) _version.value++
            }
            if (got > 0) { _version.value++; log.add("Banners", "$got banners guardados o actualizados") }
            withContext(Dispatchers.Main) { onDone(if (got > 0) "$got banners actualizados" else "Banners al día") }
        }
    }

    fun entryFor(sport: String?, competition: String?): BannerCatalog.Entry? = _catalog.value?.find(sport, competition)

    /** El fichero del banner (lo guardado o, si falta, se descarga ahora). */
    suspend fun file(e: BannerCatalog.Entry): File? = withContext(Dispatchers.IO) {
        repo.cachedFile(e) ?: runCatching { repo.ensure(e, baseUrl) }.getOrNull()
    }

    fun usedBytes(): Long = runCatching { repo.usedBytes() }.getOrDefault(0)

    /** «Borrar banners»: todo fuera; en el siguiente refresco se vuelven a descargar. */
    fun clear(onDone: () -> Unit = {}) {
        job?.cancel()
        scope.launch(Dispatchers.IO) {
            repo.clear()
            com.mivisor.tv.ui.BannerImages.clear()
            _catalog.value = null
            _version.value++
            log.add("Banners", "Banners borrados: se volverán a descargar")
            withContext(Dispatchers.Main) { onDone() }
            refresh(force = true, delayMs = 500)
        }
    }
}
