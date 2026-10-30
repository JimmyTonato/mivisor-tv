package com.mivisor.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
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
import com.mivisor.tv.ui.design.ChannelCard
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.TvClickable

/** Canales: secciones (marcas y tipos) a la izquierda y la rejilla de la sección a la derecha. */
@Composable
fun ChannelsScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val lib by graph.providers.state.collectAsState()
    val mine by graph.mylist.state.collectAsState()
    val library = lib.library
    var selected by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }

    if (library.isEmpty) {
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

    val sections = listOf("Todos los canales" to library.channels) + library.sections.map { it.name to it.channels }
    val index = selected.coerceIn(0, sections.lastIndex)
    val (title, channels) = sections[index]

    Row(Modifier.fillMaxSize().padding(start = MvDims.SafeX, end = MvDims.SafeX)) {
        LazyColumn(Modifier.width(240.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
            itemsIndexed(sections) { i, (name, list) ->
                TvClickable(
                    onClick = { selected = i }, onFocus = { selected = i }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp), focusedScale = 1.02f,
                    color = if (i == index) MvColors.Surface2 else MvColors.Bg, focusedColor = MvColors.Surface4,
                    focusRequester = if (i == 0) first else null
                ) { _ ->
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        MvText(name, MvType.BodyStrong, modifier = Modifier.weight(1f), maxLines = 1,
                            color = if (i == index) MvColors.Text else MvColors.Text2)
                        MvText("${list.size}", MvType.Caption)
                    }
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            MvText("$title · ${channels.size}", MvType.Heading, modifier = Modifier.padding(bottom = 12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(200.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp, end = 6.dp)
            ) {
                items(channels, key = { it.id }) { ch ->
                    ChannelCard(
                        name = ch.name, initials = ch.initials, key = ch.brandKey ?: ch.id, subtitle = ch.subtitle(),
                        onClick = { nav.go(Route.Channel(ch.id)) },
                        onLongClick = {
                            graph.mylist.toggleFavorite(ch.id, ch.name)
                            toaster.show(if (mine.isFavorite(ch.id)) "Quitado de Mi lista" else "Añadido a Mi lista")
                        }
                    )
                }
            }
        }
    }
    InitialFocus(first)
}
