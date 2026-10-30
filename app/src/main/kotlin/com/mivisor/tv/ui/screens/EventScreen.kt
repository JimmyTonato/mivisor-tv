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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Row(Modifier.fillMaxSize().padding(start = MvDims.SafeX, end = MvDims.SafeX)) {
        Column(Modifier.width(470.dp)) {
            EventHeader(event, now)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (best != null) MvButton("Ver ahora", { nav.go(Route.Player(best.id)) }, icon = MvIcons.Play, primary = true, focusRequester = first)
                if (status != EventStatus.FINISHED) MvButton(
                    if (reminded) "Recordatorio puesto" else "Recordarme",
                    {
                        val on = graph.reminders.toggle(event)
                        toaster.show(if (on) "Te avisaré 5 minutos antes (con MiVisor TV abierta)" else "Recordatorio quitado")
                    },
                    icon = MvIcons.Clock, focusRequester = if (best == null) first else null
                )
            }
            Spacer(Modifier.height(12.dp))
            MvButton(Competition.of(event).label, { nav.go(Route.Competition(Competition.of(event).name)) }, icon = MvIcons.Trophy)
            if (reminded) {
                Spacer(Modifier.height(12.dp))
                MvText("El aviso sale con MiVisor TV abierta. Si está cerrada a esa hora, te avisará al abrirla.", MvType.Caption)
            }
        }
        Spacer(Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MvText("Dónde verlo", MvType.Title)
                Spacer(Modifier.width(12.dp))
                MvText("de más a menos fiable", MvType.Small)
            }
            Spacer(Modifier.height(12.dp))
            when {
                announced.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Spinner(24.dp); Spacer(Modifier.width(12.dp)); MvText("Buscando en qué canales se emite…", MvType.Body)
                }
                announced.options.isEmpty() -> MvText(
                    if (announced.failed) "No se pudo consultar en qué canales se emite. Vuelve a abrir el evento más tarde."
                    else "La agenda no anuncia canales para este evento.", MvType.Body, color = MvColors.Text2
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
                items(announced.options, key = { it.announced.name + "|" + it.announced.note.orEmpty() }) { opt ->
                    WatchRow(opt, graph, onPlay = { nav.go(Route.Player(it.id)) })
                }
            }
        }
    }
    InitialFocus(first, event.id)
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
                Participant(home, event.homeVisual, h1, event.isPersonVsPerson)
                MvText("vs", MvType.Heading.copy(color = Color(0xB3FFFFFF)))
                Participant(away, event.awayVisual, a1, event.isPersonVsPerson)
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
        Visual(name, image, color, 84.dp, person)
        Spacer(Modifier.height(8.dp))
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
    Box(
        Modifier.size(size).background(if (person) color else Color(0x26FFFFFF), CircleShape).padding(if (person) 0.dp else size / 8),
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
            if (!opt.available) MvTag("No está en tus proveedores", MvColors.Text3)
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

@Suppress("unused")
private val keepRepo: Class<*> = TvChannelsRepository::class.java
