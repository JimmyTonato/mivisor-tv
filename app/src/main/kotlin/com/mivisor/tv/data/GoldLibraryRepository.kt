package com.mivisor.tv.data

import android.content.Context
import com.mivisor.tv.catalog.biblioteca.Biblioteca
import com.mivisor.tv.catalog.biblioteca.BibliotecaCrypto
import com.mivisor.tv.catalog.biblioteca.BibliotecaStore
import com.mivisor.tv.catalog.biblioteca.BibliotecaSync
import com.mivisor.tv.catalog.datos.GitHubData
import com.mivisor.tv.catalog.datos.HttpFetch
import com.mivisor.tv.catalog.logos.ChannelLogoResolver
import com.mivisor.tv.catalog.logos.LogoRepository
import com.mivisor.tv.catalog.tv.GoldLibrary
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.engine.EngineKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 4f · Biblioteca de canales que publica MiVisor Gold (rama `biblioteca` de mivisor-datos, cifrada).
 *
 *  - La contraseña la escribe el usuario en Ajustes › Biblioteca y se guarda cifrada con el Keystore.
 *  - Al abrir la app se enseña la copia guardada al momento; luego se mira el manifest y se baja solo lo que cambió.
 *  - Con la app abierta se vuelve a mirar cada [CHECK_EVERY_MS] (nunca en bucle; nada con la app cerrada).
 *  - Si algo falla (red, contraseña, caché de GitHub), se sigue con la copia guardada sin tocarla.
 *  - La app NO ordena ni filtra: lo que llega ya viene ordenado y verificado por Gold.
 */
