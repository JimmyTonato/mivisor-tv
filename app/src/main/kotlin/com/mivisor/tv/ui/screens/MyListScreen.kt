package com.mivisor.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.ago
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvRow
import com.mivisor.tv.ui.design.SectionHeader

/** Mi lista: favoritos e historial (solo en esta tele). */
@Composable
fun MyListScreen(graph: AppGraph, nav: Nav) {
    val mine by graph.mylist.state.collectAsState()
    val lib by graph.providers.state.collectAsState()
    val first = remember { FocusRequester() }
    val favorites = mine.favorites.mapNotNull { lib.library.channel(it.first) }

    if (favorites.isEmpty() && mine.history.isEmpty()) {
        EmptyState(
            "Mi lista está vacía",
            "Mantén pulsado OK sobre un canal para añadirlo a Mi lista. Lo que veas aparecerá aquí, en el historial."
        ) { MvButton("Ir a Canales", { nav.tab(Route.Channels) }, icon = MvIcons.Grid, primary = true, focusRequester = first) }
        InitialFocus(first)
        return
    }

    Column(Modifier.fillMaxSize()) {
        ChannelRow("Favoritos", favorites, onOpen = { nav.go(Route.Channel(it.id)) }, onLong = { graph.mylist.toggleFavorite(it.id, it.name) })
        Row(Modifier.padding(horizontal = MvDims.SafeX)) {
            SectionHeader("Historial", detail = "${mine.history.size}")
        }
        LazyColumn(
            contentPadding = PaddingValues(start = MvDims.SafeX, end = MvDims.SafeX, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(mine.history, key = { it.channelId }) { h ->
                val ch = lib.library.channel(h.channelId)
                MvRow(
                    title = h.name,
                    subtitle = "Visto ${ago(h.playedAt)} · ${h.seconds / 60} min" + if (ch == null) " · ya no está en tus proveedores" else "",
                    icon = MvIcons.History,
                    enabled = ch != null,
                    onClick = { ch?.let { nav.go(Route.Player(it.id)) } },
                    focusRequester = if (favorites.isEmpty() && h == mine.history.first()) first else null
                )
            }
            if (mine.history.isNotEmpty()) item {
                Row(Modifier.padding(top = 12.dp)) { MvButton("Borrar historial", { graph.mylist.clearHistory() }, icon = MvIcons.Delete) }
            }
        }
    }
    if (favorites.isEmpty()) InitialFocus(first)
}
