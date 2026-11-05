package com.mivisor.tv.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 3g · Tiempos reales de ESTE aparato (no de una simulación): cuánto tarda cada fase del arranque y de las recargas.
 * Se ven en Ajustes › Diagnóstico › «Rendimiento» y van en el informe al móvil, para optimizar con datos del TV stick.
 * Solo en memoria (se renueva en cada arranque) y sin datos personales: nombres de fase, milisegundos y recuentos.
 */
object PerfLog {
    class Mark(val name: String, val ms: Long, val detail: String?)

    @Volatile private var started = SystemClock.elapsedRealtime()
    private val done = HashSet<String>()
    private val _marks = MutableStateFlow<List<Mark>>(emptyList())
    val marks: StateFlow<List<Mark>> = _marks.asStateFlow()

    /** Al crear la app: el «cero» de los tiempos desde que se abre. */
    fun start() {
        started = SystemClock.elapsedRealtime()
    }

    fun now(): Long = SystemClock.elapsedRealtime()

    /** Apunta (o sustituye) la fase [name]. */
    @Synchronized
    fun record(name: String, ms: Long, detail: String? = null) {
        _marks.value = _marks.value.filter { it.name != name } + Mark(name, ms, detail)
    }

    /** Apunta [name] una sola vez por arranque, medido desde que se abrió la app. */
    @Synchronized
    fun once(name: String, detail: String? = null) {
        if (!done.add(name)) return
        record(name, now() - started, detail)
    }

    /** Mide [block] y lo apunta como [name]. */
    inline fun <T> time(name: String, detail: (T) -> String? = { null }, block: () -> T): T {
        val t0 = now()
        val r = block()
        record(name, now() - t0, detail(r))
        return r
    }

    /** Memoria y procesador del aparato, para saber con qué se mide. */
    fun device(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val ramMb = info.totalMem / (1024 * 1024)
        val low = if (am?.isLowRamDevice == true) " · poca memoria" else ""
        return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · ${ramMb} MB RAM (app: ${am?.memoryClass ?: 0} MB)$low · " +
            "${Runtime.getRuntime().availableProcessors()} núcleos"
    }

    fun lines(context: Context): List<String> =
        listOf("Aparato: " + device(context)) + _marks.value.map { m -> "${m.name}: ${format(m.ms)}" + (m.detail?.let { " · $it" } ?: "") }

    fun format(ms: Long): String = if (ms < 1000) "$ms ms" else "%.1f s".format(java.util.Locale("es", "ES"), ms / 1000.0)
}
