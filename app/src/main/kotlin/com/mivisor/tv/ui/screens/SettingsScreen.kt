package com.mivisor.tv.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.BuildConfig
import com.mivisor.tv.data.ExternalApps
import com.mivisor.tv.data.NetState
import com.mivisor.tv.data.ProviderInfo
import com.mivisor.tv.engine.EngineDiagnostics
import com.mivisor.tv.engine.EngineSupervisor
import com.mivisor.tv.outpace.EngineManager
import com.mivisor.tv.services.WarpPhase
import com.mivisor.tv.storage.LogRow
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.MvTextField
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.ago
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDialog
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvPanel
import com.mivisor.tv.ui.design.MvRow
import com.mivisor.tv.ui.design.MvSwitch
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.TvClickable
import com.mivisor.tv.ui.hhmm
import com.mivisor.tv.warp.WarpSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Page(val title: String, val hint: String) {
    PROVIDERS("Proveedores", "Códigos, nombres y WARP"),
    ENGINE("Motor", "Outpace y Ace Stream"),
    WARP("WARP", "Conexión y firma"),
    DISPLAY("Pantalla", "Modo ligero"),
    AGENDA("Agenda", "Datos de GitHub"),
    RELIABILITY("Fiabilidad", "Lo aprendido de las fuentes"),
    LOG("Registro", "Sin datos sensibles"),
    LATER("Próximamente", "Lo que llega en 3d"),
    ABOUT("Acerca de", "Versión y licencias")
}

@Composable
fun SettingsScreen(graph: AppGraph, nav: Nav, toaster: Toaster, onLite: (Boolean) -> Unit) {
    var page by remember { mutableStateOf(Page.PROVIDERS) }
    var editing by remember { mutableStateOf<ProviderInfo?>(null) }
    var deleting by remember { mutableStateOf<ProviderInfo?>(null) }
    var forgetting by remember { mutableStateOf(false) }
    var forgotten by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(start = MvDims.SafeX, end = MvDims.SafeX)) {
            LazyColumn(Modifier.width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
                itemsIndexed(Page.values().toList()) { i, p ->
                    TvClickable(
                        onClick = { page = p }, onFocus = { page = p }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        focusedScale = 1.02f, color = if (p == page) MvColors.Surface2 else MvColors.Bg, focusedColor = MvColors.Surface4,
                        focusRequester = if (i == 0) first else null
                    ) { _ ->
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            MvText(p.title, MvType.BodyStrong, color = if (p == page) MvColors.Text else MvColors.Text2, maxLines = 1)
                            MvText(p.hint, MvType.Caption, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.width(32.dp))
            // Columna con desplazamiento: el foco del mando la hace avanzar sola.
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 48.dp, end = 6.dp)) {
                MvText(page.title, MvType.Title, modifier = Modifier.padding(bottom = 16.dp))
                when (page) {
                    Page.PROVIDERS -> ProvidersPage(graph, nav, toaster, onEdit = { editing = it })
                    Page.ENGINE -> EnginePage(graph)
                    Page.WARP -> WarpPage(graph)
                    Page.DISPLAY -> DisplayPage(graph, onLite)
                    Page.AGENDA -> AgendaPage(graph, toaster)
                    Page.RELIABILITY -> ReliabilityPage(graph, forgotten, onForget = { forgetting = true })
                    Page.LOG -> LogPage(graph)
                    Page.LATER -> LaterPage()
                    Page.ABOUT -> AboutPage()
                }
            }
        }
        editing?.let { p -> ProviderOptions(graph, p, onClose = { editing = null }, onDelete = { editing = null; deleting = p }) }
        deleting?.let { p ->
            val yes = remember { FocusRequester() }
            BackHandler { deleting = null }
            MvDialog("¿Eliminar «${p.name}»?", "Sus canales desaparecerán de MiVisor TV. Podrás volver a añadirlo con su código.") {
                MvButton("Eliminar", { graph.providers.remove(p.id); deleting = null; toaster.show("Proveedor eliminado") }, icon = MvIcons.Delete, focusRequester = yes)
                MvButton("Cancelar", { deleting = null })
            }
            InitialFocus(yes)
        }
        if (forgetting) {
            val yes = remember { FocusRequester() }
            BackHandler { forgetting = false }
            MvDialog("¿Olvidar lo aprendido?", "Todas las fuentes vuelven a empezar como nuevas. Tus proveedores, favoritos e historial no se tocan.") {
                MvButton("Olvidar", { graph.sources.forgetAll(); forgotten++; forgetting = false; toaster.show("Fiabilidad reiniciada") }, icon = MvIcons.Delete, focusRequester = yes)
                MvButton("Cancelar", { forgetting = false })
            }
            InitialFocus(yes)
        }
    }
    InitialFocus(first)
}

