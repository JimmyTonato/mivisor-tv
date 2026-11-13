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
            // Al abrir la app directamente en el último canal: espera a que se lean los canales.
            LoadingStage(null, null, 0, 0, "Cargando tus canales…", null, null)
            return@Box
        }
        if (channel == null) {
            AlertStage(null, Alert.INFO, "Canal no disponible", "Este canal ya no está", "Puede que lo hayan quitado o renombrado. Búscalo en Canales.") {
                ChromeButton("Volver", MvIcons.Back, primary = true, focusRequester = dialogFocus, onClick = onBack)
            }
            InitialFocus(dialogFocus)
            return@Box
        }

        val currentRow = ui.sources.firstOrNull { it.current }
        // 4n · La calidad, la del canal (Gold o proveedor): 4K, Full HD, HD o SD.
        val currentQuality = channel.qualityOf(currentRow?.contentId) ?: qualityName(0, ui.quality)
        val sourceTag = if (ui.sourceCount > 0) "Fuente ${ui.sourceIndex.coerceAtLeast(1)}/${ui.sourceCount}" + (currentQuality?.let { " · $it" } ?: "") else null
        when (phase) {
            Phase.IDLE, Phase.STARTING_ENGINE, Phase.TRYING -> if (panel == null) LoadingStage(
                channel,
                event = liveEvent,
                step = when {
                    phase != Phase.TRYING -> 0
                    ui.stage == com.mivisor.tv.playback.PlaybackController.LoadStage.BUFFERING -> 2
                    ui.stage == com.mivisor.tv.playback.PlaybackController.LoadStage.PEERS || ui.peers > 0 -> 1
                    else -> 0
                },
                sourceIndex = ui.sourceIndex,
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
                    if (ui.sourceCount > 0) "Fuente ${ui.sourceIndex.coerceAtLeast(1)} de ${ui.sourceCount}" else null,
                    // 4f · Biblioteca de Gold: en qué calidad se va a ver (medida por Gold en el vídeo real).
                    currentQuality,
                    ui.provider,
                    graph.sources.typicalStart(channel.sources.map { it.contentId })?.let { "suele tardar ~$it s" }
                ).joinToString(" · ").ifEmpty { null }
            ) {
                if (offerWait) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // «Seguir esperando» con el foco: un OK sin querer no cambia de fuente.
                    ChromeButton("Seguir esperando", primary = true, focusRequester = waitFocus) { waitDismissed = true }
                    if (ui.sourceCount > 1) ChromeButton("Probar otra fuente", MvIcons.Layers) { waitDismissed = true; graph.player.nextSource() }
                    ChromeButton("Cancelar", onClick = onBack)
                }
            }
            Phase.FAILED -> if (panel == null) {
                val canAce = aceInstalled && graph.prefs.aceFallback != "never" && ui.cause != "offline" && ui.cause != "internet"
                val allResting = ui.sources.isNotEmpty() && ui.sources.all {
                    it.state == SourceState.PAUSED || it.state == SourceState.QUARANTINE || it.state == SourceState.INCOMPATIBLE
                }
                val tech = listOfNotNull(
                    if (ui.sourceCount > 0) "${ui.sourceCount} ${if (ui.sourceCount == 1) "fuente" else "fuentes"} probadas" else null,
                    ui.provider
                ).joinToString(" · ").ifEmpty { null }
                when {
                    ui.cause == "engine" -> AlertStage(
                        channel, Alert.WAIT, "El motor no ha arrancado", "No se ha podido arrancar el motor",
                        (ui.message ?: "Outpace no respondió.") + " Suele arreglarse reintentando; si se repite, el diagnóstico dice por qué.", tech
                    ) {
                        ChromeButton("Reintentar", MvIcons.Refresh, primary = true, focusRequester = dialogFocus) { graph.player.retry() }
                        if (canAce) ChromeButton("Probar con Ace Stream", MvIcons.Bolt) { scope.launch { openInAce(channel) } }
                        ChromeButton("Volver", onClick = onBack)
                    }
                    allResting && ui.cause == null -> AlertStage(
                        channel, Alert.WAIT, "Sin fuentes disponibles", "Las fuentes de ${channel.name} están descansando",
                        "Sus ${ui.sources.size} fuentes han fallado varias veces seguidas." +
                            (graph.sources.resting().filter { r -> ui.sources.any { it.contentId == r.contentId } }.mapNotNull { it.untilMs }.minOrNull()
                                ?.let { " La primera vuelve en ${((it - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)} min." } ?: ""), tech
                    ) {
                        ChromeButton("Probar igualmente", MvIcons.Play, primary = true, focusRequester = dialogFocus) { graph.player.play(channel.request(), ui.sources.first().contentId) }
                        ChromeButton("Ver cuarentena", MvIcons.Layers) { onRoute(Route.Quarantine) }
                        ChromeButton("Volver", onClick = onBack)
                    }
                    else -> AlertStage(
                        channel, if (ui.cause == "offline" || ui.cause == "internet") Alert.WAIT else Alert.ERROR,
                        if (ui.cause == "offline" || ui.cause == "internet") "Sin conexión" else "Sin imagen",
                        if (ui.cause == null) "No ha llegado imagen" else "No se puede ver ahora",
                        ui.message ?: "No se pudo reproducir el canal.", tech
                    ) {
                        ChromeButton("Reintentar", MvIcons.Refresh, primary = true, focusRequester = dialogFocus) { graph.player.retry() }
                        if (ui.sources.size > 1) ChromeButton("Cambiar fuente", MvIcons.Layers) { open(Panel.SOURCES) }
                        if (canAce) ChromeButton("Ace Stream", MvIcons.Bolt) { scope.launch { openInAce(channel) } }
                        ChromeButton("Volver", onClick = onBack)
                    }
                }
            }
            Phase.PLAYING -> if (controls || panel != null) PlayerControls(
                channel = channel,
                event = liveEvent,
                context = liveEvent?.let { ev -> listOfNotNull(ev.competition ?: sportName(ev.sport), channel.name).joinToString(" · ") }
                    ?: listOfNotNull(channel.brandName, "Televisión en directo").joinToString(" · "),
                sourceTag = sourceTag,
                signal = barsOf(currentRow),
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

        // 3h · Corte: aviso abajo a la derecha. La imagen sigue donde estaba (no se salta al directo).
        if (phase == Phase.PLAYING && panel == null && !controls) ui.recovering?.let { text ->
            StallCard(
                text = text, live = graph.player.live,
                caption = if (graph.prefs.autoSwitch == "never") "Se sigue intentando con esta fuente hasta que vuelva o elijas otra."
                else "Cuando la imagen vuelva a ser estable, la app se irá acercando sola al directo.",
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = MvDims.SafeX, bottom = 30.dp)
            ) {
                if (offerWait) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChromeButton("Seguir esperando", primary = true, focusRequester = waitFocus, height = 34.dp) { waitDismissed = true }
                    ChromeButton("Otra fuente", MvIcons.Layers, height = 34.dp) { waitDismissed = true; graph.player.nextSource() }
                }
            }
        }

        panel?.let { p ->
            val cards: List<PanelCard> = when (p) {
                Panel.SOURCES -> ui.sources.map { r ->
                    // 4f · Biblioteca de MiVisor Gold: el color es su banda (la app no puntúa esas fuentes).
                    val (label, color) = r.band?.let { goldBandLabel(it) } ?: stateLabel(r.state)
                    val q = channel.qualityOf(r.contentId) ?: qualityName(0, r.quality)
                    PanelCard(
                        big = q?.takeIf { it.length <= 7 } ?: "F${r.index}",
                        title = "Fuente ${r.index}",
                        sub = listOfNotNull(r.provider, q?.takeIf { it.length > 7 }, if (r.onTrial) "a prueba: acaba de volver" else null,
                            if (r.tried && !r.current) "probada en este intento" else null).joinToString(" · "),
                        badge = label, badgeColor = color, selected = r.current, bars = barsOf(r),
                        onLongClick = if (aceInstalled) ({
                            graph.player.stop()
                            ExternalApps.openInAce(context, r.contentId)?.let { toaster.show(it) } ?: onBack()
                        }) else null
                    ) { panel = null; if (!r.current) graph.player.selectSource(r.contentId) }
                }
                Panel.AUDIO -> ui.tracks.filter { it.type == C.TRACK_TYPE_AUDIO }.map { t ->
                    PanelCard(shortLang(t.label), t.label, t.detail ?: "", selected = t.selected, enabled = t.supported) { graph.player.selectTrack(t); panel = null }
                }
                Panel.SUBS -> {
                    val subs = ui.tracks.filter { it.type == C.TRACK_TYPE_TEXT }
                    listOf(PanelCard("OFF", "Desactivados", "Sin subtítulos", selected = subs.none { it.selected }) { graph.player.disableSubtitles(); panel = null }) +
                        subs.map { t -> PanelCard(shortLang(t.label), t.label, t.detail ?: "Subtítulos de la emisión", selected = t.selected, enabled = t.supported) { graph.player.selectTrack(t); panel = null } }
                }
                Panel.VIDEO -> FITS.mapIndexed { i, (label, _) ->
                    PanelCard(
                        when (i) { 0 -> "16:9"; 1 -> "ZOOM"; else -> "FULL" }, label,
                        when (i) { 0 -> "Imagen completa, con bandas si hace falta"; 1 -> "Llena la pantalla recortando los bordes"; else -> "Llena la pantalla deformando la imagen" },
                        badge = if (i == 0) "Recomendado" else null, selected = fit == i
                    ) { fit = i }
                }
                Panel.INFO -> infoCards(channel, ui, liveEvent, delayMs)
            }
            PanelSheet(
                current = p,
                hint = when (p) {
                    Panel.SOURCES -> "Si va a saltos o con retraso, prueba otra emisión."
                    Panel.AUDIO -> "Pistas de audio de esta fuente · se recuerda en este canal."
                    Panel.SUBS -> "Idioma de los subtítulos."
                    Panel.VIDEO -> "Cómo se ajusta la imagen a la pantalla."
                    Panel.INFO -> "Datos de la emisión actual."
                },
                cards = cards,
                note = when (p) {
                    Panel.SOURCES -> if (ui.sources.isEmpty()) "Este canal no tiene fuentes." else
                        "Al elegir una, se prueba sin salir del canal." + if (aceInstalled) " Mantén OK para abrirla en Ace Stream." else ""
                    Panel.AUDIO -> if (cards.isEmpty()) "Esta fuente no informa de pistas de audio." else null
                    Panel.SUBS -> if (cards.size <= 1) "Esta fuente no trae subtítulos." else null
                    else -> null
                },
                firstFocus = panelFocus,
                onTab = { panel = it; lastOpener = it },
                onClose = { panel = null }
            )
        }
    }
}

