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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectTapGestures
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
    // 3n · Un ID abierto a mano (Ajustes › Abrir un ID) no está en la biblioteca: canal suelto con esa única fuente.
    // 4h · Al abrir un ID suelto, su nombre se saca del descriptor (cuando el motor lo recibe) y se apunta en el historial.
    var directNamed by remember(route.channelId) { mutableIntStateOf(0) }
    val channel = lib.library.channel(route.channelId) ?: remember(route.channelId, directNamed) { directChannel(graph, route.channelId) }
    val directCid = remember(route.channelId) { com.mivisor.tv.catalog.datos.DirectStream.contentIdOf(route.channelId) }
    if (directCid != null) {
        LaunchedEffect(directCid, ui.stage, ui.phase) {
            // Solo cuando el motor ya resolvió ESTE intento (si no, la línea del descriptor podría ser de otro canal).
            val resolved = ui.stage == com.mivisor.tv.playback.PlaybackController.LoadStage.PEERS ||
                ui.stage == com.mivisor.tv.playback.PlaybackController.LoadStage.BUFFERING || ui.phase == Phase.PLAYING
            if (!resolved) return@LaunchedEffect
            val title = com.mivisor.tv.catalog.datos.DirectStream.titleOfDescriptorLine(com.mivisor.tv.engine.EngineDiagnostics.vias.descriptor)
                ?: return@LaunchedEffect
            com.mivisor.tv.catalog.datos.DirectStream.nameRecent(graph.prefs.directRecent, directCid, title)?.let {
                graph.prefs.directRecent = it
                graph.log.add("Abrir un ID", "${com.mivisor.tv.catalog.datos.DirectStream.short(directCid)}: nombre del canal según su descriptor: «$title»")
                directNamed++
            }
        }
    }
    var controls by remember { mutableStateOf(false) }
    val form = com.mivisor.tv.ui.design.LocalUiForm.current
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
    /** 3e: «Seguir esperando / Probar otra fuente» al cargar (tras 8 s) y al cortarse la imagen. */
    val waitFocus = remember { FocusRequester() }
    var waitDismissed by remember { mutableStateOf(false) }
    var loadingLong by remember { mutableStateOf(false) }
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
        if (!ch.id.startsWith(com.mivisor.tv.catalog.datos.DirectStream.PREFIX)) graph.watched(ch.id)
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
    // Carga larga: a los 8 s con la misma fuente se ofrece pasar a otra (sin forzarlo).
    LaunchedEffect(ui.phase, ui.sourceIndex) {
        loadingLong = false
        waitDismissed = false
        if (ui.phase == Phase.TRYING || ui.phase == Phase.STARTING_ENGINE) {
            delay(8_000)
            loadingLong = true
        }
    }
    // Un corte nuevo vuelve a ofrecer los botones.
    val recoveringNow = ui.phase == Phase.PLAYING && ui.recovering != null
    LaunchedEffect(recoveringNow) { if (recoveringNow) waitDismissed = false }
    val offerWait = !waitDismissed && panel == null && ((ui.phase == Phase.TRYING && loadingLong) || recoveringNow)
    LaunchedEffect(offerWait) {
        delay(80)
        runCatching {
            when {
                offerWait -> waitFocus.requestFocus()
                panel != null -> panelFocus.requestFocus()
                controls && ui.phase == Phase.PLAYING -> pauseFocus.requestFocus()
                ui.phase == Phase.FAILED -> dialogFocus.requestFocus()
                else -> catcher.requestFocus()
            }
        }
    }

    // Foco: panel → su primera fila; controles → Pausa (o el botón que abrió el panel); nada → recogedor de teclas.
    LaunchedEffect(controls, panel, ui.phase) {
        delay(80)
        runCatching {
            when {
                offerWait -> waitFocus.requestFocus()
                panel != null -> panelFocus.requestFocus()
                ui.phase == Phase.FAILED -> dialogFocus.requestFocus()
                controls -> (lastOpener?.let { openers[it] } ?: pauseFocus).requestFocus()
                else -> catcher.requestFocus()
            }
        }
    }

    BackHandler {
        when {
            offerWait -> waitDismissed = true
            panel != null -> { panel = null; touch++ }
            controls && ui.phase == Phase.PLAYING -> { controls = false; lastOpener = null }
            else -> onBack()
        }
    }

    val agenda by graph.agenda.state.collectAsState()
    val liveEvent = channel?.let { remember(it.id, agenda.agenda, now) { liveEventFor(it, agenda.agenda, lib.library) } }
    // 3h · El retraso llega del reproductor (vuelta al directo adaptativa); solo lo leen quienes lo pintan.
    val delayMs = if (panel == Panel.INFO) graph.player.live.collectAsState().value.delayMs else null
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
                // 3f · Tablet y móvil: un toque en el vídeo enseña los controles (no hay botón OK).
                .pointerInput(Unit) { detectTapGestures(onTap = { controls = true; touch++ }) }
        )

        if (channel == null && (lib.loading || lib.library.channels.isEmpty()) && (lib.providers.isNotEmpty() || graph.gold.state.value.configured)) {
            // Al abrir la app directamente en el último canal: espera a que se lean los proveedores.
            Loading(null, null, 0, 0, "Cargando tus canales…", null)
            return@Box
        }
        if (channel == null) {
            PlayerDialog("Canal no disponible", "Este canal ya no está en tus canales.") {
                MvButton("Volver", onBack, primary = true, focusRequester = dialogFocus)
            }
            InitialFocus(dialogFocus)
            return@Box
        }

        val sourceTag = if (ui.sourceCount > 0) "${ui.sourceIndex.coerceAtLeast(1)} / ${ui.sourceCount} fuentes" else null
        when (phase) {
            Phase.IDLE, Phase.STARTING_ENGINE, Phase.TRYING -> if (panel == null) Loading(
                channel,
                subtitle = liveEvent?.let { ev -> ev.title + " · en directo" },
                step = when {
                    phase != Phase.TRYING -> 0
                    ui.peers > 0 -> 2
                    else -> 1
                },
                peers = ui.peers,
                // 3p · Qué está pasando de verdad, para que se sepa si esperar o no.
                status = when {
                    phase == Phase.STARTING_ENGINE -> "Arrancando el motor…"
                    ui.stage == com.mivisor.tv.playback.PlaybackController.LoadStage.RESOLVING ->
                        "Buscando el canal…" + if (ui.stageSecs >= 3) " ${ui.stageSecs} s" else ""
                    ui.stage == com.mivisor.tv.playback.PlaybackController.LoadStage.PEERS ->
                        "Canal encontrado · buscando emisores…" + if (ui.stageSecs >= 3) " ${ui.stageSecs} s" else ""
                    ui.stage == com.mivisor.tv.playback.PlaybackController.LoadStage.BUFFERING ->
                        if (ui.direct && ui.insight?.kind.let { it == null || it == com.mivisor.tv.engine.LoadInsight.Kind.WAITING || it == com.mivisor.tv.engine.LoadInsight.Kind.RECEIVING })
                            "Recibiendo vídeo · probando la entrega directa…"
                        else ui.insight?.status ?: "Conectado a ${ui.peers} ${if (ui.peers == 1) "emisor" else "emisores"} · preparando la imagen…"
                    ui.sourceIndex > 1 -> "Probando otra fuente…"
                    else -> "Buscando señal…"
                },
                hint = when {
                    phase != Phase.TRYING -> null
                    ui.stage == com.mivisor.tv.playback.PlaybackController.LoadStage.RESOLVING ->
                        if (ui.stageSecs < 6) null
                        else "Localizando el canal en el catálogo de Ace Stream. A veces tarda hasta un minuto: no cierres, sigue buscando." +
                            if (ui.stageSecs >= 30 && graph.warp.state.value.phase != com.mivisor.tv.services.WarpPhase.CONNECTED) " Si tarda mucho, conecta WARP." else ""
                    ui.stage == com.mivisor.tv.playback.PlaybackController.LoadStage.PEERS ->
                        "Encontrado en ${ui.resolvedInSecs ?: 0} s." + when {
                            ui.stageSecs < 25 -> " Esperando a quien lo emite."
                            !com.mivisor.tv.outpace.EngineManager.fullP2p -> " Aún sin emisores. Si en Ace Stream sí se ve, activa «Aceptar conexiones de otros» en Ajustes › Motor."
                            else -> " Nadie lo está emitiendo todavía: puede que la emisión esté caída."
                        }
                    // 4d · Con emisores: qué pasa (emisión parada, no mandan vídeo, firma…).
                    ui.stage == com.mivisor.tv.playback.PlaybackController.LoadStage.BUFFERING ->
                        if (ui.direct && ui.insight?.kind.let { it == null || it == com.mivisor.tv.engine.LoadInsight.Kind.WAITING || it == com.mivisor.tv.engine.LoadInsight.Kind.RECEIVING })
                            "El vídeo llegaba pero no se podía preparar de la forma normal: ahora se reproduce tal cual llega (plan B)."
                        else ui.insight?.hint
                    else -> null
                },
                detail = listOfNotNull(
                    sourceTag?.let { "Fuente ${ui.sourceIndex} de ${ui.sourceCount}" },
                    // 4f · Biblioteca de Gold: en qué calidad se va a ver (medida por Gold en el vídeo real).
                    if (channel.sources.any { it.gold != null }) ui.quality ?: ui.provider else ui.provider,
                    graph.sources.typicalStart(channel.sources.map { it.contentId })?.let { "suele tardar ~$it s" }
                ).joinToString(" · ").ifEmpty { null }
            ) {
                if (offerWait && ui.sourceCount > 1) WaitButtons(waitFocus, onWait = { waitDismissed = true }, onSkip = { waitDismissed = true; graph.player.nextSource() })
            }
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
                live = graph.player.live,
                clock = hhmm(now),
                paused = ui.paused,
                favorite = mine.isFavorite(channel.id),
                hint = if (panel != null) null else if (form.touch) "Toca el vídeo para ocultar los controles" else "Las flechas no cambian de canal · Atrás oculta los controles",
                onClose = if (form.touch) onBack else null,
                onGoLive = { graph.player.goLive(); touch++ },
                onJump = { graph.player.jumpAhead(); touch++ },
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

        // 3h · Corte: aviso discreto abajo a la derecha. La imagen sigue donde estaba (no se salta al directo).
        if (phase == Phase.PLAYING && panel == null) ui.recovering?.let { text ->
            RecoveryCard(
                text = text, live = graph.player.live,
                caption = if (graph.prefs.autoSwitch == "never") "Se sigue intentando con esta fuente hasta que vuelva o elijas otra."
                else "Cuando la imagen vuelva a ser estable, la app se irá acercando sola al directo.",
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = MvDims.SafeX, bottom = MvDims.SafeY + 6.dp)
            ) {
                if (offerWait) WaitButtons(waitFocus, onWait = { waitDismissed = true }, onSkip = { waitDismissed = true; graph.player.nextSource() })
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
                            // 4f · Biblioteca de MiVisor Gold: el color es su banda (la app no puntúa esas fuentes).
                            val (label, color) = r.band?.let { goldBandLabel(it) } ?: stateLabel(r.state)
                            PanelRow(
                                title = "Fuente ${r.index}" + (if (r.current) " · viendo ahora" else ""),
                                subtitle = listOfNotNull(
                                    r.provider, r.quality, if (r.onTrial) "a prueba: acaba de volver" else null,
                                    if (r.tried && !r.current) "probada en este intento" else null
                                ).joinToString(" · "),
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

/**
 * 3h · Carga: el logo del canal grande en el centro con su nombre, una barra fina animada y las tres fases en texto
 * (Motor → Emisores → Imagen) para que se vea que avanza. Abajo, si tarda, «Seguir esperando» / «Probar otra fuente».
 */
@Composable
private fun Loading(
    channel: TvChannel?,
    subtitle: String?,
    /** 0 motor, 1 buscando emisores, 2 preparando la imagen. */
    step: Int,
    peers: Int,
    status: String,
    detail: String?,
    /** 3p · Explicación del paso en curso (si hay que esperar o si algo va mal). */
    hint: String? = null,
    extra: @Composable () -> Unit = {}
) {
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF142235), Color(0xFF0A1420), Color(0xFF060B12)), radius = 900f))) {
        Column(Modifier.align(Alignment.Center).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (channel != null) {
                com.mivisor.tv.ui.LogoImage(
                    channel.logo, 240.dp, Modifier.size(width = 210.dp, height = 118.dp), RoundedCornerShape(18.dp),
                    plain = Color(0xFF0E1A27), frame = Color(0x14FFFFFF), padding = 0.14f
                ) { MvText(channel.name, MvType.Heading.copy(color = MvColors.Text2), maxLines = 2, modifier = Modifier.padding(12.dp)) }
                Spacer(Modifier.height(20.dp))
                MvText(channel.name, MvType.Title.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    MvText(subtitle, MvType.Body.copy(color = MvColors.Text2), maxLines = 1)
                }
            } else MvText(status, MvType.Heading)
            Spacer(Modifier.height(26.dp))
            LoadingBar(Modifier.width(240.dp))
            // 3h.1 · Un solo mensaje sencillo («Buscando señal…», «Conectando…»), sin fases técnicas.
            if (channel != null) {
                Spacer(Modifier.height(14.dp))
                MvText(status, MvType.Body.copy(color = MvColors.Text2), maxLines = 1)
            }
            if (hint != null) {
                Spacer(Modifier.height(6.dp))
                MvText(hint, MvType.Small.copy(color = MvColors.Text3, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                    maxLines = 2, modifier = Modifier.widthIn(max = 520.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = MvDims.SafeY), horizontalAlignment = Alignment.CenterHorizontally) {
            extra()
            if (detail != null) {
                Spacer(Modifier.height(10.dp))
                MvText(detail, MvType.Caption.copy(color = MvColors.Text3), maxLines = 1)
            }
        }
    }
}

/** Fase de la carga: 0 pendiente (gris), 1 en curso (punto champán), 2 hecha (marca verde). */
@Composable
private fun LoadStep(text: String, state: Int, runningText: String = text) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            2 -> MvIcon(MvIcons.Check, size = 13.dp, tint = MvColors.Ok)
            1 -> Box(Modifier.size(7.dp).background(MvColors.Accent, CircleShape))
            else -> Box(Modifier.size(7.dp).border(1.dp, MvColors.Text3, CircleShape))
        }
        Spacer(Modifier.width(7.dp))
        MvText(
            if (state == 1) runningText else text,
            MvType.Small.copy(color = when (state) { 2 -> MvColors.Ok; 1 -> MvColors.Text; else -> MvColors.Text3 }, fontWeight = if (state == 1) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 1
        )
    }
}

