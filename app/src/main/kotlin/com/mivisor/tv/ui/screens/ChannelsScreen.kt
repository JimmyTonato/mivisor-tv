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

/** Canales (como el prototipo): cabecera con atajos, «Grupos» por marca y «Por temática». */
@Composable
fun ChannelsScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val lib by graph.providers.state.collectAsState()
    val mine by graph.mylist.state.collectAsState()
    val library = lib.library
    val first = remember { FocusRequester() }

    if (library.isEmpty && library.links.isEmpty()) {
        EmptyState(
            if (lib.loading) "Leyendo tus proveedores…" else "Aún no hay canales",
            if (lib.providers.isEmpty()) "Añade un proveedor en Ajustes para ver aquí sus canales." else "Tus proveedores no han devuelto canales reproducibles. Prueba a actualizarlos."
        ) {
            if (lib.providers.isEmpty()) MvButton("Añadir proveedor", { nav.go(Route.AddProvider()) }, icon = MvIcons.Add, primary = true, focusRequester = first)
            else MvButton("Actualizar proveedores", { graph.providers.refresh() }, icon = MvIcons.Refresh, primary = true, focusRequester = first)
        }
        InitialFocus(first)
        return
    }

    val working = remember(library) { library.channels.count { ch -> channelStatus(ch, graph.sources).second == MvColors.Ok } }
    val previous = graph.previousOf(null)?.let { library.channel(it) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Column(Modifier.padding(horizontal = MvDims.SafeX)) {
                MvText("Canales", MvType.Title)
                Spacer(Modifier.height(4.dp))
                MvText(
                    "${library.channels.size} canales · ${library.brandSections.size} grupos · ${library.providerCount} proveedores" +
                        (if (working > 0) " · $working funcionan" else ""),
                    MvType.Body.copy(color = MvColors.Text2)
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MvButton("Buscar canal", { nav.tab(Route.Search) }, icon = MvIcons.Search, focusRequester = first)
                    MvButton("Favoritos · ${mine.favorites.size}", { nav.tab(Route.MyList) }, icon = MvIcons.Star)
                    if (previous != null) MvButton("Canal anterior", { nav.go(Route.Player(previous.id)) }, icon = MvIcons.History)
                    MvButton("Todos los canales", { nav.go(Route.Group(ALL)) }, icon = MvIcons.Grid)
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        if (library.brandSections.isNotEmpty()) item {
            SectionHeader("Grupos", Modifier.padding(start = MvDims.SafeX))
            LazyRow(contentPadding = PaddingValues(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                itemsIndexed(library.brandSections, key = { _, s -> s.key }) { _, sec ->
                    val plate = ChannelPlates.of(sec.name, sec.key)
                    GroupTile(
                        name = sec.name, plateTop = plate.top, plateBottom = null, tone = plate.tone.ui(),
                        detail = groupDetail(sec, graph), onClick = { nav.go(Route.Group(sec.key)) }
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
        item {
            SectionHeader("Por temática", Modifier.padding(start = MvDims.SafeX))
            LazyRow(contentPadding = PaddingValues(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                itemsIndexed(library.themeSections, key = { _, s -> s.key }) { _, sec ->
                    ThemeTile(sec.name, "${sec.channels.size} canales", MvPalette.theme(sec.key), onClick = { nav.go(Route.Group(sec.key)) })
                }
                if (library.links.isNotEmpty()) item {
                    ThemeTile("Enlaces externos", "${library.links.size} enlaces", MvPalette.theme("links"), onClick = { nav.go(Route.Links) })
                }
            }
        }
    }
    InitialFocus(first)
}

/** Clave de la «sección» con todos los canales. */
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

    LazyVerticalGrid(
        columns = GridCells.Adaptive(200.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = MvDims.SafeX, end = MvDims.SafeX, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                MvText(title, MvType.Title)
                MvText("${channels.size} canales · mantén OK para añadir a Mi lista", MvType.Body.copy(color = MvColors.Text2))
            }
        }
        if (channels.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            MvText("Este grupo ya no tiene canales.", MvType.Body, color = MvColors.Text2)
        }
        items(channels, key = { it.id }) { ch ->
            TvChannelTile(
                ch, graph.sources, favorite = ch.id in favorites,
                onClick = { nav.go(Route.Channel(ch.id)) },
                onLongClick = {
                    graph.mylist.toggleFavorite(ch.id, ch.name)
                    toaster.show(if (ch.id in favorites) "Quitado de Mi lista" else "Añadido a Mi lista")
                },
                focusRequester = if (ch == channels.first()) first else null
            )
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