/** «Español» → «ES»; si no se reconoce, las tres primeras letras. */
private fun shortLang(label: String): String {
    val l = label.lowercase()
    return when {
        l.startsWith("espa") || l.startsWith("castell") || l == "es" || l.startsWith("spanish") -> "ES"
        l.startsWith("ingl") || l.startsWith("english") || l == "en" -> "EN"
        l.startsWith("catal") -> "CA"
        l.startsWith("portug") -> "PT"
        l.startsWith("fran") || l.startsWith("french") -> "FR"
        l.startsWith("ital") -> "IT"
        l.startsWith("alem") || l.startsWith("german") || l.startsWith("deutsch") -> "DE"
        else -> label.filter { it.isLetterOrDigit() }.take(3).uppercase().ifEmpty { "—" }
    }
}

/** 4m · Info como tarjetas: retraso, fuente, calidad, emisores, arranque, audio, Gold y motor. */
private fun infoCards(channel: TvChannel, ui: com.mivisor.tv.playback.PlaybackController.Ui, event: com.mivisor.tv.model.AgendaEvent?, delayMs: Long?): List<PanelCard> {
    val out = ArrayList<PanelCard>()
    delayMs?.let { d -> out += PanelCard(if (d >= 2_000) "−${d / 1000} s" else "0 s", "Retraso", if (d >= 2_000) "Respecto al directo" else "En directo") }
    out += PanelCard(if (ui.sourceCount > 0) "${ui.sourceIndex}/${ui.sourceCount}" else "—", "Fuente", ui.provider ?: "Emisión en uso")
    (channel.qualityOf(ui.sources.firstOrNull { it.current }?.contentId) ?: qualityName(0, ui.quality))?.let {
        out += PanelCard(it.take(7), "Calidad", "La que da el canal")
    }
    // 4f · Lo que MiVisor Gold midió de esta fuente (no lo que dice el nombre del canal).
    val gold = ui.sources.firstOrNull { it.current }?.contentId?.let { cid -> channel.sources.firstOrNull { it.contentId == cid }?.gold }
    val audio = ui.tracks.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.selected }
    if (audio != null) out += PanelCard(shortLang(audio.label), "Audio", listOfNotNull(audio.label, audio.detail).joinToString(" · "))
    else gold?.audios?.firstOrNull()?.let { out += PanelCard("AUD", "Audio", gold.audios.joinToString(" · ")) }
    if (ui.peers > 0) out += PanelCard("${ui.peers}", "Emisores", "Conectados ahora")
    ui.startMs?.let { out += PanelCard("${"%.1f".format(it / 1000.0)} s", "Arranque", "Hasta la primera imagen") }
    gold?.let { g ->
        val (label, color) = g.band?.let { goldBandLabel(it) } ?: ("Sin comprobar" to MvColors.Text3)
        out += PanelCard(
            g.nota?.let { "$it %" } ?: "—", "Biblioteca",
            listOfNotNull(g.declaredVsReal, g.checked?.let { "comprobada $it" }, (g.avisos + g.info).joinToString(" · ") { it.replace('_', ' ') }.ifBlank { null })
                .joinToString(" · "),
            badge = label, badgeColor = color
        )
    }
    event?.let { out += PanelCard("AHORA", it.title, it.competition ?: "") }
    out += PanelCard("OUT", "Motor", "Outpace")
    return out
}

/** Evento en directo que se emite en este canal (según la agenda), para la información del reproductor. */
fun liveEventFor(ch: TvChannel, agenda: com.mivisor.tv.model.Agenda?, library: com.mivisor.tv.catalog.tv.TvLibrary): com.mivisor.tv.model.AgendaEvent? {
    val now = System.currentTimeMillis()
    return agenda?.events.orEmpty().filter { it.status(now) == com.mivisor.tv.model.EventStatus.LIVE }
        .firstOrNull { ev -> channelsFor(ev, library).any { it.id == ch.id } }
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
