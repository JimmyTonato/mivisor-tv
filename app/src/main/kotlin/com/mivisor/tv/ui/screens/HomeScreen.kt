package com.mivisor.tv.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.catalog.tv.ChannelPlates
import com.mivisor.tv.catalog.tv.TvChannel
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.model.AgendaEvent
import com.mivisor.tv.model.EventStatus
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.design.Crest
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.LiveDot
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvPalette
import com.mivisor.tv.ui.design.MvPanel
import com.mivisor.tv.ui.design.MvRow
import com.mivisor.tv.ui.design.MvTag
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.SectionHeader
import com.mivisor.tv.ui.design.Spinner
import com.mivisor.tv.ui.design.TvClickable
import com.mivisor.tv.ui.hhmm

/** Inicio (como el prototipo): portada con el evento destacado, «En directo», seguir viendo, Mi lista y grupos. */
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
    // Eventos en directo o en las próximas 3 h, con los que tienen canal en tus proveedores primero.
    val events = remember(agenda.agenda, library) {
        agenda.agenda?.events.orEmpty()
            .filter { val s = it.status(now); s == EventStatus.LIVE || (s == EventStatus.UPCOMING && it.startMillis - now < 3 * 3600_000L) }
            .map { it to channelsFor(it, library) }
            .sortedWith(compareByDescending<Pair<AgendaEvent, List<TvChannel>>> { it.second.isNotEmpty() }.thenBy { it.first.startMillis })
            .take(12)
    }
    val hero = events.firstOrNull { it.second.isNotEmpty() }
    val favorites = mine.favorites.map { it.first }.toSet()
    val recent = mine.history.mapNotNull { library.channel(it.channelId) }.take(12)
    val favChannels = mine.favorites.mapNotNull { library.channel(it.first) }
    fun open(ev: AgendaEvent, chs: List<TvChannel>) {
        when (chs.size) {
            0 -> toaster.show("Ninguno de tus proveedores tiene un canal de este evento")
            1 -> nav.go(Route.Player(chs.first().id))
            else -> picking = ev
        }
    }
    val toggleFav: (TvChannel) -> Unit = { ch ->
        graph.mylist.toggleFavorite(ch.id, ch.name)
        toaster.show(if (ch.id in favorites) "Quitado de Mi lista" else "Añadido a Mi lista")
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        if (lib.offline) item { Banner("Sin conexión: se muestran los canales guardados en esta tele.") }
        lib.providers.filter { it.error != null }.takeIf { it.isNotEmpty() }?.let { bad ->
            item { Banner("No se pudo actualizar: ${bad.joinToString { it.name }}. Se usa la copia guardada.") }
        }
        if (hero != null) item {
            Hero(hero.first, hero.second, now, favorites, first,
                onWatch = { nav.go(Route.Player(hero.second.first().id)) },
                onOptions = { nav.go(Route.Event(hero.first.id)) },
                onFavorite = { toggleFav(hero.second.first()) })
        }
        if (events.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                val live = events.count { it.first.status(now) == EventStatus.LIVE }
                SectionHeader(if (live > 0) "En directo" else "Próximamente", Modifier.padding(start = MvDims.SafeX), "${events.size} eventos")
                LazyRow(contentPadding = PaddingValues(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    itemsIndexed(events, key = { _, e -> e.first.id }) { i, (ev, chs) ->
                        EventTile(ev, chs, now, onClick = { nav.go(Route.Event(ev.id)) }, focusRequester = if (hero == null && i == 0) first else null)
                    }
                }
            }
        }
        item { ChannelRow("Seguir viendo", recent, graph.sources, favorites, onOpen = { nav.go(Route.Player(it.id)) }, onLong = toggleFav) }
        item { ChannelRow("Mi lista", favChannels, graph.sources, favorites, onOpen = { nav.go(Route.Player(it.id)) }, onLong = toggleFav) }
        itemsIndexed(library.brandSections.take(6), key = { i, sec -> "sec-$i-${sec.key}" }) { _, sec ->
            ChannelRow(sec.name, sec.channels.take(16), graph.sources, favorites, onOpen = { nav.go(Route.Channel(it.id)) }, onLong = toggleFav, detail = "${sec.channels.size} canales")
        }
        item {
            Row(Modifier.padding(horizontal = MvDims.SafeX, vertical = 8.dp)) {
                MvButton("Ver todos los canales", { nav.tab(Route.Channels) }, icon = MvIcons.Grid,
                    focusRequester = if (hero == null && events.isEmpty() && recent.isEmpty()) first else null)
            }
        }
    }
    InitialFocus(first)

    picking?.let { ev -> EventChannelsDialog(ev, channelsFor(ev, library), onPick = { picking = null; nav.go(Route.Player(it.id)) }, onClose = { picking = null }) }
}

