package com.mivisor.tv.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.model.Agenda
import com.mivisor.tv.model.AgendaEvent
import com.mivisor.tv.model.Competition
import com.mivisor.tv.model.CompetitionSummaries
import com.mivisor.tv.model.CompetitionSummary
import com.mivisor.tv.model.CompetitionTemplate
import com.mivisor.tv.model.DayClock
import com.mivisor.tv.model.EventStatus
import com.mivisor.tv.model.sourceNoticeText
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.RemoteImage
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.ago
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.LiveDot
import com.mivisor.tv.ui.design.LogoPlate
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcon
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvPalette
import com.mivisor.tv.ui.design.MvTag
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.SectionHeader
import com.mivisor.tv.ui.design.Spinner
import com.mivisor.tv.ui.design.TvClickable
import com.mivisor.tv.ui.hhmm

private val SPORT_NAMES = mapOf(
    "futbol" to "Fútbol", "tenis" to "Tenis", "formula1" to "Fórmula 1", "motogp" to "MotoGP",
    "ufc" to "UFC", "baloncesto" to "Baloncesto", "motor" to "Motor"
)

fun sportName(id: String) = SPORT_NAMES[id] ?: id.replaceFirstChar { it.uppercase() }

/** Logo de la competición: el primero que traiga alguno de sus eventos. */
private fun competitionLogo(c: Competition, events: List<AgendaEvent>): String? =
    events.firstOrNull { Competition.of(it) == c && it.competitionLogo != null }?.competitionLogo

/**
 * Agenda (diseño de la fase 2, con lo de la 3c): días arriba (HOY 30, SÁB 31…), filtro por deporte, eventos del día como
 * filas «equipo contra equipo» y, a la derecha, «Dónde verlo» del evento enfocado. Las competiciones siguen debajo.
 */
@Composable
fun AgendaScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val state by graph.agenda.state.collectAsState()
    val lib by graph.providers.state.collectAsState()
    val reminders by graph.reminders.list.collectAsState()
    var sport by remember { mutableStateOf<String?>(null) }
    var day by remember { mutableIntStateOf(0) }
    var focusedEvent by remember { mutableStateOf<AgendaEvent?>(null) }
    val first = remember { FocusRequester() }
    val agenda: Agenda? = state.agenda

    if (agenda == null) {
        if (state.loading) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Spinner(); Spacer(Modifier.height(16.dp)); MvText("Descargando la agenda…")
            }
        } else {
            EmptyState("Agenda no disponible", state.error ?: "No se pudo descargar la agenda deportiva y no hay copia guardada.", icon = MvIcons.Calendar) {
                MvButton("Reintentar", { graph.agenda.load(force = true) }, icon = MvIcons.Refresh, primary = true, focusRequester = first)
            }
            InitialFocus(first)
        }
        return
    }

    val now = System.currentTimeMillis()
    val clock = remember { DayClock() }
    val summaries = remember(agenda) { CompetitionSummaries.build(agenda.events, now, clock, maxItems = 14) }
    val events = remember(agenda, sport, day) {
        agenda.events.filter {
            (sport == null || it.sport == sport) && it.status(now) != EventStatus.FINISHED &&
                clock.dayIndex(now, it.startMillis).coerceAtLeast(0) == day
        }.sortedWith(compareBy<AgendaEvent>({ it.status(now) != EventStatus.LIVE }, { it.startMillis }))
    }
    val counts = remember(agenda, sport) {
        IntArray(7) { d -> agenda.events.count { (sport == null || it.sport == sport) && it.status(now) != EventStatus.FINISHED && clock.dayIndex(now, it.startMillis).coerceAtLeast(0) == d } }
    }
    val reminded = reminders.map { it.eventId }.toSet()

    Row(Modifier.fillMaxSize().padding(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    MvText("Agenda", MvType.Title)
                    Spacer(Modifier.width(16.dp))
                    MvText((if (state.loading) "Actualizando…" else "Actualizada ${ago(state.fetchedAt)}") + if (state.offline) " · sin conexión, copia guardada" else "",
                        MvType.Small, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((0 until 7).toList()) { d ->
                        val millis = clock.startOfDay(now, d) + 12 * 3600_000L
                        DayTile(
                            top = if (d == 0) "HOY" else clock.format("EEE", millis).replace(".", "").uppercase(),
                            number = clock.format("d", millis), selected = d == day, empty = counts[d] == 0,
                            onClick = { day = d }, fr = if (d == 0) first else null
                        )
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Chip("Todo", sport == null, { sport = null }) }
                    items(agenda.categories) { id -> Chip(sportName(id), sport == id, { sport = id }) }
                    item { Chip("Actualizar", false, { graph.agenda.load(force = true); toaster.show("Actualizando la agenda…") }) }
                }
            }
            agenda.notices(sport).forEach { (sp, status) -> item { Banner(sourceNoticeText(sportName(sp).lowercase(), status), inset = false) } }
            if (events.isEmpty()) item {
                MvText(if (day == 0) "No hay eventos en directo ni próximos hoy." else "No hay eventos este día.", MvType.Body, color = MvColors.Text2,
                    modifier = Modifier.padding(vertical = 20.dp))
            }
            items(events, key = { it.id }) { ev ->
                AgendaEventRow(ev, now, channelsFor(ev, lib.library, 1).firstOrNull()?.name, ev.id in reminded,
                    onClick = { nav.go(Route.Event(ev.id)) }, onFocus = { focusedEvent = ev })
            }
            if (summaries.isNotEmpty()) item {
                SectionHeader("Competiciones", Modifier.padding(top = 20.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(summaries, key = { it.competition.name }) { sum ->
                        CompetitionTile(sum, competitionLogo(sum.competition, agenda.events), onClick = { nav.go(Route.Competition(sum.competition.name)) }, focusRequester = null)
                    }
                }
            }
        }
        WhereToWatchPanel(graph, focusedEvent?.takeIf { f -> events.any { it.id == f.id } } ?: events.firstOrNull(), reminded,
            Modifier.width(280.dp).padding(top = 150.dp))
    }
    InitialFocus(first)
}

