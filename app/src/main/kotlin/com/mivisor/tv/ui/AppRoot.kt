package com.mivisor.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.engine.EngineSupervisor
import com.mivisor.tv.outpace.EngineManager
import com.mivisor.tv.services.WarpPhase
import com.mivisor.tv.ui.design.LiveDot
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvTheme
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.TvClickable
import com.mivisor.tv.ui.screens.AddProviderScreen
import com.mivisor.tv.ui.screens.AgendaScreen
import com.mivisor.tv.ui.screens.ChannelScreen
import com.mivisor.tv.ui.screens.ChannelsScreen
import com.mivisor.tv.ui.screens.HomeScreen
import com.mivisor.tv.ui.screens.MyListScreen
import com.mivisor.tv.ui.screens.PlayerScreen
import com.mivisor.tv.ui.screens.SearchScreen
import com.mivisor.tv.ui.screens.SettingsScreen
import com.mivisor.tv.ui.screens.WizardScreen
import kotlinx.coroutines.delay

/** Mensajes breves en pantalla («Añadido a Mi lista»…). */
class Toaster {
    var text by mutableStateOf<String?>(null)
        private set
    var stamp by mutableLongStateOf(0L)
        private set

    fun show(message: String) {
        text = message
        stamp = System.nanoTime()
    }

    fun clear() {
        text = null
    }
}

@Composable
fun AppRoot(graph: AppGraph, nav: Nav, onExit: () -> Unit) {
    var lite by remember { mutableStateOf(graph.prefs.lite) }
    val toaster = remember { Toaster() }
    MvTheme(lite = lite) {
        Box(Modifier.fillMaxSize().background(MvColors.Bg)) {
            val route = nav.current
            BackHandler(enabled = true) { if (!nav.back()) onExit() }
            when (route) {
                is Route.Player -> PlayerScreen(graph, route, onBack = { nav.back() })
                Route.Wizard -> WizardScreen(onStart = { nav.go(Route.AddProvider(fromWizard = true)) }, onSkip = {
                    graph.prefs.wizardDone = true
                    nav.tab(Route.Home)
                })
                else -> Column(Modifier.fillMaxSize()) {
                    TopBar(graph, route, onTab = { nav.tab(it) })
                    Box(Modifier.fillMaxSize()) {
                        when (route) {
                            Route.Home -> HomeScreen(graph, nav, toaster)
                            Route.Agenda -> AgendaScreen(graph, nav, toaster)
                            Route.Channels -> ChannelsScreen(graph, nav, toaster)
                            Route.Search -> SearchScreen(graph, nav)
                            Route.MyList -> MyListScreen(graph, nav)
                            Route.Settings -> SettingsScreen(graph, nav, toaster, onLite = { lite = it })
                            is Route.Channel -> ChannelScreen(graph, nav, route, toaster)
                            is Route.AddProvider -> AddProviderScreen(graph, route, onDone = {
                                if (route.fromWizard) {
                                    graph.prefs.wizardDone = true
                                    nav.tab(Route.Home)
                                } else nav.back()
                            })
                            else -> Unit
                        }
                    }
                }
            }
            ToastHost(toaster)
        }
    }
}

@Composable
private fun TopBar(graph: AppGraph, route: Route, onTab: (Route) -> Unit) {
    val engine by EngineManager.state.collectAsState()
    val warp by graph.warp.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }
    val selectedTab = when (route) {
        is Route.Channel -> Route.Channels
        is Route.AddProvider -> Route.Settings
        else -> route
    }
    Row(
        Modifier.fillMaxWidth().padding(start = MvDims.SafeX, end = MvDims.SafeX, top = MvDims.SafeY, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MvText("MiVisor", MvType.Heading, color = MvColors.Text)
        MvText(" TV", MvType.Heading, color = MvColors.Accent2)
        Spacer(Modifier.width(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (tab in Route.TABS) {
                val selected = tab == selectedTab
                TvClickable(
                    onClick = { onTab(tab) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) MvColors.Surface3 else MvColors.Bg,
                    focusedColor = MvColors.Text,
                    focusedScale = 1.04f
                ) { focused ->
                    MvText(
                        tabLabel(tab), MvType.BodyStrong,
                        color = when {
                            focused -> MvColors.Bg
                            selected -> MvColors.Text
                            else -> MvColors.Text2
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp), maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        LiveDot(
            when (engine.phase) {
                EngineSupervisor.Phase.READY -> MvColors.Ok
                EngineSupervisor.Phase.STARTING -> MvColors.Soon
                EngineSupervisor.Phase.FAILED -> MvColors.Bad
                EngineSupervisor.Phase.STOPPED -> MvColors.Text3
            }
        )
        Spacer(Modifier.width(6.dp))
        MvText("Outpace", MvType.Caption)
        Spacer(Modifier.width(14.dp))
        LiveDot(
            when (warp.phase) {
                WarpPhase.CONNECTED -> MvColors.Ok
                WarpPhase.CONNECTING, WarpPhase.DISCONNECTING -> MvColors.Soon
                WarpPhase.SIGNATURE_MISMATCH, WarpPhase.ERROR -> MvColors.Bad
                else -> MvColors.Text3
            }
        )
        Spacer(Modifier.width(6.dp))
        MvText("WARP", MvType.Caption)
        Spacer(Modifier.width(18.dp))
        MvText(hhmm(now), MvType.BodyStrong)
    }
}

private fun tabLabel(r: Route) = when (r) {
    Route.Home -> "Inicio"
    Route.Agenda -> "Agenda"
    Route.Channels -> "Canales"
    Route.Search -> "Buscar"
    Route.MyList -> "Mi lista"
    Route.Settings -> "Ajustes"
    else -> ""
}

@Composable
private fun ToastHost(toaster: Toaster) {
    val text = toaster.text ?: return
    LaunchedEffect(toaster.stamp) {
        delay(3_000)
        toaster.clear()
    }
    Box(Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.background(MvColors.Surface4, RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 12.dp)
        ) { MvText(text, MvType.BodyStrong) }
    }
}

/** Placeholder de pie para pantallas con lista. */
@Composable
fun BottomSpace() = Spacer(Modifier.height(MvDims.SafeY + 16.dp))
