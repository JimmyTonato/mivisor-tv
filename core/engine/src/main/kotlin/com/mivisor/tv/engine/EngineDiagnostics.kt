package com.mivisor.tv.engine

import com.mivisor.tv.diagnostics.LogSanitizer
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Diagnóstico del motor interno (lo que se ve en Ajustes → Diagnóstico del motor, sin ADB).
 *
 *  - Log en memoria con TAMAÑO MÁXIMO ([MAX_LINES]); nunca crece sin límite. Se puede activar/desactivar, copiar
 *    (al móvil por QR) y limpiar. Desactivado solo guarda los eventos de MiVisor marcados como importantes; las
 *    líneas del proceso del motor se leen igualmente (hay que vaciar su salida) pero no se guardan.
 *  - Cada línea lleva una categoría para distinguir el problema: RESOLUCIÓN, PEERS, TIMEOUT, DISCONTINUIDAD,
 *    MEDIA3, PROCESO, PUERTO, FUENTE, BUFFER, MEMORIA.
 *  - Content IDs, hashes, URLs y tokens se limpian ([LogSanitizer]); los IDs se muestran enmascarados (abcd…wxyz).
 *  - Métricas de la sesión actual (peers, bitrate, buffers, recuperaciones, cambios de fuente, primera imagen…).
 */
object EngineDiagnostics {
    const val MAX_LINES = 800
    private const val MAX_LINE_CHARS = 300

    enum class Cat(val label: String) {
        RESOLUTION("RESOLUCIÓN"), PEERS("PEERS"), TIMEOUT("TIMEOUT"), DISCONTINUITY("DISCONTINUIDAD"), MEDIA3("MEDIA3"),
        PROCESS("PROCESO"), PORT("PUERTO"), SOURCE("FUENTE"), BUFFER("BUFFER"), MEMORY("MEMORIA"), ENGINE("MOTOR")
    }

    data class Metrics(
        val engineMode: String = "—",
        val engineState: String = "detenido",
        val port: Int? = null,
        val channel: String? = null,
        val sourceIndex: Int = 0,
        val sourceCount: Int = 0,
        val sourceId: String? = null,
        val peers: Int = 0,
        val bitrateBps: Long = 0,
        val engineBufferMs: Long = 0,
        val playerBufferMs: Long = 0,
        val bufferTargetMs: Long = 0,
        val recoveries: Long = 0,
        val skippedGaps: Long = 0,
        val sourceSwitches: Int = 0,
        val rebuffers: Int = 0,
        val firstFrameMs: Long? = null,
        val livePositionMs: Long? = null,
        val deviceRamMb: Long = 0,
        val deviceAvailMb: Long = 0,
        val appUsedMb: Long = 0,
        val lowRam: Boolean = false,
        /** Entrega a Media3: "HLS" (control de retraso exacto) o "TS continuo" (retraso estimado). */
        val transport: String = "—",
        val liveTargetMs: Long = 0,
        val liveDelayMs: Long? = null,
        val liveDelayEstimated: Boolean = false,
        val liveResyncs: Int = 0,
        val liveDiscardedMs: Long = 0,
        val liveSpeedUps: Int = 0,
        val liveSpeedUpsDone: Int = 0,
        val liveSpeeding: Boolean = false,
        /** Piezas que el motor lleva de retraso respecto al borde del enjambre (él mismo salta si se aleja). */
        val enginePiecesBehind: Long? = null,
        /** 4e · «Caja negra»: qué traía el vídeo del último canal que tardó (formato y puntos de arranque). */
        val videoSample: String? = null
    ) {
        val liveDeviationMs: Long? get() = liveDelayMs?.let { it - liveTargetMs }
    }

    private val lines = ArrayDeque<String>()
    @Volatile var enabled: Boolean = false
    @Volatile var metrics: Metrics = Metrics()
        private set
    @Volatile var revision: Long = 0
        private set

    fun log(cat: Cat, text: String, important: Boolean = false) {
        if (!enabled && !important) return
        add("${stamp()} [${cat.label}] ${LogSanitizer.clean(text).take(MAX_LINE_CHARS)}")
    }

