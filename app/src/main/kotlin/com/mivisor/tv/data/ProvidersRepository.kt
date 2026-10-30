package com.mivisor.tv.data

import com.mivisor.tv.catalog.CatalogBuilder
import com.mivisor.tv.catalog.Codes
import com.mivisor.tv.catalog.NotCachedException
import com.mivisor.tv.catalog.StreamRepository
import com.mivisor.tv.catalog.tv.ProviderCatalog
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.model.ChannelKind
import com.mivisor.tv.net.CodeNotFoundException
import com.mivisor.tv.net.DnsUnavailableException
import com.mivisor.tv.storage.CodeVault
import com.mivisor.tv.storage.MvDatabase
import com.mivisor.tv.storage.Prefs
import com.mivisor.tv.storage.ProviderRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Un proveedor tal como lo ve la interfaz: nombre, canales y estado. Nunca el código. */
data class ProviderInfo(
    val id: String,
    val name: String,
    val channels: Int,
    val lastOk: Long,
    val error: String?,
    val usesWarp: Boolean
)

data class LibraryState(
    val library: TvLibrary = TvLibrary.EMPTY,
    val providers: List<ProviderInfo> = emptyList(),
    val loading: Boolean = false,
    val loadedOnce: Boolean = false,
    /** Algún proveedor se muestra con su copia guardada porque la red falló. */
    val offline: Boolean = false,
    val updatedAt: Long = 0
)

/**
 * Proveedores: se guardan cifrados ([CodeVault]); se leen por DNS como en la v18 ([StreamRepository]) y se juntan en
 * una sola biblioteca de canales ([TvLibrary]). Sin trabajo en segundo plano: se leen al abrir la app y al pedirlo.
 */
