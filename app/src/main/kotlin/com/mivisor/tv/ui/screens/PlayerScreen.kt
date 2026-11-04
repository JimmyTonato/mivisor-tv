package com.mivisor.tv.ui.screens

import android.graphics.Color as AColor
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import com.mivisor.tv.ui.design.OrbitaMark
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
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

/** Pestañas del panel lateral del reproductor (diseño 06.1). */
private enum class Panel(val label: String) { INFO("Info"), SOURCES("Fuentes"), AUDIO("Audio"), SUBS("Subtítulos"), VIDEO("Vídeo") }

private val FITS = listOf(
    "Ajustar" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
    "Rellenar" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    "Estirar" to AspectRatioFrameLayout.RESIZE_MODE_FILL
)

/** Tiempo sin tocar el mando tras el que se ocultan los controles (el diseño recomienda 5–7 s). */
private const val HIDE_AFTER_MS = 7_000L

/**
 * Reproductor único del diseño 06.1 («Cinema»): el vídeo manda.
 *  - Carga: spinner en el centro, logo y nombre discretos en la esquina y un estado breve.
 *  - Al empezar la imagen se muestran los controles (foco en Pausa) y se ocultan solos a los 7 s sin tocar nada.
 *  - Controles ocultos: OK los muestra; las flechas NO hacen nada (no cambian de canal); Atrás vuelve a la pantalla
 *    de origen (Canales, ficha, Buscar…), que recupera el foco en el mismo canal.
 *  - Controles visibles: Atrás los oculta. Panel abierto: Atrás lo cierra y devuelve el foco al botón que lo abrió.
 *  - Cambiar de canal = volver con Atrás y elegir otro. Sin atajos ocultos.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    graph: AppGraph,
    route: Route.Player,
    toaster: Toaster,
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onSwitch: (Route.Player) -> Unit,
    @Suppress("UNUSED_PARAMETER") onChannels: () -> Unit,
    /** Ir a otra pantalla (diagnóstico, cuarentena) desde los avisos del reproductor. */
    onRoute: (Route) -> Unit = {}
) {
    val context = LocalContext.current
    val ui by graph.player.ui.collectAsState()
    val lib by graph.providers.state.collectAsState()
    val mine by graph.mylist.state.collectAsState()
    val channel = lib.library.channel(route.channelId)
    var controls by remember { mutableStateOf(false) }
    var shownOnce by remember(route) { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var lastOpener by remember { mutableStateOf<Panel?>(null) }
    var fit by remember { mutableIntStateOf(0) }
    var touch by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val catcher = remember { FocusRequester() }
    val pauseFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val openers = remember { Panel.values().associateWith { FocusRequester() } }
    val dialogFocus = remember { FocusRequester() }
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

    // Empieza al entrar (o en cuanto la biblioteca tenga el canal). La fuente la elige el motor: la más fiable según
    // lo aprendido, o la primera si ninguna se ha probado; si falla, recupera y pasa a la siguiente solo.
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
    // Al terminar la carga, los controles aparecen (una vez por canal).
    LaunchedEffect(ui.phase) {
        if (ui.phase == Phase.PLAYING && !shownOnce) {
            shownOnce = true
            controls = true
            touch++
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }
    // Autocierre de los controles (nunca con un panel abierto ni en pausa).
    LaunchedEffect(controls, panel, touch, ui.paused) {
        if (controls && panel == null && !ui.paused && ui.phase == Phase.PLAYING) {
            delay(HIDE_AFTER_MS)
            controls = false
        }
    }
    // Foco: panel → su primera fila; controles → Pausa (o el botón que abrió el panel); nada → recogedor de teclas.
    LaunchedEffect(controls, panel, ui.phase) {
        delay(80)
        runCatching {
            when {
                panel != null -> panelFocus.requestFocus()
                ui.phase == Phase.FAILED -> dialogFocus.requestFocus()
                controls -> (lastOpener?.let { openers[it] } ?: pauseFocus).requestFocus()
                else -> catcher.requestFocus()
            }
        }
    }

    BackHandler {
        when {
            panel != null -> { panel = null; touch++ }
            controls && ui.phase == Phase.PLAYING -> { controls = false; lastOpener = null }
            else -> onBack()
        }
    }

    val agenda by graph.agenda.state.collectAsState()
    val liveEvent = channel?.let { remember(it.id, agenda.agenda, now) { liveEventFor(it, agenda.agenda, lib.library) } }
    var delayMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(controls, panel) {
        while (controls || panel != null) {
            delayMs = com.mivisor.tv.engine.EngineDiagnostics.metrics.liveDelayMs
            delay(2_000)
        }
    }
    fun open(p: Panel) {
        lastOpener = p
        panel = p
        touch++
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

        // Recoge las teclas cuando no hay controles: OK los muestra; las flechas no hacen nada (sin zapping).
        Box(
            Modifier.fillMaxSize().focusRequester(catcher).onKeyEvent { e ->
                val n = e.nativeKeyEvent
                when (n.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_INFO -> {
                        if (n.action == AndroidKeyEvent.ACTION_UP) { controls = true; touch++ }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_CHANNEL_UP, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                    AndroidKeyEvent.KEYCODE_LAST_CHANNEL -> true
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AndroidKeyEvent.KEYCODE_MEDIA_PLAY, AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (n.action == AndroidKeyEvent.ACTION_UP && phase == Phase.PLAYING) graph.player.togglePause()
                        true
                    }
                    else -> false
                }
            }.focusable()
        )

        if (channel == null && (lib.loading || lib.library.channels.isEmpty()) && lib.providers.isNotEmpty()) {
            // Al abrir la app directamente en el último canal: espera a que se lean los proveedores.
            Loading(null, "Cargando tus canales…", null)
            return@Box
        }
        if (channel == null) {
            PlayerDialog("Canal no disponible", "Este canal ya no está en tus proveedores.") {
                MvButton("Volver", onBack, primary = true, focusRequester = dialogFocus)
            }
            InitialFocus(dialogFocus)
            return@Box
        }

        val sourceTag = if (ui.sourceCount > 0) "${ui.sourceIndex.coerceAtLeast(1)} / ${ui.sourceCount} fuentes" else null
        when (phase) {
            Phase.IDLE, Phase.STARTING_ENGINE, Phase.TRYING -> if (panel == null) Loading(
                channel,
                when {
                    phase == Phase.STARTING_ENGINE -> "Preparando el motor…"
                    ui.sourceIndex > 1 -> "Probando otra fuente…"
                    else -> "Conectando con la fuente…"
                },
                listOfNotNull(
                    sourceTag?.let { "Fuente ${ui.sourceIndex} de ${ui.sourceCount}" },
                    ui.provider,
                    if (phase == Phase.TRYING && ui.peers > 0) "${ui.peers} emisores" else null,
                    graph.sources.typicalStart(channel.sources.map { it.contentId })?.let { "suele tardar ~$it s" }
                ).joinToString(" · ").ifEmpty { null }
            )
            Phase.FAILED -> if (panel == null) {
                val canAce = aceInstalled && graph.prefs.aceFallback != "never" && ui.cause != "offline" && ui.cause != "internet"
                val allResting = ui.sources.isNotEmpty() && ui.sources.all {
                    it.state == SourceState.PAUSED || it.state == SourceState.QUARANTINE || it.state == SourceState.INCOMPATIBLE
                }
                when {
                    ui.cause == "engine" -> PlayerDialog(
                        "El motor no ha arrancado",
                        (ui.message ?: "Outpace no respondió.") + " Suele arreglarse reintentando; si se repite, el diagnóstico dice por qué."
                    ) {
                        MvButton("Reintentar", { graph.player.retry() }, icon = MvIcons.Refresh, primary = true, focusRequester = dialogFocus)
                        if (canAce) MvButton("Probar con Ace Stream", { scope.launch { openInAce(channel) } }, icon = MvIcons.Bolt)
                        MvButton("Volver a Canales", onBack)
                    }
                    allResting && ui.cause == null -> PlayerDialog(
                        "${channel.name} no tiene fuentes disponibles",
                        "Sus ${ui.sources.size} fuentes han fallado varias veces y están descansando." +
                            (graph.sources.resting().filter { r -> ui.sources.any { it.contentId == r.contentId } }.mapNotNull { it.untilMs }.minOrNull()
                                ?.let { " La primera vuelve en ${((it - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)} min." } ?: "")
                    ) {
                        MvButton("Probar igualmente", { graph.player.play(channel.request(), ui.sources.first().contentId) }, icon = MvIcons.Play, primary = true, focusRequester = dialogFocus)
                        MvButton("Ver cuarentena", { onRoute(Route.Quarantine) }, icon = MvIcons.Layers)
                        MvButton("Volver a Canales", onBack)
                    }
                    else -> PlayerDialog(
                        if (ui.cause == null) "Sin imagen" else "No se puede reproducir ahora",
                        ui.message ?: "No se pudo reproducir el canal."
                    ) {
                        MvButton("Reintentar", { graph.player.retry() }, icon = MvIcons.Refresh, primary = true, focusRequester = dialogFocus)
                        if (canAce) MvButton("Probar con Ace Stream", { scope.launch { openInAce(channel) } }, icon = MvIcons.Bolt)
                        MvButton("Cambiar fuente", { open(Panel.SOURCES) }, icon = MvIcons.Layers)
                        MvButton("Volver a Canales", onBack)
                    }
                }
            }
            Phase.PLAYING -> if (controls || panel != null) Controls(
                channel = channel,
                subtitle = liveEvent?.let { ev -> ev.title + (ev.competition?.let { " · $it" } ?: "") }
                    ?: listOfNotNull(channel.brandName, "Televisión en directo").joinToString(" · "),
                sourceTag = sourceTag,
                delayMs = delayMs,
                clock = hhmm(now),
                paused = ui.paused,
                favorite = mine.isFavorite(channel.id),
                hint = if (panel == null) "Las flechas no cambian de canal · Atrás oculta los controles" else null,
                pauseFocus = pauseFocus,
                openers = openers,
                dimmed = panel != null,
                onPause = { graph.player.togglePause(); touch++ },
                onPanel = { open(it) },
                onFavorite = {
                    val was = mine.isFavorite(channel.id)
                    graph.mylist.toggleFavorite(channel.id, channel.name)
                    toaster.show(if (was) "Quitado de Mi lista" else "Añadido a Mi lista")
                    touch++
                },
                onHide = { controls = false; lastOpener = null },
                onTouch = { touch++ }
            )
        }

        // Recuperación de un corte: spinner y estado en el centro, sin abrir paneles por su cuenta.
        if (phase == Phase.PLAYING) ui.recovering?.let { text ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.background(Color(0xB3080F19), RoundedCornerShape(16.dp)).padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OrbitaMark(34.dp, spinning = true)
                    Spacer(Modifier.height(10.dp))
                    MvText(text, MvType.SmallStrong)
                }
            }
        }

        panel?.let { p ->
            val hasRows = when (p) {
                Panel.INFO -> false
                Panel.SOURCES -> ui.sources.isNotEmpty()
                Panel.AUDIO -> ui.tracks.any { it.type == C.TRACK_TYPE_AUDIO }
                Panel.SUBS, Panel.VIDEO -> true
            }
            SidePanel(channel.name, p, onTab = { panel = it; lastOpener = it }, onClose = { panel = null }, firstFocus = panelFocus, hasRows = hasRows) {
                when (p) {
                    Panel.INFO -> InfoTab(channel, ui, liveEvent, delayMs)
                    Panel.SOURCES -> {
                        ui.sources.forEachIndexed { i, r ->
                            val (label, color) = stateLabel(r.state)
                            PanelRow(
                                title = "Fuente ${r.index}" + (if (r.current) " · viendo ahora" else ""),
                                subtitle = listOfNotNull(r.provider, r.quality, if (r.tried && !r.current) "probada en este intento" else null).joinToString(" · "),
                                trailing = label, trailingColor = color, selected = r.current,
                                focusRequester = if (i == 0) panelFocus else null,
                                onLongClick = if (aceInstalled) ({
                                    graph.player.stop()
                                    ExternalApps.openInAce(context, r.contentId)?.let { toaster.show(it) } ?: onBack()
                                }) else null
                            ) { panel = null; if (!r.current) graph.player.selectSource(r.contentId) }
                        }
                        if (ui.sources.isEmpty()) MvText("Este canal no tiene fuentes.", MvType.Small)
                        Spacer(Modifier.height(8.dp))
                        MvText(
                            "Al elegir una, se prueba sin salir del canal." + if (aceInstalled) " Mantén OK para abrirla en Ace Stream." else "",
                            MvType.Caption
                        )
                    }
                    Panel.AUDIO -> {
                        val audio = ui.tracks.filter { it.type == C.TRACK_TYPE_AUDIO }
                        if (audio.isEmpty()) MvText("Esta fuente no informa de pistas de audio.", MvType.Small)
                        audio.forEachIndexed { i, t ->
                            PanelRow(
                                title = t.label, subtitle = t.detail ?: "", selected = t.selected, enabled = t.supported,
                                trailing = if (t.selected) "Seleccionada" else null, focusRequester = if (i == 0) panelFocus else null
                            ) { graph.player.selectTrack(t); panel = null }
                        }
                        Spacer(Modifier.height(8.dp))
                        MvText("Se recuerda tu elección en este canal.", MvType.Caption)
                    }
                    Panel.SUBS -> {
                        val subs = ui.tracks.filter { it.type == C.TRACK_TYPE_TEXT }
                        PanelRow(
                            title = "Sin subtítulos", subtitle = "", selected = subs.none { it.selected },
                            trailing = if (subs.none { it.selected }) "Seleccionada" else null, focusRequester = panelFocus
                        ) { graph.player.disableSubtitles(); panel = null }
                        subs.forEach { t ->
                            PanelRow(title = t.label, subtitle = t.detail ?: "", selected = t.selected, enabled = t.supported,
                                trailing = if (t.selected) "Seleccionada" else null) { graph.player.selectTrack(t); panel = null }
                        }
                        if (subs.isEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            MvText("Esta fuente no trae subtítulos.", MvType.Caption)
                        }
                    }
                    Panel.VIDEO -> {
                        FITS.forEachIndexed { i, (label, _) ->
                            PanelRow(
                                title = label,
                                subtitle = when (i) { 0 -> "Imagen completa, con bandas si hace falta"; 1 -> "Llena la pantalla recortando los bordes"; else -> "Llena la pantalla deformando la imagen" },
                                selected = fit == i, trailing = if (fit == i) "Seleccionada" else null,
                                focusRequester = if (i == 0) panelFocus else null
                            ) { fit = i }
                        }
                    }
                }
            }
        }
    }
}