@Composable
private fun StepLine() {
    Box(Modifier.width(18.dp).height(1.dp).background(Color(0x2EFFFFFF)))
}

/**
 * Barra fina indeterminada: un brillo champán que recorre la pista. 3h.1: movida por el reloj de pantalla y pintada
 * en la fase de dibujo (funciona con las animaciones del sistema desactivadas y no recompone nada).
 */
@Composable
private fun LoadingBar(modifier: Modifier) {
    val loop = com.mivisor.tv.ui.design.rememberLoop(1600)
    val accent = MvColors.Accent
    Box(
        modifier.height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x1AFFFFFF)).drawBehind {
            val e = androidx.compose.animation.core.FastOutSlowInEasing.transform(loop.value)
            val w = size.width * 0.4f
            val x = (-0.4f + 1.4f * e) * size.width
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, accent, Color.Transparent), startX = x, endX = x + w),
                topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(w, size.height)
            )
        }
    )
}

/** «Seguir esperando» (foco por defecto: un OK sin querer no cambia de fuente) o «Probar otra fuente». */
@Composable
private fun WaitButtons(focus: FocusRequester, onWait: () -> Unit, onSkip: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("Seguir esperando", null, primary = true, focusRequester = focus, onClick = onWait)
        PillButton("Probar otra fuente", MvIcons.Signal, onClick = onSkip)
    }
}

