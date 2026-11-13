package com.mivisor.tv.ui.screens

import androidx.compose.ui.draw.clip
import com.mivisor.tv.ui.BannerBackground
import com.mivisor.tv.ui.rememberBanner
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mivisor.tv.ui.dayLabel
import com.mivisor.tv.model.Competition
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.catalog.tv.ChannelPlates
import com.mivisor.tv.catalog.tv.TvChannel
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.model.AgendaEvent
import com.mivisor.tv.model.EventStatus
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.design.Crest
import com.mivisor.tv.ui.design.MvIcon
import com.mivisor.tv.ui.design.MvNotice
import com.mivisor.tv.ui.ago
import androidx.compose.runtime.LaunchedEffect
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.LiveDot
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvPalette
import com.mivisor.tv.ui.design.MvPanel
import com.mivisor.tv.ui.design.MvRow
import com.mivisor.tv.ui.design.MvTag
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.SectionHeader
import com.mivisor.tv.ui.design.Spinner
import com.mivisor.tv.ui.design.TvClickable
import com.mivisor.tv.ui.hhmm

/**
 * Inicio (diseño 06, 3f). En este orden:
 *  1. Portada grande: el evento destacado (en directo o el próximo con canal) o, si no hay, el último canal visto.
 *  2. «En emisión y a continuación»: directos y lo que empieza en las próximas 3 h.
 *  (3h: sin «Continuar viendo» ni «Próximas citas»; la portada son los dos partidos principales CON canal.)
 *  4. Favoritos (hasta cuatro y «Ver todos»).
 *  5. «Competiciones seguidas» (o sugeridas si aún no sigues ninguna).
 *  6. «Agenda próxima»: las siguientes citas en orden de hora.
 */
