package com.mivisor.tv.data

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import com.mivisor.tv.AppGraph
import com.mivisor.tv.BuildConfig
import com.mivisor.tv.backup.BackupCodec
import com.mivisor.tv.backup.BackupData
import com.mivisor.tv.backup.BackupFavorite
import com.mivisor.tv.backup.BackupProvider
import com.mivisor.tv.backup.BackupReminder
import com.mivisor.tv.diagnostics.DiagnosticReport
import com.mivisor.tv.diagnostics.ReportFacts
import com.mivisor.tv.engine.EngineApi
import com.mivisor.tv.engine.EngineSupervisor
import com.mivisor.tv.model.Reminder
import com.mivisor.tv.outpace.EngineManager
import com.mivisor.tv.services.WarpPhase
import com.mivisor.tv.storage.CodeVault
import com.mivisor.tv.warp.WarpSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Resultado de una comprobación de «Probar todo». */
data class CheckResult(val name: String, val detail: String, val level: Level) {
    enum class Level { RUNNING, OK, WARN, BAD, OPTIONAL }
}

/**
 * Diagnóstico (3d): informe para enviar, «Probar todo», copias cifradas y verificador manual.
 * Todo con la app abierta; nada se queda trabajando en segundo plano.
 */
class Diagnostics(private val graph: AppGraph) {
    private val app: Context = graph.app
    private val es = Locale("es", "ES")

    // ------------------------------------------------------------------ informe

    fun device(): String {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val ram = am?.let { ActivityManager.MemoryInfo().also(it::getMemoryInfo).totalMem } ?: 0L
        val dm = app.resources.displayMetrics
        val tv = if (app.packageManager.hasSystemFeature("android.software.leanback")) "Android TV" else "Android"
        return "$tv ${Build.VERSION.RELEASE} · ${abi()} · ${"%.1f".format(es, ram / 1_073_741_824.0)} GB · ${dm.widthPixels}×${dm.heightPixels}"
    }

    private fun abi(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "ARM64"
        "armeabi-v7a" -> "ARMv7"
        null -> "?"
        else -> Build.SUPPORTED_ABIS.first()
    }

