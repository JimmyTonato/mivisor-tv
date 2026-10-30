package com.mivisor.tv.ui.screens

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
import androidx.compose.ui.unit.dp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.model.Agenda
import com.mivisor.tv.model.AgendaEvent
import com.mivisor.tv.model.EventStatus
import com.mivisor.tv.model.sourceNoticeText
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.ago
import com.mivisor.tv.ui.dayLabel
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.LiveDot
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvTag
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.Spinner
import com.mivisor.tv.ui.design.TvClickable
import com.mivisor.tv.ui.hhmm

private val SPORT_NAMES = mapOf(
    "futbol" to "Fútbol", "tenis" to "Tenis", "formula1" to "Fórmula 1", "motogp" to "MotoGP",
    "ufc" to "UFC", "baloncesto" to "Baloncesto", "motor" to "Motor"
)

fun sportName(id: String) = SPORT_NAMES[id] ?: id.replaceFirstChar { it.uppercase() }

/** Agenda básica (3a): directo y próximos por deporte; al elegir un evento se ve en uno de tus canales. */
@Composable
fun AgendaScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val state by graph.agenda.state.collectAsState()
    val lib by graph.providers.state.collectAsState()
    var sport by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<AgendaEvent?>(null) }
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
    val events = remember(agenda, sport) {
        agenda.events.filter { (sport == null || it.sport == sport) && it.status(now) != EventStatus.FINISHED }.sortedBy { it.startMillis }
    }
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = MvDims.SafeX),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            item { Chip("Todos", sport == null, { sport = null }, Modifier, first) }
            items(agenda.categories) { id -> Chip("${sportName(id)} · ${agenda.eventCount(id)}", sport == id, { sport = id }) }
            item {
                Chip(if (state.loading) "Actualizando…" else "Actualizada ${ago(state.fetchedAt)}", false, { graph.agenda.load(force = true) })
            }
        }
        agenda.notices(sport).forEach { (sp, status) -> Banner(sourceNoticeText(sportName(sp).lowercase(), status)) }
        if (state.offline) Banner("Sin conexión: se muestra la última agenda guardada.")
        if (events.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(MvDims.SafeX)) { MvText("No hay eventos en directo ni próximos.", MvType.Body, color = MvColors.Text2) }
        }
        LazyColumn(contentPadding = PaddingValues(start = MvDims.SafeX, end = MvDims.SafeX, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(events, key = { it.id }) { ev ->
                val chs = remember(ev, lib.library) { channelsFor(ev, lib.library) }
                EventRow(ev, now, chs.size) {
                    when (chs.size) {
                        0 -> toaster.show("Ninguno de tus proveedores tiene un canal de este evento")
                        1 -> nav.go(Route.Player(chs.first().id))
                        else -> picking = ev
                    }
                }
            }
        }
    }
    InitialFocus(first)
    picking?.let { ev -> EventChannelsDialog(ev, channelsFor(ev, lib.library), onPick = { picking = null; nav.go(Route.Player(it.id)) }, onClose = { picking = null }) }
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

@Composable
private fun EventRow(ev: AgendaEvent, now: Long, available: Int, onClick: () -> Unit) {
    TvClickable(onClick = onClick, modifier = Modifier.fillMaxWidth(), focusedScale = 1.02f, color = MvColors.Surface1, focusedColor = MvColors.Surface3) { _ ->
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(120.dp)) {
                if (ev.status(now) == EventStatus.LIVE) {
                    Row(verticalAlignment = Alignment.CenterVertically) { LiveDot(); Spacer(Modifier.width(8.dp)); MvText("DIRECTO", MvType.SmallStrong, color = MvColors.Live) }
                } else {
                    MvText(if (ev.timeTbd) "Por confirmar" else hhmm(ev.startMillis), MvType.Heading)
                    MvText(dayLabel(ev.startMillis), MvType.Caption)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                MvText(ev.title, MvType.BodyStrong, maxLines = 1)
                MvText(listOfNotNull(sportName(ev.sport), ev.competition).joinToString(" · "), MvType.Caption, maxLines = 1)
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(300.dp)) {
                MvText(ev.channels.joinToString(" · ") { it.name }.ifEmpty { "Sin canales anunciados" }, MvType.Small, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                if (available > 0) MvTag("$available en tus proveedores", MvColors.Accent2) else MvTag("No disponible", MvColors.Text3)
            }
        }
    }
}