@Composable
fun HomeScreen(graph: AppGraph, nav: Nav, toaster: Toaster, backdrop: com.mivisor.tv.ui.Backdrop) {
    val lib by graph.providers.state.collectAsState()
    val agenda by graph.agenda.state.collectAsState()
    val mine by graph.mylist.state.collectAsState()
    val reminders by graph.reminders.list.collectAsState()
    val first = remember { FocusRequester() }
    val form = com.mivisor.tv.ui.design.LocalUiForm.current
    // 4j · Con la Biblioteca de MiVisor Gold no hacen falta proveedores: Inicio no pide añadir uno.
    val goldState by graph.gold.state.collectAsState()
    val withLibrary = lib.library.gold || goldState.configured

    if (lib.loadedOnce && lib.providers.isEmpty() && !withLibrary) {
        LaunchedEffect(Unit) { backdrop.colors = null }
        EmptyState(
            "Añade tu primer proveedor",
            "MiVisor TV junta los canales de tus proveedores y elige sola la fuente que mejor funciona. Necesitas al menos uno para empezar.",
            icon = MvIcons.Tv
        ) {
            MvButton("Añadir proveedor", { nav.go(Route.AddProvider()) }, icon = MvIcons.Add, primary = true, focusRequester = first)
        }
        InitialFocus(first)
        return
    }
    // 4j · Biblioteca puesta pero aún sin canales (primer arranque o sin copia): se espera a la Biblioteca.
    val waitingLibrary = withLibrary && lib.library.isEmpty && lib.providers.isEmpty() &&
        (goldState.busy || goldState.checkedAt == 0L) && goldState.error == null
    if (!lib.loadedOnce || (lib.library.isEmpty && lib.loading) || waitingLibrary) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spinner()
            Spacer(Modifier.height(16.dp))
            MvText(if (withLibrary) "Cargando la Biblioteca…" else "Leyendo tus proveedores…", MvType.Body)
        }
        return
    }
    if (withLibrary && lib.library.isEmpty && lib.providers.isEmpty()) {
        LaunchedEffect(Unit) { backdrop.colors = null }
        EmptyState(
            if (goldState.wrongPassword) "Contraseña de la Biblioteca incorrecta" else "La Biblioteca aún no tiene canales",
            goldState.error ?: goldState.note.ifBlank { "Prueba a comprobarla en Ajustes › Biblioteca." },
            icon = MvIcons.Tv
        ) {
            MvButton("Ir a la Biblioteca", { openLibrarySettings(nav) }, icon = MvIcons.Settings, primary = true, focusRequester = first)
        }
        InitialFocus(first)
        return
    }

    val library = lib.library
    // 3g · Tiempo real hasta ver Inicio con canales (Ajustes › Diagnóstico › Rendimiento).
    if (!library.isEmpty) LaunchedEffect(Unit) { com.mivisor.tv.data.PerfLog.once("Inicio con canales", "${library.channels.size} canales") }
    val now = System.currentTimeMillis()
    val all = agenda.agenda?.events.orEmpty()
    // En emisión y a continuación: directos y próximas 3 h; primero los que tienen canal en tus proveedores.
    val events = remember(agenda.agenda, library) {
        all.filter { val s = it.status(now); s == EventStatus.LIVE || (s == EventStatus.UPCOMING && it.startMillis - now < 3 * 3600_000L) }
            .map { it to channelsFor(it, library) }
            .sortedWith(compareByDescending<Pair<AgendaEvent, List<TvChannel>>> { it.second.isNotEmpty() }
                .thenByDescending { com.mivisor.tv.model.EventPriority.of(it.first) }.thenBy { it.first.startMillis })
            .take(12)
    }
    val hero = events.firstOrNull { it.second.isNotEmpty() }
    // 3g · Portada doble: los dos partidos principales (directos primero; si no, lo más importante de las próximas 24 h).
    val featured = remember(agenda.agenda, library) {
        val live = events.filter { it.first.status(now) == EventStatus.LIVE }
        val soon = all.filter { it.status(now) == EventStatus.UPCOMING && it.startMillis - now < 24 * 3600_000L }
            .sortedByDescending { com.mivisor.tv.model.EventPriority.of(it) }.take(6).map { it to channelsFor(it, library) }
            .sortedByDescending { it.second.isNotEmpty() }
        // 3h · Solo partidos que se pueden ver en tus canales: sin canal no tiene sentido destacarlos.
        (live + events + soon).filter { it.second.isNotEmpty() }.distinctBy { it.first.id }.take(2)
    }
    val heroKey = featured.firstOrNull()?.first?.id ?: hero?.first?.id
    LaunchedEffect(heroKey) {
        val top = featured.firstOrNull() ?: hero
        backdrop.colors = top?.let { (ev, _) -> MvPalette.pair(ev.home ?: ev.title).first to MvPalette.pair(ev.away ?: ev.sport).first }
    }
    val reminded = reminders.map { it.eventId }.toSet()
    val shownIds = (events + featured).map { it.first.id }.toSet()
    // Agenda próxima: lo siguiente en orden de hora (48 h), sin repetir lo de arriba.
    val agendaNext = remember(agenda.agenda, featured) {
        val used = shownIds
        all.filter { it.status(now) == EventStatus.UPCOMING && it.startMillis - now < 48 * 3600_000L && it.id !in used }
            .sortedBy { it.startMillis }.take(8)
    }
    val favorites = mine.favorites.map { it.first }.toSet()
    val history = mine.history.filter { library.channel(it.channelId) != null }.distinctBy { it.channelId }.take(3)
    val favChannels = mine.favorites.mapNotNull { library.channel(it.first) }
    // 3g · Seguidas: fijas (LALIGA…) o reales de la agenda ("raw:copa libertadores"). Sin ninguna: sugeridas.
    val seen by graph.agenda.competitions.collectAsState()
    val followed = graph.prefs.followedCompetitions.filter { Competition.byName(it) != null || com.mivisor.tv.model.CompetitionNames.isRaw(it) }
    val comps = followed.ifEmpty { Competition.PINNED.map { it.name } }
    val toggleFav: (TvChannel) -> Unit = { ch ->
        graph.mylist.toggleFavorite(ch.id, ch.name)
        toaster.show(if (ch.id in favorites) "Quitado de Mi lista" else "Añadido a Mi lista")
    }
    val toggleReminder: (AgendaEvent) -> Unit = { ev ->
        toaster.show(if (graph.reminders.toggle(ev)) "Te aviso 5 min antes de que empiece" else "Recordatorio quitado")
    }
    val pad = PaddingValues(horizontal = MvDims.SafeX)

    // 3h · Bajar con el mando con pivote (sin saltos raros al ir rápido); las filas de dentro, normales.
    com.mivisor.tv.ui.design.PivotScroll {
    // 3h.1 · Columna NORMAL (no perezosa): Inicio tiene pocas secciones. Con la perezosa, al bajar de la portada a
    // «En emisión…» se construía de golpe toda esa fila (tarjetas, degradados, escudos) y al subir se reconstruía la
    // portada: ese era el tirón. Ahora todo se construye una vez y moverse solo desplaza.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
        if (lib.offline) Column(Modifier.fillMaxWidth()) {
            MvNotice("Sin conexión a Internet.", "Ves la agenda y los canales guardados. Los canales no se pueden abrir hasta que vuelva la red.",
                Modifier.padding(pad).padding(bottom = 12.dp), MvColors.Soon)
        }
        goldState.error?.takeIf { withLibrary }?.let { why ->
            Column(Modifier.fillMaxWidth()) {
                MvNotice("Biblioteca:", why, Modifier.padding(pad).padding(bottom = 12.dp), MvColors.Soon)
            }
        }
        lib.providers.filter { it.error != null }.takeIf { it.isNotEmpty() }?.let { bad ->
            Column(Modifier.fillMaxWidth()) {
                MvNotice("No se pudo actualizar:", "${bad.joinToString { it.name }}. Se usa la copia guardada.",
                    Modifier.padding(pad).padding(bottom = 12.dp), MvColors.Soon)
            }
        }
        // 1 · Portada.
        Column(Modifier.fillMaxWidth()) {
            // 3h · OK abre la ficha del partido (quién juega y dónde verlo, como en la Agenda); mantener OK lo reproduce.
            if (featured.size == 2) DuoHero(featured, now, first, form,
                onPlay = { ev, chs -> if (chs.isNotEmpty()) nav.go(Route.Player(chs.first().id)) else nav.go(Route.Event(ev.id)) },
                onOpen = { ev -> nav.go(Route.Event(ev.id)) })
            else if (hero != null) Hero(hero.first, hero.second, now, first, graph, form,
                onWatch = { nav.go(Route.Player(hero.second.first().id)) },
                onOptions = { nav.go(Route.Event(hero.first.id)) })
            else {
                val last = history.firstOrNull()?.let { h -> library.channel(h.channelId)?.let { it to h } }
                QuietHero(last?.first, last?.second?.playedAt, first, form,
                    onWatch = { last?.first?.let { nav.go(Route.Player(it.id)) } },
                    onChannels = { nav.tab(Route.Channels) }, onAgenda = { nav.tab(Route.Agenda) })
            }
        }
        // 2 · En emisión y a continuación.
        // Sin repetir los dos de la portada.
        val rowEvents = if (featured.size == 2) events.filter { e -> featured.none { it.first.id == e.first.id } } else events
        if (rowEvents.isNotEmpty()) Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
                val live = rowEvents.count { it.first.status(now) == EventStatus.LIVE }
                SectionHeader("En emisión y a continuación", Modifier.padding(pad),
                    if (live > 0) "$live en directo · ${rowEvents.size - live} en las próximas 3 h" else "en las próximas 3 h")
                com.mivisor.tv.ui.design.DefaultScroll { LazyRow(contentPadding = pad, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    itemsIndexed(rowEvents, key = { _, e -> e.first.id }) { _, (ev, chs) ->
                        EventTile(ev, chs, now, onClick = { nav.go(Route.Event(ev.id)) }, focusRequester = null, reminded = ev.id in reminded)
                    }
                } }
            }
        }
        // 4 · Favoritos.
        if (favChannels.isNotEmpty()) Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
                SectionHeader("Favoritos", Modifier.padding(pad), "${favChannels.size} canales")
                com.mivisor.tv.ui.design.DefaultScroll { LazyRow(contentPadding = pad, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(favChannels.take(4), key = { "f-" + it.id }) { ch ->
                        TvChannelTile(ch, graph.sources, favorite = true, onClick = { nav.go(Route.Player(ch.id)) }, onLongClick = { toggleFav(ch) })
                    }
                    item(key = "f-all") { SeeAllTile("Ver todos", "Mi lista") { nav.tab(Route.MyList) } }
                } }
            }
        }
        // 5 · Competiciones seguidas.
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
                SectionHeader(if (followed.isEmpty()) "Competiciones" else "Competiciones seguidas", Modifier.padding(pad),
                    if (followed.isEmpty()) "sugeridas · ábrelas y pulsa «Seguir competición»" else "${followed.size} seguidas")
                com.mivisor.tv.ui.design.DefaultScroll { LazyRow(contentPadding = pad, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(comps, key = { "c-$it" }) { id ->
                        val fixed = Competition.byName(id)
                        val mine = remember(all, id) { all.filter { com.mivisor.tv.model.CompetitionNames.matches(id, it) } }
                        val title = fixed?.label ?: mine.firstNotNullOfOrNull { it.competition?.trim() } ?: seen.firstOrNull { it.id == id }?.name ?: "Competición"
                        val logo = remember(all, id) { if (fixed != null) competitionLogo(fixed, all) else mine.firstNotNullOfOrNull { it.competitionLogo } }
                        CompetitionCard(title, logo, mine, now, onClick = { nav.go(Route.Competition(id)) })
                    }
                } }
            }
        }
        // 6 · Agenda próxima.
        if (agendaNext.isNotEmpty()) Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(pad).padding(bottom = 18.dp)) {
                SectionHeader("Agenda próxima", Modifier, "en orden de hora")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    agendaNext.forEach { ev ->
                        AgendaLine(ev, now, channelsFor(ev, library, 1).firstOrNull()?.name, ev.id in reminded) { nav.go(Route.Event(ev.id)) }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(pad).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MvButton("Toda la agenda", { nav.tab(Route.Agenda) }, icon = MvIcons.Calendar)
                MvButton("Todos los canales", { nav.tab(Route.Channels) }, icon = MvIcons.Grid)
            }
        }
    }
    }
    InitialFocus(first)
}

