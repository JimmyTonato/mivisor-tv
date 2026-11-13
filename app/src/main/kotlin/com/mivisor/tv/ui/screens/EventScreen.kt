package com.mivisor.tv.ui.screens

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.aspectRatio
import com.mivisor.tv.ui.BannerBackground
import com.mivisor.tv.ui.rememberBanner
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.catalog.TvChannelsRepository
import com.mivisor.tv.catalog.logos.LogoCatalog
import com.mivisor.tv.catalog.logos.LogoEntry
import com.mivisor.tv.catalog.logos.LogoSource
import com.mivisor.tv.catalog.tv.ChannelPlates
import com.mivisor.tv.catalog.tv.TvChannel
import com.mivisor.tv.catalog.tv.WatchOption
import com.mivisor.tv.catalog.tv.WhereToWatch
import com.mivisor.tv.model.AgendaEvent
import com.mivisor.tv.model.Competition
import com.mivisor.tv.model.EventStatus
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.RemoteImage
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.dayLabel
import com.mivisor.tv.ui.design.Crest
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.LogoPlate
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvPalette
import com.mivisor.tv.ui.design.MvTag
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.Spinner
import com.mivisor.tv.ui.design.StatusLine
import com.mivisor.tv.ui.design.TvClickable
import com.mivisor.tv.ui.hhmm

/** Lo que se sabe de los canales de un evento: los de agenda.json o, si no trae, los de TheSportsDB. */
private class Announced(val options: List<WatchOption>, val loading: Boolean, val failed: Boolean)

/**
 * Ficha de un evento (3c): cabecera con escudos o retratos, competición y hora; «Dónde verlo» con TODOS los canales
 * anunciados (primero los que puedes ver, de más a menos fiable; después los que no están en tus proveedores);
 * «Ver ahora» y «Recordarme».
 */