/** Carga (diseño 06.1): canal discreto arriba a la izquierda, Órbita girando en el centro y un estado breve debajo. */
@Composable
private fun Loading(channel: TvChannel?, status: String, detail: String?) {
    Box(Modifier.fillMaxSize().background(Color(0xFF05080D))) {
        if (channel != null) Row(
            Modifier.padding(start = MvDims.SafeX, top = MvDims.SafeY + 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoBox(channel, 34.dp)
            Spacer(Modifier.width(10.dp))
            MvText(channel.name, MvType.BodyStrong.copy(color = MvColors.Text2))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            OrbitaMark(52.dp, spinning = true)
            Spacer(Modifier.height(18.dp))
            MvText(status, MvType.Body.copy(color = MvColors.Text))
            if (detail != null) {
                Spacer(Modifier.height(4.dp))
                MvText(detail, MvType.Caption.copy(color = MvColors.Text3))
            }
        }
    }
}

/** Aviso del reproductor: tarjeta sobre la imagen atenuada, con salida siempre visible. */
@Composable
private fun PlayerDialog(title: String, body: String, actions: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC05080D)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp).background(MvColors.Surface2, RoundedCornerShape(16.dp))
                .border(1.dp, MvColors.Stroke, RoundedCornerShape(16.dp)).padding(22.dp)
        ) {
            MvText(title, MvType.Heading)
            Spacer(Modifier.height(8.dp))
            MvText(body, MvType.Body.copy(color = MvColors.Text2))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
        }
    }
}