/**
 * 3h · Botón del reproductor en pastilla: [primary] = claro (marfil) con texto oscuro; si no, translúcido con borde
 * fino. Con foco: anillo champán (en la tele) y fondo más claro.
 */
@Composable
private fun PillButton(
    text: String,
    icon: Int?,
    primary: Boolean = false,
    accent: Boolean = false,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    onFocus: (() -> Unit)? = null,
    height: androidx.compose.ui.unit.Dp = 38.dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    TvClickable(
        onClick = onClick, focusRequester = focusRequester, enabled = enabled, onFocus = onFocus, shape = shape, focusedScale = 1.04f,
        color = when { primary -> MvColors.Text; accent -> MvColors.Accent; else -> Color(0x9E101A26) },
        focusedColor = when { primary -> Color.White; accent -> MvColors.Accent2; else -> Color(0xE62A4153) },
        ringColor = MvColors.Accent2,
        modifier = Modifier.height(height).then(if (!primary && !accent) Modifier.border(1.dp, Color(0x29FFFFFF), shape) else Modifier)
    ) { _ ->
        Row(Modifier.fillMaxHeight().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            val fg = if (primary || accent) MvColors.Bg else MvColors.Text
            if (icon != null) {
                MvIcon(icon, size = 15.dp, tint = fg)
                Spacer(Modifier.width(8.dp))
            }
            MvText(text, MvType.BodyStrong.copy(color = fg), maxLines = 1)
        }
    }
}