/** Portada: evento destacado con fondo de los colores de los equipos, escudos y «Ver ahora». */
@Composable
private fun Hero(
    ev: AgendaEvent,
    chs: List<TvChannel>,
    now: Long,
    favorites: Set<String>,
    first: FocusRequester,
    onWatch: () -> Unit,
    onOptions: () -> Unit,
    onFavorite: () -> Unit
) {
    val home = ev.home ?: ev.title
    val away = ev.away
    val (h1, _) = MvPalette.pair(home)
    val (a1, _) = MvPalette.pair(away ?: ev.sport)
    val live = ev.status(now) == EventStatus.LIVE
    val main = chs.first()
    Box(
        Modifier.fillMaxWidth().height(330.dp)
            .background(Brush.horizontalGradient(listOf(MvColors.Bg, h1.copy(alpha = 0.35f), a1.copy(alpha = 0.55f))))
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MvColors.Bg))))
        if (away != null) Row(
            Modifier.align(Alignment.CenterEnd).padding(end = MvDims.SafeX * 2), verticalAlignment = Alignment.CenterVertically
        ) {
            Crest(ChannelPlates.crest(home), h1, 120.dp)
            MvText("vs", MvType.Heading.copy(color = MvColors.Text3), modifier = Modifier.padding(horizontal = 28.dp))
            Crest(ChannelPlates.crest(away), a1, 120.dp)
        }
        Column(Modifier.padding(start = MvDims.SafeX, top = 24.dp).widthIn(max = 560.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) MvTag("● EN DIRECTO", MvColors.Live, filled = true)
                else MvTag(if (ev.timeTbd) "HORA POR CONFIRMAR" else "HOY · ${hhmm(ev.startMillis)}", MvColors.Soon, filled = true)
                Spacer(Modifier.width(12.dp))
                MvText((ev.competition ?: sportName(ev.sport)).uppercase(), MvType.Eyebrow.copy(color = MvColors.Text2), maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            MvText(if (away != null) "$home\nvs $away" else ev.title, MvType.Display.copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black), maxLines = 3)
            Spacer(Modifier.height(10.dp))
            MvText(main.name + " · " + main.subtitle(), MvType.Body.copy(color = MvColors.Text2), maxLines = 1)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MvButton("Ver ahora", onWatch, icon = MvIcons.Play, primary = true, focusRequester = first)
                MvButton("Dónde verlo", onOptions, icon = MvIcons.Layers)
                MvButton(if (main.id in favorites) "En favoritos" else "Favorito", onFavorite, icon = MvIcons.Star)
            }
        }
    }
}

/** Tarjeta de evento de «En directo»: fondo de los dos equipos, escudos, competición y canal. */
@Composable
private fun EventTile(ev: AgendaEvent, chs: List<TvChannel>, now: Long, onClick: () -> Unit, focusRequester: FocusRequester?) {
    val home = ev.home ?: ev.title
    val away = ev.away
    val (h1, h2) = MvPalette.pair(home)
    val (a1, a2) = MvPalette.pair(away ?: ev.sport)
    val live = ev.status(now) == EventStatus.LIVE
    Column(Modifier.width(320.dp)) {
        TvClickable(
            onClick = onClick, focusRequester = focusRequester,
            modifier = Modifier.fillMaxWidth().height(180.dp), shape = RoundedCornerShape(MvDims.Radius),
            color = h2, focusedColor = h2
        ) { _ ->
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(h1, h2, a2, a1))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))))
            Box(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                if (live) MvTag("● EN DIRECTO", MvColors.Live, filled = true)
                else MvTag(if (ev.timeTbd) "POR CONFIRMAR" else hhmm(ev.startMillis), MvColors.Soon, filled = true)
            }
            if (away != null) Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Crest(ChannelPlates.crest(home), h1, 64.dp)
                MvText("vs", MvType.Small.copy(color = Color(0xCCFFFFFF)), modifier = Modifier.padding(horizontal = 16.dp))
                Crest(ChannelPlates.crest(away), a1, 64.dp)
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)) {
                MvText(ev.competition ?: sportName(ev.sport), MvType.SmallStrong, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        MvText(ev.title, MvType.BodyStrong, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(if (chs.isNotEmpty()) MvColors.Ok else MvColors.Text3, 7.dp)
            Spacer(Modifier.width(6.dp))
            MvText(
                when (chs.size) {
                    0 -> ev.channels.firstOrNull()?.name?.let { "$it · no está en tus proveedores" } ?: "Sin canal anunciado"
                    1 -> chs.first().name
                    else -> "${chs.first().name} · ${chs.size} opciones"
                }, MvType.Caption, maxLines = 1
            )
        }
    }
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
            MvText("Dónde verlo", MvType.Small)
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

@Suppress("unused")
private fun keepTypes(l: TvLibrary) = l
