package com.mivisor.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
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
import com.mivisor.tv.ui.dayLabel
import com.mivisor.tv.ui.hhmm
import com.mivisor.tv.model.ReminderPolicy
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
    val reminders by graph.reminders.list.collectAsState()
    // 0 = Favoritos, 1 = Recordatorios (3c), 2 = Historial.
    var tab by remember { mutableIntStateOf(if (mine.favorites.isEmpty()) (if (reminders.isNotEmpty()) 1 else 2) else 0) }
    val first = remember { FocusRequester() }
    // «Ordenar» del diseño: como los añadiste, A-Z o los que mejor funcionan primero.
    var order by remember { mutableIntStateOf(0) }
    val added = mine.favorites.mapNotNull { lib.library.channel(it.first) }
    val favorites = when (order) {
        1 -> added.sortedBy { it.name.lowercase() }
        2 -> added.sortedByDescending { c -> graph.sources.bestScore(c.sources.map { it.contentId }) }
        else -> added
    }

    if (favorites.isEmpty() && mine.history.isEmpty() && reminders.isEmpty()) {
        EmptyState(
            "Mi lista está vacía",
            "Mantén pulsado OK sobre un canal para añadirlo a Mi lista. Los recordatorios de la Agenda y lo que veas también aparecerán aquí.",
            icon = MvIcons.Star
        ) { MvButton("Ir a Canales", { nav.tab(Route.Channels) }, icon = MvIcons.Grid, primary = true, focusRequester = first) }
        InitialFocus(first)
        return
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = MvDims.SafeX)) {
            MvText("Mi lista", MvType.Title)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tab("Favoritos", tab == 0, { tab = 0 }, first)
                Tab("Recordatorios", tab == 1, { tab = 1 }, null)
                Tab("Historial", tab == 2, { tab = 2 }, null)
            }
            Spacer(Modifier.height(16.dp))
            if (tab == 0 && favorites.size > 1) {
                Row { MvButton("Ordenar: " + when (order) { 1 -> "A-Z"; 2 -> "los que mejor funcionan"; else -> "como los añadiste" }, { order = (order + 1) % 3 }, icon = MvIcons.Tune) }
                Spacer(Modifier.height(16.dp))
            }
        }
        if (tab == 0) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
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
        } else if (tab == 1) {
            val now = System.currentTimeMillis()
            LazyColumn(
                contentPadding = PaddingValues(start = MvDims.SafeX, end = MvDims.SafeX, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (reminders.isEmpty()) item {
                    MvText("No tienes recordatorios. En la Agenda, abre un evento y pulsa «Recordarme».", MvType.Body, color = MvColors.Text2)
                }
                itemsIndexed(reminders, key = { _, r -> r.eventId }) { _, r ->
                    MvRow(
                        title = r.title,
                        subtitle = "${dayLabel(r.startMillis).replaceFirstChar { it.uppercase() }} · ${hhmm(r.startMillis)} · ${ReminderPolicy.label(r, now)}" +
                            (r.channels.firstOrNull()?.let { " · $it" } ?: ""),
                        icon = MvIcons.Clock,
                        trailing = "Ver ficha",
                        onClick = { nav.go(Route.Event(r.eventId)) },
                        onLongClick = { graph.reminders.remove(r.eventId); toaster.show("Recordatorio quitado") }
                    )
                }
                if (reminders.isNotEmpty()) item {
                    MvText("Mantén pulsado OK para quitar un recordatorio. Avisan con MiVisor TV abierta, 5 minutos antes.", MvType.Caption,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = MvDims.SafeX, end = MvDims.SafeX, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(mine.history, key = { _, h -> h.channelId }) { _, h ->
                    val ch = lib.library.channel(h.channelId)
                    TvClickable(
                        onClick = { ch?.let { nav.go(Route.Player(it.id)) } }, enabled = ch != null, modifier = Modifier.fillMaxWidth(),
                        focusedScale = 1.02f, color = MvColors.Surface1, focusedColor = MvColors.Text, ringColor = MvColors.Accent2
                    ) { focused ->
                        val fg = if (focused) MvColors.Bg else MvColors.Text
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (ch != null) ChannelLogo(ch, Modifier.size(width = 60.dp, height = 32.dp), 60.dp)
                            else Box(Modifier.size(width = 60.dp, height = 32.dp).background(MvColors.Surface3, RoundedCornerShape(6.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                MvText(h.name, MvType.BodyStrong, color = fg, maxLines = 1)
                                MvText(
                                    "${dayAt(h.playedAt)} · ${duration(h.seconds)}" + if (ch == null) " · ya no está en tus proveedores" else "",
                                    MvType.Small, color = if (focused) MvColors.Bg.copy(alpha = 0.7f) else MvColors.Text2, maxLines = 1
                                )
                            }
                            if (ch != null) com.mivisor.tv.ui.design.MvBadge("Ver", if (focused) MvColors.Bg else MvColors.Text2)
                        }
                    }
                }
                if (mine.history.isNotEmpty()) item {
                    Row(Modifier.padding(top = 12.dp)) { MvButton("Borrar historial", { graph.mylist.clearHistory() }, icon = MvIcons.Delete) }
                }
            }
        }
    }
    InitialFocus(first)
}

/** «Hoy, 21:02», «Ayer, 19:10», «Dom, 20:55». */
private fun dayAt(t: Long): String {
    val clock = com.mivisor.tv.model.DayClock()
    val now = System.currentTimeMillis()
    val d = clock.startOfDay(t)
    val day = when {
        d == clock.startOfDay(now) -> "Hoy"
        d == clock.startOfDay(now, -1) -> "Ayer"
        now - t < 6 * 86_400_000L -> clock.format("EEE", t).replace(".", "").replaceFirstChar { it.uppercase() }
        else -> clock.format("d MMM", t).replace(".", "")
    }
    return "$day, ${hhmm(t)}"
}

private fun duration(seconds: Long): String {
    val m = seconds / 60
    return if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)} min" else "$m min"
}

@Composable
private fun Tab(text: String, selected: Boolean, onClick: () -> Unit, fr: FocusRequester?) {
    TvClickable(
        onClick = onClick, onFocus = onClick, shape = RoundedCornerShape(20.dp), focusRequester = fr, focusedScale = 1.04f,
        color = if (selected) MvColors.Surface4 else MvColors.Surface1, focusedColor = MvColors.Text, ringColor = MvColors.Accent2
    ) { focused ->
        MvText(text, MvType.BodyStrong, color = if (focused) MvColors.Bg else if (selected) MvColors.Text else MvColors.Text2,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp), maxLines = 1)
    }
}