/** 3h · Aviso de corte: tarjeta discreta con el retraso acumulado («sigues donde estabas, no te pierdes nada»). */
@Composable
private fun RecoveryCard(
    text: String,
    live: kotlinx.coroutines.flow.StateFlow<com.mivisor.tv.playback.PlaybackController.Live>,
    caption: String,
    modifier: Modifier,
    buttons: @Composable () -> Unit
) {
    val l by live.collectAsState()
    Column(
        modifier.width(300.dp).background(Color(0xD10A121C), RoundedCornerShape(16.dp)).border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrbitaMark(26.dp, spinning = true)
            Spacer(Modifier.width(11.dp))
            Column {
                MvText(text.substringBefore(" · "), MvType.BodyStrong, maxLines = 1)
                MvText("Sigues donde estabas · no te pierdes nada", MvType.Caption.copy(color = MvColors.Text2), maxLines = 1)
            }
        }
        l.delayMs?.let { d ->
            Row {
                MvText("Retraso acumulado", MvType.Caption.copy(color = MvColors.Text2), modifier = Modifier.weight(1f))
                MvText("${d / 1000} s", MvType.SmallStrong)
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x1FFFFFFF), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth((d / 60_000f).coerceIn(0.04f, 1f)).fillMaxHeight().background(MvColors.Soon, RoundedCornerShape(2.dp)))
            }
        }
        MvText(caption, MvType.Caption.copy(color = MvColors.Text3))
        buttons()
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
    live: kotlinx.coroutines.flow.StateFlow<com.mivisor.tv.playback.PlaybackController.Live>,
    clock: String,
    paused: Boolean,
    favorite: Boolean,
    hint: String?,
    onGoLive: () -> Unit,
    onJump: () -> Unit,
    pauseFocus: FocusRequester,
    openers: Map<Panel, FocusRequester>,
    dimmed: Boolean,
    onPause: () -> Unit,
    onPanel: (Panel) -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onTouch: () -> Unit,
    /** 3f · Solo en pantallas táctiles: flecha para salir del reproductor (no hay botón Atrás a la vista). */
    onClose: (() -> Unit)? = null
) {
    val l by live.collectAsState()
    val delay = l.delayMs ?: 0
    // 3f · Un toque fuera de los botones oculta los controles (los botones se quedan su propio toque).
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onHide() }) }) {
        // 3h · Degradados largos y suaves arriba y abajo (nada de cajas): el vídeo sigue mandando.
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(160.dp).background(Brush.verticalGradient(listOf(Color(0xDB04080D), Color(0x7304080D), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x9E04080D), Color(0xF004080D)))))
        // Arriba: logo GRANDE (más alto que el texto), canal y evento; a la derecha fuente y hora.
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = MvDims.SafeX, end = MvDims.SafeX, top = MvDims.SafeY),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onClose != null) {
                TvClickable(
                    onClick = onClose, shape = CircleShape, focusedScale = 1f, color = Color(0x66000000),
                    focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
                    modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center
                ) { _ -> MvIcon(MvIcons.Back, size = 22.dp, tint = MvColors.Text) }
                Spacer(Modifier.width(14.dp))
            }
            com.mivisor.tv.ui.LogoImage(
                channel.logo, 120.dp, Modifier.size(width = 92.dp, height = 52.dp), RoundedCornerShape(10.dp),
                plain = Color(0xCC0E1A27), frame = Color(0x1AFFFFFF), padding = 0.1f
            ) { MvIcon(MvIcons.Signal, size = 22.dp, tint = MvColors.Text2) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                MvText(channel.name, MvType.Title.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(Modifier.height(2.dp))
                MvText(subtitle, MvType.Small.copy(color = MvColors.Text2), maxLines = 1)
            }
            if (sourceTag != null) {
                Row(
                    Modifier.background(Color(0x8C0C141E), RoundedCornerShape(50)).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).background(MvColors.Ok, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    MvText(sourceTag, MvType.SmallStrong, maxLines = 1)
                }
            }
            Spacer(Modifier.width(16.dp))
            MvText(clock, MvType.Heading.copy(fontWeight = FontWeight.SemiBold))
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = MvDims.SafeX, end = MvDims.SafeX, bottom = MvDims.SafeY)
                .then(if (dimmed) Modifier.alpha(0.35f) else Modifier)
        ) {
            // Línea del directo: «DIRECTO» (al día) o «DIRECTO · −32 s» con lo que va por detrás y lo descargado.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.background(if (l.behind || paused) Color(0x29E58C83) else MvColors.LiveBg, RoundedCornerShape(6.dp)).padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).background(MvColors.Live, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    MvText(
                        when { paused -> "EN PAUSA"; delay >= 2_000 -> "DIRECTO · −${delay / 1000} s"; else -> "EN DIRECTO" },
                        MvType.Caption.copy(color = if (l.behind || paused) Color(0xFFF2B1AA) else MvColors.Text, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                MvText(
                    when {
                        paused -> "Al reanudar vuelves al directo."
                        l.behind && l.state == com.mivisor.tv.engine.CatchUpController.State.CATCHING_UP -> "Acercándose al directo poco a poco…"
                        l.behind && l.aheadMs >= 15_000 -> "Vas por detrás por los cortes. La imagen hasta el directo ya está descargada."
                        l.behind -> "Vas por detrás por los cortes. Se irá acercando sola cuando la imagen esté estable."
                        else -> hint ?: ""
                    },
                    MvType.Caption.copy(color = MvColors.Text2), maxLines = 1, modifier = Modifier.weight(1f)
                )
            }
            if (l.behind) {
                Spacer(Modifier.height(10.dp))
                LiveLine(delay, l.aheadMs)
            }
            Spacer(Modifier.height(14.dp))
            androidx.compose.runtime.CompositionLocalProvider(LocalBarEnabled provides !dimmed) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Pausa: botón redondo marfil; al reanudar vuelve al directo.
                    TvClickable(
                        onClick = onPause, focusRequester = pauseFocus, onFocus = onTouch, shape = CircleShape, focusedScale = 1.06f, enabled = !dimmed,
                        color = MvColors.Text, focusedColor = Color.White, ringColor = MvColors.Accent2,
                        contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp)
                    ) { _ ->
                        if (paused) MvIcon(MvIcons.Play, size = 20.dp, tint = MvColors.Bg)
                        else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(width = 4.dp, height = 15.dp).background(MvColors.Bg, RoundedCornerShape(1.dp)))
                            Box(Modifier.size(width = 4.dp, height = 15.dp).background(MvColors.Bg, RoundedCornerShape(1.dp)))
                        }
                    }
                    if (l.behind) PillButton("Ir al directo", MvIcons.Live, accent = true, enabled = !dimmed, onFocus = onTouch, onClick = onGoLive)
                    if (l.canJump15) PillButton("Adelantar 15 s", MvIcons.ChevronRight, enabled = !dimmed, onFocus = onTouch, onClick = onJump)
                    Spacer(Modifier.weight(1f))
                    PillButton("Fuentes", null, focusRequester = openers.getValue(Panel.SOURCES), enabled = !dimmed, onFocus = onTouch) { onPanel(Panel.SOURCES) }
                    PillButton("Audio", null, focusRequester = openers.getValue(Panel.AUDIO), enabled = !dimmed, onFocus = onTouch) { onPanel(Panel.AUDIO) }
                    PillButton("Subtítulos", null, focusRequester = openers.getValue(Panel.SUBS), enabled = !dimmed, onFocus = onTouch) { onPanel(Panel.SUBS) }
                    PillButton("Vídeo", null, focusRequester = openers.getValue(Panel.VIDEO), enabled = !dimmed, onFocus = onTouch) { onPanel(Panel.VIDEO) }
                    PillButton("Info", null, focusRequester = openers.getValue(Panel.INFO), enabled = !dimmed, onFocus = onTouch) { onPanel(Panel.INFO) }
                    PillButton(if (favorite) "En Mi lista" else "Mi lista", MvIcons.Star, enabled = !dimmed, onFocus = onTouch, onClick = onFavorite)
                }
            }
        }
    }
}