/**
 * Portada: evento destacado con escudos (o retratos), competición, título, canal. Un botón principal («Ver ahora»
 * reproduce el canal más fiable) y como mucho uno secundario (diseño 06).
 */
@Composable
private fun Hero(
    ev: AgendaEvent,
    chs: List<TvChannel>,
    now: Long,
    first: FocusRequester,
    graph: AppGraph,
    form: com.mivisor.tv.ui.design.UiForm,
    onWatch: () -> Unit,
    onOptions: () -> Unit
) {
    val home = ev.home ?: ev.title
    val away = ev.away
    val (h1, _) = MvPalette.pair(home)
    val (a1, _) = MvPalette.pair(away ?: ev.sport)
    val live = ev.status(now) == EventStatus.LIVE
    val main = chs.first()
    val start = graph.sources.typicalStart(main.sources.map { it.contentId })
    val phone = form == com.mivisor.tv.ui.design.UiForm.PHONE
    val big = if (phone) 36.sp else 46.sp
    val banner = rememberBanner(ev.sport, ev.competition, 1920)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(if (phone) 250.dp else 300.dp)) {
        // 3j · Banner de la competición de fondo (si lo hay en GitHub).
        val withBanner = BannerBackground(banner)
        val showCrests = away != null && maxWidth >= 720.dp
        // 3k · Con banner, los escudos van hacia el centro (zona libre del diseño) y no tapan el logo de la derecha.
        val crestSize = if (withBanner) 104.dp else 130.dp
        if (showCrests) Row(
            // Centrados en la zona libre del banner (el logo de la competición queda a la derecha).
            if (withBanner) Modifier.align(Alignment.Center).padding(start = maxWidth * 0.12f, bottom = 20.dp)
            else Modifier.align(Alignment.CenterEnd).padding(end = MvDims.SafeX * 1.5f, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Visual(home, ev.homeVisual, h1, crestSize, ev.isPersonVsPerson)
            VsBadge(30.dp, Modifier.padding(horizontal = 18.dp))
            Visual(away!!, ev.awayVisual, a1, crestSize, ev.isPersonVsPerson)
        }
        Column(Modifier.padding(start = MvDims.SafeX, top = 18.dp, end = MvDims.SafeX).widthIn(max = if (!showCrests) 900.dp else if (withBanner) maxWidth * 0.36f else 540.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) MvTag("● EN DIRECTO", MvColors.Live, filled = true)
                else MvTag(if (ev.timeTbd) "HORA POR CONFIRMAR" else "HOY · ${hhmm(ev.startMillis)}", MvColors.Soon, filled = true)
                Spacer(Modifier.width(14.dp))
                MvText((ev.competition ?: sportName(ev.sport)).uppercase(), MvType.Eyebrow.copy(color = MvColors.Text2, fontSize = 14.sp), maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            val titleStyle = MvType.Display.copy(fontSize = big, lineHeight = big * 1.1f, fontWeight = FontWeight.Black)
            if (away != null) {
                MvText(home, titleStyle, maxLines = 1)
                MvText("vs $away", titleStyle.copy(color = MvColors.Text2), maxLines = 1)
            } else MvText(ev.title, titleStyle, maxLines = 2)
            Spacer(Modifier.height(10.dp))
            MvText(main.name + " · " + (if (start != null) "lista, arranca en ~$start s" else main.subtitle()) +
                (if (chs.size > 1) " · ${chs.size} canales" else ""),
                MvType.Heading.copy(color = MvColors.Text2, fontWeight = FontWeight.Normal), maxLines = 1)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 3h · Primero la ficha (dónde verlo y todos los canales, como en la Agenda); «Ver ahora» al lado.
                MvButton("Dónde verlo", onOptions, icon = MvIcons.Layers, primary = true, focusRequester = first)
                MvButton("Ver ahora en ${main.name}", onWatch, icon = MvIcons.Play)
            }
        }
    }
}

