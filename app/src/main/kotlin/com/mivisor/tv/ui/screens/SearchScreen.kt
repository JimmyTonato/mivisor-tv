package com.mivisor.tv.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.catalog.tv.TvSearch
import com.mivisor.tv.model.EventStatus
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.PhoneInputPanel
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcon
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.TvClickable

private val KEYS = listOf(
    listOf("a", "b", "c", "d", "e", "f", "g"),
    listOf("h", "i", "j", "k", "l", "m", "n"),
    listOf("ñ", "o", "p", "q", "r", "s", "t"),
    listOf("u", "v", "w", "x", "y", "z", "+")
)

/**
 * Buscar (diseño de la fase 2): teclado en pantalla a la izquierda (sin abrir el teclado del sistema, que en muchas teles
 * tapa media pantalla) y resultados a la derecha: eventos de la agenda y tus canales. Tolera erratas.
 */
@Composable
fun SearchScreen(graph: AppGraph, nav: Nav) {
    val lib by graph.providers.state.collectAsState()
    val agenda by graph.agenda.state.collectAsState()
    val mine by graph.mylist.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }
    val q = query.trim()
    val channels = remember(q, lib.library) { if (q.length < 2) emptyList() else TvSearch.channels(q, lib.library.channels, 30) }
    val now = System.currentTimeMillis()
    val events = remember(q, agenda.agenda) {
        if (q.length < 3) emptyList()
        else agenda.agenda?.events.orEmpty()
            .filter { it.status(now) != EventStatus.FINISHED }
            .map { it to TvSearch.score(q, it.title + " " + (it.competition ?: "")) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(8).map { it.first }
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.width(358.dp)) {
                MvText("Buscar", MvType.Title, modifier = Modifier.padding(bottom = 14.dp))
                // Lo escrito, con su cursor.
                Row(
                    Modifier.fillMaxWidth().height(50.dp).background(MvColors.Surface1, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (query.isEmpty()) MvText("Canal o evento: dazn, betis, f1…", MvType.Body.copy(color = MvColors.Text3), maxLines = 1)
                    else MvText(query, MvType.Title.copy(fontSize = 22.sp), maxLines = 1)
                    Box(Modifier.padding(start = 2.dp).width(2.dp).height(24.dp).background(MvColors.Accent2))
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KEYS.forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEachIndexed { c, k ->
                                Key(k, Modifier.width(46.dp), if (r == 0 && c == 0) firstKey else null) { query += if (k == "+") "+" else k }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Key("Espacio", Modifier.width(150.dp)) { if (query.isNotEmpty() && !query.endsWith(" ")) query += " " }
                        Key("Borrar", Modifier.width(98.dp), onLong = { query = "" }) { query = query.dropLast(1) }
                        Key("Móvil", Modifier.width(98.dp), icon = MvIcons.Phone) { phone = true }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MvText("Mantén OK en «Borrar» para vaciar.", MvType.Caption)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 46.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (q.length < 2) item {
                    MvText("Escribe al menos dos letras.", MvType.Heading, color = MvColors.Text2)
                    Spacer(Modifier.height(8.dp))
                    MvText("Busca en tus canales y en la agenda. No hace falta escribirlo exacto.", MvType.Body.copy(color = MvColors.Text3))
                }
                if (q.length >= 2 && channels.isEmpty() && events.isEmpty()) item {
                    MvText("Nada para «$q»", MvType.Title)
                    Spacer(Modifier.height(8.dp))
                    MvText("Prueba con menos letras o con el nombre del canal (por ejemplo «dazn» o «m+»).", MvType.Body.copy(color = MvColors.Text2))
                }
                if (events.isNotEmpty()) {
                    item { ResultHeader("Eventos", events.size) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(end = 24.dp)) {
                            items(events, key = { "ev-" + it.id }) { ev ->
                                EventTile(ev, channelsFor(ev, lib.library), now, onClick = { nav.go(Route.Event(ev.id)) }, focusRequester = null)
                            }
                        }
                    }
                }
                if (channels.isNotEmpty()) {
                    item { ResultHeader("Canales", channels.size) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(end = 24.dp)) {
                            items(channels, key = { it.id }) { ch ->
                                TvChannelTile(ch, graph.sources, favorite = mine.isFavorite(ch.id), onClick = { nav.go(Route.Channel(ch.id)) }, width = 180.dp)
                            }
                        }
                    }
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
    InitialFocus(firstKey)
}

@Composable
private fun ResultHeader(title: String, count: Int) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
        MvText(title, MvType.Title)
        Spacer(Modifier.width(10.dp))
        MvText("$count", MvType.Body.copy(color = MvColors.Text2))
    }
}

@Composable
private fun Key(
    text: String,
    modifier: Modifier,
    fr: FocusRequester? = null,
    icon: Int? = null,
    onLong: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    TvClickable(
        onClick = onClick, onLongClick = onLong, modifier = modifier.height(42.dp), focusRequester = fr, focusedScale = 1.06f,
        shape = RoundedCornerShape(8.dp), color = MvColors.Surface1, focusedColor = MvColors.Text, ringColor = MvColors.Accent2,
        contentAlignment = Alignment.Center
    ) { focused ->
        val fg = if (focused) MvColors.Bg else MvColors.Text
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                MvIcon(icon, size = 16.dp, tint = fg)
                Spacer(Modifier.width(6.dp))
            }
            MvText(text, if (text.length == 1) MvType.Heading else MvType.BodyStrong, color = fg, maxLines = 1)
        }
    }
}
