package com.mivisor.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.data.CheckResult
import com.mivisor.tv.outpace.EngineManager
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.PhoneQrCard
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.design.MvBadge
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvRow
import com.mivisor.tv.ui.design.MvSwitch
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.TvClickable
import kotlinx.coroutines.launch

/** Paso del asistente: se conserva al ir a «Escribir con el mando» o «Importar copia» y volver. */
private object WizardStep {
    var value by mutableIntStateOf(0)
}

/**
 * Primera configuración (como el diseño): 5 pasos con barra de progreso.
 * 0 Bienvenida · 1 Tus proveedores (QR) · 2 Comprobación rápida · 3 Menú y modo ligero · 4 Todo listo.
 */
@Composable
fun WizardScreen(graph: AppGraph, nav: Nav, onDone: () -> Unit) {
    var step by WizardStep::value
    BackHandler(enabled = step > 0) { step-- }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(260.dp).background(Brush.verticalGradient(listOf(Color(0xFF13233D), MvColors.Bg))))
        Column(Modifier.fillMaxSize().padding(horizontal = MvDims.SafeX * 1.5f, vertical = MvDims.SafeY)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) { i ->
                    Box(Modifier.size(width = 36.dp, height = 4.dp).background(if (i <= step) MvColors.Accent2 else MvColors.Surface4, RoundedCornerShape(2.dp)))
                }
            }
            Spacer(Modifier.height(28.dp))
            when (step) {
                0 -> Welcome(onStart = { step = 1 }, onImport = { nav.go(Route.Backup(export = false)) })
                1 -> Providers(graph, nav, onNext = { step = 2 })
                2 -> QuickCheck(graph, onNext = { step = 3 })
                3 -> MenuStyle(graph, onNext = { step = 4 })
                else -> AllSet(graph, onDone = { WizardStep.value = 0; onDone() })
            }
        }
        if (step > 0) MvText(
            "Atrás  paso anterior", MvType.Caption,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = MvDims.SafeX * 1.5f, bottom = MvDims.SafeY)
        )
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).background(Brush.linearGradient(listOf(MvColors.Accent, MvColors.Accent2)), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) { MvText("M", MvType.BodyStrong.copy(fontWeight = FontWeight.Black, color = Color(0xFF061226))) }
        Spacer(Modifier.width(10.dp))
        MvText("MiVisor", MvType.Heading.copy(fontWeight = FontWeight.Black))
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, onImport: () -> Unit) {
    val first = remember { FocusRequester() }
    Column(Modifier.widthIn(max = 720.dp)) {
        Brand()
        Spacer(Modifier.height(18.dp))
        MvText("Vamos a dejarlo listo", MvType.Display)
        Spacer(Modifier.height(10.dp))
        MvText("Tres pasos: tus proveedores, una comprobación rápida y cómo quieres ver el menú. Puedes cambiarlo todo después en Ajustes.",
            MvType.Body.copy(color = MvColors.Text2))
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MvButton("Empezar", onStart, icon = MvIcons.Play, primary = true, focusRequester = first)
            MvButton("Importar copia de otra tele", onImport, icon = MvIcons.Phone)
        }
    }
    InitialFocus(first, "w0")
}

@Composable
private fun Providers(graph: AppGraph, nav: Nav, onNext: () -> Unit) {
    val lib by graph.providers.state.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    Row {
        Column(Modifier.weight(1f)) {
            MvText("Tus proveedores", MvType.Title)
            Spacer(Modifier.height(8.dp))
            MvText("Escanea el código con el móvil y escribe allí los códigos de tus proveedores. Se guardan solo en esta tele.",
                MvType.Body.copy(color = MvColors.Text2))
            Spacer(Modifier.height(18.dp))
            lib.providers.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).background(MvColors.Surface1, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        MvText(p.name, MvType.BodyStrong)
                        MvText("${p.channels} canales leídos", MvType.Caption)
                    }
                    MvBadge("Añadido", MvColors.Ok)
                }
            }
            message?.let { MvText(it, MvType.SmallStrong, color = MvColors.Soon, modifier = Modifier.padding(bottom = 8.dp)) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MvButton("Continuar", onNext, icon = MvIcons.Play, primary = true, focusRequester = first)
                MvButton("Escribir con el mando", { nav.go(Route.AddProvider(fromWizard = true)) }, icon = MvIcons.Add)
                MvButton("Más tarde", onNext)
            }
        }
        Spacer(Modifier.width(40.dp))
        PhoneQrCard(
            "Escribe desde el móvil", "Cada código que envíes se comprueba y se añade aquí.",
            onText = { code ->
                message = "Comprobando el código…"
                scope.launch { message = graph.providers.add(code, "", false) ?: "Proveedor añadido" }
            },
            modifier = Modifier.width(360.dp)
        )
    }
    InitialFocus(first, "w1")
}

@Composable
private fun QuickCheck(graph: AppGraph, onNext: () -> Unit) {
    var results by remember { mutableStateOf<List<CheckResult>>(emptyList()) }
    val first = remember { FocusRequester() }
    val context = LocalContext.current
    LaunchedEffect(Unit) { graph.diagnostics.checkAll { results = it } }
    Column(Modifier.widthIn(max = 760.dp)) {
        MvText("Comprobación rápida", MvType.Title)
        Spacer(Modifier.height(16.dp))
        results.filter { it.name != "Servidor del móvil" && it.name != "Red" }.forEach { r -> CheckRow(r) }
        val lowRam = remember { EngineManager.isLowRam(context) }
        CheckRow(CheckResult("Este aparato", graph.diagnostics.device(), if (lowRam) CheckResult.Level.WARN else CheckResult.Level.OK), warnLabel = "Modesto")
        Spacer(Modifier.height(16.dp))
        MvButton("Continuar", onNext, icon = MvIcons.Play, primary = true, focusRequester = first)
    }
    InitialFocus(first, "w2")
}