@Composable
private fun DayTile(top: String, number: String, selected: Boolean, empty: Boolean, onClick: () -> Unit, fr: FocusRequester?) {
    TvClickable(
        onClick = onClick, onFocus = onClick, focusRequester = fr, modifier = Modifier.width(58.dp).height(58.dp),
        shape = RoundedCornerShape(10.dp), color = if (selected) MvColors.Accent else MvColors.Surface1, focusedColor = MvColors.Text,
        ringColor = MvColors.Accent2, contentAlignment = Alignment.Center
    ) { focused ->
        val fg = if (focused) MvColors.Bg else if (selected) Color.White else if (empty) MvColors.Text3 else MvColors.Text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MvText(top, MvType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 1.sp), color = if (focused || selected) fg else MvColors.Text2)
            MvText(number, MvType.Title, color = fg)
        }
    }
}

/** «DÓNDE VERLO» del evento enfocado: tus canales que lo emiten, el mejor primero. */
@Composable
private fun WhereToWatchPanel(graph: AppGraph, ev: AgendaEvent?, reminded: Set<String>, modifier: Modifier) {
    if (ev == null) return
    val lib by graph.providers.state.collectAsState()
    val mine = remember(ev.id, lib) {
        channelsFor(ev, lib.library).sortedByDescending { c -> graph.sources.bestScore(c.sources.map { it.contentId }) }.take(3)
    }
    Column(modifier.background(MvColors.Surface1, RoundedCornerShape(14.dp)).padding(18.dp)) {
        MvText("DÓNDE VERLO", MvType.Eyebrow.copy(color = MvColors.Text2, letterSpacing = 2.sp))
        Spacer(Modifier.height(16.dp))
        if (mine.isEmpty()) {
            MvText("No está en tus proveedores", MvType.Heading)
            Spacer(Modifier.height(6.dp))
            MvText(ev.channels.joinToString(" · ") { it.name }.ifEmpty { "La agenda no dice en qué canal se emite: abre la ficha para buscarlo." },
                MvType.Small, maxLines = 3)
        }
        mine.forEachIndexed { i, c ->
            val (status, color) = channelStatus(c, graph.sources)
            Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(c, Modifier.size(width = 70.dp, height = 38.dp), 70.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    MvText(c.name, MvType.BodyStrong, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                        Spacer(Modifier.width(8.dp))
                        MvText(status, MvType.Caption.copy(color = MvColors.Text2), maxLines = 2)
                    }
                }
                if (i == 0) com.mivisor.tv.ui.design.MvBadge("Mejor", MvColors.Ok)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MvColors.Surface3))
        Spacer(Modifier.height(14.dp))
        MvText("OK: ficha del evento · " + if (ev.id in reminded) "con recordatorio" else "sin recordatorio", MvType.Small)
    }
}