@Composable
fun EventScreen(graph: AppGraph, nav: Nav, route: Route.Event, toaster: Toaster) {
    val agenda by graph.agenda.state.collectAsState()
    val lib by graph.providers.state.collectAsState()
    val reminders by graph.reminders.list.collectAsState()
    val mylist by graph.mylist.state.collectAsState()
    val first = remember { FocusRequester() }
    val event = agenda.agenda?.events?.firstOrNull { it.id == route.id }
    if (event == null) {
        EmptyState("Evento no disponible", "Este evento ya no está en la agenda (puede que haya terminado o cambiado de hora).") {
            MvButton("Volver", { nav.back() }, focusRequester = first)
        }
        InitialFocus(first)
        return
    }
    val library = lib.library
    val rank: (TvChannel) -> Double = { ch -> graph.sources.bestScore(ch.sources.map { it.contentId }) }
    val announced by produceState(
        Announced(WhereToWatch.build(graph.tvChannels.known(event).orEmpty(), library, rank), graph.tvChannels.known(event) == null, false),
        event.id, library
    ) {
        val known = graph.tvChannels.known(event)
        if (known != null) {
            value = Announced(WhereToWatch.build(known, library, rank), false, false)
        } else {
            val result = graph.tvChannels.channelsFor(event)
            // Logos de TheSportsDB: se guardan para los canales de tus proveedores (última fuente de logos).
            graph.providers.harvestLogos(result.channels.mapNotNull { c ->
                c.logo?.takeIf { LogoCatalog.usable(it) }?.let { LogoEntry(c.name, it, LogoSource.THESPORTSDB) }
            })
            value = Announced(WhereToWatch.build(result.channels, library, rank), false, result.failed)
        }
    }
    val now = System.currentTimeMillis()
    val status = event.status(now)
    val best = WhereToWatch.best(announced.options)
    val reminded = reminders.any { it.eventId == event.id }

    val home = event.home ?: event.title
    val (h1, _) = MvPalette.pair(home)
    val (a1, _) = MvPalette.pair(event.away ?: event.sport)
    val mine = announced.options.flatMap { it.yours }.distinctBy { it.id }

    Box(Modifier.fillMaxSize()) {
        // Fondo del diseño: los colores de los dos equipos, de izquierda a derecha, fundidos con el fondo.
        // 3j · Banner de la competición (si lo hay); si no, los colores de los dos equipos.
        // 3l · A todo el ancho como antes, pero «alejado»: el banner se ve ENTERO (16:9, sin recortar arriba ni abajo),
        // así el logo sale completo y no parece ampliado. Por debajo se funde con el fondo (ahí van «Dónde verlo» y la
        // lista). Mismo cálculo en tele y tablet, y decodificado al ancho real de la pantalla.
        val screenPx = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics.widthPixels.coerceIn(1280, 2560)
        val banner = rememberBanner(event.sport, event.competition, screenPx)
        val heroBox = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        if (!BannerBackground(banner, heroBox)) Box(
            heroBox.background(Brush.horizontalGradient(listOf(MatchGlow.Home.copy(alpha = 0.22f), Color.Transparent, MatchGlow.Away.copy(alpha = 0.30f))))
        )
        Box(heroBox.background(Brush.verticalGradient(0.5f to Color.Transparent, 0.82f to MvColors.Bg.copy(alpha = 0.85f), 1f to MvColors.Bg)))
        // 4n · La ficha empieza SIEMPRE arriba del todo (marcador entero) y, al volver con el foco a sus botones, se
        // vuelve arriba: antes la lista se quedaba desplazada y el marcador salía cortado por arriba.
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val listScope = androidx.compose.runtime.rememberCoroutineScope()
        LaunchedEffect(event.id) {
            kotlinx.coroutines.delay(150)
            listState.scrollToItem(0)
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = MvDims.SafeX, end = MvDims.SafeX, bottom = 48.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp)) {
                    // 4k · Partido: el marcador del diseño (escudos en sus aros, VS en el centro, nombres debajo).
                    if (event.away != null) MatchBoard(event, status, best?.name, mine.size)
                    else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusTag(event, status)
                            Spacer(Modifier.width(10.dp))
                            event.competitionLogo?.let {
                                LogoPlate(Modifier.size(20.dp)) { RemoteImage(it, 20.dp, Modifier.fillMaxSize()) {} }
                                Spacer(Modifier.width(8.dp))
                            }
                            MvText((event.competition ?: sportName(event.sport)).uppercase(), MvType.Eyebrow.copy(color = MvColors.Text2, letterSpacing = 2.sp), maxLines = 1)
                        }
                        Spacer(Modifier.height(10.dp))
                        MvText(event.title, MvType.Display.copy(fontWeight = FontWeight.Black), maxLines = 2)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.onFocusChanged { f ->
                            if (f.hasFocus && (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0))
                                listScope.launch { listState.animateScrollToItem(0) }
                        }
                    ) {
                        if (best != null) MvButton("Ver en ${best.name}", { nav.go(Route.Player(best.id)) }, icon = MvIcons.Play, primary = true, focusRequester = first)
                        if (status != EventStatus.FINISHED) MvButton(
                            if (reminded) "Recordatorio puesto" else "Recordarme",
                            {
                                val on = graph.reminders.toggle(event)
                                toaster.show(if (on) "Te avisaré 5 minutos antes (con MiVisor TV abierta)" else "Recordatorio quitado")
                            },
                            icon = MvIcons.Clock, focusRequester = if (best == null) first else null
                        )
                        MvButton(competitionLabel(event), { nav.go(Route.Competition(competitionRoute(event))) }, icon = MvIcons.Trophy)
                    }
                    if (reminded) {
                        Spacer(Modifier.height(8.dp))
                        MvText("El aviso sale con MiVisor TV abierta. Si está cerrada a esa hora, te avisará al abrirla.", MvType.Caption)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 12.dp)) {
                    MvText("Dónde verlo", MvType.Title)
                    Spacer(Modifier.width(12.dp))
                    MvText(
                        if (mine.isEmpty()) "ordenados por fiabilidad"
                        else "${mine.size} ${if (mine.size == 1) "canal" else "canales"} para verlo", MvType.Small
                    )
                }
            }
            when {
                announced.loading -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spinner(24.dp); Spacer(Modifier.width(12.dp)); MvText("Buscando en qué canales se emite…", MvType.Body)
                    }
                }
                announced.options.isEmpty() -> item {
                    MvText(
                        if (announced.failed) "No se pudo consultar en qué canales se emite. Vuelve a abrir el evento más tarde."
                        else "La agenda no anuncia canales para este evento.", MvType.Body, color = MvColors.Text2
                    )
                }
            }
            // 3i · Tarjetas: los tuyos de más a menos fiable (el primero, «Recomendado · arranca en ~N s») y después los
            // anunciados que no están en tus proveedores, para saber dónde lo echan.
            val missing = announced.options.filter { !it.available }
            if (mine.isNotEmpty() || missing.isNotEmpty()) item {
                val prewarm = com.mivisor.tv.ui.LocalPrewarm.current
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 2.dp, bottom = 22.dp)) {
                    itemsIndexed(mine, key = { _, c -> c.id }) { i, c ->
                        WatchCard(c, i == 0, mylist.isFavorite(c.id), graph, onFocus = { prewarm(c) }) { nav.go(Route.Player(c.id)) }
                    }
                    items(missing, key = { "x|" + it.announced.name + "|" + it.announced.note.orEmpty() }) { opt -> MissingCard(opt) }
                }
            }
            if (announced.options.isNotEmpty()) item {
                MvText("Todos los canales anunciados", MvType.Heading, modifier = Modifier.padding(bottom = 10.dp))
            }
            items(announced.options, key = { it.announced.name + "|" + it.announced.note.orEmpty() }) { opt ->
                Box(Modifier.padding(bottom = 10.dp)) { WatchRow(opt, graph, onPlay = { nav.go(Route.Player(it.id)) }) }
            }
        }
    }
    InitialFocus(first, event.id)
}

