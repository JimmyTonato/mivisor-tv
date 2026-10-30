package com.mivisor.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.mivisor.tv.ui.screens.DiagnosticsScreen
import com.mivisor.tv.ui.screens.ReportScreen
import com.mivisor.tv.ui.screens.ChecksScreen
import com.mivisor.tv.ui.screens.ExportScreen
import com.mivisor.tv.ui.screens.ImportScreen
import com.mivisor.tv.ui.screens.QuarantineScreen
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.mivisor.tv.ui.design.MvIcon
import com.mivisor.tv.ui.design.MvIcons
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
import com.mivisor.tv.ui.screens.GroupScreen
import com.mivisor.tv.ui.screens.EventScreen
import com.mivisor.tv.ui.screens.CompetitionScreen
import com.mivisor.tv.ui.screens.ReminderNotice
import com.mivisor.tv.ui.screens.LinksScreen
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

/** Fondo de color de la portada de Inicio, pintado también detrás de la barra superior (como el diseño). */
class Backdrop {
    var colors by mutableStateOf<Pair<Color, Color>?>(null)
}

@Composable
fun AppRoot(graph: AppGraph, nav: Nav, onExit: () -> Unit) {
    var lite by remember { mutableStateOf(graph.prefs.lite) }
    var bigText by remember { mutableStateOf(graph.prefs.bigText) }
    var side by remember { mutableStateOf(graph.prefs.navStyle == "side") }
    val onLook = {
        lite = graph.prefs.lite
        bigText = graph.prefs.bigText
        side = graph.prefs.navStyle == "side"
    }
    val toaster = remember { Toaster() }
    val backdrop = remember { Backdrop() }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides if (bigText) Density(density.density, density.fontScale * 1.15f) else density) {
    MvTheme(lite = lite) {
        Box(Modifier.fillMaxSize().background(MvColors.Bg)) {
            val route = nav.current
            BackHandler(enabled = true) { if (!nav.back()) onExit() }
            val heroColors = backdrop.colors
            if (route == Route.Home && heroColors != null) {
                Box(
                    Modifier.fillMaxWidth().height(620.dp)
                        .background(Brush.horizontalGradient(listOf(MvColors.Bg, heroColors.first.copy(alpha = 0.32f), heroColors.second.copy(alpha = 0.62f))))
                )
                Box(Modifier.fillMaxWidth().height(620.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, MvColors.Bg))))
            }
            when (route) {
                is Route.Player -> PlayerScreen(graph, route, toaster, onBack = { nav.back() }, onSwitch = { nav.replace(it) }, onChannels = { nav.tab(Route.Channels) }, onRoute = { nav.go(it) })
                Route.Wizard -> WizardScreen(graph, nav, onDone = {
                    graph.prefs.wizardDone = true
                    onLook()
                    nav.tab(Route.Home)
                })
                else -> {
                    val content: @Composable () -> Unit = {
                        if (lite) Screen(graph, nav, route, toaster, backdrop, onLook)
                        else Crossfade(targetState = route, animationSpec = tween(180), label = "pantalla") { r -> Screen(graph, nav, r, toaster, backdrop, onLook) }
                    }
                    if (side) Row(Modifier.fillMaxSize()) {
                        SideRail(graph, route, onTab = { nav.tab(it) })
                        Box(Modifier.fillMaxSize().padding(top = MvDims.SafeY)) { content() }
                    } else Column(Modifier.fillMaxSize()) {
                        TopBar(graph, route, onTab = { nav.tab(it) })
                        Box(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
            ReminderNotice(graph, nav, onPlaying = { })
            ToastHost(toaster)
        }
    }
    }
}

/** Una pantalla con barra (todas menos el reproductor y el asistente). */
@Composable
private fun Screen(graph: AppGraph, nav: Nav, route: Route, toaster: Toaster, backdrop: Backdrop, onLook: () -> Unit) {
    LaunchedEffect(route) { if (route != Route.Home) backdrop.colors = null }
    Box(Modifier.fillMaxSize()) {
        when (route) {
            Route.Home -> HomeScreen(graph, nav, toaster, backdrop)
            Route.Agenda -> AgendaScreen(graph, nav, toaster)
            Route.Channels -> ChannelsScreen(graph, nav, toaster)
            Route.Search -> SearchScreen(graph, nav)
            Route.MyList -> MyListScreen(graph, nav, toaster)
            Route.Settings -> SettingsScreen(graph, nav, toaster, onLook = onLook)
            is Route.Channel -> ChannelScreen(graph, nav, route, toaster)
            is Route.Group -> GroupScreen(graph, nav, route, toaster)
            Route.Links -> LinksScreen(graph, toaster)
            is Route.Event -> EventScreen(graph, nav, route, toaster)
            is Route.Competition -> CompetitionScreen(graph, nav, route)
            Route.Diagnostics -> DiagnosticsScreen(graph, nav, toaster)
            Route.Report -> ReportScreen(graph, nav)
            Route.Checks -> ChecksScreen(graph, nav)
            is Route.Backup -> if (route.export) ExportScreen(graph, nav) else ImportScreen(graph, nav, toaster)
            Route.Quarantine -> QuarantineScreen(graph, nav, toaster)
            is Route.AddProvider -> AddProviderScreen(graph, route, onDone = {
                if (route.fromWizard) nav.back() else nav.back()
            })
            else -> Unit
        }
    }
}

/** Menú lateral del diseño («En un lateral»): iconos a la izquierda que se despliegan con el foco. */
@Composable
private fun SideRail(graph: AppGraph, route: Route, onTab: (Route) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selectedTab = selectedTabOf(route)
    Column(
        Modifier.fillMaxHeight().width(if (open) 240.dp else 88.dp)
            .background(if (open) MvColors.Surface1 else Color.Transparent)
            .onFocusChanged { open = it.hasFocus }
            .padding(start = 24.dp, top = MvDims.SafeY, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(36.dp).background(Brush.linearGradient(listOf(MvColors.Accent, MvColors.Accent2)), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { MvText("M", MvType.BodyStrong.copy(fontWeight = FontWeight.Black, color = Color(0xFF061226))) }
        Spacer(Modifier.height(24.dp))
        for (tab in Route.TABS) {
            val selected = tab == selectedTab
            TvClickable(
                onClick = { onTab(tab) }, shape = RoundedCornerShape(12.dp), focusedScale = 1.03f,
                color = if (selected) MvColors.Surface2 else Color.Transparent, focusedColor = MvColors.Text,
                modifier = Modifier.fillMaxWidth()
            ) { focused ->
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MvIcon(tabIcon(tab), size = 20.dp, tint = if (focused) MvColors.Bg else if (selected) MvColors.Accent2 else MvColors.Text2)
                    if (open) {
                        Spacer(Modifier.width(14.dp))
                        MvText(tabLabel(tab), MvType.BodyStrong, color = if (focused) MvColors.Bg else MvColors.Text, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun tabIcon(r: Route) = when (r) {
    Route.Home -> MvIcons.Home
    Route.Agenda -> MvIcons.Calendar
    Route.Channels -> MvIcons.Tv
    Route.Search -> MvIcons.Search
    Route.MyList -> MvIcons.Star
    else -> MvIcons.Settings
}

private fun selectedTabOf(route: Route): Route = when (route) {
    is Route.Channel, is Route.Group, Route.Links -> Route.Channels
    is Route.Event, is Route.Competition -> Route.Agenda
    is Route.AddProvider, Route.Diagnostics, Route.Report, Route.Checks, is Route.Backup, Route.Quarantine -> Route.Settings
    else -> route
}

/** Barra superior del prototipo: marca, pestañas con punto bajo la elegida, ajustes, estado de Outpace y WARP, hora. */
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
    val selectedTab = selectedTabOf(route)
    Row(
        Modifier.fillMaxWidth().padding(start = MvDims.SafeX, end = MvDims.SafeX, top = MvDims.SafeY, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).background(Brush.linearGradient(listOf(MvColors.Accent, MvColors.Accent2)), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) { MvText("M", MvType.SmallStrong.copy(fontWeight = FontWeight.Black, color = Color(0xFF061226))) }
        Spacer(Modifier.width(8.dp))
        MvText("MiVisor", MvType.Heading.copy(fontWeight = FontWeight.Black))
        Spacer(Modifier.width(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (tab in Route.TABS.filter { it != Route.Settings }) {
                val selected = tab == selectedTab
                TvClickable(
                    onClick = { onTab(tab) }, shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent, focusedColor = MvColors.Text, focusedScale = 1.04f
                ) { focused ->
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val ink = when {
                                focused -> MvColors.Bg
                                selected -> MvColors.Text
                                else -> MvColors.Text2
                            }
                            if (tab == Route.Search) {
                                MvIcon(MvIcons.Search, size = 16.dp, tint = ink)
                                Spacer(Modifier.width(6.dp))
                            }
                            MvText(tabLabel(tab), MvType.Body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium), color = ink, maxLines = 1)
                        }
                        Box(Modifier.padding(top = 3.dp).size(4.dp).background(if (selected && !focused) MvColors.Accent2 else Color.Transparent, CircleShape))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TvClickable(
            onClick = { onTab(Route.Settings) }, shape = CircleShape, focusedScale = 1.08f,
            color = if (selectedTab == Route.Settings) MvColors.Surface3 else Color.Transparent, focusedColor = MvColors.Text,
            contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)
        ) { focused -> MvIcon(MvIcons.Settings, size = 18.dp, tint = if (focused) MvColors.Bg else MvColors.Text2) }
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier.background(MvColors.Surface2, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiveDot(
                when (engine.phase) {
                    EngineSupervisor.Phase.READY -> MvColors.Ok
                    EngineSupervisor.Phase.STARTING -> MvColors.Soon
                    EngineSupervisor.Phase.FAILED -> MvColors.Bad
                    EngineSupervisor.Phase.STOPPED -> MvColors.Text3
                }, 7.dp
            )
            Spacer(Modifier.width(6.dp))
            MvText(if (graph.prefs.engine == "ace") "Ace Stream" else "Outpace", MvType.Caption.copy(color = MvColors.Text2))
            Spacer(Modifier.width(12.dp))
            LiveDot(
                when (warp.phase) {
                    WarpPhase.CONNECTED -> MvColors.Ok
                    WarpPhase.CONNECTING, WarpPhase.DISCONNECTING -> MvColors.Soon
                    WarpPhase.SIGNATURE_MISMATCH, WarpPhase.ERROR -> MvColors.Bad
                    else -> MvColors.Text3
                }, 7.dp
            )
            Spacer(Modifier.width(6.dp))
            MvText("WARP", MvType.Caption.copy(color = MvColors.Text2))
        }
        Spacer(Modifier.width(18.dp))
        MvText(hhmm(now), MvType.Heading)
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