class GoldLibraryRepository(
    app: Context,
    private val scope: CoroutineScope,
    private val agendaUrl: () -> String,
    private val providers: ProvidersRepository,
    private val logos: LogoRepository,
    private val log: LogBook
) {
    data class State(
        val configured: Boolean = false,
        val revision: Long = 0,
        val published: String? = null,
        val channels: Int = 0,
        val ids: Int = 0,
        val categories: Int = 0,
        val checkedAt: Long = 0,
        val busy: Boolean = false,
        val wrongPassword: Boolean = false,
        val note: String = "",
        val error: String? = null
    )

    private val passwords = GoldPasswordStore(app)
    private val store = BibliotecaStore(File(app.filesDir, "biblioteca"))
    private val sync = BibliotecaSync({ GitHubData.biblioteca(agendaUrl()) }, ::fetch)
    private val lock = Mutex()
    private val _state = MutableStateFlow(State(configured = passwords.isSet))
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var current: Biblioteca? = null
    @Volatile private var shown: TvLibrary? = null
    @Volatile private var builtWith: ChannelLogoResolver? = null
    private var ticker: Job? = null

    /** Al abrir la app: la copia guardada a la pantalla YA; después, el manifest. */
    fun start() {
        scope.launch {
            val saved = withContext(Dispatchers.IO) { store.load() }
            if (saved != null && passwords.isSet) {
                current = saved
                publish(saved, touched = null, touchedCats = emptySet())
                _state.value = _state.value.copy(revision = saved.revision, channels = saved.canales.size,
                    ids = saved.canales.values.sumOf { it.ids.size }, categories = saved.categorias.size)
            }
            check(force = true)
        }
        // Llegan logos nuevos: se vuelven a poner (los canales se rehacen enteros, es poco trabajo).
        scope.launch {
            providers.logoResolver.drop(1).filterNotNull().collect { r ->
                val bib = current ?: return@collect
                if (r !== builtWith) publish(bib, touched = null, touchedCats = emptySet(), rebuild = true)
            }
        }
    }

    /** Con la app en pantalla: una comprobación cada [CHECK_EVERY_MS]. */
    fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            // Al volver a la app tras un rato, se mira ya si toca (check se lo salta si se miró hace poco).
            while (true) {
                if (passwords.isSet && _state.value.checkedAt > 0) check(force = false)
                delay(CHECK_EVERY_MS)
            }
        }
    }

    fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    /** Guarda la contraseña (cifrada) y comprueba al momento. */
    fun setPassword(password: CharArray, onDone: (String) -> Unit = {}) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { passwords.save(password) }.isSuccess }
            password.fill(' ')
            if (!ok) { onDone("No se pudo guardar la contraseña en este aparato"); return@launch }
            BibliotecaCrypto.forgetKeys()
            _state.value = _state.value.copy(configured = true, wrongPassword = false, error = null)
            log.add("Biblioteca", "Contraseña guardada (cifrada en el aparato)")
            onDone(check(force = true))
        }
    }

    /** Olvida la contraseña y la copia: la app vuelve a los canales de los proveedores. */
    fun forget() {
        scope.launch {
            lock.withLock {
                withContext(Dispatchers.IO) { passwords.clear(); store.clear() }
                BibliotecaCrypto.forgetKeys()
                EngineKeys.clear()
                current = null
                shown = null
                providers.setGoldLibrary(null)
                _state.value = State(configured = false, note = "Biblioteca quitada: se usan los proveedores")
                log.add("Biblioteca", "Contraseña y copia olvidadas")
            }
        }
    }

    fun checkNow(onDone: (String) -> Unit = {}) {
        scope.launch { onDone(check(force = true)) }
    }

    /** Mira el manifest y aplica lo que haga falta. Devuelve una frase para enseñar. */
    private suspend fun check(force: Boolean): String = lock.withLock {
        if (!force && System.currentTimeMillis() - _state.value.checkedAt < CHECK_EVERY_MS / 2) return@withLock _state.value.note
        val password = withContext(Dispatchers.IO) { passwords.load() }
        if (password == null) {
            val msg = if (passwords.isSet) "Escribe otra vez la contraseña de la Biblioteca (este aparato ya no puede leerla)" else ""
            _state.value = _state.value.copy(configured = passwords.isSet, note = msg)
            return@withLock msg
        }
        _state.value = _state.value.copy(busy = true)
        val outcome = try {
            withContext(Dispatchers.IO) { sync.sync(current, password) }
        } catch (e: CancellationException) {
            throw e
        } finally {
            password.fill(' ')
        }
        val now = System.currentTimeMillis()
        val s = _state.value.copy(busy = false, checkedAt = now, configured = true)
        val msg = when (outcome) {
            is BibliotecaSync.Outcome.Unchanged -> {
                _state.value = s.copy(wrongPassword = false, error = null, published = outcome.manifest.publicado, note = "Al día (revisión ${outcome.manifest.revision})")
                _state.value.note
            }
            is BibliotecaSync.Outcome.Updated -> {
                val lib = outcome.result.biblioteca
                val saved = withContext(Dispatchers.IO) { runCatching { store.save(lib) } }
                if (saved.isFailure) {
                    _state.value = s.copy(error = "No se pudo guardar la Biblioteca en el aparato: se sigue con la anterior")
                    return@withLock _state.value.error!!
                }
                val old = current
                current = lib
                val (touched, touchedCats) = if (old == null) null to emptySet() else diff(old, lib)
                publish(lib, touched, touchedCats)
                val what = if (outcome.full) "completa" else "${outcome.result.canales.size} canales cambiados"
                log.add("Biblioteca", "Revisión ${lib.revision} ($what)")
                _state.value = s.copy(
                    wrongPassword = false, error = null, revision = lib.revision, published = outcome.manifest.publicado,
                    channels = lib.canales.size, ids = lib.canales.values.sumOf { it.ids.size }, categories = lib.categorias.size,
                    note = "Actualizada a la revisión ${lib.revision} ($what)"
                )
                _state.value.note
            }
            is BibliotecaSync.Outcome.NotReady -> {
                _state.value = s.copy(note = "Hay una versión nueva, pero GitHub aún sirve la anterior: se reintenta en un rato")
                _state.value.note
            }
            BibliotecaSync.Outcome.WrongPassword -> {
                log.add("Biblioteca", "Contraseña de la Biblioteca incorrecta")
                _state.value = s.copy(wrongPassword = true, error = "Contraseña de la Biblioteca incorrecta")
                _state.value.error!!
            }
            is BibliotecaSync.Outcome.Failed -> {
                val keep = if (current != null) ": se sigue con la copia guardada" else ""
                _state.value = s.copy(error = "No se pudo actualizar (${outcome.why})$keep")
                _state.value.error!!
            }
        }
        msg
    }

    /** Pasa la Biblioteca a la pantalla, reutilizando los canales que no cambiaron, y los descriptores al motor. */
    private val publishLock = Mutex()

    private suspend fun publish(bib: Biblioteca, touched: Set<String>?, touchedCats: Set<String>, rebuild: Boolean = false) = publishLock.withLock {
        val resolver = providers.logoResolver.value ?: runCatching { logos.cached() }.getOrNull()
        val tv = withContext(Dispatchers.Default) {
            if (resolver != null) GoldLibrary.build(bib, resolver, if (rebuild) null else shown, touched, touchedCats)
            else GoldLibrary.build(bib, previous = if (rebuild) null else shown, touchedChannels = touched, touchedCategories = touchedCats)
        }
        builtWith = resolver
        shown = tv
        EngineKeys.replaceAll(
            bib.canales.values.flatMap { c -> c.ids.mapNotNull { id -> id.verificacion?.descriptor?.base64?.let { id.assetId to it } } }.toMap()
        )
        providers.setGoldLibrary(tv)
    }

    /** Qué canales y categorías cambiaron entre dos copias (por su línea tal cual). */
    private fun diff(old: Biblioteca, new: Biblioteca): Pair<Set<String>, Set<String>> {
        val ch = HashSet<String>()
        for ((id, c) in new.canales) if (old.canales[id]?.raw != c.raw) ch += id
        for (id in old.canales.keys) if (id !in new.canales) ch += id
        val cats = HashSet<String>()
        for ((id, c) in new.categorias) if (old.categorias[id]?.raw != c.raw) cats += id
        for (id in old.categorias.keys) if (id !in new.categorias) cats += id
        return ch to cats
    }

    private fun fetch(url: String): ByteArray {
        val r = HttpFetch.get(url, null, MAX_FILE)
        return r.body ?: throw IOException("HTTP ${r.status}")
    }

    companion object {
        /** Cada cuánto se mira el manifest con la app abierta (el contrato pide 30–60 min). */
        const val CHECK_EVERY_MS = 45 * 60_000L
        private const val MAX_FILE = 32 * 1024 * 1024
    }
}