/** Etiqueta de estado: EN DIRECTO, día y hora, o terminado. */
@Composable
private fun StatusTag(event: AgendaEvent, status: EventStatus) {
    when (status) {
        EventStatus.LIVE -> MvTag("● EN DIRECTO", MvColors.Live, filled = true)
        EventStatus.UPCOMING -> MvTag(
            if (event.timeTbd) "${dayLabel(event.startMillis)} · hora por confirmar".uppercase()
            else "${dayLabel(event.startMillis)} · ${hhmm(event.startMillis)}".uppercase(), MvColors.Soon, filled = true
        )
        EventStatus.FINISHED -> MvTag("TERMINADO", MvColors.Text3, filled = true)
    }
}

/**
 * 4k · Marcador del partido (según el diseño que pasó el usuario): tarjeta con el carmesí del local a la izquierda y el
 * azul del visitante a la derecha; arriba la competición entre líneas doradas y el estado; en el centro los escudos en
 * sus aros con el VS entre ellos; los nombres en mayúsculas con un subrayado dorado; abajo una cápsula con la
 * competición, el canal y la fecha.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
private fun MatchBoard(event: AgendaEvent, status: EventStatus, channel: String?, channels: Int) {
    val home = event.home ?: event.title
    val away = event.away ?: ""
    val shape = RoundedCornerShape(26.dp)
    val phone = com.mivisor.tv.ui.design.LocalUiForm.current == com.mivisor.tv.ui.design.UiForm.PHONE
    val ring = if (phone) 80.dp else 96.dp
    Box(
        // 4n · Más bajo (270 dp en la tele) para que la ficha quepa entera con los botones sin desplazarse, y con base
        // opaca: sobre el banner de la competición los colores se mezclaban.
        Modifier.fillMaxWidth().height(if (phone) 250.dp else 270.dp).clip(shape)
            .background(MvColors.Bg.copy(alpha = 0.9f))
            .background(Brush.horizontalGradient(listOf(MatchGlow.Home.copy(alpha = 0.30f), MvColors.Bg.copy(alpha = 0.86f), MatchGlow.Away.copy(alpha = 0.30f))))
            .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to MvColors.Bg.copy(alpha = 0.55f)))
            .border(1.dp, Brush.horizontalGradient(listOf(MatchGlow.Home.copy(alpha = 0.7f), Color(0x10FFFFFF), Color(0x10FFFFFF), MatchGlow.Away.copy(alpha = 0.7f))), shape)
    ) {
        // Arriba: competición entre líneas doradas y, debajo, el estado.
        Column(Modifier.align(Alignment.TopCenter).padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoldLine(70.dp, toRight = false, thickness = 1.dp)
                Spacer(Modifier.width(12.dp))
                event.competitionLogo?.let {
                    RemoteImage(it, 22.dp, Modifier.size(22.dp)) {}
                    Spacer(Modifier.width(10.dp))
                }
                MvText((event.competition ?: sportName(event.sport)).uppercase(), MvType.Eyebrow.copy(color = MvColors.Text, fontSize = 13.sp, letterSpacing = 3.sp), maxLines = 1)
                Spacer(Modifier.width(12.dp))
                GoldLine(70.dp, toRight = true, thickness = 1.dp)
            }
            Spacer(Modifier.height(8.dp))
            when (status) {
                EventStatus.LIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    com.mivisor.tv.ui.design.LiveDot(MvColors.Live, 8.dp)
                    Spacer(Modifier.width(8.dp))
                    MvText("EN DIRECTO", MvType.Eyebrow.copy(color = MvColors.Live, letterSpacing = 4.sp))
                }
                EventStatus.UPCOMING -> MvText(
                    (if (event.timeTbd) "${dayLabel(event.startMillis)} · hora por confirmar" else "${dayLabel(event.startMillis)} · ${hhmm(event.startMillis)}").uppercase(),
                    MvType.Eyebrow.copy(color = MvColors.Accent, letterSpacing = 4.sp), maxLines = 1
                )
                EventStatus.FINISHED -> MvText("TERMINADO", MvType.Eyebrow.copy(color = MvColors.Text3, letterSpacing = 4.sp))
            }
        }
        // Centro: local · VS · visitante.
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = if (phone) 56.dp else 62.dp), verticalAlignment = Alignment.Top) {
            BoardTeam(home, event.homeVisual, MatchGlow.Home, ring, event.isPersonVsPerson, phone, Modifier.weight(1f))
            Box(Modifier.height(ring), contentAlignment = Alignment.Center) {
                VsBadge(if (phone) 24.dp else 30.dp, lines = if (phone) 22.dp else 40.dp)
            }
            BoardTeam(away, event.awayVisual, MatchGlow.Away, ring, event.isPersonVsPerson, phone, Modifier.weight(1f))
        }
        // Abajo: cápsula con competición, canal y fecha.
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).height(32.dp)
                .background(MvColors.Bg.copy(alpha = 0.55f), RoundedCornerShape(19.dp))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(19.dp))
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val parts = listOfNotNull(
                MvIcons.Trophy to (event.competition ?: sportName(event.sport)),
                channel?.let { MvIcons.Play to (if (channels > 1) "$it · $channels canales" else it) },
                if (phone) null else MvIcons.Calendar to (if (event.timeTbd) dayLabel(event.startMillis) else "${dayLabel(event.startMillis)} · ${hhmm(event.startMillis)}")
            )
            parts.forEachIndexed { i, (icon, text) ->
                if (i > 0) Box(Modifier.padding(horizontal = 16.dp).width(1.dp).height(16.dp).background(Color(0x24FFFFFF)))
                com.mivisor.tv.ui.design.MvIcon(icon, size = 15.dp, tint = MvColors.Accent)
                Spacer(Modifier.width(8.dp))
                MvText(text.uppercase(), MvType.Caption.copy(color = Color(0xFFDDE3E8), letterSpacing = 1.sp), maxLines = 1)
            }
        }
    }
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
private fun BoardTeam(name: String, image: String?, glow: Color, ring: Dp, person: Boolean, phone: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CrestRing(name, image, glow, ring, person)
        Spacer(Modifier.height(10.dp))
        val fs = if (phone) 17.sp else 20.sp
        MvText(
            name.uppercase(),
            MvType.Display.copy(
                fontSize = fs, lineHeight = fs * 1.15f, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp,
                brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFC9D1DA))),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            ),
            maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(56.dp).height(2.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, MvColors.Accent, Color.Transparent))))
    }
}

/** Cabecera del evento: competición, escudos o retratos, título, hora o EN DIRECTO. */
@Composable
fun EventHeader(event: AgendaEvent, now: Long) {
    val home = event.home ?: event.title
    val away = event.away
    val (h1, h2) = MvPalette.pair(home)
    val (a1, a2) = MvPalette.pair(away ?: event.sport)
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(h2, h1.copy(alpha = 0.6f), a1.copy(alpha = 0.6f), a2)), RoundedCornerShape(MvDims.RadiusLarge))
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            event.competitionLogo?.let {
                LogoPlate(Modifier.size(34.dp)) { RemoteImage(it, 34.dp, Modifier.fillMaxSize()) {} }
                Spacer(Modifier.width(10.dp))
            }
            MvText((event.competition ?: sportName(event.sport)).uppercase(), MvType.Eyebrow.copy(color = Color(0xE6FFFFFF)), maxLines = 1)
        }
        Spacer(Modifier.height(18.dp))
        if (away != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Participant(home, event.homeVisual, MatchGlow.Home, event.isPersonVsPerson)
                VsBadge(26.dp, lines = 28.dp)
                Participant(away, event.awayVisual, MatchGlow.Away, event.isPersonVsPerson)
            }
            Spacer(Modifier.height(16.dp))
        }
        MvText(event.title, MvType.Title.copy(fontSize = 26.sp, fontWeight = FontWeight.Black), maxLines = 2)
        Spacer(Modifier.height(10.dp))
        when (event.status(now)) {
            EventStatus.LIVE -> MvTag("● EN DIRECTO", MvColors.Live, filled = true)
            EventStatus.UPCOMING -> MvTag(if (event.timeTbd) "${dayLabel(event.startMillis)} · hora por confirmar" else "${dayLabel(event.startMillis)} · ${hhmm(event.startMillis)}", MvColors.Soon, filled = true)
            EventStatus.FINISHED -> MvTag("Terminado", MvColors.Text3, filled = true)
        }
    }
}