// ------------------------------------------------------------------ Proveedores

@Composable
private fun ProvidersPage(graph: AppGraph, nav: Nav, toaster: Toaster, onEdit: (ProviderInfo) -> Unit) {
    val lib by graph.providers.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MvButton("Añadir proveedor", { nav.go(Route.AddProvider()) }, icon = MvIcons.Add, primary = true)
            MvButton(if (lib.loading) "Actualizando…" else "Actualizar todos", { graph.providers.refresh(); toaster.show("Actualizando proveedores…") }, icon = MvIcons.Refresh, enabled = lib.providers.isNotEmpty())
        }
        if (lib.providers.isEmpty()) MvText("Todavía no hay proveedores.", MvType.Body, color = MvColors.Text2)
        lib.providers.forEach { p ->
            MvRow(
                title = p.name,
                subtitle = (if (p.error != null) "Error: ${p.error}" else "${p.channels} canales · actualizado ${ago(p.lastOk)}") + if (p.usesWarp) " · necesita WARP" else "",
                icon = MvIcons.Layers,
                trailing = if (p.error != null) "Revisar" else "OK", trailingColor = if (p.error != null) MvColors.Bad else MvColors.Ok,
                onClick = { onEdit(p) }
            )
        }
        Spacer(Modifier.height(4.dp))
        MvText("Los canales con el mismo nombre en varios proveedores se juntan en uno; sus enlaces pasan a ser fuentes de ese canal.", MvType.Caption)
    }
}

@Composable
private fun ProviderOptions(graph: AppGraph, p: ProviderInfo, onClose: () -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var warp by remember { mutableStateOf(p.usesWarp) }
    val first = remember { FocusRequester() }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(MvColors.Scrim), contentAlignment = Alignment.Center) {
        MvPanel(Modifier.width(620.dp)) {
            MvText(p.name, MvType.Title)
            Spacer(Modifier.height(16.dp))
            MvTextField(name, { name = it }, "Nombre", focusRequester = first)
            Spacer(Modifier.height(14.dp))
            MvRow("Necesita WARP", { warp = !warp }, subtitle = "Sin WARP conectada, sus fallos no penalizan a las fuentes.", icon = MvIcons.Shield) { MvSwitch(warp) }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MvButton("Guardar", {
                    if (name.isNotBlank() && name != p.name) graph.providers.rename(p.id, name)
                    if (warp != p.usesWarp) graph.providers.setUsesWarp(p.id, warp)
                    onClose()
                }, primary = true)
                MvButton("Eliminar", onDelete, icon = MvIcons.Delete)
                MvButton("Cerrar", onClose)
            }
        }
    }
    InitialFocus(first)
}

// ------------------------------------------------------------------ Motor