    /** Línea del proceso del motor: se clasifica por palabras clave para que se distinga el tipo de problema. */
    /**
     * 3t · Vías por las que el motor busca gente (parche 0002 de Outpace). Se guardan SIEMPRE, aunque el registro
     * detallado esté apagado, para enseñarlas en Ajustes › Motor y en el informe.
     */
    data class Vias(
        val trackers: String? = null, val porTracker: String? = null, val porDht: String? = null,
        /** 3u · Descriptor (parche 0003): «infohash=… firmado=… pieza=… titulo=…», sus trackers completos y qué
         *  servidor del catálogo lo dio (o los que fallaron). */
        val descriptor: String? = null, val descriptorTrackers: List<String> = emptyList(),
        val catalogo: String? = null, val catalogoFallos: List<String> = emptyList(),
        /** 3x · Campos del descriptor y su tipo («trackers:lista name:texto …»). */
        val claves: String? = null,
        /** 3y · Intentos de conexión con la gente encontrada, desde el último descriptor: resultado → veces. */
        val intentos: Map<String, Int> = emptyMap(),
        /** 3z · Si el router abrió el puerto (P2P completo) y si nos llegan conexiones de fuera. */
        val router: String? = null, val desdeFuera: String? = null,
        /** 4a · Qué emisión (infohash) dio cada servidor del catálogo y cuánta gente tiene cada una. */
        val emisionPorServidor: Map<String, String> = emptyMap(), val gentePorEmision: Map<String, Int> = emptyMap(),
        /** 4b · Qué dijo el catálogo de la emisión ACTUAL del descriptor (/checktorrent). */
        val emisionActual: String? = null,
        /** 4a · El último «descriptor:» cierra una resolución: la siguiente línea del catálogo empieza otra. */
        val resolucionCerrada: Boolean = false
    ) {
        /** «5.252.161.191:8081 → fe6ba458 (3 personas)», una por servidor que respondió. */
        fun emisionesTexto(): List<String> = emisionPorServidor.map { (host, ih) ->
            "$host → ${ih.take(8)}" + (gentePorEmision[ih]?.let { " ($it personas)" } ?: "")
        }

        /** «2 conectados · 5 no contestan · 1 cuelga al saludar» (en orden fijo, solo los que hay). */
        fun intentosTexto(): String? {
            if (intentos.isEmpty()) return null
            val names = linkedMapOf(
                "connected" to "conectados", "tcp" to "no contestan", "total_timeout" to "tardan demasiado",
                "handshake" to "cuelgan al saludar", "window" to "conectan pero no envían"
            )
            val parts = names.mapNotNull { (k, label) -> intentos[k]?.let { "$it $label" } } +
                intentos.filterKeys { it !in names }.map { (k, n) -> "$n $k" }
            return parts.joinToString(" · ")
        }
    }
    @Volatile var vias: Vias = Vias()
        private set

