package com.mivisor.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.mivisor.tv.catalog.Codes
import com.mivisor.tv.catalog.datos.DirectStream
import com.mivisor.tv.data.NetState
import com.mivisor.tv.outpace.EngineManager
import com.mivisor.tv.ui.AppRoot
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.Route
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val graph: AppGraph get() = (application as MiVisorTvApp).graph
    private var nav: Nav? = null
    private var pendingCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        pendingCode = codeFrom(intent)
        if (savedInstanceState == null) graph.start()
        // 4h · Abierta desde un enlace acestream:// (otra app o el navegador): directa al reproductor.
        val direct = if (savedInstanceState == null) directFrom(intent) else null
        setContent {
            val start = when {
                pendingCode != null -> Route.AddProvider(pendingCode)
                direct != null -> direct
                !graph.prefs.wizardDone -> Route.Wizard
                // Ajustes › Pantalla › «Al abrir MiVisor» (3d).
                graph.prefs.startScreen == "agenda" -> Route.Agenda
                graph.prefs.startScreen == "channels" -> Route.Channels
                else -> Route.Home
            }
            val n = remember {
                Nav(start).also { nn ->
                    nav = nn
                    val last = graph.prefs.lastChannel
                    if (start == Route.Home && graph.prefs.startScreen == "last" && last != null) nn.go(Route.Player(last))
                }
            }
            AppRoot(graph, n, onExit = { finish() })
        }
    }

    override fun onStart() {
        super.onStart()
        graph.sources.foreground = true
        EngineManager.cancelBackgroundStop()
        NetState.start(this)
        graph.reminders.startTicker()
        graph.gold.startTicker()
        graph.warp.startListening()
        lifecycleScope.launch { runCatching { graph.warp.refresh() } }
    }

    override fun onStop() {
        // Sin trabajo en segundo plano: se para la reproducción ya y el motor al cabo de un rato.
        graph.sources.foreground = false
        graph.player.stop()
        graph.preview.release()
        graph.prewarm.releaseAll()
        if (nav?.current is Route.Player) nav?.back()
        graph.warp.stopListening()
        graph.reminders.stopTicker()
        graph.gold.stopTicker()
        NetState.stop(this)
        EngineManager.onBackground(this)
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) graph.player.release()
        super.onDestroy()
    }

    /** Un segundo enlace mivisor:… llega aquí (singleTask). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        codeFrom(intent)?.let { nav?.go(Route.AddProvider(it)) }
        directFrom(intent)?.let { nav?.go(it) }
    }

    /**
     * Red de seguridad para el mando: si la búsqueda de foco de Compose falla en una lista perezosa, se ignora esa
     * pulsación en lugar de cerrar la app (como la v18) y se apunta en el registro.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = try {
        if (throttleRepeat(event)) true else super.dispatchKeyEvent(event)
    } catch (e: IllegalStateException) {
        recoverKey(e)
    } catch (e: IllegalArgumentException) {
        recoverKey(e)
    } catch (e: IndexOutOfBoundsException) {
        recoverKey(e)
    }

    private var lastRepeatAt = 0L

    /**
     * 3h · Flecha MANTENIDA: el mando repite la tecla muy deprisa (20-30 por segundo) y una tele modesta no puede
     * dibujar tantos movimientos de foco: se encolan y la lista avanza a tirones. Se deja pasar como mucho una
     * repetición cada [KEY_REPEAT_MIN_MS] (≈ 12 por segundo). La primera pulsación y el soltar nunca se tocan.
     */
    private fun throttleRepeat(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount == 0) return false
        if (event.keyCode !in DPAD_DIRECTIONS) return false
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastRepeatAt < KEY_REPEAT_MIN_MS) return true
        lastRepeatAt = now
        return false
    }

    private fun recoverKey(e: RuntimeException): Boolean {
        graph.log.add("Foco", "Tecla ignorada: ${e.javaClass.simpleName}")
        return true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * 4h · acestream://<ID> (o cualquier enlace de ese tipo con el ID dentro): se apunta en el historial de «Abrir un
     * ID» (el nombre llega luego del descriptor) y se abre en el reproductor con el motor de MiVisor TV.
     */
    private fun directFrom(intent: Intent?): Route.Player? {
        val data = intent?.data ?: return null
        if (!data.scheme.equals("acestream", ignoreCase = true)) return null
        val cid = DirectStream.parse(data.toString()) ?: return null
        graph.prefs.directRecent = graph.prefs.directRecent.firstOrNull { it.substringBefore('\t') == cid }.let { old ->
            listOf(old ?: (cid + "\t")) + graph.prefs.directRecent.filter { it.substringBefore('\t') != cid }
        }
        graph.log.add("Abrir un ID", "Se abre " + DirectStream.short(cid) + " desde un enlace acestream:// de otra app")
        return Route.Player(DirectStream.channelId(cid))
    }

    private fun codeFrom(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "mivisor") return null
        return Codes.normalizeOrNull(data.toString())
    }

    private companion object {
        const val KEY_REPEAT_MIN_MS = 85L
        val DPAD_DIRECTIONS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
    }
}