@Composable
private fun EnginePage(graph: AppGraph) {
    val context = LocalContext.current
    val engine by EngineManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2_000); tick++ } }     // solo mientras se ve esta página
    val m = remember(tick) { EngineDiagnostics.metrics }
    val supported = remember { EngineManager.unsupportedReason(context) }
    val aceInstalled = remember { ExternalApps.aceInstalled(context) }
    var engineChoice by remember { mutableStateOf(graph.prefs.engine) }
    var fallback by remember { mutableStateOf(graph.prefs.aceFallback) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MvText("Outpace va dentro de MiVisor TV y es el que se usa siempre que funciona. Ace Stream es una app aparte que puedes elegir como alternativa.", MvType.Small)
        MvRow("Outpace (integrado)", { engineChoice = "outpace"; graph.prefs.engine = "outpace" },
            subtitle = "Recomendado: elige la fuente, recupera los cortes y recuerda el audio", icon = MvIcons.Bolt) { MvSwitch(engineChoice == "outpace") }
        MvRow("Ace Stream (app externa)", { if (aceInstalled) { engineChoice = "ace"; graph.prefs.engine = "ace" } },
            subtitle = if (aceInstalled) "Abre cada canal en la app de Ace Stream; se vuelve con Atrás. MiVisor TV no controla audio ni subtítulos"
            else "No está instalada en esta tele", icon = MvIcons.Play, enabled = aceInstalled) { MvSwitch(engineChoice == "ace") }
        MvRow("Si Outpace no da imagen", {
            fallback = when (fallback) { "ask" -> "auto"; "auto" -> "never"; else -> "ask" }
            graph.prefs.aceFallback = fallback
        }, subtitle = when (fallback) {
            "auto" -> "Abro solo Ace Stream y te aviso"
            "never" -> "No hago nada; sigo con Outpace"
            else -> "Te ofrezco probar con Ace Stream"
        }, icon = MvIcons.Refresh, trailing = when (fallback) { "auto" -> "Automático"; "never" -> "Nunca"; else -> "Preguntar" })
        Spacer(Modifier.height(6.dp))
        MvPanel {
            KeyValue("Estado", when (engine.phase) {
                EngineSupervisor.Phase.READY -> "En marcha"
                EngineSupervisor.Phase.STARTING -> "Arrancando…"
                EngineSupervisor.Phase.FAILED -> "Error: ${engine.message ?: "sin detalle"}"
                EngineSupervisor.Phase.STOPPED -> "Parado (arranca al ver un canal)"
            })
            KeyValue("Puerto local", engine.port?.let { "127.0.0.1:$it (solo esta tele)" } ?: "—")
            KeyValue("Compilado desde", "outpace @ ${BuildConfig.OUTPACE_COMMIT.take(12)}")
            KeyValue("Este aparato", "Android ${Build.VERSION.RELEASE} · ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"} · ${EngineManager.totalRamMb(context)} MB" + if (EngineManager.isLowRam(context)) " · modo poca memoria" else "")
            supported?.let { KeyValue("Aviso", it) }
            if (m.channel != null) {
                KeyValue("Ahora", "${m.channel} · fuente ${m.sourceIndex}/${m.sourceCount} · ${m.peers} emisores · ${m.bitrateBps / 1000} kbps")
                KeyValue("Buffer", "motor ${m.engineBufferMs} ms · reproductor ${m.playerBufferMs} ms · objetivo ${m.bufferTargetMs} ms · cortes ${m.rebuffers}")
                m.liveDelayMs?.let { KeyValue("Retraso", "$it ms (objetivo ${m.liveTargetMs} ms) · saltos al directo ${m.liveResyncs}") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MvButton("Arrancar", { scope.launch { EngineManager.ensureStarted(context) } }, icon = MvIcons.Play, enabled = supported == null)
            MvButton("Parar", { graph.player.stop(); scope.launch { EngineManager.stop() } }, icon = MvIcons.Close)
        }
        MvText("Outpace va dentro de MiVisor TV y solo escucha en esta tele. Se para solo al salir de la app (sin trabajo en segundo plano).", MvType.Caption)
    }
}

// ------------------------------------------------------------------ WARP

@Composable
private fun WarpPage(graph: AppGraph) {
    val context = LocalContext.current
    val warp by graph.warp.state.collectAsState()
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<WarpSignature.Report?>(null) }
    LaunchedEffect(warp.phase) { report = withContext(Dispatchers.IO) { WarpSignature.report(context) } }
    val vpn = remember(warp.phase) { NetState.vpnActive(context) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MvPanel {
            KeyValue("Estado", warpText(warp.phase, warp.detail))
            KeyValue("VPN activa en la tele", when (vpn) { true -> "Sí"; false -> "No"; null -> "No se sabe" })
            val r = report
            KeyValue("Firma de MiVisor TV", r?.own?.let { short(it) } ?: "—")
            KeyValue("Firma de WARP", r?.warp?.let { short(it) } ?: "No instalada")
            KeyValue("Control", when {
                r == null -> "Comprobando…"
                r.warp == null -> "WARP no está instalada (es opcional)"
                r.sameSigner && r.permissionGranted -> "Correcto: misma huella SHA-256 y permiso concedido"
                r.sameSigner -> "Misma huella, pero Android no ha concedido el permiso: reinstala WARP o MiVisor TV"
                else -> "Huella distinta: MiVisor TV no puede controlar esta WARP (no se rebaja la seguridad)"
            })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (warp.phase) {
                WarpPhase.CONNECTED -> MvButton("Desconectar", { graph.warp.disconnect() }, icon = MvIcons.Shield, primary = true)
                WarpPhase.PERMISSION_REQUIRED -> MvButton("Autorizar VPN", { graph.warp.requestPermission() }, icon = MvIcons.Shield, primary = true)
                WarpPhase.NEEDS_SETUP -> MvButton("Abrir WARP", { graph.warp.openApp() }, icon = MvIcons.Shield, primary = true)
                WarpPhase.DISCONNECTED, WarpPhase.ERROR -> MvButton("Conectar", { graph.warp.connect() }, icon = MvIcons.Shield, primary = true)
                else -> Unit
            }
            MvButton("Comprobar", { scope.launch { graph.warp.refresh() } }, icon = MvIcons.Refresh)
        }
        MvText("La firma se compara por la huella SHA-256 completa del certificado, no por su nombre.", MvType.Caption)
    }
}

