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
        setContent {
            val start = when {
                pendingCode != null -> Route.AddProvider(pendingCode)
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
        graph.warp.startListening()
        lifecycleScope.launch { runCatching { graph.warp.refresh() } }
    }

    override fun onStop() {
        // Sin trabajo en segundo plano: se para la reproducción ya y el motor al cabo de un rato.
        graph.sources.foreground = false
        graph.player.stop()
        if (nav?.current is Route.Player) nav?.back()
        graph.warp.stopListening()
        graph.reminders.stopTicker()
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
    }

    /**
     * Red de seguridad para el mando: si la búsqueda de foco de Compose falla en una lista perezosa, se ignora esa
     * pulsación en lugar de cerrar la app (como la v18) y se apunta en el registro.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = try {
        super.dispatchKeyEvent(event)
    } catch (e: IllegalStateException) {
        recoverKey(e)
    } catch (e: IllegalArgumentException) {
        recoverKey(e)
    } catch (e: IndexOutOfBoundsException) {
        recoverKey(e)
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

    private fun codeFrom(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "mivisor") return null
        return Codes.normalizeOrNull(data.toString())
    }
}
