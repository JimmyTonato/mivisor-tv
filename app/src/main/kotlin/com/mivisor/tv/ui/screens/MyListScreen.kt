package com.mivisor.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.ago
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvRow
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.TvClickable

/** Mi lista (como el prototipo): pestañas Favoritos e Historial. Solo en esta tele. */
@Composable
fun MyListScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val mine by graph.mylist.state.collectAsState()
    val lib by graph.providers.state.collectAsState()
    var tab by remember { mutableIntStateOf(if (mine.favorites.isEmpty() && mine.history.isNotEmpty()) 1 else 0) }
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
        Column(Modifier.padding(horizontal = MvDims.SafeX)) {
            MvText("Mi lista", MvType.Title)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tab("Favoritos · ${favorites.size}", tab == 0, { tab = 0 }, first)
                Tab("Historial · ${mine.history.size}", tab == 1, { tab = 1 }, null)
            }
            Spacer(Modifier.height(20.dp))
        }
        if (tab == 0) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(200.dp),
                contentPadding = PaddingValues(start = MvDims.SafeX, end = MvDims.SafeX, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (favorites.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    MvText("Aún no tienes favoritos. Mantén pulsado OK sobre un canal para añadirlo.", MvType.Body, color = MvColors.Text2)
                }
                items(favorites, key = { it.id }) { ch ->
                    TvChannelTile(
                        ch, graph.sources, favorite = true, onClick = { nav.go(Route.Player(ch.id)) },
                        onLongClick = { graph.mylist.toggleFavorite(ch.id, ch.name); toaster.show("Quitado de Mi lista") }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = MvDims.SafeX, end = MvDims.SafeX, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(mine.history, key = { _, h -> h.channelId }) { _, h ->
                    val ch = lib.library.channel(h.channelId)
                    MvRow(
                        title = h.name,
                        subtitle = "${ago(h.playedAt).replaceFirstChar { it.uppercase() }} · ${h.seconds / 60} min" + if (ch == null) " · ya no está en tus proveedores" else "",
                        icon = MvIcons.History,
                        trailing = if (ch != null) "Ver" else null,
                        enabled = ch != null,
                        onClick = { ch?.let { nav.go(Route.Player(it.id)) } }
                    )
                }
                if (mine.history.isNotEmpty()) item {
                    Row(Modifier.padding(top = 12.dp)) { MvButton("Borrar historial", { graph.mylist.clearHistory() }, icon = MvIcons.Delete) }
                }
            }
        }
    }
    InitialFocus(first)
}

@Composable
private fun Tab(text: String, selected: Boolean, onClick: () -> Unit, fr: FocusRequester?) {
    TvClickable(
        onClick = onClick, onFocus = onClick, shape = RoundedCornerShape(20.dp), focusRequester = fr, focusedScale = 1.04f,
        color = if (selected) MvColors.Text else MvColors.Surface2, focusedColor = MvColors.Accent2
    ) { focused ->
        MvText(text, MvType.SmallStrong, color = if (selected || focused) MvColors.Bg else MvColors.Text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp), maxLines = 1)
    }
}