class ProvidersRepository(
    private val db: MvDatabase,
    private val vault: CodeVault,
    private val prefs: Prefs,
    private val streams: StreamRepository,
    private val log: LogBook,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()
    private var loadJob: Job? = null

    /** Primera carga: copia local al instante y luego red. */
    fun start() {
        refresh(cacheFirst = true)
    }

    fun refresh(cacheFirst: Boolean = false) {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (cacheFirst) load(StreamRepository.Mode.CACHE_ONLY)
            load(StreamRepository.Mode.PREFER_CACHE.takeIf { cacheFirst } ?: StreamRepository.Mode.NETWORK)
        }
    }

    private suspend fun load(mode: StreamRepository.Mode) {
        val rows = withContext(Dispatchers.IO) { db.providers() }
        val warp = prefs.warpProviders
        if (rows.isEmpty()) {
            _state.value = LibraryState(loadedOnce = true)
            return
        }
        _state.value = _state.value.copy(loading = true, providers = rows.map { it.info(warp) })
        val offline = java.util.concurrent.atomic.AtomicBoolean(false)
        val catalogs = withContext(Dispatchers.IO) {
            rows.map { row ->
                async {
                    val code = vault.open(row.sealedCode) ?: return@async null
                    log.redactor.addSecret(code)
                    try {
                        val result = streams.load(code, mode)
                        val catalog = CatalogBuilder.build(result.items)
                        if (result.offline) offline.set(true)
                        if (mode != StreamRepository.Mode.CACHE_ONLY) db.providerResult(row.id, System.currentTimeMillis(), catalog.channelCount, null)
                        // Enlaces que no son Ace Stream (magnet, Telegram, TVGram…): se abren con otra app.
                        val links = CatalogBuilder.build(result.items, aceOnly = false).sections
                            .flatMap { it.channels }.filter { it.kind != ChannelKind.ACE }
                        ProviderCatalog(row.id, row.name, catalog, row.id in warp, links)
                    } catch (e: NotCachedException) {
                        null
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val text = friendly(e)
                        if (mode != StreamRepository.Mode.CACHE_ONLY) {
                            db.providerResult(row.id, null, null, text)
                            log.add("Proveedores", "${row.name}: $text")
                        }
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val library = withContext(Dispatchers.Default) { TvLibrary.build(catalogs) }
        val fresh = withContext(Dispatchers.IO) { db.providers() }
        _state.value = LibraryState(
            library = if (library.isEmpty && !_state.value.library.isEmpty && mode == StreamRepository.Mode.CACHE_ONLY) _state.value.library else library,
            providers = fresh.map { it.info(warp) },
            loading = mode == StreamRepository.Mode.CACHE_ONLY,
            loadedOnce = true,
            offline = offline.get(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Comprueba y guarda un proveedor. Devuelve null si va bien o el motivo (sin mostrar el código).
     * El código se normaliza como en la v18 (admite mivisor:… y mylinkspaste:…).
     */
    suspend fun add(rawCode: String, name: String, usesWarp: Boolean): String? {
        val code = Codes.normalizeOrNull(rawCode) ?: return "Ese código no tiene un formato válido"
        val id = idOf(code)
        log.redactor.addSecret(code)
        val existing = withContext(Dispatchers.IO) { db.providers() }
        if (existing.any { it.id == id }) return "Ese proveedor ya está añadido"
        val result = try {
            withContext(Dispatchers.IO) { streams.load(code, StreamRepository.Mode.NETWORK) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return friendly(e)
        }
        val catalog = CatalogBuilder.build(result.items)
        val linkCount = CatalogBuilder.build(result.items, aceOnly = false).channelCount - catalog.channelCount
        if (catalog.channelCount == 0 && linkCount <= 0) return "El proveedor no tiene canales ni enlaces que MiVisor TV pueda abrir"
        val title = name.trim().ifEmpty { "Proveedor ${existing.size + 1}" }.take(40)
        withContext(Dispatchers.IO) {
            db.upsertProvider(ProviderRow(id, title, vault.seal(code), System.currentTimeMillis(), System.currentTimeMillis(), catalog.channelCount, null, existing.size))
        }
        if (usesWarp) prefs.warpProviders = prefs.warpProviders + id
        log.add("Proveedores", "Añadido «$title» con ${catalog.channelCount} canales")
        refresh(cacheFirst = true)
        return null
    }

    fun remove(id: String) {
        scope.launch {
            withContext(Dispatchers.IO) { db.deleteProvider(id) }
            prefs.warpProviders = prefs.warpProviders - id
            refresh(cacheFirst = true)
        }
    }

    fun rename(id: String, name: String) {
        scope.launch {
            withContext(Dispatchers.IO) { db.renameProvider(id, name.trim().take(40)) }
            refresh(cacheFirst = true)
        }
    }

    fun setUsesWarp(id: String, on: Boolean) {
        prefs.warpProviders = if (on) prefs.warpProviders + id else prefs.warpProviders - id
        _state.value = _state.value.copy(providers = _state.value.providers.map { if (it.id == id) it.copy(usesWarp = on) else it })
    }

    fun usesWarp(providerId: String): Boolean = providerId in prefs.warpProviders

    private fun ProviderRow.info(warp: Set<String>) = ProviderInfo(id, name, channels, lastOk, lastError, id in warp)

    companion object {
        /** Id interno estable del proveedor: huella del código (el código nunca se guarda en claro ni se muestra). */
        fun idOf(code: String): String =
            MessageDigest.getInstance("SHA-256").digest(code.toByteArray()).take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }

        fun friendly(e: Throwable): String = when (e) {
            is CodeNotFoundException -> "Ese código no existe o ya no está publicado"
            is DnsUnavailableException -> "No se pudo consultar el código: revisa la conexión a Internet"
            is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.io.IOException -> "Sin conexión al leer el proveedor"
            is com.mivisor.tv.catalog.InvalidPayloadException -> "Los datos del proveedor no tienen un formato válido"
            else -> "No se pudo leer el proveedor (${e.javaClass.simpleName})"
        }
    }
}