@Composable
private fun Participant(name: String, image: String?, color: Color, person: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(150.dp)) {
        CrestRing(name, image, color, 100.dp, person)
        Spacer(Modifier.height(10.dp))
        MvText(name, MvType.SmallStrong, maxLines = 2)
    }
}

/** Escudo (o retrato en tenis y UFC) real; si no hay o no carga, las siglas sobre su color. */
@Composable
fun Visual(name: String, image: String?, color: Color, size: Dp, person: Boolean) {
    if (image == null) {
        Crest(ChannelPlates.crest(name), color, size)
        return
    }
    // 3k · El escudo tal cual, sin el círculo blanco detrás (se veía raro). Solo una sombra muy suave para que se
    // despegue de un banner claro. Las fotos de deportistas siguen recortadas en círculo.
    Box(
        Modifier.size(size).then(
            if (person) Modifier.background(color, CircleShape).clip(CircleShape)
            else Modifier.background(Brush.radialGradient(listOf(Color(0x59000000), Color.Transparent)), CircleShape)
        ).padding(if (person) 0.dp else size / 14),
        contentAlignment = Alignment.Center
    ) {
        RemoteImage(image, size, Modifier.fillMaxSize(), crop = person) { Crest(ChannelPlates.crest(name), color, size) }
    }
}