/** Línea «Tú ─── Directo»: lo que se ve (champán), lo ya descargado (champán tenue) y el borde del directo (rojo). */
@Composable
private fun LiveLine(delayMs: Long, aheadMs: Long) {
    // Escala: los últimos 90 s (o el retraso, si es mayor) hasta el directo.
    val span = maxOf(90_000L, delayMs + 10_000L).toFloat()
    val you = 1f - delayMs / span
    val loaded = (you + aheadMs / span).coerceAtMost(1f)
    Column {
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x24FFFFFF), RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(loaded).fillMaxHeight().background(Color(0x59E9C99E), RoundedCornerShape(2.dp)))
            Box(Modifier.fillMaxWidth(you).fillMaxHeight().background(MvColors.Accent, RoundedCornerShape(2.dp)))
            Box(Modifier.align(Alignment.CenterEnd).size(width = 3.dp, height = 10.dp).background(MvColors.Live, RoundedCornerShape(1.dp)))
        }
        Spacer(Modifier.height(4.dp))
        Row {
            MvText("Tú", MvType.Caption.copy(color = MvColors.Text3), modifier = Modifier.weight(1f))
            MvText("Directo", MvType.Caption.copy(color = MvColors.Text3))
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
    // 4f · Lo que MiVisor Gold midió de esta fuente (no lo que dice el nombre del canal).
    val gold = ui.sources.firstOrNull { it.current }?.contentId?.let { cid -> channel.sources.firstOrNull { it.contentId == cid }?.gold }
    gold?.let { g ->
        if (g.audios.isNotEmpty()) kv("Audio (Gold)", g.audios.joinToString(" · "))
        g.declaredVsReal?.let { kv("Declarada", it) }
        val nota = listOfNotNull(g.band?.let { goldBandLabel(it).first }, g.nota?.let { "$it %" }, g.checked?.let { "comprobada $it" })
        if (nota.isNotEmpty()) kv("Gold", nota.joinToString(" · "))
        val notes = (g.avisos + g.info).map { it.replace('_', ' ') }
        if (notes.isNotEmpty()) kv("Avisos", notes.joinToString(" · "))
    }
    if (ui.peers > 0) kv("Emisores", "${ui.peers}")
    delayMs?.let { kv("Retraso", "${"%.1f".format(it / 1000.0)} s respecto al directo") }
    ui.startMs?.let { kv("Arranque", "imagen en ${"%.1f".format(it / 1000.0)} s") }
    kv("Motor", "Outpace")
    val audio = ui.tracks.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.selected }
    audio?.let { kv("Audio", it.label + (it.detail?.let { d -> " · $d" } ?: "")) }
}


/** 3n · Canal suelto para un ID de Ace Stream abierto a mano (con el nombre que le diste, si le diste uno). */
private fun directChannel(graph: AppGraph, channelId: String): TvChannel? {
    val cid = com.mivisor.tv.catalog.datos.DirectStream.contentIdOf(channelId) ?: return null
    val name = graph.prefs.directRecent.firstOrNull { it.substringBefore('\t') == cid }?.substringAfter('\t', "")?.takeIf { it.isNotBlank() }
        ?: "Enlace directo " + com.mivisor.tv.catalog.datos.DirectStream.short(cid)
    return TvChannel(
        id = channelId, name = name, brandKey = null, brandName = null,
        sources = listOf(com.mivisor.tv.catalog.tv.TvSource(cid, "direct", "Abierto a mano", 0, null, "")),
        initials = "ID"
    )
}