    /** Pura: actualiza [Vias] con una línea del motor, si es de las suyas. */
    fun parseVia(current: Vias, raw: String): Vias {
        // 3y · Intentos de conexión del motor (línea propia de Outpace, no del parche).
        val a = raw.indexOf("[ace] upstream attempt: result=")
        if (a >= 0) {
            val result = raw.substring(a + 31).substringBefore(' ').trim()
            if (result.isEmpty()) return current
            return current.copy(intentos = current.intentos + (result to (current.intentos[result] ?: 0) + 1))
        }
        // 3z · Apertura de puerto (UPnP/NAT-PMP) y alcanzabilidad, líneas propias de Outpace.
        raw.indexOf("[portmap] ").takeIf { it >= 0 }?.let { p ->
            val t = raw.substring(p + 10).trim()
            return when {
                t.startsWith("mapped external ") -> {
                    val ext = t.removePrefix("mapped external ").substringBefore(' ')
                    current.copy(router = "abrió el puerto ($ext, ${t.substringAfter(" via ", "").substringBefore(' ')})")
                }
                t.startsWith("no backend could map") -> current.copy(router = "no abrió el puerto (UPnP/NAT-PMP desactivado o no disponible)")
                t.contains("expired") -> current.copy(router = "el puerto abierto caducó")
                else -> current
            }
        }
        raw.indexOf("[reachability] externally reachable: ").takeIf { it >= 0 }?.let { p ->
            val t = raw.substring(p + 37).trim()
            val yes = t.startsWith("yes")
            val inbound = Regex("""\((\d+) inbound""").find(t)?.groupValues?.get(1) ?: "0"
            return current.copy(desdeFuera = (if (yes) "sí" else "no") + " · $inbound conexiones entrantes")
        }
        val i = raw.indexOf("[mivisor] ")
        if (i < 0) return current
        val text = raw.substring(i + 10).trim()
        return when {
            text.startsWith("trackers del descriptor:") -> current.copy(trackers = text.removePrefix("trackers del descriptor:").trim())
            text.startsWith("pares por tracker:") -> current.copy(porTracker = text.removePrefix("pares por tracker:").trim())
            text.startsWith("pares por dht:") -> current.copy(porDht = text.removePrefix("pares por dht:").trim())
            text.startsWith("descriptor claves:") -> current.copy(claves = text.removePrefix("descriptor claves:").trim())
            text.startsWith("descriptor trackers:") -> current.copy(descriptorTrackers =
                text.removePrefix("descriptor trackers:").trim().split(' ').filter { it.isNotBlank() && it != "ninguno" })
            text.startsWith("descriptor del catalogo") -> current.copy(catalogo = text.removePrefix("descriptor del catalogo").trim())
            text.startsWith("descriptor:") -> current.copy(descriptor = text.removePrefix("descriptor:").trim(), intentos = emptyMap(),
                resolucionCerrada = true)
            text.startsWith("catalogo ") -> {
                // Una resolución nueva empieza: se olvida lo de la anterior.
                val base = if (current.resolucionCerrada) current.copy(catalogoFallos = emptyList(),
                    emisionPorServidor = emptyMap(), gentePorEmision = emptyMap(), emisionActual = null,
                    resolucionCerrada = false) else current
                val rest = text.removePrefix("catalogo ").trim()
                if (" da emision " in rest) base.copy(emisionPorServidor = base.emisionPorServidor +
                    (rest.substringBefore(" da emision ") to rest.substringAfter(" da emision ").trim()))
                else base.copy(catalogoFallos = (base.catalogoFallos + rest).takeLast(6))
            }
            text.startsWith("checktorrent: emision actual ") -> {
                val now = text.removePrefix("checktorrent: emision actual ").substringBefore(' ')
                val old = text.substringAfter("daba ", "").substringBefore(')')
                current.copy(emisionActual = "${now.take(8)}… (el descriptor daba ${old.take(8)}…, emisión antigua)")
            }
            text.startsWith("checktorrent: la emision del descriptor es la actual") ->
                current.copy(emisionActual = "la del descriptor (al día)")
            text.startsWith("checktorrent: sin respuesta valida") ->
                current.copy(emisionActual = "el catálogo no contestó; se usa la del descriptor")
            text.startsWith("emision ") && " tiene " in text -> {
                val ih = text.removePrefix("emision ").substringBefore(' ')
                val n = text.substringAfter(" tiene ").substringBefore(' ').toIntOrNull() ?: 0
                current.copy(gentePorEmision = current.gentePorEmision + (ih to n))
            }
            else -> current
        }
    }

    fun engineLine(raw: String) {
        if ("[mivisor] " in raw || "[ace] upstream attempt:" in raw || "[portmap] " in raw || "[reachability] " in raw)
            vias = parseVia(vias, raw)
        if (!enabled) return
        add("${stamp()} [motor/${classify(raw).label}] ${LogSanitizer.clean(raw).take(MAX_LINE_CHARS)}")
    }