private fun short(fp: String): String = fp.split(':').let { if (it.size > 8) it.take(4).joinToString(":") + " … " + it.takeLast(4).joinToString(":") else fp }

private fun warpText(phase: WarpPhase, detail: String?): String = when (phase) {
    WarpPhase.CHECKING -> "Comprobando…"
    WarpPhase.NOT_INSTALLED -> "No instalada"
    WarpPhase.SIGNATURE_MISMATCH -> "Firma distinta: no se puede controlar"
    WarpPhase.DISCONNECTED -> "Desconectada"
    WarpPhase.CONNECTING -> "Conectando…"
    WarpPhase.CONNECTED -> "Conectada"
    WarpPhase.DISCONNECTING -> "Desconectando…"
    WarpPhase.PERMISSION_REQUIRED -> "Falta autorizar la VPN"
    WarpPhase.NEEDS_SETUP -> "WARP necesita terminar su configuración"
    WarpPhase.ERROR -> "Error" + (detail?.let { " ($it)" } ?: "")
}

// ------------------------------------------------------------------ Pantalla

@Composable
private fun DisplayPage(graph: AppGraph, onLite: (Boolean) -> Unit) {
    var lite by remember { mutableStateOf(graph.prefs.lite) }
    var info by remember { mutableStateOf(graph.prefs.showSourceInfo) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MvRow("Modo ligero", { lite = !lite; graph.prefs.lite = lite; onLite(lite) },
            subtitle = "Sin animaciones ni escalados. Recomendado en teles con 1 GB de memoria.", icon = MvIcons.Bolt) { MvSwitch(lite) }
        MvRow("Información de la fuente", { info = !info; graph.prefs.showSourceInfo = info },
            subtitle = "Muestra proveedor, calidad y emisores en los controles del reproductor.", icon = MvIcons.Info) { MvSwitch(info) }
        MvText("Toda la interfaz respeta la zona segura de TV y usa textos de 12 sp como mínimo.", MvType.Caption)
    }
}

// ------------------------------------------------------------------ Agenda

@Composable
private fun AgendaPage(graph: AppGraph, toaster: Toaster) {
    val state by graph.agenda.state.collectAsState()
    var url by remember { mutableStateOf(graph.prefs.agendaUrl) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MvPanel {
            KeyValue("Origen", if (graph.prefs.agendaUrl.isBlank()) "mivisor-datos (GitHub), por defecto" else "Dirección propia")
            KeyValue("Descargada", if (state.fetchedAt > 0) "${hhmm(state.fetchedAt)} · ${ago(state.fetchedAt)}" else "Todavía no")
            state.agenda?.let { KeyValue("Generada", "${hhmm(it.generatedAt)} · ${it.events.size} eventos") }
            state.error?.let { KeyValue("Último error", it) }
        }
        MvTextField(url, { url = it }, "Dirección de agenda.json (vacío = la de por defecto)", placeholder = BuildConfig.AGENDA_URL)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MvButton("Guardar y recargar", { graph.prefs.agendaUrl = url; graph.agenda.load(force = true); toaster.show("Recargando agenda…") }, icon = MvIcons.Refresh, primary = true)
            MvButton("Usar la de por defecto", { url = ""; graph.prefs.agendaUrl = ""; graph.agenda.load(force = true) })
        }
        MvText("MiVisor TV solo lee la agenda publicada en GitHub. No depende de la Raspberry ni cambia nada en ella.", MvType.Caption)
    }
}