/** Evento en directo que se emite en este canal (según la agenda), para la información del reproductor. */
fun liveEventFor(ch: TvChannel, agenda: com.mivisor.tv.model.Agenda?, library: com.mivisor.tv.catalog.tv.TvLibrary): com.mivisor.tv.model.AgendaEvent? {
    val now = System.currentTimeMillis()
    return agenda?.events.orEmpty().filter { it.status(now) == com.mivisor.tv.model.EventStatus.LIVE }
        .firstOrNull { ev -> channelsFor(ev, library).any { it.id == ch.id } }
}

/**
 * Controles 06.1: arriba logo, canal y su contexto, EN DIRECTO y «x / y fuentes»; abajo una barra con Info, Fuentes y
 * Mi lista a la izquierda, Pausa en el centro y Audio, Subtítulos, Vídeo y Ocultar a la derecha. Sin barra de progreso
 * (en directo no la hay).
 */
@Composable
private fun Controls(
    channel: TvChannel,
    subtitle: String,
    sourceTag: String?,
    delayMs: Long?,
    clock: String,
    paused: Boolean,
    favorite: Boolean,
    hint: String?,
    pauseFocus: FocusRequester,
    openers: Map<Panel, FocusRequester>,
    dimmed: Boolean,
    onPause: () -> Unit,
    onPanel: (Panel) -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onTouch: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // Degradados solo arriba y abajo: el vídeo sigue siendo el protagonista.
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0xCC05080D), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE605080D)))))
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = MvDims.SafeX, end = MvDims.SafeX, top = MvDims.SafeY + 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogoBox(channel, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                MvText(channel.name, MvType.Heading, maxLines = 1)
                MvText(subtitle, MvType.Small.copy(color = MvColors.Text2), maxLines = 1)
            }
            Row(
                Modifier.background(MvColors.LiveBg, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(6.dp).background(MvColors.Live, CircleShape))
                Spacer(Modifier.width(6.dp))
                MvText(if (paused) "EN PAUSA" else "EN DIRECTO", MvType.Caption.copy(color = MvColors.Text, fontWeight = FontWeight.Bold))
            }
            if (sourceTag != null) {
                Spacer(Modifier.width(8.dp))
                InfoPill(sourceTag)
            }
            if (delayMs != null && delayMs >= 1_000) {
                Spacer(Modifier.width(8.dp))
                InfoPill("−${delayMs / 1000} s")
            }
            Spacer(Modifier.width(14.dp))
            MvText(clock, MvType.Heading)
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = MvDims.SafeX - 10.dp, end = MvDims.SafeX - 10.dp, bottom = MvDims.SafeY)
                .then(if (dimmed) Modifier.alpha(0.35f) else Modifier)
        ) {
            Row(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp)) {
                MvText(if (paused) "SEÑAL EN PAUSA" else "SEÑAL EN REPRODUCCIÓN", MvType.Caption.copy(color = MvColors.Text2, letterSpacing = 1.sp))
                Spacer(Modifier.weight(1f))
                if (hint != null) MvText(hint, MvType.Caption.copy(color = MvColors.Text3))
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalBarEnabled provides !dimmed) {
            Row(
                Modifier.fillMaxWidth().height(72.dp)
                    .background(Color(0xE6132232), RoundedCornerShape(16.dp))
                    .border(1.dp, MvColors.Stroke, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BarButton("Info", MvIcons.Info, openers.getValue(Panel.INFO), onTouch) { onPanel(Panel.INFO) }
                Spacer(Modifier.width(8.dp))
                BarButton("Fuentes", MvIcons.Signal, openers.getValue(Panel.SOURCES), onTouch) { onPanel(Panel.SOURCES) }
                Spacer(Modifier.width(8.dp))
                BarButton(if (favorite) "En Mi lista" else "Mi lista", MvIcons.Star, null, onTouch, highlighted = favorite, onClick = onFavorite)
                Spacer(Modifier.weight(1f))
                // Pausa: botón redondo champán en el centro; al reanudar vuelve al directo.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TvClickable(
                        onClick = onPause, focusRequester = pauseFocus, onFocus = onTouch, shape = CircleShape, focusedScale = 1.04f, enabled = !dimmed,
                        color = MvColors.Accent, focusedColor = MvColors.Accent2, ringColor = MvColors.Text,
                        contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp)
                    ) { _ ->
                        if (paused) MvIcon(MvIcons.Play, size = 20.dp, tint = MvColors.Bg)
                        else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(width = 4.dp, height = 15.dp).background(MvColors.Bg, RoundedCornerShape(1.dp)))
                            Box(Modifier.size(width = 4.dp, height = 15.dp).background(MvColors.Bg, RoundedCornerShape(1.dp)))
                        }
                    }
                    MvText(if (paused) "Reanudar" else "Pausar", MvType.Caption.copy(color = MvColors.Text2))
                }
                Spacer(Modifier.weight(1f))
                BarButton("Audio", MvIcons.Volume, openers.getValue(Panel.AUDIO), onTouch) { onPanel(Panel.AUDIO) }
                Spacer(Modifier.width(8.dp))
                BarButton("Subtítulos", MvIcons.List, openers.getValue(Panel.SUBS), onTouch) { onPanel(Panel.SUBS) }
                Spacer(Modifier.width(8.dp))
                BarButton("Vídeo", MvIcons.Tune, openers.getValue(Panel.VIDEO), onTouch) { onPanel(Panel.VIDEO) }
                Spacer(Modifier.width(8.dp))
                BarButton("Ocultar", MvIcons.Close, null, onTouch, onClick = onHide)
            }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Box(Modifier.background(Color(0x99091421), RoundedCornerShape(7.dp)).border(1.dp, MvColors.Stroke, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 4.dp)) {
        MvText(text, MvType.Caption.copy(color = MvColors.Text, fontWeight = FontWeight.Bold))
    }
}

