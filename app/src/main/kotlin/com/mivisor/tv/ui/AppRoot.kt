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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
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
    var deviceOverride by remember { mutableStateOf(graph.prefs.deviceClass) }
    var navPosition by remember { mutableStateOf(graph.prefs.navPosition) }
    val onLook = {
        lite = graph.prefs.lite
        bigText = graph.prefs.bigText
        deviceOverride = graph.prefs.deviceClass
        navPosition = graph.prefs.navPosition
    }
    // 3f · Tele, tablet o móvil: decide la escala, el menú y la orientación.
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val device = remember(deviceOverride, config.smallestScreenWidthDp) { Devices.detect(context, deviceOverride) }
    LaunchedEffect(device) { Devices.applyOrientation(context as? android.app.Activity, device) }
    // Preparación (diseño 06): la Órbita se ve solo mientras se leen los proveedores guardados; sin duración mínima.
    var booting by remember { mutableStateOf(true) }
    val libState by graph.providers.state.collectAsState()
    LaunchedEffect(Unit) {
        val t0 = System.currentTimeMillis()
        while (booting) {
            val s = graph.providers.state.value
            // 4j · Con Biblioteca y sin proveedores, se espera a la Biblioteca (con el tope de abajo).
            // 4o · Con Biblioteca, se espera a ELLA (no a los proveedores): así Inicio no sale primero con los canales de
            // los proveedores y luego cambia. Tope: 8 s; después Inicio avisa de que se sigue cargando.
            val g = graph.gold.state.value
            val ready = if (g.configured) s.library.gold || g.error != null || g.wrongPassword || (g.checkedAt > 0 && g.revision == 0L)
            else !s.library.isEmpty || s.providers.isEmpty() || !s.loading
            if ((ready && System.currentTimeMillis() - t0 > 250) || System.currentTimeMillis() - t0 > 8_000) booting = false
            else delay(100)
        }
    }
    val toaster = remember { Toaster() }
    val backdrop = remember { Backdrop() }
    val density = LocalDensity.current
    val metrics = context.resources.displayMetrics
    val drawDensity = remember(device, config.screenWidthDp, config.screenHeightDp, density.density) {
        com.mivisor.tv.model.DeviceClass.density(device, metrics.widthPixels, metrics.heightPixels, density.density)
    }
    val form = device.ui()
    CompositionLocalProvider(
        LocalDensity provides Density(drawDensity, density.fontScale * (if (bigText) 1.15f else 1f)),
        com.mivisor.tv.ui.design.LocalUiForm provides form,
        LocalPrewarm provides graph.prewarm::onFocus,
        LocalBanners provides graph.banners
    ) {
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
            if (booting && route != Route.Wizard && route !is Route.AddProvider) {
                val g by graph.gold.state.collectAsState()
                BootScreen(
                    slow = libState.loading || (g.configured && !libState.library.gold),
                    what = when {
                        g.configured && g.revision == 0L -> "Primera vez: descargando la Biblioteca. Puede tardar un poco…"
                        g.configured -> "Cargando la Biblioteca (${g.channels} canales)…"
                        else -> "Leyendo tus proveedores…"
                    }
                )
                return@Box
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
                    when {
                        // 3f · Móvil (en horizontal): barra lateral fina solo con iconos; nada de cabecera, la altura vale oro.
                        form == com.mivisor.tv.ui.design.UiForm.PHONE -> Row(Modifier.fillMaxSize()) {
                            PhoneRail(route, onTab = { nav.tab(it) })
                            Box(Modifier.weight(1f).fillMaxHeight().padding(top = 10.dp)) { content() }
                        }
                        // Rail del diseño 06 (Ajustes › Pantalla › Menú › Lateral): se despliega SOBRE el contenido.
                        navPosition == "side" -> Box(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize().padding(start = RAIL_W - MvDims.SafeX + 37.dp)) {
                                HeaderBar(graph)
                                Box(Modifier.fillMaxSize()) { content() }
                            }
                            Rail(route, onTab = { nav.tab(it) })
                        }
                        // 3f · Por defecto: barra superior (como Google TV o Netflix). Todo el ancho para el contenido.
                        else -> Column(Modifier.fillMaxSize()) {
                            TopNav(graph, route, onTab = { nav.tab(it) })
                            Box(Modifier.fillMaxSize()) { content() }
                        }
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

/** Ancho reservado del rail (170 px del diseño × 0,6). */
private val RAIL_W = 102.dp

/** Orden canónico del diseño 06. */
private val RAIL_TABS = listOf(Route.Home, Route.Channels, Route.Agenda, Route.Search, Route.MyList, Route.Settings)

/**
 * Rail compacto del diseño 06: solo iconos; al recibir el foco se despliega a 210 dp ENCIMA del contenido (sin reflujo)
 * y muestra las etiquetas. Activo (sección actual) ≠ enfocado (anillo champán).
 */
@Composable
private fun Rail(route: Route, onTab: (Route) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selectedTab = selectedTabOf(route)
    val lite = com.mivisor.tv.ui.design.LocalLite.current
    val width = if (lite) (if (open) 210.dp else RAIL_W)
    else androidx.compose.animation.core.animateDpAsState(if (open) 210.dp else RAIL_W, tween(180), label = "rail").value
    Column(
        Modifier.fillMaxHeight().width(width)
            .background(MvColors.Nav)
            .drawBehind { drawLine(MvColors.Stroke, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), 1f) }
            .onFocusChanged { open = it.hasFocus }
            .padding(start = 26.dp, end = 12.dp, top = MvDims.SafeY + 2.dp, bottom = MvDims.SafeY),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.width(50.dp), contentAlignment = Alignment.Center) { com.mivisor.tv.ui.design.OrbitaMark(30.dp) }
        Spacer(Modifier.height(30.dp))
        for (tab in RAIL_TABS) {
            val selected = tab == selectedTab
            TvClickable(
                onClick = { onTab(tab) }, shape = RoundedCornerShape(12.dp), focusedScale = 1f,
                color = if (selected) MvColors.Accent.copy(alpha = 0.12f) else Color.Transparent,
                focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) { _ ->
                Row(Modifier.fillMaxSize().padding(start = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    MvIcon(tabIcon(tab), size = 20.dp, tint = if (selected) MvColors.Accent else MvColors.Text2)
                    if (open) {
                        Spacer(Modifier.width(14.dp))
                        MvText(tabLabel(tab), MvType.BodyStrong, color = if (selected) MvColors.Accent else MvColors.Text, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (open) MvText("MiVisor TV", MvType.Caption.copy(color = MvColors.Text3))
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

/** Pestañas de la barra superior; Buscar y Ajustes van como iconos a la derecha. */
private val TOP_TABS = listOf(Route.Home, Route.Channels, Route.Agenda, Route.MyList)

/**
 * 3f · Barra superior: marca a la izquierda, las cuatro secciones como texto, y a la derecha Buscar, Ajustes, el
 * estado de los servicios y la hora. Con el mando se llega pulsando ↑ desde el contenido. Sección activa = texto
 * champán con una raya debajo; enfocada = pastilla con anillo (como en el resto de la app).
 */
@Composable
private fun TopNav(graph: AppGraph, route: Route, onTab: (Route) -> Unit) {
    val selected = selectedTabOf(route)
    val engine by EngineManager.state.collectAsState()
    val warp by graph.warp.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = MvDims.SafeX, end = MvDims.SafeX, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.mivisor.tv.ui.design.OrbitaMark(26.dp)
        Spacer(Modifier.width(10.dp))
        com.mivisor.tv.ui.design.MvWordmark(18.sp)
        Spacer(Modifier.width(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (tab in TOP_TABS) {
                val on = tab == selected
                TvClickable(
                    onClick = { onTab(tab) }, shape = RoundedCornerShape(20.dp), focusedScale = 1f,
                    color = Color.Transparent, focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
                    modifier = Modifier.height(40.dp)
                ) { focused ->
                    Column(Modifier.fillMaxHeight().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        MvText(tabLabel(tab), MvType.BodyStrong, color = if (on) MvColors.Accent else if (focused) MvColors.Text else MvColors.Text2, maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.width(18.dp).height(2.dp).background(if (on) MvColors.Accent else Color.Transparent, RoundedCornerShape(1.dp)))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        NavIcon(MvIcons.Search, selected == Route.Search) { onTab(Route.Search) }
        Spacer(Modifier.width(6.dp))
        NavIcon(MvIcons.Settings, selected == Route.Settings) { onTab(Route.Settings) }
        Spacer(Modifier.width(18.dp))
        LiveDot(
            when (engine.phase) {
                EngineSupervisor.Phase.READY -> MvColors.Ok
                EngineSupervisor.Phase.STARTING -> MvColors.Soon
                EngineSupervisor.Phase.FAILED -> MvColors.Bad
                EngineSupervisor.Phase.STOPPED -> MvColors.Text3
            }, 6.dp
        )
        Spacer(Modifier.width(5.dp))
        MvText("Motor", MvType.Caption.copy(color = MvColors.Text3))
        Spacer(Modifier.width(10.dp))
        LiveDot(
            when (warp.phase) {
                WarpPhase.CONNECTED -> MvColors.Ok
                WarpPhase.CONNECTING, WarpPhase.DISCONNECTING -> MvColors.Soon
                WarpPhase.SIGNATURE_MISMATCH, WarpPhase.ERROR -> MvColors.Bad
                else -> MvColors.Text3
            }, 6.dp
        )
        Spacer(Modifier.width(5.dp))
        MvText("WARP", MvType.Caption.copy(color = MvColors.Text3))
        Spacer(Modifier.width(16.dp))
        MvText(hhmm(now), MvType.BodyStrong)
    }
}

@Composable
private fun NavIcon(icon: Int, selected: Boolean, onClick: () -> Unit) {
    TvClickable(
        onClick = onClick, shape = CircleShape, focusedScale = 1f,
        color = if (selected) MvColors.Accent.copy(alpha = 0.14f) else Color.Transparent,
        focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
        modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center
    ) { _ -> MvIcon(icon, size = 20.dp, tint = if (selected) MvColors.Accent else MvColors.Text2) }
}

/** 3f · Móvil: barra lateral fina con las seis secciones (solo iconos, con la etiqueta pequeña debajo). */
@Composable
private fun PhoneRail(route: Route, onTab: (Route) -> Unit) {
    val selected = selectedTabOf(route)
    Column(
        Modifier.fillMaxHeight().width(68.dp).background(MvColors.Nav).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        com.mivisor.tv.ui.design.OrbitaMark(24.dp)
        Spacer(Modifier.height(6.dp))
        for (tab in RAIL_TABS) {
            val on = tab == selected
            TvClickable(
                onClick = { onTab(tab) }, shape = RoundedCornerShape(12.dp), focusedScale = 1f,
                color = if (on) MvColors.Accent.copy(alpha = 0.14f) else Color.Transparent,
                focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
                modifier = Modifier.width(58.dp).weight(1f, fill = false).height(50.dp), contentAlignment = Alignment.Center
            ) { _ ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MvIcon(tabIcon(tab), size = 20.dp, tint = if (on) MvColors.Accent else MvColors.Text2)
                    MvText(tabLabel(tab), MvType.Caption.copy(fontSize = 10.sp, color = if (on) MvColors.Accent else MvColors.Text3), maxLines = 1)
                }
            }
        }
    }
}

/** Barra superior fina (diseño 06): marca, estado de los servicios en una línea y la hora. No es enfocable. */
@Composable
private fun HeaderBar(graph: AppGraph) {
    val engine by EngineManager.state.collectAsState()
    val warp by graph.warp.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(start = MvDims.SafeX, end = MvDims.SafeX, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.mivisor.tv.ui.design.MvWordmark(20.sp)
        Spacer(Modifier.weight(1f))
        LiveDot(
            when (engine.phase) {
                EngineSupervisor.Phase.READY -> MvColors.Ok
                EngineSupervisor.Phase.STARTING -> MvColors.Soon
                EngineSupervisor.Phase.FAILED -> MvColors.Bad
                EngineSupervisor.Phase.STOPPED -> MvColors.Text3
            }, 6.dp
        )
        Spacer(Modifier.width(6.dp))
        MvText(if (graph.prefs.engine == "ace") "Ace Stream" else "Motor", MvType.Caption.copy(color = MvColors.Text2))
        Spacer(Modifier.width(14.dp))
        LiveDot(
            when (warp.phase) {
                WarpPhase.CONNECTED -> MvColors.Ok
                WarpPhase.CONNECTING, WarpPhase.DISCONNECTING -> MvColors.Soon
                WarpPhase.SIGNATURE_MISMATCH, WarpPhase.ERROR -> MvColors.Bad
                else -> MvColors.Text3
            }, 6.dp
        )
        Spacer(Modifier.width(6.dp))
        MvText(
            when (warp.phase) {
                WarpPhase.CONNECTED -> "WARP conectada"
                WarpPhase.SIGNATURE_MISMATCH -> "WARP: firma distinta"
                WarpPhase.NOT_INSTALLED -> "Sin WARP"
                else -> "WARP"
            }, MvType.Caption.copy(color = MvColors.Text2)
        )
        Spacer(Modifier.width(18.dp))
        MvText(hhmm(now), MvType.BodyStrong)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(MvColors.Stroke.copy(alpha = 0.6f)))
}

/** Preparación (diseño 06): Órbita y marca mientras se leen los datos guardados. Sin botón «Saltar» ni tiempo mínimo. */
@Composable
private fun BootScreen(slow: Boolean, what: String = "Preparando tus canales…") {
    var late by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1_500); late = true }
    Box(Modifier.fillMaxSize().background(MvColors.Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.mivisor.tv.ui.design.OrbitaMark(96.dp, spinning = true)
            Spacer(Modifier.height(22.dp))
            com.mivisor.tv.ui.design.MvWordmark(40.sp)
            Spacer(Modifier.height(6.dp))
            MvText("TU DEPORTE. EN UNA SOLA EXPERIENCIA.", MvType.Caption.copy(color = MvColors.Text2, letterSpacing = 1.5.sp))
            Spacer(Modifier.height(28.dp))
            MvText(if (late) what else "Preparando tus canales…", MvType.Small.copy(color = MvColors.Text2))
            if (late && slow) {
                Spacer(Modifier.height(4.dp))
                MvText("Va más lento de lo normal: en cuanto esté, entras solo.", MvType.Caption.copy(color = MvColors.Text3))
            }
        }
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
