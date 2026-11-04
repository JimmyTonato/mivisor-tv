package com.mivisor.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import com.mivisor.tv.ui.design.MvTag
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.catalog.tv.ChannelPlates
import com.mivisor.tv.catalog.tv.TvSection
import com.mivisor.tv.data.ExternalApps
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.GroupTile
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvPalette
import com.mivisor.tv.ui.design.MvRow
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.SectionHeader
import com.mivisor.tv.ui.design.ThemeTile

const val ALL = "all"

private fun groupDetail(sec: TvSection, graph: AppGraph): String {
    val ok = sec.channels.count { channelStatus(it, graph.sources).second == MvColors.Ok }
    return "${sec.channels.size} canales" + if (ok > 0) " · $ok funcionan" else ""
}

/** Un grupo o temática: todos sus canales en rejilla. Mantener OK sobre uno lo añade o quita de Mi lista. */
@Composable
fun GroupScreen(graph: AppGraph, nav: Nav, route: Route.Group, toaster: Toaster) {
    val lib by graph.providers.state.collectAsState()
    val mine by graph.mylist.state.collectAsState()
    val first = remember { FocusRequester() }
    val library = lib.library
    val (title, channels) = if (route.key == ALL) "Todos los canales" to library.channels
    else library.section(route.key)?.let { it.name to it.channels } ?: ("Grupo" to emptyList())
    val favorites = mine.favorites.map { it.first }.toSet()

    val agenda by graph.agenda.state.collectAsState()
    val ready = remember(channels, lib) { channels.count { channelStatus(it, graph.sources).second == MvColors.Ok } }
    var focused by remember(route.key) { mutableStateOf(channels.firstOrNull()) }

    Row(Modifier.fillMaxSize().padding(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                // Cabecera del diseño: rótulo del grupo, nombre y «6 canales · 5 listos».
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    channels.firstOrNull()?.let { ChannelLogo(it, Modifier.size(width = 100.dp, height = 50.dp), 100.dp) }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        MvText(title, MvType.Title, maxLines = 1)
                        MvText("${channels.size} canales · $ready ${if (ready == 1) "lista" else "listos"}", MvType.Body.copy(color = MvColors.Text2))
                    }
                }
            }
            if (channels.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                MvText("Este grupo ya no tiene canales.", MvType.Body, color = MvColors.Text2)
            }
            items(channels, key = { it.id }) { ch ->
                Box(Modifier.onFocusChanged { if (it.hasFocus) focused = ch }) {
                    TvChannelTile(
                        ch, graph.sources, favorite = ch.id in favorites,
                        onClick = { nav.go(Route.Player(ch.id)) },
                        onLongClick = {
                            graph.mylist.toggleFavorite(ch.id, ch.name)
                            toaster.show(if (ch.id in favorites) "Quitado de Mi lista" else "Añadido a Mi lista")
                        },
                        width = 180.dp,
                        focusRequester = if (ch == channels.first()) first else null
                    )
                }
            }
        }
        // Panel del canal enfocado (en el diseño lleva vista previa en vídeo; aquí, de momento, su información).
        focused?.let { ch ->
            val ev = liveEventFor(ch, agenda.agenda, library)
            val (status, color) = channelStatus(ch, graph.sources)
            Column(Modifier.width(256.dp).padding(top = 74.dp)) {
                Column(Modifier.fillMaxWidth().background(MvColors.Surface1, RoundedCornerShape(14.dp))) {
                    Box(
                        Modifier.fillMaxWidth().height(144.dp).background(MvColors.Surface2, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        ChannelLogo(ch, Modifier.size(width = 120.dp, height = 60.dp), 120.dp)
                        if (ev != null) Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { MvTag("● EN DIRECTO", MvColors.Live, filled = true) }
                    }
                    Column(Modifier.padding(14.dp)) {
                        MvText(ev?.title ?: ch.name, MvType.Heading, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                            Spacer(Modifier.width(6.dp))
                            MvText(status, MvType.Small, maxLines = 2)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                MvText("OK: ficha del canal · mantén OK: Mi lista", MvType.Small)
            }
        }
    }
    InitialFocus(first, route.key)
}

/** Enlaces externos de los proveedores: se abren con la app que Android tenga para cada uno. */
@Composable
fun LinksScreen(graph: AppGraph, toaster: Toaster) {
    val lib by graph.providers.state.collectAsState()
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    val links = lib.library.links
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(start = MvDims.SafeX, end = MvDims.SafeX, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                MvText("Enlaces externos", MvType.Title)
                MvText("No se reproducen en MiVisor TV: se abren con la app que tengas para cada tipo (Telegram, TVGram, magnet…).", MvType.Body.copy(color = MvColors.Text2))
            }
        }
        itemsIndexed(links, key = { _, l -> l.id }) { i, l ->
            MvRow(
                title = l.name, subtitle = "${l.kindLabel} · ${l.providerName}", icon = MvIcons.Link,
                onClick = { ExternalApps.openLink(context, l.url)?.let { toaster.show(it) } },
                focusRequester = if (i == 0) first else null
            )
        }
    }
    InitialFocus(first)
}
