package com.mivisor.tv.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
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
import com.mivisor.tv.ui.design.MvIcon
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvRadio
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

/** Menú de Ajustes del diseño (con su icono y el valor actual a la derecha). */
private enum class Page(val title: String, val icon: Int, val intro: String?) {
    PROVIDERS("Proveedores", MvIcons.Box, null),
    ENGINE("Motor", MvIcons.Chip, "Outpace va dentro de MiVisor TV y es el que se usa siempre que funciona. Ace Stream es una app aparte que puedes elegir como alternativa."),
    WARP("WARP", MvIcons.Shield, null),
    AUDIO("Audio y subtítulos", MvIcons.Volume, "Se aplican al abrir un canal. Si la emisión no trae tu idioma, se usa la pista principal."),
    SOURCES("Fuentes y recuperación", MvIcons.Layers, "MiVisor TV aprende de lo que ves con la app abierta y lo recuerda entre sesiones. Cuando una fuente deja de dar imagen, espera, reintenta y cambia de fuente sin que hagas nada."),
    VERIFIER("Verificador", MvIcons.Check, "Prueba fuentes mientras MiVisor TV está abierta, para que «lista» signifique algo. Nunca trabaja con la tele apagada ni en segundo plano. Viene desactivado: MiVisor TV ya aprende de lo que ves."),
    DISPLAY("Pantalla y rendimiento", MvIcons.Tune, "El modo ligero quita animaciones, sombras y escalados. Se recomienda en aparatos con 1,5 GB de memoria o menos."),
    AGENDA("Agenda", MvIcons.Calendar, null),
    DIAGNOSTICS("Diagnóstico", MvIcons.Pulse, null),
    MOBILE("Móvil y copias", MvIcons.Phone, "El móvil se conecta a la tele por la red de casa, sin servidores externos. Cada código QR caduca en 5 minutos."),
    ABOUT("Acerca de", MvIcons.Info, null)
}