/** Una línea de «Dónde verlo»: el canal anunciado y, debajo, los tuyos que lo emiten (o «no está en tus proveedores»). */
@Composable
private fun WatchRow(opt: WatchOption, graph: AppGraph, onPlay: (TvChannel) -> Unit) {
    val a = opt.announced
    val detail = listOfNotNull(a.dial?.let { "Dial $it" }, a.note).joinToString(" · ")
    Column(
        Modifier.fillMaxWidth().background(MvColors.Surface1, RoundedCornerShape(MvDims.Radius)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoPlate(Modifier.size(width = 72.dp, height = 40.dp)) {
                RemoteImage(a.logo, 72.dp, Modifier.fillMaxSize()) { MvText(ChannelPlates.of(a.name, null).top, MvType.Caption.copy(color = MvColors.Bg)) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                MvText(a.name, MvType.BodyStrong, maxLines = 1)
                if (detail.isNotEmpty()) MvText(detail, MvType.Caption, maxLines = 1)
            }
            if (!opt.available) MvTag("No está en tus canales", MvColors.Text3)
        }
        opt.yours.forEach { ch ->
            Spacer(Modifier.height(8.dp))
            val (status, color) = channelStatus(ch, graph.sources)
            TvClickable(
                onClick = { onPlay(ch) }, modifier = Modifier.fillMaxWidth(), focusedScale = 1.02f,
                color = MvColors.Surface2, focusedColor = MvColors.Surface4
            ) { _ ->
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(ch, Modifier.size(width = 64.dp, height = 34.dp), 64.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        MvText(ch.name, MvType.BodyStrong, maxLines = 1)
                        StatusLine(status, color)
                    }
                    MvTag("Ver", MvColors.Accent2)
                }
            }
        }
    }
}