/** Con un panel abierto la barra queda atenuada y fuera del recorrido del foco (nunca se navega «al fondo»). */
private val LocalBarEnabled = androidx.compose.runtime.compositionLocalOf { true }

/** Botón de la barra del reproductor: contorno fino, icono y texto; con foco, superficie clara y anillo champán. */
@Composable
private fun BarButton(
    text: String,
    icon: Int,
    focusRequester: FocusRequester?,
    onTouch: () -> Unit,
    highlighted: Boolean = false,
    enabled: Boolean = LocalBarEnabled.current,
    onClick: () -> Unit
) {
    TvClickable(
        onClick = onClick, focusRequester = focusRequester, enabled = enabled, onFocus = onTouch, shape = RoundedCornerShape(10.dp), focusedScale = 1f,
        color = if (highlighted) MvColors.Accent.copy(alpha = 0.18f) else MvColors.Surface2, focusedColor = MvColors.Surface4,
        ringColor = MvColors.Accent2, modifier = Modifier.height(36.dp)
    ) { _ ->
        Row(Modifier.fillMaxHeight().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            MvIcon(icon, size = 15.dp, tint = if (highlighted) MvColors.Accent else MvColors.Text)
            Spacer(Modifier.width(7.dp))
            MvText(text, MvType.SmallStrong, maxLines = 1)
        }
    }
}