private fun dayTitle(clock: DayClock, now: Long, millis: Long): String = when (clock.dayIndex(now, millis)) {
    -1, 0 -> "Hoy"
    1 -> "Mañana"
    else -> clock.format("EEEE d 'de' MMMM", millis).replaceFirstChar { it.uppercase() }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, fr: FocusRequester? = null) {
    TvClickable(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp), focusRequester = fr, focusedScale = 1.04f,
        color = if (selected) MvColors.Surface4 else MvColors.Surface1, focusedColor = MvColors.Text, ringColor = MvColors.Accent2
    ) { focused ->
        MvText(text, MvType.SmallStrong, color = if (focused) MvColors.Bg else if (selected) MvColors.Text else MvColors.Text2,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), maxLines = 1)
    }
}

/** Tarjeta de competición: logo, nombre y «3 hoy · 1 en directo». */
@Composable
private fun CompetitionTile(sum: CompetitionSummary, logo: String?, onClick: () -> Unit, focusRequester: FocusRequester?) {
    val (c1, c2) = MvPalette.pair(sum.competition.name)
    TvClickable(
        onClick = onClick, focusRequester = focusRequester, modifier = Modifier.width(240.dp).height(118.dp),
        shape = RoundedCornerShape(MvDims.Radius), color = c2, focusedColor = c2
    ) { _ ->
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c1.copy(alpha = 0.75f), c2))))
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logo != null) {
                    LogoPlate(Modifier.size(36.dp)) { RemoteImage(logo, 36.dp, Modifier.fillMaxSize()) {} }
                    Spacer(Modifier.width(10.dp))
                }
                MvText(sum.competition.label, MvType.BodyStrong, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sum.live > 0) {
                    LiveDot(MvColors.Live, 7.dp)
                    Spacer(Modifier.width(6.dp))
                    MvText(sum.liveText ?: "", MvType.Caption.copy(color = Color.White), maxLines = 1)
                    if (sum.text.isNotEmpty()) MvText(" · ", MvType.Caption.copy(color = Color(0xCCFFFFFF)))
                }
                MvText(sum.text, MvType.Caption.copy(color = Color(0xD9FFFFFF)), maxLines = 1)
            }
        }
    }
}

/**
 * Fila de evento del diseño: a la izquierda DIRECTO (pastilla roja) u hora, luego los dos equipos con su escudo y, a la
 * derecha, la competición y el canal. Si no hay rival, el título. Con el foco se vuelve blanca.
 */
