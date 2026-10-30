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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
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
 * Agenda (3c): competiciones arriba (con lo que tienen hoy y en directo), filtro por deporte y los eventos por día.
 * Cada evento abre su ficha con «Dónde verlo» y el recordatorio.
 */
@Composable
fun AgendaScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val state by graph.agenda.state.collectAsState()
    val lib by graph.providers.state.collectAsState()
    val reminders by graph.reminders.list.collectAsState()
    var sport by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val agenda: Agenda? = state.agenda

    if (agenda == null) {
        if (state.loading) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Spinner(); Spacer(Modifier.height(16.dp)); MvText("Descargando la agenda…")
            }
        } else {
            EmptyState("Agenda no disponible", state.error ?: "No se pudo descargar la agenda deportiva y no hay copia guardada.") {
                MvButton("Reintentar", { graph.agenda.load(force = true) }, icon = MvIcons.Refresh, primary = true, focusRequester = first)
            }
            InitialFocus(first)
        }
        return
    }

    val now = System.currentTimeMillis()
    val clock = remember { DayClock() }
    val summaries = remember(agenda) { CompetitionSummaries.build(agenda.events, now, clock, maxItems = 14) }
    val events = remember(agenda, sport) {
        agenda.events.filter { (sport == null || it.sport == sport) && it.status(now) != EventStatus.FINISHED }.sortedBy { it.startMillis }
    }
    val byDay = remember(events) { events.groupBy { clock.dayIndex(now, it.startMillis).coerceAtLeast(0) }.toSortedMap() }
    val reminded = reminders.map { it.eventId }.toSet()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Row(Modifier.padding(horizontal = MvDims.SafeX), verticalAlignment = Alignment.Bottom) {
                MvText("Agenda", MvType.Title)
                Spacer(Modifier.width(14.dp))
                MvText(
                    (if (state.loading) "Actualizando…" else "Actualizada ${ago(state.fetchedAt)}") + " · ${agenda.events.size} eventos",
                    MvType.Small, modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
        }
        if (summaries.isNotEmpty()) item {
            SectionHeader("Competiciones", Modifier.padding(start = MvDims.SafeX))
            LazyRow(contentPadding = PaddingValues(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(summaries, key = { it.competition.name }) { sum ->
                    CompetitionTile(sum, competitionLogo(sum.competition, agenda.events), onClick = { nav.go(Route.Competition(sum.competition.name)) },
                        focusRequester = if (sum == summaries.first()) first else null)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Chip("Todos", sport == null, { sport = null }, Modifier, if (summaries.isEmpty()) first else null) }
                items(agenda.categories) { id -> Chip("${sportName(id)} · ${agenda.eventCount(id)}", sport == id, { sport = id }) }
                item { Chip("Actualizar", false, { graph.agenda.load(force = true); toaster.show("Actualizando la agenda…") }) }
            }
            Spacer(Modifier.height(12.dp))
        }
        agenda.notices(sport).forEach { (sp, status) -> item { Banner(sourceNoticeText(sportName(sp).lowercase(), status)) } }
        if (state.offline) item { Banner("Sin conexión: se muestra la última agenda guardada.") }
        if (events.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(MvDims.SafeX)) { MvText("No hay eventos en directo ni próximos.", MvType.Body, color = MvColors.Text2) }
        }
        byDay.forEach { (day, list) ->
            item(key = "day-$day") {
                MvText(dayTitle(clock, now, list.first().startMillis), MvType.Heading,
                    modifier = Modifier.padding(start = MvDims.SafeX, top = 14.dp, bottom = 10.dp))
            }
            items(list, key = { it.id }) { ev ->
                Box(Modifier.padding(horizontal = MvDims.SafeX, vertical = 5.dp)) {
                    AgendaEventRow(ev, now, channelsFor(ev, lib.library).size, ev.id in reminded, onClick = { nav.go(Route.Event(ev.id)) })
                }
            }
        }
    }
    InitialFocus(first)
}

private fun dayTitle(clock: DayClock, now: Long, millis: Long): String = when (clock.dayIndex(now, millis)) {
    -1, 0 -> "Hoy"
    1 -> "Mañana"
    else -> clock.format("EEEE d 'de' MMMM", millis).replaceFirstChar { it.uppercase() }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, fr: FocusRequester? = null) {
    TvClickable(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), focusRequester = fr, focusedScale = 1.04f,
        color = if (selected) MvColors.Accent else MvColors.Surface2, focusedColor = MvColors.Text
    ) { focused ->
        MvText(text, MvType.SmallStrong, color = if (focused) MvColors.Bg else MvColors.Text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp), maxLines = 1)
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

/** Fila de evento: hora o DIRECTO, escudos pequeños, título, competición, canales y recordatorio. */
@Composable
fun AgendaEventRow(ev: AgendaEvent, now: Long, available: Int, reminded: Boolean, onClick: () -> Unit) {
    val home = ev.home ?: ev.title
    val (h1, _) = MvPalette.pair(home)
    val (a1, _) = MvPalette.pair(ev.away ?: ev.sport)
    TvClickable(onClick = onClick, modifier = Modifier.fillMaxWidth(), focusedScale = 1.02f, color = MvColors.Surface1, focusedColor = MvColors.Surface3) { _ ->
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(96.dp)) {
                if (ev.status(now) == EventStatus.LIVE) {
                    Row(verticalAlignment = Alignment.CenterVertically) { LiveDot(); Spacer(Modifier.width(6.dp)); MvText("DIRECTO", MvType.SmallStrong, color = MvColors.Live) }
                } else {
                    MvText(if (ev.timeTbd) "Por conf." else hhmm(ev.startMillis), MvType.Heading)
                }
            }
            if (ev.away != null) {
                Visual(home, ev.homeVisual, h1, 38.dp, ev.isPersonVsPerson)
                Spacer(Modifier.width(6.dp))
                Visual(ev.away ?: "", ev.awayVisual, a1, 38.dp, ev.isPersonVsPerson)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                MvText(ev.title, MvType.BodyStrong, maxLines = 1)
                MvText(listOfNotNull(sportName(ev.sport), ev.competition).distinct().joinToString(" · "), MvType.Caption, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            if (reminded) {
                MvIcon(MvIcons.Clock, size = 18.dp, tint = MvColors.Accent2)
                Spacer(Modifier.width(12.dp))
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(280.dp)) {
                MvText(ev.channels.joinToString(" · ") { it.name }.ifEmpty { "Canales: abre el evento" }, MvType.Small, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                if (available > 0) MvTag("$available en tus proveedores", MvColors.Accent2) else MvTag("Ver dónde se emite", MvColors.Text3)
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
                    AgendaEventRow(ev, now, channelsFor(ev, lib.library).size, ev.id in reminded, onClick = { nav.go(Route.Event(ev.id)) })
                }
            }
        }
    }
    InitialFocus(first, route.name)
}
