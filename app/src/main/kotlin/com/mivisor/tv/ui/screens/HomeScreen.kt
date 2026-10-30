package com.mivisor.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import com.mivisor.tv.ui.design.MvPanel
import com.mivisor.tv.ui.design.MvRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.catalog.tv.TvChannel
import com.mivisor.tv.model.AgendaEvent
import com.mivisor.tv.model.EventStatus
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.SectionHeader
import com.mivisor.tv.ui.design.Spinner
import com.mivisor.tv.ui.design.MvColors

@Composable
fun HomeScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val lib by graph.providers.state.collectAsState()
    val agenda by graph.agenda.state.collectAsState()
    val mine by graph.mylist.state.collectAsState()
    val first = remember { FocusRequester() }
    var picking by remember { mutableStateOf<AgendaEvent?>(null) }

    if (lib.loadedOnce && lib.providers.isEmpty()) {
        EmptyState(
            "Añade tu primer proveedor",
            "MiVisor TV junta los canales de tus proveedores y elige sola la fuente que mejor funciona. Necesitas al menos uno para empezar."
        ) {
            MvButton("Añadir proveedor", { nav.go(Route.AddProvider()) }, icon = MvIcons.Add, primary = true, focusRequester = first)
        }
        InitialFocus(first)
        return
    }
    if (!lib.loadedOnce || (lib.library.isEmpty && lib.loading)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Spinner()
            Spacer(Modifier.height(16.dp))
            MvText("Leyendo tus proveedores…", MvType.Body)
        }
        return
    }

    val library = lib.library
    val now = System.currentTimeMillis()
    val events = remember(agenda.agenda, library) {
        agenda.agenda?.events.orEmpty()
            .filter { val s = it.status(now); s == EventStatus.LIVE || (s == EventStatus.UPCOMING && it.startMillis - now < 3 * 3600_000L) }
            .sortedBy { it.startMillis }
            .map { it to channelsFor(it, library).size }
            .sortedByDescending { it.second > 0 }
            .take(12)
    }
    val recent = mine.history.mapNotNull { library.channel(it.channelId) }.take(12)
    val favorites = mine.favorites.mapNotNull { library.channel(it.first) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp)) {
        if (lib.offline) item { Banner("Sin conexión: se muestran los canales guardados en esta tele.") }
        lib.providers.filter { it.error != null }.takeIf { it.isNotEmpty() }?.let { bad ->
            item { Banner("No se pudo actualizar: ${bad.joinToString { it.name }}. Se usa la copia guardada.") }
        }
        if (events.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                SectionHeader("Ahora y próximamente", Modifier.padding(start = MvDims.SafeX), "Agenda deportiva")
                LazyRow(contentPadding = PaddingValues(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(events, key = { it.first.id }) { (ev, n) ->
                        EventCard(ev, now, n, onClick = {
                            val chs = channelsFor(ev, library)
                            when (chs.size) {
                                0 -> toaster.show("Ninguno de tus proveedores tiene un canal de este evento")
                                1 -> nav.go(Route.Player(chs.first().id))
                                else -> picking = ev
                            }
                        })
                    }
                }
            }
        }
        item { ChannelRow("Seguir viendo", recent, onOpen = { nav.go(Route.Player(it.id)) }) }
        item { ChannelRow("Mi lista", favorites, onOpen = { nav.go(Route.Channel(it.id)) }) }
        itemsIndexed(library.sections.take(8), key = { i, sec -> "sec-$i-${sec.key}" }) { _, sec ->
            ChannelRow(sec.name, sec.channels.take(16), onOpen = { nav.go(Route.Channel(it.id)) }, onLong = { ch ->
                graph.mylist.toggleFavorite(ch.id, ch.name)
                toaster.show(if (mine.isFavorite(ch.id)) "Quitado de Mi lista" else "Añadido a Mi lista")
            }, detail = "${sec.channels.size} canales")
        }
        item {
            Row(Modifier.padding(horizontal = MvDims.SafeX, vertical = 8.dp)) {
                MvButton("Ver todos los canales", { nav.tab(Route.Channels) }, icon = MvIcons.Grid)
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }

    picking?.let { ev -> EventChannelsDialog(ev, channelsFor(ev, library), onPick = { picking = null; nav.go(Route.Player(it.id)) }, onClose = { picking = null }) }
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
            MvText("Elige canal", MvType.Small)
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
