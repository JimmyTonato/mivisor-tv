package com.mivisor.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.mivisor.tv.catalog.tv.TvSearch
import com.mivisor.tv.model.EventStatus
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.MvTextField
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.PhoneInputPanel
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.design.ChannelCard
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.SectionHeader

/** Búsqueda tolerante a erratas en tus canales y en la agenda. */
@Composable
fun SearchScreen(graph: AppGraph, nav: Nav) {
    val lib by graph.providers.state.collectAsState()
    val agenda by graph.agenda.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val q = query.trim()
    val channels = remember(q, lib.library) { if (q.length < 2) emptyList() else TvSearch.channels(q, lib.library.channels, 30) }
    val now = System.currentTimeMillis()
    val events = remember(q, agenda.agenda) {
        if (q.length < 3) emptyList()
        else agenda.agenda?.events.orEmpty()
            .filter { it.status(now) != EventStatus.FINISHED }
            .map { it to TvSearch.score(q, it.title + " " + (it.competition ?: "")) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(6).map { it.first }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(200.dp),
            contentPadding = PaddingValues(start = MvDims.SafeX, end = MvDims.SafeX, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    MvTextField(query, { query = it }, "Buscar canal o evento", Modifier.weight(1f), placeholder = "Ej.: dazn laliga, betis, f1…", focusRequester = field)
                    Spacer(Modifier.width(16.dp))
                    MvButton("Escribir con el móvil", { phone = true }, icon = MvIcons.Phone)
                }
            }
            if (q.length >= 2 && channels.isEmpty() && events.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                MvText("Sin resultados para «$q».", MvType.Body, color = MvColors.Text2)
            }
            if (events.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("En la agenda", Modifier.padding(top = 8.dp)) }
                items(events, key = { "ev-" + it.id }, span = { GridItemSpan(maxLineSpan) }) { ev ->
                    val chs = channelsFor(ev, lib.library)
                    EventCard(ev, now, chs.size, onClick = { nav.go(Route.Event(ev.id)) }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (channels.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Canales", Modifier.padding(top = 8.dp), "${channels.size}") }
                items(channels, key = { it.id }) { ch ->
                    TvChannelTile(ch, graph.sources, favorite = false, onClick = { nav.go(Route.Channel(ch.id)) })
                }
            }
        }
        if (phone) {
            Box(Modifier.fillMaxSize().background(MvColors.Scrim), contentAlignment = Alignment.Center) {
                PhoneInputPanel(onText = { query = it.trim() }, onClose = { phone = false })
            }
            androidx.activity.compose.BackHandler { phone = false }
        }
    }
    InitialFocus(field)
}