@Composable
fun SettingsScreen(graph: AppGraph, nav: Nav, toaster: Toaster, onLook: () -> Unit) {
    var page by remember { mutableStateOf(Page.PROVIDERS) }
    var editing by remember { mutableStateOf<ProviderInfo?>(null) }
    var deleting by remember { mutableStateOf<ProviderInfo?>(null) }
    var forgetting by remember { mutableStateOf(false) }
    var forgotten by remember { mutableIntStateOf(0) }
    var values by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    val lib by graph.providers.state.collectAsState()
    val warp by graph.warp.state.collectAsState()
    fun valueOf(p: Page): String? = when (p) {
        Page.PROVIDERS -> lib.providers.size.toString()
        Page.ENGINE -> if (graph.prefs.engine == "ace") "Ace Stream" else "Outpace"
        Page.WARP -> when (warp.phase) {
            WarpPhase.CONNECTED -> "Conectada"
            WarpPhase.NOT_INSTALLED -> "No instalada"
            WarpPhase.SIGNATURE_MISMATCH, WarpPhase.ERROR -> "Error"
            else -> "Desconectada"
        }
        Page.VERIFIER -> if (graph.prefs.verifier) "Sí" else "No"
        else -> null
    }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(start = MvDims.SafeX, end = MvDims.SafeX)) {
            Column(Modifier.width(300.dp).fillMaxHeight()) {
                MvText("Ajustes", MvType.Title, modifier = Modifier.padding(bottom = 14.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
                    itemsIndexed(Page.values().toList()) { i, p ->
                        val v = remember(values, lib, warp) { valueOf(p) }
                        TvClickable(
                            onClick = { page = p }, onFocus = { page = p }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            focusedScale = 1.02f, color = if (p == page) MvColors.Surface2 else MvColors.Bg, focusedColor = MvColors.Text,
                            ringColor = MvColors.Accent2, focusRequester = if (i == 0) first else null
                        ) { focused ->
                            val fg = if (focused) MvColors.Bg else if (p == page) MvColors.Text else MvColors.Text2
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                MvIcon(p.icon, size = 18.dp, tint = fg)
                                Spacer(Modifier.width(12.dp))
                                MvText(p.title, MvType.BodyStrong, color = fg, maxLines = 1, modifier = Modifier.weight(1f))
                                if (v != null) MvText(v, MvType.SmallStrong, color = if (focused) MvColors.Bg else MvColors.Text2, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(32.dp))
            // Columna con desplazamiento: el foco del mando la hace avanzar sola.
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 46.dp, bottom = 48.dp, end = 6.dp)) {
                MvText(page.title, MvType.Title, modifier = Modifier.padding(bottom = 8.dp))
                page.intro?.let { MvText(it, MvType.Body.copy(color = MvColors.Text2), modifier = Modifier.padding(bottom = 16.dp)) }
                when (page) {
                    Page.PROVIDERS -> ProvidersPage(graph, nav, toaster, onEdit = { editing = it })
                    Page.ENGINE -> EnginePage(graph, onChange = { values++ })
                    Page.WARP -> WarpPage(graph, nav)
                    Page.AUDIO -> AudioPage(graph)
                    Page.SOURCES -> SourcesPage(graph, nav, forgotten, onForget = { forgetting = true })
                    Page.VERIFIER -> VerifierPage(graph, onChange = { values++ })
                    Page.DISPLAY -> DisplayPage(graph, onLook)
                    Page.AGENDA -> AgendaPage(graph, toaster)
                    Page.DIAGNOSTICS -> DiagnosticsPage(graph, nav)
                    Page.MOBILE -> MobilePage(nav)
                    Page.ABOUT -> AboutPage(graph, nav)
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
private fun EnginePage(graph: AppGraph, onChange: () -> Unit) {
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
        MvRow("Outpace (integrado)", { engineChoice = "outpace"; graph.prefs.engine = "outpace"; onChange() },
            subtitle = "Recomendado · elige la fuente, recupera los cortes y recuerda el audio") { MvRadio(engineChoice == "outpace") }
        MvRow("Ace Stream (app externa)", { if (aceInstalled) { engineChoice = "ace"; graph.prefs.engine = "ace"; onChange() } },
            subtitle = if (aceInstalled) "Instalada · abre cada canal en su app y se vuelve con Atrás; MiVisor TV no controla audio ni subtítulos"
            else "No está instalada en esta tele", enabled = aceInstalled) { MvRadio(engineChoice == "ace") }
        MvRow("Si Outpace falla", {
            fallback = when (fallback) { "ask" -> "auto"; "auto" -> "never"; else -> "ask" }
            graph.prefs.aceFallback = fallback
        }, subtitle = when (fallback) {
            "auto" -> "Abro solo Ace Stream y te aviso"
            "never" -> "No hago nada; sigo con Outpace"
            else -> "Te ofrezco probar con Ace Stream"
        }, trailing = when (fallback) { "auto" -> "Automático"; "never" -> "Nunca"; else -> "Preguntar" })
        Spacer(Modifier.height(6.dp))
        MvPanel {
            MvText("ESTADO", MvType.Eyebrow.copy(color = MvColors.Text2), modifier = Modifier.padding(bottom = 8.dp))
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
private fun WarpPage(graph: AppGraph, nav: Nav) {
    val context = LocalContext.current
    val warp by graph.warp.state.collectAsState()
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<WarpSignature.Report?>(null) }
    LaunchedEffect(warp.phase) { report = withContext(Dispatchers.IO) { WarpSignature.report(context) } }
    val vpn = remember(warp.phase) { NetState.vpnActive(context) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val bad = report?.let { it.warp != null && !it.sameSigner } == true
        if (bad) {
            val r = report!!
            Column(
                Modifier.fillMaxWidth().background(MvColors.Bad.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .border(1.dp, MvColors.Bad.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).padding(18.dp)
            ) {
                MvText("MiVisor TV no puede controlar WARP", MvType.Heading)
                Spacer(Modifier.height(8.dp))
                MvText("WARP solo acepta órdenes de apps firmadas con su misma clave, y esta copia de MiVisor TV está firmada con otra. " +
                    "Reproducir sigue funcionando; solo falla conectar y desconectar WARP desde aquí.", MvType.Body.copy(color = MvColors.Text2))
                Spacer(Modifier.height(14.dp))
                FingerRow("Huella esperada", r.warp?.let { short(it) } ?: "—")
                FingerRow("Huella de esta app", r.own?.let { short(it) } ?: "—")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MvButton("Abrir WARP", { graph.warp.openApp() }, icon = MvIcons.Shield)
                MvButton("Ver diagnóstico", { nav.go(Route.Diagnostics) }, icon = MvIcons.Pulse)
            }
        }
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

@Composable
private fun FingerRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        MvText(label, MvType.Body.copy(color = MvColors.Text2), modifier = Modifier.width(150.dp))
        MvText(value, MvType.BodyStrong.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
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

// ------------------------------------------------------------------ Pantalla y rendimiento

@Composable
private fun DisplayPage(graph: AppGraph, onLook: () -> Unit) {
    var side by remember { mutableStateOf(graph.prefs.navStyle == "side") }
    var lite by remember { mutableStateOf(graph.prefs.lite) }
    var big by remember { mutableStateOf(graph.prefs.bigText) }
    var info by remember { mutableStateOf(graph.prefs.showSourceInfo) }
    var start by remember { mutableStateOf(graph.prefs.startScreen) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MvRow("Menú", { side = !side; graph.prefs.navStyle = if (side) "side" else "top"; onLook() },
            subtitle = "Arriba, o en un lateral que se despliega", trailing = if (side) "En un lateral" else "Arriba")
        MvRow("Modo ligero", { lite = !lite; graph.prefs.lite = lite; onLook() }) { MvSwitch(lite) }
        MvRow("Vista previa en Canales", {}, subtitle = "Todavía no disponible: llega en una entrega posterior", enabled = false) { MvSwitch(false) }
        MvRow("Texto más grande", { big = !big; graph.prefs.bigText = big; onLook() }) { MvSwitch(big) }
        MvRow("Al abrir MiVisor", {
            start = when (start) { "home" -> "agenda"; "agenda" -> "channels"; "channels" -> "last"; else -> "home" }
            graph.prefs.startScreen = start
        }, trailing = when (start) { "agenda" -> "Agenda"; "channels" -> "Canales"; "last" -> "Último canal"; else -> "Inicio" })
        MvRow("Información de la fuente", { info = !info; graph.prefs.showSourceInfo = info },
            subtitle = "Proveedor, calidad y emisores en los controles del reproductor") { MvSwitch(info) }
    }
}

// ------------------------------------------------------------------ Audio y subtítulos

private val LANGS = listOf("es" to "Español", "en" to "Inglés", "ca" to "Catalán", "pt" to "Portugués", "fr" to "Francés", "it" to "Italiano")

@Composable
private fun AudioPage(graph: AppGraph) {
    var lang by remember { mutableStateOf(graph.prefs.audioLanguage) }
    var subs by remember { mutableStateOf(graph.prefs.subtitles) }
    var size by remember { mutableStateOf(graph.prefs.subtitleSize) }
    var keep by remember { mutableStateOf(graph.prefs.rememberAudio) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MvRow("Idioma de audio preferido", {
            val i = LANGS.indexOfFirst { it.first == lang }
            lang = LANGS[(i + 1) % LANGS.size].first
            graph.prefs.audioLanguage = lang
        }, trailing = LANGS.firstOrNull { it.first == lang }?.second ?: lang)
        MvRow("Subtítulos", { subs = !subs; graph.prefs.subtitles = subs }) { MvSwitch(subs) }
        MvRow("Tamaño de los subtítulos", {
            size = when (size) { "normal" -> "grande"; "grande" -> "muy-grande"; else -> "normal" }
            graph.prefs.subtitleSize = size
        }, trailing = when (size) { "grande" -> "Grande"; "muy-grande" -> "Muy grande"; else -> "Normal" })
        MvRow("Recordar la elección en cada canal", { keep = !keep; graph.prefs.rememberAudio = keep },
            subtitle = "Si cambias de pista en un canal, se usa esa la próxima vez") { MvSwitch(keep) }
        MvText("Los cambios se aplican al abrir el siguiente canal.", MvType.Caption)
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

// ------------------------------------------------------------------ Fuentes y recuperación

@Composable
private fun SourcesPage(graph: AppGraph, nav: Nav, forgotten: Int, onForget: () -> Unit) {
    val known = remember(forgotten) { graph.sources.knownSources() }
    val resting = remember(forgotten) { graph.sources.resting().size }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MvRow("Retraso respecto al directo", {}, subtitle = "Más retraso, menos cortes · lo ajusta Outpace según la fuente", trailing = "Automático")
        MvRow("Sin imagen: reintentar la misma fuente", {}, subtitle = "Con escalones de recuperación antes de cambiar", trailing = "Fijo")
        MvRow("Cambiar de fuente", {}, subtitle = "Cuando la fuente no responde o lleva demasiado sin datos", trailing = "Fijo")
        MvRow("No penalizar fuentes por fallos de la red, de la VPN o del aparato", {},
            subtitle = "Si el corte es de casa, la fuente no va a cuarentena") { MvSwitch(true) }
        MvRow("Fuentes que descansan", { nav.go(Route.Quarantine) },
            subtitle = "En pausa o en cuarentena; puedes liberarlas", trailing = "$resting")
        MvPanel {
            MvText("LÍMITES", MvType.Eyebrow.copy(color = MvColors.Text2), modifier = Modifier.padding(bottom = 8.dp))
            KeyValue("Fuentes conocidas", "$known")
            KeyValue("Pausas", "Tras 3 fallos seguidos: 10 min, 30 min, 2 h, 6 h y como mucho 12 h")
            KeyValue("Cuarentena", "Más de 3 días fallando sin un solo éxito; se revisa a los 1, 2, 4 y luego cada 7 días")
            KeyValue("No penaliza", "Cortes de Internet, WARP, el motor, poca memoria o fallos de muchos canales a la vez")
        }
        Row { MvButton("Olvidar lo aprendido", onForget, icon = MvIcons.Delete) }
    }
}

// ------------------------------------------------------------------ Verificador

@Composable
private fun VerifierPage(graph: AppGraph, onChange: () -> Unit) {
    val scope = rememberCoroutineScope()
    var favsOnly by remember { mutableStateOf(graph.prefs.verifierFavoritesOnly) }
    var running by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var last by remember { mutableStateOf(graph.prefs.verifierLast) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { cancel = true } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MvRow("Probar fuentes con MiVisor abierto", {}, subtitle = "Automático: no está disponible en esta entrega; queda desactivado", enabled = false) { MvSwitch(false) }
        MvRow(if (running) "Comprobando… (OK para parar)" else "Comprobar ahora", {
            if (running) { cancel = true; return@MvRow }
            running = true; cancel = false; progress = "Arrancando Outpace…"
            graph.player.stop()
            scope.launch {
                val text = graph.diagnostics.verify(
                    onProgress = { done, total, ok -> progress = "$done de $total fuentes · $ok con imagen" },
                    isCancelled = { cancel }
                )
                running = false; progress = null; last = graph.prefs.verifierLast.ifBlank { text }; onChange()
            }
        }, subtitle = progress ?: "Una pasada ahora, con la app abierta. Solo apunta las fuentes que funcionan; nunca penaliza")
        MvRow("Solo mis favoritos", { favsOnly = !favsOnly; graph.prefs.verifierFavoritesOnly = favsOnly },
            subtitle = "Si no, prueba las fuentes de todos los canales (como mucho 60)") { MvSwitch(favsOnly) }
        if (last.isNotBlank()) MvText("Última comprobación: $last", MvType.Caption)
    }
}

// ------------------------------------------------------------------ Diagnóstico

@Composable
private fun DiagnosticsPage(graph: AppGraph, nav: Nav) {
    var showLog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MvRow("¿Algo no va bien?", { nav.go(Route.Diagnostics) }, subtitle = "Lo último que ha pasado y enviar el informe al móvil", icon = MvIcons.Pulse)
        MvRow("Probar todo", { nav.go(Route.Checks) }, subtitle = "Motor, red, WARP, Ace Stream, proveedores, agenda y servidor del móvil", icon = MvIcons.Bolt)
        MvRow("Ver informe en la tele", { nav.go(Route.Report) }, subtitle = "Sin claves, códigos ni enlaces privados", icon = MvIcons.List)
        MvRow(if (showLog) "Ocultar el registro" else "Registro completo", { showLog = !showLog }, subtitle = "Las últimas 150 entradas", icon = MvIcons.History)
        if (showLog) LogPage(graph)
    }
}

// ------------------------------------------------------------------ Móvil y copias

@Composable
private fun MobilePage(nav: Nav) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MvRow("Escribir con el móvil", { nav.go(Route.AddProvider()) }, subtitle = "Para códigos, nombres y búsquedas: aparece junto a cada campo")
        MvRow("Exportar copia cifrada", { nav.go(Route.Backup(export = true)) }, subtitle = "Proveedores, favoritos, recordatorios y ajustes, cifrados con un código")
        MvRow("Importar copia", { nav.go(Route.Backup(export = false)) }, subtitle = "De otra tele con MiVisor TV")
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

// ------------------------------------------------------------------ Acerca de

@Composable
private fun AboutPage(graph: AppGraph, nav: Nav) {
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
        MvRow("Repetir la primera configuración", {
            graph.prefs.wizardDone = false
            nav.tab(Route.Wizard)
        }, subtitle = "No borra nada: vuelve a mostrar el asistente", icon = MvIcons.Refresh)
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

