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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mivisor.tv.AppGraph
import com.mivisor.tv.catalog.tv.TvChannel
import com.mivisor.tv.data.ExternalApps
import com.mivisor.tv.playback.PlaybackController.Phase
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.design.BrandPlate
import com.mivisor.tv.ui.design.MvRadio
import com.mivisor.tv.reliability.SourceState
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcon
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvPanel
import com.mivisor.tv.ui.design.MvRow
import com.mivisor.tv.ui.design.MvTag
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.Spinner
import com.mivisor.tv.ui.design.TvClickable
import com.mivisor.tv.ui.hhmm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Panel { SOURCES, TRACKS, CHANNELS }

private val FITS = listOf(
    "Ajustar" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
    "Rellenar" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    "Estirar" to AspectRatioFrameLayout.RESIZE_MODE_FILL
)

/**
 * Reproductor a pantalla completa (como el prototipo).
 *  - OK muestra los controles; Atrás los oculta y, después, sale.
 *  - Con los controles ocultos, ▲▼ (o CH+/CH−) cambian de canal dentro de su grupo; la tecla «canal anterior» vuelve
 *    al último visto.
 *  - Si Outpace no da imagen, se ofrece Ace Stream (o se abre sola, según Ajustes › Motor).
 *  - Con el motor «Ace Stream» elegido en Ajustes, el canal se abre directamente en su app y se vuelve con Atrás.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    graph: AppGraph,
    route: Route.Player,
    toaster: Toaster,
    onBack: () -> Unit,
    onSwitch: (Route.Player) -> Unit,
    onChannels: () -> Unit,
    /** Ir a otra pantalla (diagnóstico, cuarentena) desde los avisos del reproductor. */
    onRoute: (Route) -> Unit = {}
) {
    val context = LocalContext.current
    val ui by graph.player.ui.collectAsState()
    val lib by graph.providers.state.collectAsState()
    val mine by graph.mylist.state.collectAsState()
    val channel = lib.library.channel(route.channelId)
    var controls by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var fit by remember { mutableIntStateOf(0) }
    var touch by remember { mutableIntStateOf(0) }
    var zapLabel by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val catcher = remember { FocusRequester() }
    val firstControl = remember { FocusRequester() }
    val aceInstalled = remember { ExternalApps.aceInstalled(context) }
    val scope = rememberCoroutineScope()

    /** Abre en Ace Stream la mejor fuente del canal (por fiabilidad) y sale del reproductor. */
    suspend fun openInAce(ch: TvChannel) {
        val req = ch.request()
        val best = withContext(Dispatchers.Default) { graph.sources.order(req.channelId, req.sources, route.chosen) }.firstOrNull()
            ?: return
        graph.player.stop()
        graph.log.add("Ace Stream", "${ch.name}: se abre en la app de Ace Stream")
        val problem = ExternalApps.openInAce(context, best)
        if (problem != null) toaster.show(problem) else onBack()
    }

    // Empieza al entrar o al cambiar de canal (o en cuanto la biblioteca tenga el canal).
    LaunchedEffect(route, channel != null) {
        val ch = channel ?: return@LaunchedEffect
        graph.watched(ch.id)
        if (graph.prefs.engine == "ace" && aceInstalled) {
            openInAce(ch)
            return@LaunchedEffect
        }
        if (graph.player.ui.value.channelId != ch.id || route.chosen != null) graph.player.play(ch.request(), route.chosen)
    }
    DisposableEffect(Unit) { onDispose { graph.player.stop() } }

    // Si Outpace no da imagen y en Ajustes está «Automático», se pasa solo a Ace Stream.
    LaunchedEffect(ui.phase, ui.cause) {
        val ch = channel
        if (ui.phase == Phase.FAILED && ch != null && aceInstalled && graph.prefs.aceFallback == "auto" &&
            (ui.cause == null || ui.cause == "engine")) {
            toaster.show("Outpace no da imagen: se abre en Ace Stream")
            openInAce(ch)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(zapLabel) {
        if (zapLabel != null) {
            delay(1_500)
            zapLabel = null
        }
    }
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

    fun zap(step: Int) {
        val next = lib.library.neighbor(route.channelId, step) ?: return
        val sec = lib.library.sectionOf(next.id)
        val pos = sec?.channels?.indexOfFirst { it.id == next.id } ?: -1
        zapLabel = next.id + "\u0000" + (sec?.let { "${it.name} · ${pos + 1} de ${it.channels.size}" } ?: "")
        onSwitch(Route.Player(next.id))
    }

    fun previous() {
        val prev = graph.previousOf(route.channelId)?.takeIf { it != route.channelId && lib.library.channel(it) != null }
        if (prev == null) toaster.show("Todavía no hay un canal anterior") else onSwitch(Route.Player(prev))
    }

    val agenda by graph.agenda.state.collectAsState()
    val liveEvent = channel?.let { remember(it.id, agenda.agenda, now) { liveEventFor(it, agenda.agenda, lib.library) } }
    var delayMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(controls) {
        while (controls) {
            delayMs = com.mivisor.tv.engine.EngineDiagnostics.metrics.liveDelayMs
            delay(2_000)
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
                    // Tamaño de los subtítulos (Ajustes › Audio y subtítulos).
                    subtitleView?.setFractionalTextSize(
                        androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * when (graph.prefs.subtitleSize) {
                            "grande" -> 1.3f
                            "muy-grande" -> 1.6f
                            else -> 1f
                        }
                    )
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
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); true }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(1); true }
                    AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> { previous(); true }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_INFO -> { controls = true; touch++; true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_MEDIA_PLAY, AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        graph.player.togglePause(); true
                    }
                    else -> false
                }
            }.focusable()
        )

        if (channel == null && (lib.loading || lib.library.channels.isEmpty()) && lib.providers.isNotEmpty()) {
            // Al abrir la app directamente en el último canal: espera a que se lean los proveedores.
            Status("Cargando tus canales…", "", busy = true) {}
            return@Box
        }
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
            Phase.FAILED -> if (panel == null) {
                val canAce = aceInstalled && graph.prefs.aceFallback != "never" && ui.cause != "offline" && ui.cause != "internet"
                val allResting = ui.sources.isNotEmpty() && ui.sources.all {
                    it.state == SourceState.PAUSED || it.state == SourceState.QUARANTINE || it.state == SourceState.INCOMPATIBLE
                }
                when {
                    ui.cause == "engine" -> PlayerDialog(
                        "El motor no ha arrancado",
                        (ui.message ?: "Outpace no respondió.") + " Suele arreglarse reintentando; si se repite, el informe de diagnóstico dice por qué."
                    ) {
                        MvButton("Reintentar", { graph.player.retry() }, icon = MvIcons.Refresh, primary = true, focusRequester = firstControl)
                        if (canAce) MvButton("Probar con Ace Stream", { scope.launch { openInAce(channel) } }, icon = MvIcons.Bolt)
                        MvButton("Ver diagnóstico", { onRoute(Route.Diagnostics) }, icon = MvIcons.Info)
                    }
                    allResting && ui.cause == null -> PlayerDialog(
                        "${channel.name} no tiene fuentes disponibles",
                        "Sus ${ui.sources.size} fuentes han fallado varias veces y están descansando." +
                            (graph.sources.resting().filter { r -> ui.sources.any { it.contentId == r.contentId } }.mapNotNull { it.untilMs }.minOrNull()
                                ?.let { " La primera vuelve en ${((it - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)} min." } ?: "")
                    ) {
                        MvButton("Probar igualmente", { graph.player.play(channel.request(), ui.sources.first().contentId) }, icon = MvIcons.Play, primary = true, focusRequester = firstControl)
                        MvButton("Ver cuarentena", { onRoute(Route.Quarantine) }, icon = MvIcons.Layers)
                        MvButton("Cancelar", onBack)
                    }
                    else -> PlayerDialog(
                        if (ui.cause == null) "Sin imagen" else "No se puede reproducir ahora",
                        ui.message ?: "No se pudo reproducir el canal."
                    ) {
                        MvButton("Reintentar", { graph.player.retry() }, icon = MvIcons.Refresh, primary = true, focusRequester = firstControl)
                        if (canAce) MvButton("Probar con Ace Stream", { scope.launch { openInAce(channel) } }, icon = MvIcons.Bolt)
                        MvButton("Fuentes", { panel = Panel.SOURCES }, icon = MvIcons.Layers)
                        MvButton("Ver diagnóstico", { onRoute(Route.Diagnostics) }, icon = MvIcons.Info)
                    }
                }
            }
            Phase.PLAYING -> if (controls && panel == null) Controls(
                channel = channel,
                event = liveEvent?.let { ev -> ev.title + (ev.competition?.let { " · $it" } ?: "") },
                delayMs = delayMs,
                audioSummary = audioSummary(ui.tracks),
                detail = listOfNotNull(
                    "Fuente ${ui.sourceIndex} de ${ui.sourceCount}", ui.quality, ui.provider, "${ui.peers} emisores", "Outpace"
                ).joinToString(" · "),
                sourceLabel = "Fuente ${ui.sourceIndex} de ${ui.sourceCount}",
                paused = ui.paused,
                favorite = mine.isFavorite(channel.id),
                clock = hhmm(now),
                fitLabel = FITS[fit].first,
                first = firstControl,
                onPause = { graph.player.togglePause(); touch++ },
                onTracks = { panel = Panel.TRACKS },
                onSources = { panel = Panel.SOURCES },
                onChannels = { panel = Panel.CHANNELS },
                onPrevious = { previous() },
                onFavorite = {
                    graph.mylist.toggleFavorite(channel.id, channel.name)
                    toaster.show(if (mine.isFavorite(channel.id)) "Quitado de Mi lista" else "Añadido a Mi lista")
                    touch++
                },
                onFit = { fit = (fit + 1) % FITS.size; touch++ }
            )
        }

        // Recuperación de un corte (o espera a que vuelva la red): aviso arriba, sin tapar la imagen.
        ui.recovering?.let { text ->
            Box(Modifier.fillMaxWidth().padding(top = MvDims.SafeY + 8.dp), contentAlignment = Alignment.TopCenter) {
                Row(
                    Modifier.background(Color(0xD907090D), RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spinner(18.dp)
                    Spacer(Modifier.width(10.dp))
                    MvText(text, MvType.SmallStrong)
                }
            }
        }

        // Cambio de canal con ▲▼: nombre del canal un momento.
        zapLabel?.let { label ->
            val zapped = lib.library.channel(label.substringBefore('\u0000'))
            Box(Modifier.fillMaxSize().padding(start = MvDims.SafeX, bottom = MvDims.SafeY + 20.dp), contentAlignment = Alignment.BottomStart) {
                Row(
                    Modifier.background(Color(0xE607090D), RoundedCornerShape(14.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (zapped != null) ChannelLogo(zapped, Modifier.size(width = 64.dp, height = 36.dp), 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        MvText(zapped?.name ?: "", MvType.Heading)
                        MvText(label.substringAfter('\u0000'), MvType.Small)
                    }
                }
            }
        }

        when (panel) {
            Panel.SOURCES -> SidePanel("Fuentes", eyebrow = channel.name.uppercase()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(ui.sources, key = { _, r -> r.contentId }) { i, r ->
                        val (label, color) = stateLabel(r.state)
                        MvRow(
                            title = "${r.label} · ${r.provider}",
                            subtitle = listOfNotNull(r.quality, if (r.current) "Viendo ahora" else if (r.tried) "Probada en este intento" else null).joinToString(" · ").ifEmpty { null },
                            trailing = label, trailingColor = color, icon = MvIcons.Play,
                            onClick = { panel = null; graph.player.selectSource(r.contentId) },
                            onLongClick = if (aceInstalled) ({
                                graph.player.stop()
                                ExternalApps.openInAce(context, r.contentId)?.let { toaster.show(it) } ?: onBack()
                            }) else null,
                            focusRequester = if (i == 0) firstControl else null
                        )
                    }
                    if (aceInstalled) item {
                        MvText("Mantén pulsado OK en una fuente para abrirla en Ace Stream.", MvType.Caption, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Panel.TRACKS -> SidePanel("Audio y subtítulos", eyebrow = channel.name.uppercase()) {
                val audio = ui.tracks.filter { it.type == C.TRACK_TYPE_AUDIO }
                val subs = ui.tracks.filter { it.type == C.TRACK_TYPE_TEXT }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (audio.isEmpty()) item { MvText("Esta fuente solo tiene una pista de audio.", MvType.Small) }
                    itemsIndexed(audio) { i, t ->
                        MvRow(t.label, { if (t.supported) { graph.player.selectTrack(t); panel = null } }, subtitle = t.detail,
                            enabled = t.supported, focusRequester = if (i == 0) firstControl else null) { MvRadio(t.selected) }
                    }
                    item { MvText("SUBTÍTULOS", MvType.Eyebrow.copy(color = MvColors.Text2), modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) }
                    item {
                        MvRow("No", { graph.player.disableSubtitles(); panel = null },
                            focusRequester = if (audio.isEmpty()) firstControl else null) { MvRadio(subs.none { it.selected }) }
                    }
                    itemsIndexed(subs) { _, t ->
                        MvRow(t.label, { graph.player.selectTrack(t); panel = null }, subtitle = t.detail, enabled = t.supported) { MvRadio(t.selected) }
                    }
                    item { MvText("Se recuerda tu elección en este canal.", MvType.Caption, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
            Panel.CHANNELS -> SidePanel("Canales", eyebrow = (lib.library.sectionOf(channel.id)?.name ?: channel.name).uppercase()) {
                val list = lib.library.sectionOf(channel.id)?.channels ?: lib.library.channels
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                        val ev = liveEventFor(c, agenda.agenda, lib.library)
                        TvClickable(
                            onClick = { panel = null; if (c.id != channel.id) onSwitch(Route.Player(c.id)) },
                            modifier = Modifier.fillMaxWidth(), focusedScale = 1.02f,
                            color = MvColors.Surface2, focusedColor = MvColors.Surface4,
                            focusRequester = if (c.id == channel.id || (i == 0 && list.none { it.id == channel.id })) firstControl else null
                        ) { _ ->
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                ChannelLogo(c, Modifier.size(width = 48.dp, height = 27.dp), 48.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    MvText(c.name, MvType.BodyStrong, maxLines = 1)
                                    MvText(ev?.title ?: channelStatus(c, graph.sources).first, MvType.Caption, maxLines = 1)
                                }
                                if (c.id == channel.id) MvTag("Ahora", MvColors.Text2)
                            }
                        }
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 720.dp)) {
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

/** Aviso del reproductor como en el diseño: tarjeta sobre la imagen atenuada. */
@Composable
private fun PlayerDialog(title: String, body: String, actions: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB307090D)), contentAlignment = Alignment.Center) {
        MvPanel(Modifier.widthIn(max = 420.dp), PaddingValues(20.dp)) {
            MvText(title, MvType.Title)
            Spacer(Modifier.height(10.dp))
            MvText(body, MvType.Body.copy(color = MvColors.Text2))
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions() }
        }
    }
}

/** «Español · subtítulos no». */
private fun audioSummary(tracks: List<com.mivisor.tv.playback.PlaybackController.TrackOption>): String? {
    val audio = tracks.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.selected } ?: return null
    val sub = tracks.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.selected }
    return audio.label + " · subtítulos " + (sub?.label?.lowercase() ?: "no")
}

/** Evento en directo que se emite en este canal (según la agenda), para la información del reproductor. */
fun liveEventFor(ch: TvChannel, agenda: com.mivisor.tv.model.Agenda?, library: com.mivisor.tv.catalog.tv.TvLibrary): com.mivisor.tv.model.AgendaEvent? {
    val now = System.currentTimeMillis()
    return agenda?.events.orEmpty().filter { it.status(now) == com.mivisor.tv.model.EventStatus.LIVE }
        .firstOrNull { ev -> channelsFor(ev, library).any { it.id == ch.id } }
}

/**
 * Controles del diseño: arriba logo, canal, evento, EN DIRECTO, retraso y hora; abajo datos de la fuente con la barra
 * hasta «Directo», pausa redonda, Audio y subtítulos, Fuente x de y, Canales, Canal anterior, favorito y el audio activo.
 */
@Composable
private fun Controls(
    channel: TvChannel,
    event: String?,
    delayMs: Long?,
    audioSummary: String?,
    detail: String,
    sourceLabel: String,
    paused: Boolean,
    favorite: Boolean,
    clock: String,
    fitLabel: String,
    first: FocusRequester,
    onPause: () -> Unit,
    onTracks: () -> Unit,
    onSources: () -> Unit,
    onChannels: () -> Unit,
    onPrevious: () -> Unit,
    onFavorite: () -> Unit,
    onFit: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xD1000000), Color.Transparent)))
                .padding(start = MvDims.SafeX, end = MvDims.SafeX, top = MvDims.SafeY, bottom = 60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelLogo(channel, Modifier.width(72.dp).height(40.dp), 72.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                MvText(channel.name, MvType.Heading, maxLines = 1)
                MvText(event ?: channel.brandName ?: "", MvType.Body.copy(color = MvColors.Text2), maxLines = 1)
            }
            MvTag(if (paused) "EN PAUSA" else "● EN DIRECTO", if (paused) MvColors.Soon else MvColors.Live, filled = true)
            if (delayMs != null) {
                Spacer(Modifier.width(14.dp))
                Box(Modifier.background(Color(0x99000000), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    MvText("Retraso ${(delayMs / 1000).coerceAtLeast(1)} s", MvType.SmallStrong.copy(color = MvColors.Text2))
                }
            }
            Spacer(Modifier.width(20.dp))
            MvText(clock, MvType.Heading)
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = MvDims.SafeX, end = MvDims.SafeX, bottom = MvDims.SafeY, top = 80.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MvText(detail, MvType.Small, maxLines = 1)
                Spacer(Modifier.width(16.dp))
                // Barra hasta el directo: en directo siempre está casi al final (la distancia es el retraso).
                Box(Modifier.weight(1f).height(4.dp).background(Color(0x33FFFFFF), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(if (paused) 0.9f else 0.97f).height(4.dp).background(MvColors.Accent, RoundedCornerShape(2.dp)))
                }
                Spacer(Modifier.width(16.dp))
                MvText("Directo", MvType.Small)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TvClickable(
                    onClick = onPause, focusRequester = first, shape = CircleShape, modifier = Modifier.size(48.dp),
                    color = MvColors.Text, focusedColor = MvColors.Text, ringColor = MvColors.Accent2, contentAlignment = Alignment.Center
                ) { _ ->
                    if (paused) MvIcon(MvIcons.Play, size = 22.dp, tint = MvColors.Bg)
                    else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(width = 4.dp, height = 16.dp).background(MvColors.Bg))
                        Box(Modifier.size(width = 4.dp, height = 16.dp).background(MvColors.Bg))
                    }
                }
                MvButton("Audio y subtítulos", onTracks, icon = MvIcons.List)
                MvButton(sourceLabel, onSources, icon = MvIcons.Layers)
                MvButton("Canales", onChannels, icon = MvIcons.Grid)
                MvButton("Canal anterior", onPrevious, icon = MvIcons.History)
                TvClickable(
                    onClick = onFavorite, shape = CircleShape, modifier = Modifier.size(48.dp),
                    color = if (favorite) Color(0x553D8BFF) else MvColors.Surface3, focusedColor = MvColors.Text, ringColor = MvColors.Accent2,
                    contentAlignment = Alignment.Center
                ) { focused -> MvIcon(MvIcons.Star, size = 20.dp, tint = if (focused) MvColors.Bg else if (favorite) MvColors.Accent2 else MvColors.Text2) }
                MvButton(fitLabel, onFit, icon = MvIcons.Tv)
                if (audioSummary != null) MvText(audioSummary, MvType.Small, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            MvText("▲▼ con los controles ocultos: cambiar de canal · Atrás: ocultar controles", MvType.Caption)
        }
    }
}

@Composable
private fun SidePanel(title: String, eyebrow: String? = null, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.fillMaxHeight().width(300.dp).background(MvColors.Surface1)
                .padding(start = 20.dp, end = MvDims.SafeX, top = MvDims.SafeY, bottom = MvDims.SafeY)
        ) {
            if (eyebrow != null) MvText(eyebrow, MvType.Eyebrow.copy(color = MvColors.Text2), maxLines = 1)
            MvText(title, MvType.Title, maxLines = 1)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}