    fun network(): String {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "desconocida"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return "sin red") ?: return "sin red"
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Cable"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Móvil"
            else -> "Otra"
        }
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return "$kind · ${if (online) "Internet OK" else "sin Internet"}" + if (NetState.vpnActive(app) == true) " · VPN activa" else ""
    }

    private fun engineText(): String {
        val s = EngineManager.state.value
        return when (s.phase) {
            EngineSupervisor.Phase.READY -> "Outpace en marcha · 127.0.0.1:${s.port}"
            EngineSupervisor.Phase.STARTING -> "Outpace arrancando"
            EngineSupervisor.Phase.FAILED -> "Outpace con error: ${s.message ?: "sin detalle"}"
            EngineSupervisor.Phase.STOPPED -> "Outpace parado (arranca al ver un canal)"
        } + " · commit ${BuildConfig.OUTPACE_COMMIT.take(12)}"
    }

    private fun warpText(): String {
        val r = WarpSignature.report(app)
        val phase = when (graph.warp.state.value.phase) {
            WarpPhase.CONNECTED -> "conectada"
            WarpPhase.DISCONNECTED -> "desconectada"
            WarpPhase.NOT_INSTALLED -> "no instalada"
            WarpPhase.SIGNATURE_MISMATCH -> "otra firma"
            else -> graph.warp.state.value.phase.name.lowercase()
        }
        val sig = when {
            r.warp == null -> ""
            r.sameSigner -> " · firma correcta"
            else -> " · firma distinta (no se puede controlar)"
        }
        return phase + sig
    }

    suspend fun facts(): ReportFacts = withContext(Dispatchers.IO) {
        val lib = graph.providers.state.value
        val agenda = graph.agenda.state.value
        val hm = SimpleDateFormat("HH:mm", es)
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val avail = am?.let { ActivityManager.MemoryInfo().also(it::getMemoryInfo).availMem shr 20 } ?: 0
        ReportFacts(
            app = "MiVisor TV ${BuildConfig.VERSION_NAME}",
            createdAt = SimpleDateFormat("dd/MM/yyyy HH:mm", es).format(Date()),
            device = device(),
            network = network(),
            engine = engineText(),
            ace = if (ExternalApps.aceInstalled(app)) "instalada" else "no instalada",
            warp = warpText(),
            providers = "${lib.providers.size} · ${lib.library.channels.size} canales · códigos ocultos" +
                lib.providers.count { it.error != null }.let { if (it > 0) " · $it con error" else "" } +
                // 4f · Biblioteca de MiVisor Gold (nunca la contraseña).
                graph.gold.state.value.let { g ->
                    if (!g.configured) " · Biblioteca Gold: no"
                    else " · Biblioteca Gold: rev. ${g.revision}, ${g.channels} canales, ${g.ids} IDs" +
                        (if (lib.library.gold) " (en pantalla)" else "") + (g.error?.let { " · $it" } ?: "")
                },
            agenda = agenda.agenda?.let { "generada ${hm.format(Date(it.generatedAt))} · descargada ${hm.format(Date(agenda.fetchedAt))}" + if (agenda.offline) " · copia guardada" else "" }
                ?: (agenda.error ?: "sin agenda"),
            quarantined = graph.sources.resting().size,
            memory = "$avail MB",
            storage = "%.1f GB".format(es, app.filesDir.usableSpace / 1_073_741_824.0)
        )
    }

    /** Últimas líneas del registro, ya limpias, con hora. */
    fun recentLines(limit: Int = 40): List<String> {
        val hm = SimpleDateFormat("HH:mm", es)
        return graph.log.recent(limit).map { "${hm.format(Date(it.t))}  ${it.cat}  ${it.text}" }
    }

    suspend fun report(): String {
        val f = facts()
        // 3g · Tiempos de este arranque al principio del registro del informe.
        return DiagnosticReport.build(f, PerfLog.lines(app).map { "Rendimiento  $it" } + recentLines(), graph.redactor)
    }

    // ------------------------------------------------------------------ «Probar todo»

    /** Comprobaciones en orden; [onUpdate] recibe la lista según avanza. */
    suspend fun checkAll(onUpdate: (List<CheckResult>) -> Unit) {
        val out = ArrayList<CheckResult>()
        suspend fun step(name: String, block: suspend () -> CheckResult) {
            out += CheckResult(name, "Comprobando…", CheckResult.Level.RUNNING)
            onUpdate(out.toList())
            val r = runCatching { block() }.getOrElse { CheckResult(name, "No se pudo comprobar (${it.javaClass.simpleName})", CheckResult.Level.BAD) }
            out[out.lastIndex] = r
            onUpdate(out.toList())
        }
        step("Motor Outpace") {
            val unsupported = EngineManager.unsupportedReason(app)
            if (unsupported != null) return@step CheckResult("Motor Outpace", unsupported, CheckResult.Level.BAD)
            val t0 = SystemClock.elapsedRealtime()
            val s = EngineManager.ensureStarted(app)
            val ms = SystemClock.elapsedRealtime() - t0
            if (s.baseUrl != null) CheckResult("Motor Outpace", "Arranca y responde en ${"%.1f".format(es, ms / 1000.0)} s", CheckResult.Level.OK)
            else CheckResult("Motor Outpace", s.message ?: "No arrancó", CheckResult.Level.BAD)
        }
        step("Red") {
            val online = NetState.online(app)
            CheckResult("Red", network(), if (online == false) CheckResult.Level.BAD else CheckResult.Level.OK)
        }
        step("WARP") {
            val st = runCatching { graph.warp.refresh() }.getOrNull()?.phase ?: graph.warp.state.value.phase
            val r = WarpSignature.report(app)
            when {
                st == WarpPhase.NOT_INSTALLED -> CheckResult("WARP", "No instalada (es opcional)", CheckResult.Level.OPTIONAL)
                !r.sameSigner -> CheckResult("WARP", "Firma distinta: MiVisor TV no puede controlarla", CheckResult.Level.BAD)
                st == WarpPhase.CONNECTED -> CheckResult("WARP", "Firma correcta · conectada", CheckResult.Level.OK)
                else -> CheckResult("WARP", "Firma correcta · ${warpText()}", CheckResult.Level.OK)
            }
        }
        step("Ace Stream") {
            if (ExternalApps.aceInstalled(app)) CheckResult("Ace Stream", "Instalada (alternativa a Outpace)", CheckResult.Level.OK)
            else CheckResult("Ace Stream", "No instalada (es opcional)", CheckResult.Level.OPTIONAL)
        }
        step("Proveedores") {
            val lib = graph.providers.state.value
            val bad = lib.providers.filter { it.error != null }
            when {
                lib.providers.isEmpty() -> CheckResult("Proveedores", "Ninguno configurado", CheckResult.Level.WARN)
                bad.isNotEmpty() -> CheckResult("Proveedores", "${bad.size} sin actualizar: ${bad.joinToString { it.name }}", CheckResult.Level.WARN)
                else -> CheckResult("Proveedores", "${lib.providers.size} leídos · ${lib.library.channels.size} canales", CheckResult.Level.OK)
            }
        }
        step("Agenda (GitHub)") {
            val a = graph.agenda.state.value
            val ag = a.agenda
            when {
                ag == null -> CheckResult("Agenda (GitHub)", a.error ?: "No se pudo descargar", CheckResult.Level.BAD)
                a.offline -> CheckResult("Agenda (GitHub)", "Copia guardada: no se pudo actualizar", CheckResult.Level.WARN)
                else -> CheckResult("Agenda (GitHub)", "Al día · generada ${SimpleDateFormat("HH:mm", es).format(Date(ag.generatedAt))}", CheckResult.Level.OK)
            }
        }
        step("Servidor del móvil") {
            val lan = AndroidLan.pick(app)
            if (lan == null) CheckResult("Servidor del móvil", "La tele no está en una red de casa (Wi-Fi o cable)", CheckResult.Level.WARN)
            else CheckResult("Servidor del móvil", "Se abrirá en la red de casa (${lan.interfaceName}) al usar un QR", CheckResult.Level.OK)
        }
        graph.log.add("Diagnóstico", "Probar todo: " + out.joinToString(" · ") { "${it.name} ${it.level.name.lowercase()}" })
    }

    // ------------------------------------------------------------------ copias cifradas

    class Export(val fileName: String, val bytes: ByteArray, val pin: String, val summary: String)

    /** Copia de proveedores (con su código), favoritos, recordatorios y ajustes, cifrada con un PIN nuevo. */
    suspend fun export(): Export = withContext(Dispatchers.IO) {
        val vault = CodeVault()
        val providers = graph.db.providers().mapNotNull { row ->
            vault.open(row.sealedCode)?.let { BackupProvider(row.name, it, row.id in graph.prefs.warpProviders) }
        }
        val data = BackupData(
            createdAt = System.currentTimeMillis(),
            providers = providers,
            favorites = graph.db.favorites().map { BackupFavorite(it.first, it.second) },
            reminders = graph.reminders.list.value.map { BackupReminder(it.eventId, it.title, it.startMillis, it.sport, it.competition, it.channels) },
            settings = graph.prefs.exportable()
        )
        val pin = BackupCodec.newPin()
        val bytes = BackupCodec.encrypt(data, pin)
        graph.log.add("Copias", "Copia exportada: ${data.summary()}")
        Export(BackupCodec.fileName(SimpleDateFormat("yyyy-MM-dd", es).format(Date())), bytes, pin, data.summary())
    }

    suspend fun open(bytes: ByteArray, pin: String): BackupCodec.Result = withContext(Dispatchers.Default) { BackupCodec.decrypt(bytes, pin) }

    /**
     * Importa SIN borrar nada: añade los proveedores que falten (comprobando cada código como al añadirlo a mano),
     * junta favoritos y recordatorios y aplica los ajustes. Devuelve un resumen.
     */
    suspend fun import(data: BackupData): String {
        var added = 0
        var failed = 0
        for (p in data.providers) {
            val problem = graph.providers.add(p.code, p.name, p.usesWarp)
            when {
                problem == null -> added++
                problem.contains("ya está añadido") -> Unit
                else -> failed++
            }
        }
        withContext(Dispatchers.IO) {
            val existing = graph.db.favorites().map { it.first }.toSet()
            data.favorites.filter { it.channelId !in existing }.forEach { graph.db.setFavorite(it.channelId, it.name, true) }
        }
        graph.mylist.load()
        val now = System.currentTimeMillis()
        data.reminders.filter { it.startMillis > now - 3 * 3600_000L && !graph.reminders.isSet(it.eventId) }.forEach {
            graph.reminders.restore(Reminder(it.eventId, it.title, it.startMillis, it.sport, it.competition, it.channels))
        }
        graph.prefs.import(data.settings)
        graph.agenda.load(force = true)
        val text = "Importada: $added proveedor(es) nuevo(s)" + (if (failed > 0) ", $failed no se pudieron leer ahora" else "") +
            ", ${data.favorites.size} favoritos y ${data.reminders.size} recordatorios revisados, ajustes aplicados"
        graph.log.add("Copias", text)
        return text
    }

    // ------------------------------------------------------------------ verificador manual

    /**
     * «Comprobar ahora» (3d): con la app abierta, pide a Outpace cada fuente de tus favoritos (o de todos los canales)
     * y apunta las que responden. No penaliza nunca: una fuente que ahora no responde sigue igual.
     */
    suspend fun verify(
        onProgress: (done: Int, total: Int, ok: Int) -> Unit,
        isCancelled: () -> Boolean,
        /** 3k · Solo las fuentes de este proveedor (null = todos). */
        providerId: String? = null
    ): String {
        val lib = graph.providers.state.value.library
        // Sin favoritos, «Solo mis favoritos» no dejaría nada que comprobar: se usan todos los canales.
        val favs = graph.mylist.state.value.favorites.mapNotNull { lib.channel(it.first) }
        // 3h.1 · Primero lo que más ves: favoritos, luego lo último visto y después el resto.
        val seen = graph.mylist.state.value.history.mapNotNull { lib.channel(it.channelId) }
        val channels = if (providerId == null && graph.prefs.verifierFavoritesOnly && favs.isNotEmpty()) favs
        else (favs + seen + lib.channels).distinctBy { it.id }
        // Cada fuente con el canal al que pertenece (para decir cuáles arrancan antes).
        val pairs = channels.flatMap { ch -> ch.sources.filter { providerId == null || it.providerId == providerId }.map { ch to it } }
            .distinctBy { it.second.contentId }.take(VERIFY_MAX)
        val sources = pairs.map { it.second }
        if (sources.isEmpty()) return "No hay fuentes que comprobar"
        val times = ArrayList<Pair<String, Long>>()
        val engine = EngineManager.ensureStarted(app)
        val base = engine.baseUrl ?: return "Outpace no arrancó: ${engine.message ?: "sin detalle"}"
        var ok = 0
        var bad = 0
        sources.forEachIndexed { i, s ->
            if (isCancelled()) return "Comprobación cancelada · $ok de $i con imagen"
            val t0 = SystemClock.elapsedRealtime()
            val good = withContext(Dispatchers.IO) { probe(EngineApi.hlsUrl(base, s.contentId)) }
            if (good) {
                ok++
                val ms = SystemClock.elapsedRealtime() - t0
                graph.sources.verifiedOk(s.contentId, ms)
                times += pairs[i].first.name to ms
            } else if (graph.sources.verifiedBad(com.mivisor.tv.playback.PlaySource(s.contentId, s.providerId, s.providerName, s.quality, s.qualityLabel))) {
                // 3h.1 · Sin imagen: nota más baja (sin cuarentena), así al reproducir se prueba antes la que sí va.
                bad++
            }
            withContext(Dispatchers.IO) { runCatching { EngineApi.stop(base, s.contentId) } }
            onProgress(i + 1, sources.size, ok)
        }
        // 3k · Los canales que antes arrancan (la mejor fuente de cada uno) y el tiempo medio.
        val fastest = times.groupBy { it.first }.map { (name, l) -> name to l.minOf { it.second } }.sortedBy { it.second }.take(3)
        val avg = if (times.isNotEmpty()) times.sumOf { it.second } / times.size / 1000.0 else null
        val text = "${SimpleDateFormat("dd/MM HH:mm", es).format(Date())} · ${sources.size} fuentes · $ok con imagen" +
            (if (bad > 0) " · $bad sin imagen" else "") +
            (avg?.let { " · arranque medio %.1f s".format(es, it) } ?: "") +
            (if (fastest.isNotEmpty()) " · más rápidos: " + fastest.joinToString(", ") { "${it.first} (%.1f s)".format(es, it.second / 1000.0) } else "")
        if (providerId == null) graph.prefs.verifierLast = text
        else graph.prefs.verifierByProvider = graph.prefs.verifierByProvider + (providerId to text)
        val who = providerId?.let { id -> graph.providers.state.value.providers.firstOrNull { it.id == id }?.name }
        graph.log.add("Verificador", (who?.let { "$it: " } ?: "") + text)
        return text
    }

    /** La lista HLS de la fuente llega con su primer segmento: la fuente da imagen. */
    private fun probe(url: String): Boolean {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
            c.connectTimeout = 3_000
            c.readTimeout = 25_000
            c.responseCode == 200 && c.inputStream.bufferedReader().use { r -> r.readText().contains("#EXTINF") }
        } catch (e: Exception) {
            false
        } finally {
            c?.disconnect()
        }
    }

    private companion object {
        /** Fuentes por comprobación (cada una tarda de 2 a 28 s): favoritos y lo último visto entran primero. */
        const val VERIFY_MAX = 80
    }
}
