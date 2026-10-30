package com.mivisor.tv.outpace

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.mivisor.tv.engine.EngineConfig
import com.mivisor.tv.engine.EngineDiagnostics
import com.mivisor.tv.engine.EngineSupervisor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Motor Outpace integrado (el de la v18, sin cambios de comportamiento). Un único proceso para toda la app,
 * gestionado por [EngineSupervisor], escuchando solo en 127.0.0.1.
 *
 *  - Android 6.0+ y ARM64/ARMv7 (el binario liboutpace.so lo compila la CI desde el commit fijado de outpace).
 *  - Sin trabajo en segundo plano: al salir de la app se para la reproducción y, al cabo de un rato, el motor.
 */
object EngineManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var supervisor: EngineSupervisor? = null
    private var backgroundStop: Job? = null
    private val _state = MutableStateFlow(EngineSupervisor.State(EngineSupervisor.Phase.STOPPED))
    val state: StateFlow<EngineSupervisor.State> = _state.asStateFlow()

    fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, EngineConfig.BINARY_NAME)

    fun isSupported(context: Context): Boolean = Build.VERSION.SDK_INT >= 23 && binary(context).isFile

    fun unsupportedReason(context: Context): String? = when {
        Build.VERSION.SDK_INT < 23 -> "Outpace necesita Android 6.0 o superior"
        !binary(context).isFile -> "Outpace no está incluido para esta arquitectura (${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"})"
        else -> null
    }

    fun isLowRam(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return true
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return am.isLowRamDevice || info.totalMem in 1..(1_536L shl 20)
    }

    fun totalRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem shr 20
    }

    /** ¿Anda justo de memoria ahora mismo? (para no culpar a una fuente de un fallo del aparato). */
    fun lowMemoryNow(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.lowMemory
    }

    @Synchronized
    private fun supervisor(context: Context): EngineSupervisor = supervisor ?: EngineSupervisor(
        binary = binary(context),
        workDir = File(context.filesDir, "engine"),
        lowRam = isLowRam(context),
        killPid = { pid -> android.os.Process.killProcess(pid) },    // solo procesos de nuestro propio uid
        onLine = EngineDiagnostics::engineLine
    ).also { supervisor = it }

    /** Arranca o reutiliza el motor. Idempotente: nunca crea un segundo proceso. */
    suspend fun ensureStarted(context: Context): EngineSupervisor.State {
        cancelBackgroundStop()
        unsupportedReason(context)?.let {
            return EngineSupervisor.State(EngineSupervisor.Phase.FAILED, message = it).also { s -> _state.value = s }
        }
        return supervisor(context.applicationContext).ensureStarted { s ->
            _state.value = s
            EngineDiagnostics.update { m -> m.copy(engineState = s.phase.label(), port = s.port) }
        }
    }

    suspend fun stop() {
        supervisor?.stop()
        _state.value = EngineSupervisor.State(EngineSupervisor.Phase.STOPPED)
        EngineDiagnostics.update { it.copy(engineState = "detenido", port = null) }
    }

    /** ¿Responde el motor ahora? */
    fun healthy(): Boolean = EngineSupervisor.health(_state.value.port)

    /** La app pasa a segundo plano: el motor se para si no vuelve en 30 s (poca RAM) o 2 min. */
    fun onBackground(context: Context) {
        val delayMs = if (isLowRam(context)) 30_000L else 120_000L
        backgroundStop?.cancel()
        backgroundStop = scope.launch {
            delay(delayMs)
            EngineDiagnostics.log(EngineDiagnostics.Cat.PROCESS, "MiVisor en segundo plano: se para el motor para liberar memoria", important = true)
            stop()
        }
    }

    fun cancelBackgroundStop() {
        backgroundStop?.cancel()
        backgroundStop = null
    }

    private fun EngineSupervisor.Phase.label() = when (this) {
        EngineSupervisor.Phase.STOPPED -> "detenido"
        EngineSupervisor.Phase.STARTING -> "arrancando"
        EngineSupervisor.Phase.READY -> "preparado"
        EngineSupervisor.Phase.FAILED -> "error"
    }
}