/** Fila de comprobación con su etiqueta de estado («Bien», «Revisar», «Error»…). */
@Composable
fun CheckRow(r: CheckResult, warnLabel: String = "Revisar") {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).background(MvColors.Surface1, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            MvText(r.name, MvType.BodyStrong)
            MvText(r.detail, MvType.Caption, maxLines = 2)
        }
        when (r.level) {
            CheckResult.Level.RUNNING -> com.mivisor.tv.ui.design.Spinner(20.dp)
            CheckResult.Level.OK -> MvBadge("Bien", MvColors.Ok)
            CheckResult.Level.WARN -> MvBadge(warnLabel, MvColors.Soon)
            CheckResult.Level.BAD -> MvBadge("Error", MvColors.Bad)
            CheckResult.Level.OPTIONAL -> MvBadge("Opcional", MvColors.Text3)
        }
    }
}

@Composable
private fun MenuStyle(graph: AppGraph, onNext: () -> Unit) {
    val context = LocalContext.current
    val lowRam = remember { EngineManager.isLowRam(context) }
    var side by remember { mutableStateOf(graph.prefs.navStyle == "side") }
    var lite by remember { mutableStateOf(graph.prefs.lite || lowRam) }
    val first = remember { FocusRequester() }
    Column {
        MvText("¿Cómo quieres el menú?", MvType.Title)
        Spacer(Modifier.height(6.dp))
        MvText(if (lowRam) "Este aparato es modesto: te recomiendo el modo ligero. Se puede cambiar en Ajustes." else "Se puede cambiar en Ajustes.",
            MvType.Body.copy(color = MvColors.Text2))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            MenuCard("Arriba", "Las secciones en una barra que se esconde al bajar", !side, top = true, fr = first) { side = false }
            MenuCard("En un lateral", "Iconos a la izquierda que se despliegan", side, top = false, fr = null) { side = true }
        }
        Spacer(Modifier.height(16.dp))
        MvRow("Modo ligero", { lite = !lite }, subtitle = if (lowRam) "Recomendado para este aparato" else "Sin animaciones ni sombras",
            modifier = Modifier.widthIn(max = 640.dp)) { MvSwitch(lite) }
        Spacer(Modifier.height(16.dp))
        MvButton("Continuar", {
            graph.prefs.navStyle = if (side) "side" else "top"
            graph.prefs.lite = lite
            onNext()
        }, icon = MvIcons.Play, primary = true)
    }
    InitialFocus(first, "w3")
}

@Composable
private fun MenuCard(title: String, body: String, selected: Boolean, top: Boolean, fr: FocusRequester?, onClick: () -> Unit) {
    TvClickable(
        onClick = onClick, focusRequester = fr, modifier = Modifier.width(300.dp),
        color = MvColors.Surface1, focusedColor = MvColors.Surface2
    ) { _ ->
        Column(
            Modifier.then(if (selected) Modifier.border(2.dp, MvColors.Accent2, RoundedCornerShape(MvDims.Radius)) else Modifier).padding(14.dp)
        ) {
            // Miniatura del menú.
            Box(Modifier.fillMaxWidth().height(110.dp).background(MvColors.Bg, RoundedCornerShape(8.dp)).padding(10.dp)) {
                if (top) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(4) { i -> Box(Modifier.size(width = 34.dp, height = 8.dp).background(if (i % 2 == 0) MvColors.Text else MvColors.Surface4, RoundedCornerShape(4.dp))) }
                    }
                    repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(3) { Box(Modifier.size(width = 70.dp, height = 28.dp).background(MvColors.Surface3, RoundedCornerShape(4.dp))) } } }
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { repeat(5) { i -> Box(Modifier.size(10.dp).background(if (i == 0) MvColors.Text else MvColors.Surface4, RoundedCornerShape(3.dp))) } }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(3) { Box(Modifier.size(width = 64.dp, height = 36.dp).background(MvColors.Surface3, RoundedCornerShape(4.dp))) } } }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            MvText(title, MvType.Heading)
            MvText(body, MvType.Small)
        }
    }
}

@Composable
private fun AllSet(graph: AppGraph, onDone: () -> Unit) {
    val lib by graph.providers.state.collectAsState()
    val agenda by graph.agenda.state.collectAsState()
    val first = remember { FocusRequester() }
    Column(Modifier.widthIn(max = 760.dp)) {
        Box(Modifier.size(56.dp).background(MvColors.Ok, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            MvText("✓", MvType.Title.copy(color = MvColors.Bg))
        }
        Spacer(Modifier.height(20.dp))
        MvText("Todo listo", MvType.Display)
        Spacer(Modifier.height(10.dp))
        MvText(
            "${lib.providers.size} proveedores · ${lib.library.channels.size} canales · " +
                (if (agenda.agenda != null) "agenda al día" else "agenda pendiente") +
                ". Si algo falla, en Ajustes › Diagnóstico tienes el informe para enviarlo.",
            MvType.Body.copy(color = MvColors.Text2)
        )
        Spacer(Modifier.height(24.dp))
        MvButton("Ir a Inicio", onDone, icon = MvIcons.Home, primary = true, focusRequester = first)
    }
    InitialFocus(first, "w4")
}
