package com.mivisor.tv.ui.screens

import android.graphics.Color as AColor
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mivisor.tv.AppGraph
import com.mivisor.tv.playback.PlaybackController.Phase
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvPanel
import com.mivisor.tv.ui.design.MvRow
import com.mivisor.tv.ui.design.MvTag
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.Spinner
import kotlinx.coroutines.delay

private enum class Panel { SOURCES, TRACKS }

private val FITS = listOf(
    "Ajustar" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
    "Rellenar" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    "Estirar" to AspectRatioFrameLayout.RESIZE_MODE_FILL
)

/** Reproductor a pantalla completa. OK muestra los controles; Atrás los oculta y, después, sale. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(graph: AppGraph, route: Route.Player, onBack: () -> Unit) {
    val ui by graph.player.ui.collectAsState()
    val lib by graph.providers.state.collectAsState()
    val channel = lib.library.channel(route.channelId)
    var controls by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var fit by remember { mutableIntStateOf(0) }
    var touch by remember { mutableIntStateOf(0) }
    val catcher = remember { FocusRequester() }
    val firstControl = remember { FocusRequester() }

    // Empieza al entrar (o en cuanto la biblioteca tenga el canal, si se abrió antes de leer los proveedores).
    LaunchedEffect(route, channel != null) {
        val ch = channel
        if (ch != null && graph.player.ui.value.channelId != ch.id) graph.player.play(ch.request(), route.chosen)
    }
    DisposableEffect(Unit) { onDispose { graph.player.stop() } }

    // Los controles se ocultan solos tras 6 s sin tocar el mando (no si hay un panel abierto).
    LaunchedEffect(controls, panel, touch) {
        if (controls && panel == null && ui.phase == Phase.PLAYING) {
            delay(6_000)
            controls = false
        }
    }
    LaunchedEffect(controls, panel) {
        delay(80)
        runCatching { if (controls || panel != null) firstControl.requestFocus() else catcher.requestFocus() }
    }

    BackHandler {
        when {
            panel != null -> panel = null
            controls && ui.phase == Phase.PLAYING -> controls = false
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val phase = ui.phase
        val fitMode = FITS[fit].second
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(AColor.BLACK)
                    setKeepContentOnPlayerReset(false)
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = graph.player.player
                }
            },
            onRelease = { it.player = null },
            update = { view ->
                val p = graph.player.player
                if (view.player !== p) view.player = p
                if (view.resizeMode != fitMode) view.resizeMode = fitMode
                view.keepScreenOn = phase != Phase.FAILED && phase != Phase.IDLE
            },
            modifier = Modifier.fillMaxSize()
        )

        // Recoge las teclas cuando no hay controles a la vista.
        Box(
            Modifier.fillMaxSize().focusRequester(catcher).onKeyEvent { e ->
                val n = e.nativeKeyEvent
                if (n.action != AndroidKeyEvent.ACTION_UP) return@onKeyEvent false
                when (n.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_INFO -> { controls = true; touch++; true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_MEDIA_PLAY, AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        graph.player.togglePause(); true
                    }
                    else -> false
                }
            }.focusable()
        )

        if (channel == null) {
            Status("Canal no disponible", "Este canal ya no está en tus proveedores.", busy = false) {
                MvButton("Volver", onBack, focusRequester = firstControl)
            }
            InitialFocus(firstControl)
            return@Box
        }

        when (phase) {
            Phase.IDLE, Phase.STARTING_ENGINE, Phase.TRYING -> if (panel == null) Status(
                ui.channelName.ifEmpty { channel.name },
                listOfNotNull(
                    ui.message,
                    if (ui.sourceIndex > 0) "Fuente ${ui.sourceIndex} de ${ui.sourceCount}" + (ui.provider?.let { " · $it" } ?: "") + (ui.quality?.let { " · $it" } ?: "") else null,
                    if (phase == Phase.TRYING) "${ui.peers} emisores" else null
                ).joinToString("\n"),
                busy = true
            ) {
                if (controls) {
                    MvButton("Fuentes", { panel = Panel.SOURCES }, icon = MvIcons.Layers, focusRequester = firstControl)
                    MvButton("Volver", onBack, icon = MvIcons.Close)
                }
            }
            Phase.FAILED -> if (panel == null) Status(
                if (ui.cause == null) "Sin imagen" else "No se puede reproducir ahora",
                ui.message ?: "No se pudo reproducir el canal.",
                busy = false
            ) {
                MvButton("Reintentar", { graph.player.retry() }, icon = MvIcons.Refresh, primary = true, focusRequester = firstControl)
                MvButton("Fuentes", { panel = Panel.SOURCES }, icon = MvIcons.Layers)
                MvButton("Volver", onBack, icon = MvIcons.Close)
            }
            Phase.PLAYING -> if (controls && panel == null) Controls(
                title = ui.channelName,
                detail = listOfNotNull("Fuente ${ui.sourceIndex} de ${ui.sourceCount}", ui.provider, ui.quality, "${ui.peers} emisores",
                    ui.startMs?.let { "imagen en ${"%.1f".format(it / 1000.0)} s" }).joinToString(" · "),
                paused = ui.paused,
                fitLabel = FITS[fit].first,
                first = firstControl,
                onPause = { graph.player.togglePause(); touch++ },
                onSources = { panel = Panel.SOURCES },
                onTracks = { panel = Panel.TRACKS },
                onFit = { fit = (fit + 1) % FITS.size; touch++ }
            )
        }

        when (panel) {
            Panel.SOURCES -> SidePanel("Fuentes de ${ui.channelName}") {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(ui.sources, key = { _, r -> r.contentId }) { i, r ->
                        val (label, color) = stateLabel(r.state)
                        MvRow(
                            title = "${r.label} · ${r.provider}",
                            subtitle = listOfNotNull(r.quality, if (r.current) "Viendo ahora" else if (r.tried) "Probada en este intento" else null).joinToString(" · ").ifEmpty { null },
                            trailing = label, trailingColor = color, icon = MvIcons.Play,
                            onClick = { panel = null; graph.player.selectSource(r.contentId) },
                            focusRequester = if (i == 0) firstControl else null
                        )
                    }
                }
            }
            Panel.TRACKS -> SidePanel("Audio y subtítulos") {
                val audio = ui.tracks.filter { it.type == C.TRACK_TYPE_AUDIO }
                val subs = ui.tracks.filter { it.type == C.TRACK_TYPE_TEXT }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { MvText("Audio", MvType.SmallStrong, color = MvColors.Text2) }
                    if (audio.isEmpty()) item { MvText("Esta fuente solo tiene una pista de audio.", MvType.Small) }
                    itemsIndexed(audio) { i, t ->
                        MvRow(t.label, { graph.player.selectTrack(t); panel = null }, trailing = if (t.selected) "Activo" else null,
                            trailingColor = MvColors.Ok, focusRequester = if (i == 0) firstControl else null)
                    }
                    item { MvText("Subtítulos", MvType.SmallStrong, color = MvColors.Text2, modifier = Modifier.padding(top = 12.dp)) }
                    item {
                        MvRow("Sin subtítulos", { graph.player.disableSubtitles(); panel = null },
                            focusRequester = if (audio.isEmpty()) firstControl else null)
                    }
                    itemsIndexed(subs) { _, t ->
                        MvRow(t.label, { graph.player.selectTrack(t); panel = null }, trailing = if (t.selected) "Activo" else null, trailingColor = MvColors.Ok)
                    }
                }
            }
            null -> Unit
        }
    }
}

@Composable
private fun Status(title: String, body: String, busy: Boolean, actions: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MvColors.Scrim), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 640.dp)) {
            if (busy) {
                Spinner(40.dp)
                Spacer(Modifier.height(20.dp))
            }
            MvText(title, MvType.Title)
            Spacer(Modifier.height(10.dp))
            MvText(body, MvType.Body.copy(color = MvColors.Text2))
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions() }
        }
    }
}

@Composable
private fun Controls(
    title: String,
    detail: String,
    paused: Boolean,
    fitLabel: String,
    first: FocusRequester,
    onPause: () -> Unit,
    onSources: () -> Unit,
    onTracks: () -> Unit,
    onFit: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE607090D))))
                .padding(start = MvDims.SafeX, end = MvDims.SafeX, bottom = MvDims.SafeY + 8.dp, top = 80.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MvTag(if (paused) "EN PAUSA" else "EN DIRECTO", if (paused) MvColors.Soon else MvColors.Live, filled = true)
                Spacer(Modifier.width(12.dp))
                MvText(title, MvType.Title, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            MvText(detail, MvType.Small, maxLines = 1)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MvButton(if (paused) "Reanudar" else "Pausa", onPause, icon = MvIcons.Play, primary = true, focusRequester = first)
                MvButton("Fuentes", onSources, icon = MvIcons.Layers)
                MvButton("Audio y subtítulos", onTracks, icon = MvIcons.List)
                MvButton("Imagen: $fitLabel", onFit, icon = MvIcons.Grid)
            }
        }
    }
}

@Composable
private fun SidePanel(title: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.CenterEnd) {
        MvPanel(
            Modifier.fillMaxHeight().width(460.dp).padding(top = MvDims.SafeY, bottom = MvDims.SafeY, end = MvDims.SafeX),
            PaddingValues(24.dp)
        ) {
            MvText(title, MvType.Heading, maxLines = 1)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Suppress("unused")
private val rounded = RoundedCornerShape(8.dp)