/** Panel lateral anclado a la derecha con pestañas (Info, Fuentes, Audio, Subtítulos, Vídeo) y «Cerrar». */
@Composable
private fun SidePanel(
    channelName: String,
    current: Panel,
    onTab: (Panel) -> Unit,
    onClose: () -> Unit,
    firstFocus: FocusRequester,
    /** La pestaña tiene filas que reciben el foco; si no, lo recibe su propia pestaña. */
    hasRows: Boolean,
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.padding(end = 32.dp, top = 28.dp, bottom = 28.dp).fillMaxHeight().width(300.dp)
                .background(Color(0xF2132232), RoundedCornerShape(16.dp))
                .border(1.dp, MvColors.Stroke, RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MvText(channelName.uppercase(), MvType.Eyebrow.copy(color = MvColors.Accent), maxLines = 1)
                    MvText(current.label, MvType.Title.copy(fontSize = 22.sp), maxLines = 1)
                }
                TvClickable(
                    onClick = onClose, shape = RoundedCornerShape(9.dp), focusedScale = 1f, color = Color.Transparent,
                    focusedColor = MvColors.Surface4, ringColor = MvColors.Accent2, modifier = Modifier.height(30.dp)
                ) { _ ->
                    Row(Modifier.fillMaxHeight().padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        MvIcon(MvIcons.Close, size = 13.dp, tint = MvColors.Text)
                        Spacer(Modifier.width(5.dp))
                        MvText("Cerrar", MvType.SmallStrong)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Panel.values().forEach { p ->
                    TvClickable(
                        onClick = { onTab(p) }, shape = RoundedCornerShape(8.dp), focusedScale = 1f,
                        color = if (p == current) MvColors.Surface4 else Color.Transparent, focusedColor = MvColors.Surface4, ringColor = MvColors.Accent2,
                        focusRequester = if (p == current && !hasRows) firstFocus else null,
                        modifier = Modifier.height(28.dp)
                    ) { _ ->
                        Box(Modifier.fillMaxHeight().padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                            MvText(p.label, MvType.Caption.copy(color = if (p == current) MvColors.Accent else MvColors.Text2, fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) { content() }
            MvText("Atrás cierra el panel sin detener el vídeo ni cambiar de canal.", MvType.Caption)
        }
    }
}

/** Fila del panel: título, detalle y estado; seleccionada con borde verde agua (distinto del foco champán). */
@Composable
private fun PanelRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    trailing: String? = null,
    trailingColor: Color = MvColors.Text2,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    TvClickable(
        onClick = onClick, onLongClick = onLongClick, enabled = enabled, focusRequester = focusRequester,
        shape = RoundedCornerShape(10.dp), focusedScale = 1f,
        color = if (selected) Color(0x269FD6C8) else MvColors.Surface1, focusedColor = MvColors.Surface4, ringColor = MvColors.Accent2,
        modifier = Modifier.fillMaxWidth()
    ) { _ ->
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MvText(title, MvType.SmallStrong, maxLines = 2)
                if (subtitle.isNotEmpty()) MvText(subtitle, MvType.Caption.copy(color = MvColors.Text2), maxLines = 2)
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                MvText(trailing, MvType.Caption.copy(color = if (selected) MvColors.Ok else trailingColor, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

@Composable
private fun InfoTab(channel: TvChannel, ui: com.mivisor.tv.playback.PlaybackController.Ui, event: com.mivisor.tv.model.AgendaEvent?, delayMs: Long?) {
    @Composable
    fun kv(k: String, v: String) {
        Row(Modifier.padding(vertical = 2.dp)) {
            MvText(k, MvType.Caption.copy(color = MvColors.Text3), modifier = Modifier.width(96.dp))
            MvText(v, MvType.Small.copy(color = MvColors.Text), modifier = Modifier.weight(1f))
        }
    }
    kv("Canal", channel.name)
    channel.brandName?.let { kv("Grupo", it) }
    event?.let { kv("Ahora", it.title + (it.competition?.let { c -> " · $c" } ?: "")) }
    kv("Fuente", if (ui.sourceCount > 0) "${ui.sourceIndex} de ${ui.sourceCount}" + (ui.provider?.let { " · $it" } ?: "") else "—")
    ui.quality?.let { kv("Calidad", it) }
    if (ui.peers > 0) kv("Emisores", "${ui.peers}")
    delayMs?.let { kv("Retraso", "${"%.1f".format(it / 1000.0)} s respecto al directo") }
    ui.startMs?.let { kv("Arranque", "imagen en ${"%.1f".format(it / 1000.0)} s") }
    kv("Motor", "Outpace")
    val audio = ui.tracks.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.selected }
    audio?.let { kv("Audio", it.label + (it.detail?.let { d -> " · $d" } ?: "")) }
}