/** 3i · Tarjeta de «Dónde verlo»: logo, nombre y por qué este (recomendado / fuentes y proveedor). */
@Composable
private fun WatchCard(ch: TvChannel, recommended: Boolean, favorite: Boolean, graph: AppGraph, onFocus: () -> Unit, onClick: () -> Unit) {
    val (status, color) = channelStatus(ch, graph.sources)
    val start = if (recommended) graph.sources.typicalStart(ch.sources.map { it.contentId }) else null
    val n = ch.sources.size
    val providers = ch.sources.map { it.providerName }.distinct()
    val line = when {
        recommended -> if (start != null) "Recomendado · arranca en ~$start s" else "Recomendado"
        else -> "$n ${if (n == 1) "fuente" else "fuentes"} · " + (if (providers.size == 1) providers.first() else "${providers.size} proveedores")
    }
    TvClickable(
        onClick = onClick, onFocus = onFocus, modifier = Modifier.width(262.dp).height(84.dp), shape = RoundedCornerShape(MvDims.RadiusLarge),
        focusedScale = 1.04f, color = Color(0xEB132232), focusedColor = MvColors.Surface2, ringColor = MvColors.Accent2
    ) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(ch, Modifier.size(width = 84.dp, height = 48.dp), 84.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MvText(ch.name, MvType.BodyStrong, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (favorite) { Spacer(Modifier.width(6.dp)); com.mivisor.tv.ui.design.MvIcon(MvIcons.Star, size = 12.dp, tint = MvColors.Accent) }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(if (recommended) MvColors.Ok else color, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    MvText(if (recommended || color == MvColors.Ok) line else status.substringBefore(" · ") + " · " + line,
                        MvType.Caption.copy(color = if (recommended) MvColors.Ok else MvColors.Text3), maxLines = 1)
                }
            }
        }
    }
}

/** 3i · Canal anunciado que no tienes (no se puede abrir: solo informa). */
@Composable
private fun MissingCard(opt: WatchOption) {
    val a = opt.announced
    Row(
        Modifier.width(262.dp).height(84.dp).background(Color(0x99132232), RoundedCornerShape(MvDims.RadiusLarge)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LogoPlate(Modifier.size(width = 84.dp, height = 48.dp)) {
            RemoteImage(a.logo, 84.dp, Modifier.fillMaxSize()) { MvText(ChannelPlates.of(a.name, null).top, MvType.Caption.copy(color = MvColors.Bg)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            MvText(a.name, MvType.BodyStrong, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            MvText("Anunciado · no está en tus canales", MvType.Caption.copy(color = MvColors.Soon), maxLines = 1)
        }
    }
}

@Suppress("unused")
private val keepRepo: Class<*> = TvChannelsRepository::class.java


/** 3g · Si la competición fija es genérica («Más fútbol», «Tenis»…) y el evento trae la suya, se abre la real. */
private val GENERIC = setOf(Competition.FUTBOL, Competition.TENIS, Competition.BALONCESTO, Competition.MOTOR, Competition.OTHER)

fun competitionRoute(event: AgendaEvent): String {
    val fixed = Competition.of(event)
    return if (fixed in GENERIC && !event.competition.isNullOrBlank()) com.mivisor.tv.model.CompetitionNames.id(event) else fixed.name
}

fun competitionLabel(event: AgendaEvent): String {
    val fixed = Competition.of(event)
    return if (fixed in GENERIC && !event.competition.isNullOrBlank()) event.competition!!.trim() else fixed.label
}
