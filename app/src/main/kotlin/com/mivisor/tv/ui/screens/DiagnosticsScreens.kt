package com.mivisor.tv.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.backup.BackupCodec
import com.mivisor.tv.backup.BackupData
import com.mivisor.tv.data.AndroidLan
import com.mivisor.tv.data.CheckResult
import com.mivisor.tv.data.Diagnostics
import com.mivisor.tv.diagnostics.DiagnosticReport
import com.mivisor.tv.reliability.SourceState
import com.mivisor.tv.remote.MobileInputManager
import com.mivisor.tv.remote.QrGenerator
import com.mivisor.tv.remote.RemoteState
import com.mivisor.tv.storage.LogRow
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.QrView
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.ago
import com.mivisor.tv.ui.hhmm
import com.mivisor.tv.ui.design.MvBadge
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvNotice
import com.mivisor.tv.ui.design.MvPanel
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.Spinner
import com.mivisor.tv.ui.design.TvClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* Fase 3d · diagnóstico, informe, «Probar todo», copias cifradas y cuarentena, como en las pantallas del diseño. */

/** Miga «AJUSTES › …» y título de página. */
@Composable
fun PageHeader(eyebrow: String, title: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 20.dp)) {
        MvText(eyebrow.uppercase(), MvType.Eyebrow.copy(color = MvColors.Text2))
        Spacer(Modifier.height(6.dp))
        MvText(title, MvType.Title)
        if (sub != null) {
            Spacer(Modifier.height(10.dp))
            MvText(sub, MvType.Body.copy(color = MvColors.Text2), modifier = Modifier.widthIn(max = 600.dp))
        }
    }
}

/** Categorías del registro que cuentan como «algo ha pasado» al reproducir. */
private val INCIDENT_CATS = setOf("Fuente", "Recuperación", "Motor", "Ace Stream", "Red", "WARP")

private fun dotColor(row: LogRow): Color {
    val t = row.text.lowercase()
    return when {
        listOf("error", "falló", "fallo", "no arrancó", "sin imagen", "no responde").any { it in t } -> MvColors.Bad
        row.cat in setOf("Fuente", "Recuperación") -> MvColors.Soon
        else -> MvColors.Ok
    }
}

// ---------------------------------------------------------------------------------------------- ¿Algo no va bien?

@Composable
fun DiagnosticsScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val first = remember { FocusRequester() }
    var rows by remember { mutableStateOf<List<LogRow>>(emptyList()) }
    var report by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        rows = withContext(Dispatchers.IO) { graph.log.recent(30) }
        report = graph.diagnostics.report()
    }
    val incident = rows.firstOrNull { it.cat in INCIDENT_CATS }

    Row(Modifier.fillMaxSize().padding(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PageHeader("Ajustes › Diagnóstico", "¿Algo no va bien?") }
            item {
                val color = if (incident == null) MvColors.Ok else dotColor(incident).takeIf { it != MvColors.Ok } ?: MvColors.Soon
                Column(
                    Modifier.fillMaxWidth().background(color.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(color, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        MvText(
                            if (incident == null) "Sin incidencias recientes" else "${ago(incident.t).replaceFirstChar { it.uppercase() }} · ${incident.cat}",
                            MvType.SmallStrong.copy(color = color)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    MvText(incident?.text ?: "Todo lo que ha pasado al reproducir está en orden.", MvType.Heading, maxLines = 3)
                    Spacer(Modifier.height(10.dp))
                    MvText(
                        if (incident == null) "Si algo falla, aquí verás qué pasó y podrás enviar el informe."
                        else "Los detalles (motor, red, WARP y fuente) están en el informe. Envíalo si quieres que lo revisemos.",
                        MvType.Body.copy(color = MvColors.Text2)
                    )
                }
            }
            item {
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MvButton("Probar todo", { nav.go(Route.Checks) }, icon = MvIcons.Bolt, primary = true, focusRequester = first)
                    MvButton("Ver informe en la tele", { nav.go(Route.Report) }, icon = MvIcons.List)
                    MvButton("Fuentes que descansan", { nav.go(Route.Quarantine) }, icon = MvIcons.Layers)
                }
            }
            item {
                Row(Modifier.padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                    MvText("Últimos avisos", MvType.Title)
                    Spacer(Modifier.width(10.dp))
                    MvText("de todos los servicios", MvType.Small)
                }
            }
            if (rows.isEmpty()) item { MvText("Todavía no hay avisos.", MvType.Body.copy(color = MvColors.Text2)) }
            items(rows.take(12)) { r ->
                TvClickable(onClick = {}, modifier = Modifier.fillMaxWidth(), focusedScale = 1.01f, color = MvColors.Surface1, focusedColor = MvColors.Surface3) { _ ->
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(dotColor(r), CircleShape))
                                Spacer(Modifier.width(10.dp))
                                MvText(r.cat, MvType.BodyStrong)
                            }
                            MvText(r.text, MvType.Small, maxLines = 2)
                        }
                        Spacer(Modifier.width(16.dp))
                        MvText(hhmm(r.t), MvType.Heading)
                    }
                }
            }
        }
        SendReportPanel(report, toaster, Modifier.width(300.dp).padding(top = 56.dp))
    }
    InitialFocus(first)
}