/**
 * 3g · Portada doble: los dos partidos principales lado a lado (solo con canal en tus proveedores). 3h: OK abre su
 * ficha (quién juega, dónde verlo, todos los canales); mantener OK lo reproduce directamente.
 */
@Composable
private fun DuoHero(
    items: List<Pair<AgendaEvent, List<TvChannel>>>,
    now: Long,
    first: FocusRequester,
    form: com.mivisor.tv.ui.design.UiForm,
    onPlay: (AgendaEvent, List<TvChannel>) -> Unit,
    onOpen: (AgendaEvent) -> Unit
) {
    val phone = form == com.mivisor.tv.ui.design.UiForm.PHONE
    Column(Modifier.fillMaxWidth().padding(horizontal = MvDims.SafeX).padding(top = 6.dp, bottom = 22.dp)) {
        MvText(if (items.any { it.first.status(now) == EventStatus.LIVE }) "PARTIDOS PRINCIPALES · AHORA" else "PARTIDOS PRINCIPALES · HOY",
            MvType.Eyebrow.copy(color = MvColors.Accent), modifier = Modifier.padding(bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items.forEachIndexed { i, (ev, chs) ->
                FeaturedMatch(ev, chs, now, phone, if (i == 0) first else null, Modifier.weight(1f), onClick = { onOpen(ev) }, onLong = { onPlay(ev, chs) })
            }
        }
    }
}

@Composable
private fun FeaturedMatch(
    ev: AgendaEvent,
    chs: List<TvChannel>,
    now: Long,
    phone: Boolean,
    fr: FocusRequester?,
    modifier: Modifier,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    val home = ev.home ?: ev.title
    val away = ev.away
    val (h1, _) = MvPalette.pair(home)
    val (a1, _) = MvPalette.pair(away ?: ev.sport)
    val live = ev.status(now) == EventStatus.LIVE
    val shape = RoundedCornerShape(18.dp)
    val prewarm = com.mivisor.tv.ui.LocalPrewarm.current
    TvClickable(
        onClick = onClick, onLongClick = onLong, focusRequester = fr, shape = shape, focusedScale = 1.02f, onFocus = { prewarm(chs.firstOrNull()) },
        color = MvColors.Surface1, focusedColor = MvColors.Surface2, ringColor = MvColors.Accent2,
        modifier = modifier.height(if (phone) 200.dp else 236.dp)
    ) { focused ->
        // 3j · Banner de la competición; si no hay, el fondo de los colores de los equipos.
        val banner = rememberBanner(ev.sport, ev.competition, 960)
        if (!BannerBackground(banner, Modifier.fillMaxSize().clip(shape)))
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(h1.copy(alpha = 0.32f), Color.Transparent, a1.copy(alpha = 0.32f))), shape))
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) MvTag("● EN DIRECTO", MvColors.Live, filled = true)
                else MvTag(if (ev.timeTbd) "POR CONFIRMAR" else "${dayWord(ev.startMillis, now).uppercase()} · ${hhmm(ev.startMillis)}", MvColors.Soon, filled = true)
                Spacer(Modifier.width(10.dp))
                MvText((ev.competition ?: sportName(ev.sport)).uppercase(), MvType.Eyebrow.copy(color = MvColors.Text2), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            // 3k · Los dos escudos juntos a la izquierda (local · visitante) y los nombres al lado: el lado derecho queda
            // libre para el logo de la competición del banner (antes el escudo visitante se le ponía encima).
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (away != null) {
                    val crest = if (phone) 40.dp else 50.dp
                    Visual(home, ev.homeVisual, h1, crest, ev.isPersonVsPerson)
                    VsBadge(if (phone) 16.dp else 18.dp, Modifier.padding(horizontal = 6.dp))
                    Visual(away, ev.awayVisual, a1, crest, ev.isPersonVsPerson)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f).padding(end = if (phone) 0.dp else 120.dp)) {
                    val big = if (phone) 21.sp else 24.sp
                    val st = MvType.Display.copy(fontSize = big, lineHeight = big * 1.12f, fontWeight = FontWeight.Black)
                    if (away != null) {
                        MvText(home, st, maxLines = 1)
                        MvText(away, st.copy(color = MvColors.Text2), maxLines = 1)
                    } else MvText(ev.title, st, maxLines = 2)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MvIcon(MvIcons.Layers, size = 16.dp, tint = if (focused) MvColors.Accent else MvColors.Text2)
                Spacer(Modifier.width(8.dp))
                MvText(
                    "Dónde verlo · " + (if (chs.size > 1) "${chs.size} canales" else chs.firstOrNull()?.name ?: "") +
                        (if (focused) "   ·   mantén OK para verlo ya" else ""),
                    MvType.BodyStrong.copy(color = if (focused) MvColors.Accent else MvColors.Text), maxLines = 1, modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Portada sin evento destacado: el último canal visto o, si no hay, la portada de la marca. */
@Composable
private fun QuietHero(
    last: TvChannel?,
    playedAt: Long?,
    first: FocusRequester,
    form: com.mivisor.tv.ui.design.UiForm,
    onWatch: () -> Unit,
    onChannels: () -> Unit,
    onAgenda: () -> Unit
) {
    val phone = form == com.mivisor.tv.ui.design.UiForm.PHONE
    Box(
        Modifier.fillMaxWidth().height(if (phone) 220.dp else 260.dp).padding(horizontal = MvDims.SafeX).padding(bottom = 20.dp)
            .background(Brush.horizontalGradient(listOf(MvColors.Surface1, MvColors.Surface2.copy(alpha = 0.6f))), RoundedCornerShape(18.dp))
            .border(1.dp, MvColors.Stroke, RoundedCornerShape(18.dp))
    ) {
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 40.dp)) {
            if (last != null) ChannelLogo(last, Modifier.size(width = 200.dp, height = 112.dp), 200.dp)
            else com.mivisor.tv.ui.design.OrbitaMark(120.dp)
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 30.dp, end = 280.dp)) {
            if (last != null) {
                MvText("SEGUIR VIENDO", MvType.Eyebrow.copy(color = MvColors.Accent))
                Spacer(Modifier.height(8.dp))
                MvText(last.name, MvType.Display.copy(fontSize = if (phone) 32.sp else 40.sp, fontWeight = FontWeight.Black), maxLines = 1)
                Spacer(Modifier.height(6.dp))
                MvText("Lo viste ${playedAt?.let { ago(it) } ?: "hace poco"} · ${last.subtitle()}", MvType.Body.copy(color = MvColors.Text2), maxLines = 1)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MvButton("Ver ahora", onWatch, icon = MvIcons.Play, primary = true, focusRequester = first)
                    MvButton("Canales", onChannels, icon = MvIcons.Grid)
                }
            } else {
                MvText("TU DEPORTE. EN UNA SOLA EXPERIENCIA.", MvType.Eyebrow.copy(color = MvColors.Accent))
                Spacer(Modifier.height(8.dp))
                MvText("Ahora mismo no hay nada destacado", MvType.Display.copy(fontSize = if (phone) 28.sp else 34.sp, fontWeight = FontWeight.Black), maxLines = 2)
                Spacer(Modifier.height(6.dp))
                MvText("Mira la agenda para ver lo próximo o entra en tus canales.", MvType.Body.copy(color = MvColors.Text2), maxLines = 2)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MvButton("Ver canales", onChannels, icon = MvIcons.Grid, primary = true, focusRequester = first)
                    MvButton("Agenda", onAgenda, icon = MvIcons.Calendar)
                }
            }
        }
    }
}