@Composable
fun AgendaEventRow(ev: AgendaEvent, now: Long, bestChannel: String?, reminded: Boolean, onClick: () -> Unit, onFocus: (() -> Unit)? = null) {
    val home = ev.home ?: ev.title
    val (h1, _) = MvPalette.pair(home)
    val (a1, _) = MvPalette.pair(ev.away ?: ev.sport)
    val live = ev.status(now) == EventStatus.LIVE
    TvClickable(
        onClick = onClick, onFocus = onFocus, modifier = Modifier.fillMaxWidth().height(54.dp), focusedScale = 1.02f,
        color = MvColors.Surface1, focusedColor = MvColors.Text, ringColor = MvColors.Accent2
    ) { focused ->
        val fg = if (focused) MvColors.Bg else MvColors.Text
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp)) {
                if (live) Row(
                    Modifier.background(MvColors.Live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(5.dp).background(Color.White, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(4.dp))
                    MvText("DIRECTO", MvType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color.White)
                }
                else MvText(if (ev.timeTbd) "Por conf." else hhmm(ev.startMillis), MvType.Heading, color = fg)
            }
            if (ev.away != null) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Visual(home, ev.homeVisual, h1, 32.dp, ev.isPersonVsPerson)
                    Spacer(Modifier.width(10.dp))
                    MvText(home, MvType.Heading, color = fg, maxLines = 1)
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Visual(ev.away ?: "", ev.awayVisual, a1, 32.dp, ev.isPersonVsPerson)
                    Spacer(Modifier.width(10.dp))
                    MvText(ev.away ?: "", MvType.Heading, color = fg, maxLines = 1)
                }
            } else {
                MvText(ev.title, MvType.Heading, color = fg, maxLines = 1, modifier = Modifier.weight(2f))
            }
            if (reminded) {
                MvIcon(MvIcons.Clock, size = 20.dp, tint = if (focused) MvColors.Bg else MvColors.Accent2)
                Spacer(Modifier.width(12.dp))
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 180.dp)) {
                MvText(ev.competition ?: sportName(ev.sport), MvType.Small, color = if (focused) MvColors.Bg else MvColors.Text2, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (bestChannel != null) MvColors.Ok else MvColors.Text3, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(8.dp))
                    MvText(bestChannel ?: ev.channels.firstOrNull()?.name ?: "Dónde verlo", MvType.Small, color = fg, maxLines = 1)
                }
            }
        }
    }
}

/** Una competición (3c): cabecera y sus eventos por día; en motor, las sesiones del fin de semana. */
@Composable
fun CompetitionScreen(graph: AppGraph, nav: Nav, route: Route.Competition) {
    val state by graph.agenda.state.collectAsState()
    val lib by graph.providers.state.collectAsState()
    val reminders by graph.reminders.list.collectAsState()
    val first = remember { FocusRequester() }
    val competition = Competition.byName(route.name)
    val all = state.agenda?.events.orEmpty()
    if (competition == null) {
        EmptyState("Competición no disponible", "No se encuentra esta competición en la agenda.") { MvButton("Volver", { nav.back() }, focusRequester = first) }
        InitialFocus(first)
        return
    }
    val now = System.currentTimeMillis()
    val clock = remember { DayClock() }
    val events = remember(all, competition) {
        all.filter { Competition.of(it) == competition && it.status(now) != EventStatus.FINISHED }.sortedBy { it.startMillis }
    }
    val summary = remember(all, competition) { CompetitionSummaries.summary(competition, all.filter { Competition.of(it) == competition }, now, clock) }
    val logo = competitionLogo(competition, all)
    val reminded = reminders.map { it.eventId }.toSet()
    val byDay = remember(events) { events.groupBy { clock.dayIndex(now, it.startMillis).coerceAtLeast(0) }.toSortedMap() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = MvDims.SafeX, end = MvDims.SafeX, bottom = 48.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logo != null) {
                    LogoPlate(Modifier.size(64.dp)) { RemoteImage(logo, 64.dp, Modifier.fillMaxSize()) {} }
                    Spacer(Modifier.width(16.dp))
                }
                Column {
                    MvText(competition.label, MvType.Title)
                    MvText(
                        listOfNotNull(summary.liveText, summary.text.ifEmpty { null }, "${events.size} eventos").joinToString(" · ") +
                            if (competition.template == CompetitionTemplate.RACING) " · sesiones del fin de semana" else "",
                        MvType.Body.copy(color = MvColors.Text2)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MvButton("Toda la agenda", { nav.back() }, icon = MvIcons.Calendar, focusRequester = first)
            }
        }
        if (events.isEmpty()) item {
            MvText("Sin eventos próximos en la agenda.", MvType.Body, color = MvColors.Text2, modifier = Modifier.padding(top = 20.dp))
        }
        byDay.forEach { (day, list) ->
            item(key = "day-$day") {
                MvText(dayTitle(clock, now, list.first().startMillis), MvType.Heading, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
            }
            items(list, key = { it.id }) { ev ->
                Box(Modifier.padding(vertical = 5.dp)) {
                    AgendaEventRow(ev, now, channelsFor(ev, lib.library, 1).firstOrNull()?.name, ev.id in reminded, onClick = { nav.go(Route.Event(ev.id)) })
                }
            }
        }
    }
    InitialFocus(first, route.name)
}