    fun update(transform: (Metrics) -> Metrics) {
        metrics = transform(metrics)
        revision++
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() {
        lines.clear()
        revision++
    }

    /** Texto para copiar (al móvil por QR) con las métricas y el log. */
    fun report(): String = buildString {
        val m = metrics
        appendLine("Diagnóstico del motor de reproducción · ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("Motor seleccionado: ${m.engineMode} · estado: ${m.engineState} · puerto localhost: ${m.port ?: "—"}")
        appendLine("Canal: ${m.channel ?: "—"} · fuente ${m.sourceIndex}/${m.sourceCount} · ${m.sourceId ?: "—"}")
        appendLine("Peers: ${m.peers} · bitrate: ${"%.2f".format(Locale.US, m.bitrateBps / 1_000_000.0)} Mb/s")
        vias.let { v ->
            appendLine("Trackers del descriptor: ${v.trackers ?: "—"}")
            appendLine("Pares encontrados · trackers: ${v.porTracker ?: "—"} · DHT: ${v.porDht ?: "—"}")
            appendLine("Descriptor: ${v.descriptor ?: "—"} · del catálogo ${v.catalogo ?: "—"}")
            appendLine("Trackers completos: ${v.descriptorTrackers.joinToString(" ").ifEmpty { "ninguno" }}")
            appendLine("Campos del descriptor: ${v.claves ?: "—"}")
            appendLine("Conexiones con la gente: ${v.intentosTexto() ?: "—"}")
            appendLine("Router: ${v.router ?: "—"} · Desde fuera: ${v.desdeFuera ?: "—"}")
            if (v.catalogoFallos.isNotEmpty()) appendLine("Catálogo sin descriptor: ${v.catalogoFallos.joinToString(" · ")}")
            if (v.emisionPorServidor.isNotEmpty()) appendLine("Emisiones por servidor: ${v.emisionesTexto().joinToString(" · ")}")
            appendLine("Emisión actual (checktorrent): ${v.emisionActual ?: "—"}")
        }
        appendLine("Buffer motor: ${m.engineBufferMs} ms · buffer Media3: ${m.playerBufferMs} ms (objetivo ${m.bufferTargetMs} ms)")
        appendLine("Directo (${m.transport}): objetivo ${m.liveTargetMs} ms · retraso ${m.liveDelayMs?.let { "$it ms" + if (m.liveDelayEstimated) " (estimado)" else "" } ?: "—"} · desviación ${m.liveDeviationMs?.let { (if (it >= 0) "+" else "") + it + " ms" } ?: "—"}")
        appendLine("Resyncs: ${m.liveResyncs} (${m.liveDiscardedMs / 1000.0} s descartados) · recuperaciones suaves 1.06x: ${m.liveSpeedUpsDone}/${m.liveSpeedUps}" + (if (m.liveSpeeding) " (acelerando ahora)" else "") + " · motor: ${m.enginePiecesBehind?.let { "$it piezas tras el borde" } ?: "—"}")
        appendLine("Recuperaciones: ${m.recoveries} · huecos saltados: ${m.skippedGaps} · cambios de fuente: ${m.sourceSwitches} · cortes: ${m.rebuffers}")
        appendLine("Primera imagen: ${m.firstFrameMs?.let { "$it ms" } ?: "—"} · posición en directo: ${m.livePositionMs?.let { "$it ms" } ?: "—"}")
        appendLine("Vídeo del último canal que tardó: ${m.videoSample ?: "—"}")
        appendLine("RAM dispositivo: ${m.deviceRamMb} MB (libre ${m.deviceAvailMb} MB) · MiVisor: ${m.appUsedMb} MB · low-RAM: ${if (m.lowRam) "sí" else "no"}")
        appendLine()
        snapshot().forEach { appendLine(it) }
    }

    fun classify(line: String): Cat {
        val l = line.lowercase(Locale.ROOT)
        return when {
            "address in use" in l || "addrinuse" in l || "bind" in l && "fail" in l -> Cat.PORT
            "catalog" in l || "descriptor" in l || "resolv" in l || "bep9" in l || "bep-9" in l -> Cat.RESOLUTION
            "discontinu" in l || "keyframe" in l || "gap" in l || "skipped" in l -> Cat.DISCONTINUITY
            "peer" in l || "tracker" in l || "dht" in l || "upstream" in l -> Cat.PEERS
            "timeout" in l || "timed out" in l -> Cat.TIMEOUT
            "panic" in l || "fatal" in l || "error" in l -> Cat.PROCESS
            else -> Cat.ENGINE
        }
    }

    @Synchronized
    private fun add(line: String) {
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        revision++
    }

    private fun stamp() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
}