/** «Enviar el informe»: por la red de casa (QR a una página), sin red (el informe en varios QR) o compartir. */
@Composable
private fun SendReportPanel(report: String?, toaster: Toaster, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { MobileInputManager(scope, { AndroidLan.pick(context) }) }
    val state by manager.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var part by remember { mutableIntStateOf(0) }
    val parts = remember(report) { report?.let { DiagnosticReport.parts(it) } ?: emptyList() }
    DisposableEffect(Unit) { onDispose { manager.cancel() } }
    LaunchedEffect(tab, report) {
        if (tab == 0 && report != null) manager.startShow(report) else manager.cancel()
    }

    MvPanel(modifier, PaddingValues(18.dp)) {
        MvText("Enviar el informe", MvType.Heading, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(6.dp))
        MvText("Sin claves, códigos ni enlaces privados.", MvType.Small, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Red de casa", "Sin red", "Compartir").forEachIndexed { i, label -> PillTab(label, tab == i) { tab = i } }
        }
        Spacer(Modifier.height(20.dp))
        if (report == null) {
            Box(Modifier.align(Alignment.CenterHorizontally)) { Spinner(36.dp) }
            return@MvPanel
        }
        when (tab) {
            0 -> when (val s = state) {
                is RemoteState.Waiting -> {
                    QrView(s.qr, Modifier.size(150.dp).align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(14.dp))
                    MvText("El móvil abre el informe por la red de casa · caduca a las ${hhmm(s.expiresAt)}", MvType.Small,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is RemoteState.Failed -> MvText("La tele no está en una red de casa. Prueba «Sin red».", MvType.Body.copy(color = MvColors.Text2))
                RemoteState.Expired -> MvButton("Generar otro código", { manager.startShow(report) }, icon = MvIcons.Refresh)
                else -> Box(Modifier.align(Alignment.CenterHorizontally)) { Spinner(36.dp) }
            }
            1 -> {
                val i = part.coerceIn(0, (parts.size - 1).coerceAtLeast(0))
                val qr = remember(i, parts) { parts.getOrNull(i)?.let { QrGenerator.encode(it) } }
                if (qr != null) QrView(qr, Modifier.size(170.dp).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))
                MvText("Parte ${i + 1} de ${parts.size} · escanéalas en orden con la cámara del móvil", MvType.Small,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MvButton("Anterior", { part = (i - 1).coerceAtLeast(0) }, enabled = i > 0)
                    MvButton("Siguiente", { part = (i + 1).coerceAtMost(parts.size - 1) }, enabled = i < parts.size - 1)
                }
            }
            else -> {
                MvText("Envía el informe con otra app de la tele (correo, mensajería…), si tienes alguna instalada.",
                    MvType.Body.copy(color = MvColors.Text2))
                Spacer(Modifier.height(16.dp))
                MvButton("Compartir informe", {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Informe de MiVisor TV").putExtra(Intent.EXTRA_TEXT, report)
                    val ok = runCatching {
                        context.startActivity(Intent.createChooser(send, "Enviar informe").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.isSuccess
                    if (!ok) toaster.show("No hay ninguna app para compartir en esta tele: usa «Red de casa» o «Sin red»")
                }, icon = MvIcons.Link, primary = true)
            }
        }
    }
}

@Composable
private fun PillTab(text: String, selected: Boolean, onClick: () -> Unit) {
    TvClickable(
        onClick = onClick, shape = RoundedCornerShape(22.dp), focusedScale = 1.04f,
        color = if (selected) MvColors.Text else MvColors.Surface3, focusedColor = MvColors.Text, ringColor = MvColors.Accent2
    ) { focused ->
        MvText(text, MvType.BodyStrong, color = if (selected || focused) MvColors.Bg else MvColors.Text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------- Informe en la tele

@Composable
fun ReportScreen(graph: AppGraph, nav: Nav) {
    val first = remember { FocusRequester() }
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { text = graph.diagnostics.report() }
    val lines = text?.lines() ?: emptyList()
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = MvDims.SafeX),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        item { PageHeader("Ajustes › Diagnóstico", "Informe", "Es lo mismo que se envía. Las claves, códigos y direcciones privadas ya van ocultos.") }
        item {
            Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MvButton("Volver", { nav.back() }, icon = MvIcons.Close, primary = true, focusRequester = first)
            }
        }
        if (text == null) item { Spinner(36.dp) }
        // Cada bloque de líneas se puede enfocar para desplazarse con el mando.
        items(lines.chunked(8)) { block ->
            TvClickable(onClick = {}, modifier = Modifier.fillMaxWidth(), focusedScale = 1f, color = MvColors.Surface1, focusedColor = MvColors.Surface2,
                shape = RoundedCornerShape(0.dp)) { _ ->
                MvText(block.joinToString("\n"), MvType.Small.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = MvColors.Text),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
        }
    }
    InitialFocus(first)
}

// ---------------------------------------------------------------------------------------------- Probar todo

@Composable
fun ChecksScreen(graph: AppGraph, nav: Nav) {
    val first = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<CheckResult>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    fun run() {
        if (running) return
        running = true
        scope.launch {
            graph.diagnostics.checkAll { results = it }
            running = false
        }
    }
    LaunchedEffect(Unit) { run() }
    val bad = results.count { it.level == CheckResult.Level.BAD }
    val warn = results.count { it.level == CheckResult.Level.WARN }

    Row(Modifier.fillMaxSize().padding(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 48.dp)) {
            item { PageHeader("Ajustes › Diagnóstico", "Probar todo", "Comprueba el motor, la red, WARP, Ace Stream, tus proveedores, la agenda y el servidor del móvil.") }
            items(results) { r -> CheckRow(r) }
        }
        Column(Modifier.width(300.dp).padding(top = 56.dp)) {
            MvPanel(Modifier.fillMaxWidth(), PaddingValues(18.dp)) {
                MvText(
                    when {
                        running -> "Comprobando…"
                        bad > 0 -> "$bad cosa(s) no funcionan"
                        warn > 0 -> "Funciona, con $warn aviso(s)"
                        else -> "Todo funciona"
                    }, MvType.Title
                )
                Spacer(Modifier.height(8.dp))
                MvText(
                    if (running) "Tarda unos segundos: el motor se arranca de verdad."
                    else if (bad + warn > 0) "Si no sabes qué hacer, envía el informe: lleva estos resultados."
                    else "No hace falta hacer nada.",
                    MvType.Body.copy(color = MvColors.Text2)
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MvButton("Repetir", { run() }, icon = MvIcons.Refresh, primary = true, enabled = !running, focusRequester = first)
                    MvButton("Enviar informe", { nav.go(Route.Diagnostics) }, icon = MvIcons.Phone)
                }
            }
        }
    }
    InitialFocus(first)
}

// ---------------------------------------------------------------------------------------------- Copias cifradas

@Composable
private fun StepCard(title: String, sub: String? = null, done: Boolean = false) {
    Column(Modifier.fillMaxWidth().background(MvColors.Surface1, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MvText(title, MvType.Heading, modifier = Modifier.weight(1f))
            if (done) MvBadge("Hecho", MvColors.Ok)
        }
        if (sub != null) MvText(sub, MvType.Small)
    }
}

/** Panel del QR de copias con sus etiquetas «Solo red de casa · Caduca … · Cifrada». */
@Composable
private fun CopyQrPanel(state: RemoteState, onRetry: () -> Unit, modifier: Modifier) {
    MvPanel(modifier, PaddingValues(20.dp)) {
        when (state) {
            is RemoteState.Waiting -> {
                QrView(state.qr, Modifier.size(180.dp).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(18.dp))
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MvBadge("Solo red de casa", MvColors.Ok)
                    MvBadge("Caduca a las ${hhmm(state.expiresAt)}", MvColors.Text2)
                    MvBadge("Cifrada", MvColors.Text2)
                }
            }
            is RemoteState.Failed -> MvText(
                if (state.reason == RemoteState.Reason.NO_LAN) "La tele no está en una red de casa (Wi-Fi o cable): las copias van por el móvil y necesitan esa red."
                else "No se pudo abrir el servidor local para el móvil.", MvType.Body.copy(color = MvColors.Text2)
            )
            RemoteState.Expired -> {
                MvText("El código ha caducado.", MvType.Body.copy(color = MvColors.Text2))
                Spacer(Modifier.height(12.dp))
                MvButton("Generar otro", onRetry, icon = MvIcons.Refresh)
            }
            else -> Box(Modifier.align(Alignment.CenterHorizontally)) { Spinner(40.dp) }
        }
    }
}

@Composable
fun ExportScreen(graph: AppGraph, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val manager = remember { MobileInputManager(scope, { AndroidLan.pick(context) }, log = { graph.log.add("Copias", it) }) }
    val state by manager.state.collectAsState()
    var export by remember { mutableStateOf<Diagnostics.Export?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { manager.cancel() } }
    LaunchedEffect(Unit) {
        val e = runCatching { graph.diagnostics.export() }.getOrElse { error = "No se pudo preparar la copia (${it.javaClass.simpleName})"; null }
        export = e
        if (e != null) manager.startDownload(e.fileName, e.bytes)
    }

    Row(Modifier.fillMaxSize().padding(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { PageHeader("Ajustes › Móvil y copias", "Exportar copia") }
            item { StepCard("1 · Escanea el código con el móvil") }
            item { StepCard("2 · Guarda el archivo .mvb", export?.let { "${it.fileName} · ${it.summary}" }) }
            item { StepCard("3 · Apunta el código de la copia", "Sin él no se puede abrir: no se guarda en ningún sitio") }
            item {
                val pin = export?.pin
                Column(
                    Modifier.fillMaxWidth().background(MvColors.Accent.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .border(1.dp, MvColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).padding(18.dp)
                ) {
                    MvText("CÓDIGO DE LA COPIA", MvType.Eyebrow)
                    Spacer(Modifier.height(8.dp))
                    MvText(pin?.let { it.take(3) + " " + it.drop(3) } ?: (error ?: "Preparando…"),
                        if (pin != null) MvType.Display.copy(letterSpacing = 6.sp) else MvType.Body)
                    Spacer(Modifier.height(8.dp))
                    MvText("La copia va cifrada: lleva tus proveedores con sus códigos, favoritos, recordatorios y ajustes.", MvType.Small)
                }
            }
            item {
                Row(Modifier.padding(top = 8.dp)) { MvButton("Hecho", { nav.back() }, icon = MvIcons.Close, primary = true, focusRequester = first) }
            }
        }
        CopyQrPanel(state, { export?.let { manager.startDownload(it.fileName, it.bytes) } }, Modifier.width(340.dp).padding(top = 56.dp))
    }
    InitialFocus(first)
}

@Composable
fun ImportScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val importFocus = remember { FocusRequester() }
    val manager = remember { MobileInputManager(scope, { AndroidLan.pick(context) }, log = { graph.log.add("Copias", it) }) }
    val state by manager.state.collectAsState()
    val upload by manager.uploads.collectAsState()
    var checked by remember { mutableStateOf<BackupData?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        manager.startUpload()
        onDispose { manager.cancel() }
    }
    LaunchedEffect(upload) {
        val u = upload ?: return@LaunchedEffect
        when (val r = graph.diagnostics.open(u.bytes, u.pin)) {
            is BackupCodec.Result.Ok -> { checked = r.data; problem = null; runCatching { importFocus.requestFocus() } }
            BackupCodec.Result.WrongPin -> { problem = "El código de la copia no es correcto. Vuelve a escanear y escríbelo otra vez."; manager.startUpload() }
            BackupCodec.Result.NotABackup -> { problem = "Ese archivo no es una copia de MiVisor TV (.mvb)."; manager.startUpload() }
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { PageHeader("Ajustes › Móvil y copias", "Importar copia") }
            item { StepCard("1 · Escanea el código con el móvil", done = checked != null) }
            item { StepCard("2 · Sube el archivo .mvb", done = checked != null) }
            item { StepCard("3 · Escribe el código de la copia en el móvil", "La tele comprueba la copia antes de cambiar nada", done = checked != null) }
            problem?.let { p -> item { MvNotice("No se ha importado", p, Modifier.fillMaxWidth(), color = MvColors.Bad) } }
            checked?.let { d ->
                item {
                    Column(
                        Modifier.fillMaxWidth().background(MvColors.Ok.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .border(1.dp, MvColors.Ok.copy(alpha = 0.45f), RoundedCornerShape(14.dp)).padding(18.dp)
                    ) {
                        MvText("Copia comprobada", MvType.Heading)
                        Spacer(Modifier.height(8.dp))
                        MvText(d.summary().replaceFirstChar { it.uppercase() } + ". Lo que ya tengas se conserva si no está en la copia.",
                            MvType.Body.copy(color = MvColors.Text2))
                    }
                }
            }
            item {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val d = checked
                    if (d != null) MvButton(if (busy) "Importando…" else "Importar ahora", {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                val text = runCatching { graph.diagnostics.import(d) }.getOrElse { "No se pudo importar (${it.javaClass.simpleName})" }
                                busy = false
                                toaster.show(text)
                                graph.prefs.wizardDone = true
                                nav.back()
                            }
                        }
                    }, icon = MvIcons.Add, primary = true, enabled = !busy, focusRequester = importFocus)
                    MvButton("Cancelar", { nav.back() }, icon = MvIcons.Close, focusRequester = first)
                }
            }
        }
        CopyQrPanel(state, { manager.startUpload() }, Modifier.width(340.dp).padding(top = 56.dp))
    }
    InitialFocus(first)
}