/** «Hoy», «Mañana» o el día de la semana. */
private fun dayWord(t: Long, now: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = now
    val today = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    cal.timeInMillis = t
    val that = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    cal.timeInMillis = now
    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
    val tomorrow = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    return when (that) {
        today -> "Hoy"
        tomorrow -> "Mañana"
        else -> dayLabel(t)
    }
}

/** Tarjeta «Ver todos» al final de una fila. */
@Composable
private fun SeeAllTile(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.width(150.dp)) {
        TvClickable(
            onClick = onClick, shape = RoundedCornerShape(MvDims.Radius), color = MvColors.Surface1, focusedColor = MvColors.Surface3,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f * 150f / 200f).border(1.dp, MvColors.Stroke, RoundedCornerShape(MvDims.Radius)),
            contentAlignment = Alignment.Center
        ) { _ ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MvIcon(MvIcons.ChevronRight, size = 26.dp, tint = MvColors.Accent)
                Spacer(Modifier.height(6.dp))
                MvText(title, MvType.BodyStrong)
            }
        }
        Spacer(Modifier.height(10.dp))
        MvText(detail, MvType.Caption.copy(color = MvColors.Text3))
    }
}

/** Tarjeta de competición: logo, nombre y lo próximo (o cuántos directos hay). OK abre su página. */
@Composable
private fun CompetitionCard(title: String, logo: String?, events: List<AgendaEvent>, now: Long, onClick: () -> Unit) {
    val mine = remember(events) { events.filter { it.status(now) != EventStatus.FINISHED }.sortedBy { it.startMillis } }
    val live = mine.count { it.status(now) == EventStatus.LIVE }
    val next = mine.firstOrNull { it.status(now) == EventStatus.UPCOMING }
    TvClickable(
        onClick = onClick, shape = RoundedCornerShape(14.dp), focusedScale = 1f,
        color = MvColors.Surface1, focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
        modifier = Modifier.width(250.dp).height(84.dp)
    ) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.mivisor.tv.ui.LogoImage(logo, 48.dp, Modifier.size(48.dp), RoundedCornerShape(10.dp), plain = MvColors.Surface2, padding = 0.12f) {
                MvIcon(MvIcons.Trophy, size = 22.dp, tint = MvColors.Accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                MvText(title, MvType.BodyStrong, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                when {
                    live > 0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveDot(MvColors.Live, 6.dp)
                        Spacer(Modifier.width(6.dp))
                        MvText(if (live == 1) "1 en directo" else "$live en directo", MvType.Caption.copy(color = MvColors.Live), maxLines = 1)
                    }
                    next != null -> MvText((if (next.timeTbd) dayWord(next.startMillis, now) else dayWord(next.startMillis, now) + " " + hhmm(next.startMillis)) + " · " + next.title,
                        MvType.Caption.copy(color = MvColors.Text2), maxLines = 2)
                    else -> MvText("Sin eventos en la agenda", MvType.Caption.copy(color = MvColors.Text3), maxLines = 1)
                }
            }
        }
    }
}