// ------------------------------------------------------------------ Fiabilidad

@Composable
private fun ReliabilityPage(graph: AppGraph, forgotten: Int, onForget: () -> Unit) {
    val known = remember(forgotten) { graph.sources.knownSources() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MvPanel {
            KeyValue("Fuentes conocidas", "$known")
            KeyValue("Aprende", "Solo de lo que ves con la app abierta; se guarda entre sesiones")
            KeyValue("Pausas", "10 min, 30 min, 2 h, 6 h y como mucho 12 h")
            KeyValue("Cuarentena", "Tras 3 fallos en días distintos; se revisa a los 1, 2, 4 y 7 días")
            KeyValue("No penaliza", "Cortes de Internet, WARP, el motor, poca memoria o fallos de muchos canales a la vez")
        }
        MvRow("Verificador automático", {}, subtitle = "Desactivado. Llegará en una entrega posterior y seguirá desactivado por defecto.", icon = MvIcons.Clock, enabled = false) { MvSwitch(false) }
        MvButton("Olvidar lo aprendido", onForget, icon = MvIcons.Delete)
    }
}

// ------------------------------------------------------------------ Registro

@Composable
private fun LogPage(graph: AppGraph) {
    var rows by remember { mutableStateOf<List<LogRow>>(emptyList()) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(version) { rows = withContext(Dispatchers.IO) { graph.log.recent(150) } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MvButton("Actualizar", { version++ }, icon = MvIcons.Refresh)
            MvButton("Borrar", { graph.log.clear(); rows = emptyList() }, icon = MvIcons.Delete)
        }
        MvText("Sin códigos, Content IDs completos, URLs ni IPs públicas: se limpian antes de guardarse.", MvType.Caption)
        Spacer(Modifier.height(6.dp))
        if (rows.isEmpty()) MvText("No hay entradas.", MvType.Body, color = MvColors.Text2)
        rows.forEach { r ->
            Row {
                MvText(hhmm(r.t), MvType.Caption, modifier = Modifier.width(56.dp))
                MvText(r.cat, MvType.Caption, color = MvColors.Accent2, modifier = Modifier.width(110.dp), maxLines = 1)
                MvText(r.text, MvType.Small, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ------------------------------------------------------------------ Próximamente y Acerca de

@Composable
private fun LaterPage() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            "Ace Stream dentro de MiVisor TV (su motor con el reproductor de MiVisor)" to "Más adelante",
            "Vista previa del canal al pasar por encima" to "Más adelante",
            "Copia de seguridad cifrada y restauración" to "Entrega 3d",
            "Diagnóstico completo, «Probar todo» e informe al móvil" to "Entrega 3d",
            "Verificador automático (desactivado por defecto)" to "Entrega 3d"
        ).forEach { (what, whenText) ->
            MvRow(what, {}, trailing = whenText, enabled = false)
        }
    }
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MvPanel {
            KeyValue("Versión", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            KeyValue("Paquete", BuildConfig.APPLICATION_ID)
            KeyValue("Motor", "Outpace @ ${BuildConfig.OUTPACE_COMMIT.take(12)} (AGPL-3.0-or-later)")
        }
        listOf(
            "Outpace (motor P2P)" to "licenses/NOTICE-outpace.txt",
            "Outpace · licencia completa" to "licenses/outpace-LICENSE.txt",
            "Inter (tipografía)" to "licenses/inter-OFL.txt",
            "QR Code generator (Nayuki)" to "licenses/qrcodegen-MIT.txt"
        ).forEach { (title, path) ->
            MvRow(title, { notice = runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull() }, icon = MvIcons.Info)
        }
        notice?.let { text ->
            MvPanel { MvText(text.take(6_000) + if (text.length > 6_000) "\n…" else "", MvType.Caption) }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        MvText(key, MvType.Small, modifier = Modifier.width(200.dp))
        MvText(value, MvType.Body, modifier = Modifier.weight(1f))
    }
}

