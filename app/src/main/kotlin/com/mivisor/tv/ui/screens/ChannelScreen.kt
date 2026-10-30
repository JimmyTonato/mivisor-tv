package com.mivisor.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.reliability.SourceState
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvRow
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType

/** Ficha de un canal: ver ahora, Mi lista y sus fuentes con lo que la app ha aprendido de cada una. */
@Composable
fun ChannelScreen(graph: AppGraph, nav: Nav, route: Route.Channel, toaster: Toaster) {
    val lib by graph.providers.state.collectAsState()
    val mine by graph.mylist.state.collectAsState()
    val play = remember { FocusRequester() }
    var refresh by remember { mutableIntStateOf(0) }
    val ch = lib.library.channel(route.id)
    if (ch == null) {
        EmptyState("Canal no disponible", "Este canal ya no está en tus proveedores (puede que lo hayan quitado o renombrado).") {
            MvButton("Volver", { nav.back() }, focusRequester = play)
        }
        InitialFocus(play)
        return
    }
    val states = remember(ch, refresh) { ch.sources.associate { it.contentId to graph.sources.state(it.contentId) } }
    val fav = mine.isFavorite(ch.id)

    Row(Modifier.fillMaxSize().padding(start = MvDims.SafeX, end = MvDims.SafeX)) {
        Column(Modifier.width(360.dp)) {
            Box(
                Modifier.size(width = 360.dp, height = 200.dp).background(MvColors.Surface2, RoundedCornerShape(MvDims.RadiusLarge)),
                contentAlignment = Alignment.Center
            ) { ChannelLogo(ch, Modifier.size(width = 230.dp, height = 100.dp), 230.dp) }
            Spacer(Modifier.height(20.dp))
            MvText(ch.name, MvType.Title, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            MvText(listOfNotNull(ch.brandName, ch.subtitle()).joinToString(" · "), MvType.Small)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MvButton("Ver ahora", { nav.go(Route.Player(ch.id)) }, icon = MvIcons.Play, primary = true, focusRequester = play)
                MvButton(if (fav) "En Mi lista" else "Mi lista", {
                    graph.mylist.toggleFavorite(ch.id, ch.name)
                    toaster.show(if (fav) "Quitado de Mi lista" else "Añadido a Mi lista")
                }, icon = MvIcons.Star)
            }
            Spacer(Modifier.height(16.dp))
            MvText("MiVisor TV prueba las fuentes en el orden que mejor ha funcionado en esta tele. Puedes elegir una a mano.", MvType.Caption)
        }
        Spacer(Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            MvText("Fuentes · ${ch.sources.size}", MvType.Heading, modifier = Modifier.padding(bottom = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 48.dp)) {
                itemsIndexed(ch.sources, key = { _, s -> s.contentId }) { i, s ->
                    val st = states[s.contentId] ?: SourceState.NEW
                    val (label, color) = stateLabel(st)
                    val resting = st == SourceState.PAUSED || st == SourceState.QUARANTINE || st == SourceState.QUARANTINE_DUE
                    MvRow(
                        title = "Fuente ${i + 1} · ${s.providerName}",
                        subtitle = listOfNotNull(s.qualityLabel, s.variantName.takeIf { it != ch.name }).joinToString(" · ").ifEmpty { null },
                        icon = MvIcons.Play,
                        trailing = label, trailingColor = color,
                        onClick = { nav.go(Route.Player(ch.id, chosen = s.contentId)) },
                        onLongClick = if (resting) ({
                            graph.sources.release(s.contentId)
                            refresh++
                            toaster.show("Fuente ${i + 1}: se vuelve a probar con normalidad")
                        }) else null
                    )
                }
            }
            MvText("Mantén pulsado OK en una fuente en pausa o en cuarentena para volver a probarla con normalidad.", MvType.Caption, color = MvColors.Text3)
        }
    }
    InitialFocus(play, route.id)
}

@Suppress("unused")
private val align = Alignment.Center