/** Línea de «Agenda próxima»: hora, competición, evento y canal. OK abre la ficha. */
@Composable
private fun AgendaLine(ev: AgendaEvent, now: Long, channel: String?, reminded: Boolean, onClick: () -> Unit) {
    TvClickable(
        onClick = onClick, shape = RoundedCornerShape(10.dp), focusedScale = 1f,
        color = MvColors.Surface1.copy(alpha = 0.6f), focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
        modifier = Modifier.fillMaxWidth().height(46.dp)
    ) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MvText(if (ev.timeTbd) "--:--" else hhmm(ev.startMillis), MvType.BodyStrong, modifier = Modifier.width(54.dp))
            MvText(dayWord(ev.startMillis, now), MvType.Caption.copy(color = MvColors.Text3), modifier = Modifier.width(70.dp), maxLines = 1)
            MvText((ev.competition ?: sportName(ev.sport)), MvType.Caption.copy(color = MvColors.Accent), maxLines = 1, modifier = Modifier.width(150.dp))
            MvText(ev.title, MvType.BodyStrong, maxLines = 1, modifier = Modifier.weight(1f))
            if (reminded) {
                MvIcon(MvIcons.Clock, size = 14.dp, tint = MvColors.Soon)
                Spacer(Modifier.width(8.dp))
            }
            MvText(channel ?: "—", MvType.Caption.copy(color = if (channel != null) MvColors.Text2 else MvColors.Text3), maxLines = 1,
                modifier = Modifier.widthIn(max = 180.dp))
        }
    }
}