// ---------------------------------------------------------------------------------------------- Cuarentena

@Composable
fun QuarantineScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val lib by graph.providers.state.collectAsState()
    val first = remember { FocusRequester() }
    var tick by remember { mutableIntStateOf(0) }
    val resting = remember(tick) { graph.sources.resting() }
    val now = System.currentTimeMillis()

    /** «M+ Golf · Fuente 2» a partir del Content ID (sin enseñarlo). */
    fun nameOf(cid: String): String {
        for (ch in lib.library.channels) {
            val i = ch.sources.indexOfFirst { it.contentId == cid }
            if (i >= 0) return "${ch.name} · Fuente ${i + 1}"
        }
        return "Fuente que ya no está en tus proveedores"
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = MvDims.SafeX),
        contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(
                "Ajustes › Fuentes", "Fuentes que descansan",
                "Pausa: tras 3 fallos seguidos, 10 min, luego 30 min, 2 h, 6 h y como mucho 12 h; al volver tiene un intento de prueba. " +
                    "Cuarentena: si falla durante más de 3 días sin un solo éxito, se revisa al cabo de 1, 2, 4 y luego cada 7 días. " +
                    "Los fallos de Internet, WARP o del aparato no cuentan."
            )
        }
        if (resting.isEmpty()) item {
            MvNotice("Ninguna fuente descansa", "Todas tus fuentes están disponibles.", Modifier.fillMaxWidth(), color = MvColors.Ok)
        }
        items(resting, key = { it.contentId }) { r ->
            val quarantine = r.state != SourceState.PAUSED
            val until = r.untilMs
            val left = until?.let { ((it - now) / 60_000).coerceAtLeast(1) }
            val whenText = when {
                r.state == SourceState.QUARANTINE_DUE -> "se revisa la próxima vez que la abras"
                left == null -> ""
                left < 60 -> (if (quarantine) "se revisa en " else "vuelve en ") + "$left min"
                left < 48 * 60 -> (if (quarantine) "se revisa en " else "vuelve en ") + "${left / 60} h"
                else -> "se revisa al abrirla después del ${com.mivisor.tv.ui.dayLabel(until ?: now)}"
            }
            TvClickable(
                onClick = { graph.sources.release(r.contentId); tick++; toaster.show("Fuente liberada") },
                modifier = Modifier.fillMaxWidth(), focusedScale = 1.02f, color = MvColors.Surface1,
                focusedColor = MvColors.Text, ringColor = MvColors.Accent2
            ) { focused ->
                val fg = if (focused) MvColors.Bg else MvColors.Text
                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        MvText(nameOf(r.contentId), MvType.Heading, color = fg, maxLines = 1)
                        MvText(
                            listOfNotNull(
                                r.reason?.let { reasonText(it) },
                                if (quarantine) "${r.trips}.ª revisión" else "${r.trips}.ª pausa"
                            ).joinToString(" · "),
                            MvType.Small, color = if (focused) MvColors.Bg.copy(alpha = 0.7f) else MvColors.Text2, maxLines = 1
                        )
                    }
                    MvBadge(if (quarantine) "Cuarentena" else "Pausa", if (quarantine) MvColors.Bad else MvColors.Soon)
                    Spacer(Modifier.width(14.dp))
                    MvText(whenText, MvType.BodyStrong, color = fg, maxLines = 1)
                    Spacer(Modifier.width(14.dp))
                    MvBadge("Liberar", if (focused) MvColors.Bg else MvColors.Text2)
                }
            }
        }
        item {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (resting.isNotEmpty()) MvButton("Liberar todas", {
                    graph.sources.releaseAll(); tick++; toaster.show("Todas las fuentes vuelven a estar disponibles")
                }, icon = MvIcons.Refresh, focusRequester = first)
                MvButton("Volver", { nav.back() }, focusRequester = if (resting.isEmpty()) first else null)
            }
        }
    }
    InitialFocus(first)
}

/** Motivo del último fallo, en palabras. */
private fun reasonText(k: String): String = when (k) {
    "no_peers" -> "el motor no encontró emisores"
    "no_video" -> "no dio imagen"
    "no_progress" -> "dejó de recibir datos"
    "not_found" -> "la fuente ya no existe"
    "client_error" -> "error al abrirla"
    "frozen" -> "imagen congelada"
    "stalled_early" -> "se cortó al empezar"
    else -> "fallos seguidos"
}