/** Tarjeta de evento de «En directo»: fondo de los dos equipos, escudos, competición y canal. */
@Composable
fun EventTile(ev: AgendaEvent, chs: List<TvChannel>, now: Long, onClick: () -> Unit, focusRequester: FocusRequester?, reminded: Boolean = false) {
    val home = ev.home ?: ev.title
    val away = ev.away
    val (h1, h2) = MvPalette.pair(home)
    val (a1, a2) = MvPalette.pair(away ?: ev.sport)
    val live = ev.status(now) == EventStatus.LIVE
    val prewarm = com.mivisor.tv.ui.LocalPrewarm.current
    Column(Modifier.width(320.dp)) {
        TvClickable(
            onClick = onClick, focusRequester = focusRequester, onFocus = { prewarm(chs.firstOrNull()) },
            modifier = Modifier.fillMaxWidth().height(180.dp), shape = RoundedCornerShape(MvDims.Radius),
            color = h2, focusedColor = h2
        ) { _ ->
            // 3j · La tarjetita con el banner de la competición detrás (si no hay, los colores de los equipos).
            val banner = rememberBanner(ev.sport, ev.competition, 640)
            if (!BannerBackground(banner, scrim = false)) Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(h1, h2, a2, a1))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
            Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                if (live) MvTag("● EN DIRECTO", MvColors.Live, filled = true)
                else MvTag(if (ev.timeTbd) "POR CONFIRMAR" else hhmm(ev.startMillis), MvColors.Soon, filled = true)
            }
            // 3l · Escudos más a la izquierda: el logo de la competición del banner (a la derecha) queda a la vista.
            if (away != null) Row(Modifier.align(Alignment.CenterStart).padding(start = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Visual(home, ev.homeVisual, h1, 62.dp, ev.isPersonVsPerson)
                VsBadge(20.dp, Modifier.padding(horizontal = 10.dp))
                Visual(away, ev.awayVisual, a1, 64.dp, ev.isPersonVsPerson)
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MvText(ev.competition ?: sportName(ev.sport), MvType.SmallStrong, maxLines = 1, modifier = Modifier.weight(1f))
                if (reminded) {
                    MvIcon(MvIcons.Clock, size = 16.dp, tint = MvColors.Soon)
                    Spacer(Modifier.width(6.dp))
                }
                MvText(if (live) "en directo" else if (ev.timeTbd) "" else hhmm(ev.startMillis), MvType.SmallStrong)
            }
        }
        Spacer(Modifier.height(10.dp))
        MvText(ev.title, MvType.BodyStrong, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(if (chs.isNotEmpty()) MvColors.Ok else MvColors.Text3, 7.dp)
            Spacer(Modifier.width(6.dp))
            MvText(
                when (chs.size) {
                    0 -> ev.channels.firstOrNull()?.name?.let { "$it · no está en tus canales" } ?: "Sin canal anunciado"
                    1 -> chs.first().name
                    else -> "${chs.first().name} · ${chs.size} opciones"
                }, MvType.Caption, maxLines = 1
            )
        }
    }
}

/** Elegir canal para un evento con varios canales disponibles. */
@Composable
fun EventChannelsDialog(event: AgendaEvent, channels: List<TvChannel>, onPick: (TvChannel) -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(MvColors.Scrim), contentAlignment = Alignment.Center) {
        MvPanel(Modifier.fillMaxWidth(0.6f)) {
            MvText(event.title, MvType.Title)
            Spacer(Modifier.height(6.dp))
            MvText("Dónde verlo", MvType.Small)
            Spacer(Modifier.height(16.dp))
            channels.forEachIndexed { i, ch ->
                MvRow(ch.name, { onPick(ch) }, subtitle = ch.subtitle(), icon = MvIcons.Play, focusRequester = if (i == 0) first else null)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            MvButton("Cerrar", onClose, icon = MvIcons.Close)
        }
    }
    InitialFocus(first)
}

@Suppress("unused")
private fun keepTypes(l: TvLibrary) = l
