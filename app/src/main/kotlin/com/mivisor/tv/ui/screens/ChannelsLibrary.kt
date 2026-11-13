package com.mivisor.tv.ui.screens

import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.catalog.tv.ChannelFamily
import com.mivisor.tv.catalog.tv.ChannelSeriesGrouping
import com.mivisor.tv.catalog.tv.TvChannel
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.model.OnChannels
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.RemoteImage
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcon
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.TvClickable
import kotlinx.coroutines.delay

/**
 * Lo que Canales recuerda entre visitas (diseño 06: al volver del reproductor, el mismo proveedor, la misma familia y
 * el foco en el mismo canal, aunque esté muy abajo).
 */
object ChannelsMemory {
    /** 0 = Proveedores, 1 = Categorías, 2 = Favoritos. */
    var view by mutableStateOf(0)
    var provider by mutableStateOf<String?>(null)          // null = Todos
    var family by mutableStateOf<ChannelFamily?>(null)      // null = Todas
    /** 3e · Serie dentro del proveedor («M+ LaLiga» → 1, 2, 3…); null = todas, [SINGLES] = canales sueltos. */
    var series by mutableStateOf<String?>(null)
    const val SINGLES = "__sueltos"
    /** Último canal abierto desde aquí: recibe el foco al volver. */
    var lastChannel: String? = null
    /** 3i · «Solo con partido ahora»: los canales que, según la agenda, emiten algo en directo. */
    var onlyLive by mutableStateOf(false)
    /** 3k · «En línea»: solo los canales con alguna fuente que dio imagen hace poco (se comprobó y va bien). */
    var onlineOnly by mutableStateOf(false)
    var gridIndex = 0
}

/** Medidas de Canales (3f). */
private object Lib {
    val Logo = 40.dp
    /** Ancho mínimo de una tarjeta: 4 columnas en la tele, 3 en el móvil en horizontal, 5-6 en una tablet grande. */
    val TileMin = 190.dp
}

/** Una entrada de la rejilla: título de grupo (ocupa toda la fila) o canal. */
private sealed class Entry(val key: String) {
    class Header(val title: String, val count: Int, val hint: String?) : Entry("h-$title-$count")
    class Item(val channel: TvChannel) : Entry(channel.id)
}

/**
 * Canales (3f): sin columnas laterales. Arriba, el modo (Proveedores · Categorías · Favoritos) y una fila de fichas
 * con los proveedores (o las categorías). Debajo, la rejilla a todo el ancho con los canales agrupados por series
 * («M+ LaLiga»: LaLiga, 2, 3…), cada grupo con su título; una segunda fila de fichas salta a un grupo concreto.
 * Las versiones HD/FHD/4K de un canal van juntas como fuentes. OK sobre un canal lo reproduce al momento;
 * proveedor, grupo y categoría solo filtran. Mantener OK (o pulsación larga): Mi lista.
 */
@Composable
fun ChannelsScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val lib by graph.providers.state.collectAsState()
    val mine by graph.mylist.state.collectAsState()
    val library = lib.library
    val first = remember { FocusRequester() }

    val goldOn = graph.gold.state.collectAsState().value.configured
    if (library.isEmpty && library.links.isEmpty()) {
        EmptyState(
            if (goldOn && lib.providers.isEmpty()) "Cargando la Biblioteca…" else if (lib.loading) "Leyendo tus proveedores…" else "Aún no hay canales",
            if (goldOn && lib.providers.isEmpty()) "Si tarda, compruébala en Ajustes › Biblioteca."
            else if (lib.providers.isEmpty()) "Añade un proveedor para ver aquí sus canales." else "Tus proveedores no han devuelto canales reproducibles. Prueba a actualizarlos.",
            icon = MvIcons.Tv
        ) {
            if (goldOn && lib.providers.isEmpty()) MvButton("Ir a la Biblioteca", { openLibrarySettings(nav) }, icon = MvIcons.Settings, primary = true, focusRequester = first)
            else if (lib.providers.isEmpty()) MvButton("Añadir proveedor", { nav.go(Route.AddProvider()) }, icon = MvIcons.Add, primary = true, focusRequester = first)
            else MvButton("Actualizar proveedores", { graph.providers.refresh() }, icon = MvIcons.Refresh, primary = true, focusRequester = first)
        }
        InitialFocus(first)
        return
    }

    val mem = ChannelsMemory
    // 3k · La vista previa solo vive en esta pantalla.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { graph.preview.release() } }
    // 3i · Qué echan ahora y a continuación en cada canal (de la agenda), fuera del hilo de la pantalla y al minuto.
    val agendaState by graph.agenda.state.collectAsState()
    val onAir by androidx.compose.runtime.produceState(emptyMap<String, OnChannels.NowNext>(), agendaState.agenda, library) {
        val events = agendaState.agenda?.events.orEmpty()
        while (true) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                OnChannels.build(events, System.currentTimeMillis(), { name -> library.forAgenda(name, 3).map { it.id } })
            }
            delay(60_000)
        }
    }
    val liveIds = remember(onAir) { onAir.filterValues { it.now != null }.keys }
    // 3k · Canales en línea, recalculados al entrar y cada minuto (fuera del hilo de la pantalla).
    val onlineIds by androidx.compose.runtime.produceState(emptySet<String>(), library) {
        while (true) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                // 4f · Biblioteca de MiVisor Gold: «en línea» = algún ID en banda verde según Gold (la app no puntúa).
                // 4i · Y los IDs nuevos de tus proveedores, por lo que la app ha aprendido de ellos.
                if (library.gold) library.channels.filter { ch ->
                    ch.sources.any { it.gold?.band == "verde" } ||
                        ch.sources.filter { it.gold == null }.map { it.contentId }.let { it.isNotEmpty() && graph.sources.online(it) }
                }.map { it.id }.toSet()
                else library.channels.filter { ch -> graph.sources.online(ch.sources.map { it.contentId }) }.map { it.id }.toSet()
            }
            delay(60_000)
        }
    }
    val online = mem.onlineOnly
    val favorites = mine.favorites.map { it.first }.toSet()
    val providers = library.providers
    val providerSec = providers.firstOrNull { it.key == mem.provider }
    val base: List<TvChannel> = remember(library, mem.view, providerSec, mine.favorites, online, onlineIds) {
        when (mem.view) {
            0 -> providerSec?.channels ?: library.channels
            1 -> library.channels
            else -> mine.favorites.mapNotNull { library.channel(it.first) }
        }.let { all -> if (online) all.filter { it.id in onlineIds } else all }
    }
    val families = remember(base) { TvLibrary.familiesOf(base) }
    val family = mem.family?.takeIf { f -> families.any { it.first == f } }
    // Proveedor → serie («M+ LaLiga» con sus 1, 2, 3…) → canal. Los canales sin hermanos van a «Otros canales».
    val allSeries = remember(base) { ChannelSeriesGrouping.group(base) }
    val multiSeries = allSeries.filter { it.channels.size > 1 }
    val singles = remember(allSeries) { TvLibrary.sorted(allSeries.filter { it.channels.size == 1 }.flatMap { it.channels }) }
    val series = mem.series?.takeIf { k -> k == ChannelsMemory.SINGLES && singles.isNotEmpty() || multiSeries.any { it.key == k } }
    val onlyLive = mem.onlyLive && liveIds.isNotEmpty()
    val entries: List<Entry> = remember(base, family, series, mem.view, multiSeries.size, onlyLive, liveIds) {
        when {
            onlyLive -> TvLibrary.sorted(base.filter { it.id in liveIds }).map { Entry.Item(it) }
            mem.view == 2 -> base.map { Entry.Item(it) }
            mem.view == 1 -> TvLibrary.sorted(if (family == null) base else base.filter { it.family == family }).map { Entry.Item(it) }
            series == ChannelsMemory.SINGLES -> singles.map { Entry.Item(it) }
            series != null -> multiSeries.first { it.key == series }.channels.map { Entry.Item(it) }
            multiSeries.isEmpty() -> TvLibrary.sorted(base).map { Entry.Item(it) }
            else -> buildList<Entry> {
                for (sr in multiSeries) {
                    add(Entry.Header(sr.name, sr.channels.size, null))
                    sr.channels.forEach { add(Entry.Item(it)) }
                }
                if (singles.isNotEmpty()) {
                    add(Entry.Header("Otros canales", singles.size, null))
                    singles.forEach { add(Entry.Item(it)) }
                }
            }
        }
    }
    val shownCount = entries.count { it is Entry.Item }

    // Volver del reproductor: foco en el mismo canal (decidido UNA vez al entrar).
    val grid = remember { LazyGridState(mem.gridIndex) }
    val restore = remember { FocusRequester() }
    val target = mem.lastChannel?.takeIf { id -> entries.any { it is Entry.Item && it.channel.id == id } }
    val entryTarget = remember { target }
    LaunchedEffect(entryTarget) {
        if (entryTarget != null) {
            val i = entries.indexOfFirst { it is Entry.Item && it.channel.id == entryTarget }
            if (i >= 0) grid.scrollToItem(i)
            delay(60)
            runCatching { restore.requestFocus() }
        }
    }
    val form = com.mivisor.tv.ui.design.LocalUiForm.current

    Column(Modifier.fillMaxSize().padding(start = MvDims.SafeX, end = MvDims.SafeX)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MvText("Canales", MvType.Title)
            Spacer(Modifier.width(12.dp))
            MvText("${library.channels.size} canales · ${providers.size} proveedores", MvType.Small, modifier = Modifier.weight(1f), maxLines = 1)
            SmallAction("Buscar", MvIcons.Search) { nav.tab(Route.Search) }
            if (library.links.isNotEmpty()) { Spacer(Modifier.width(6.dp)); SmallAction("Enlaces · ${library.links.size}", MvIcons.Link) { nav.go(Route.Links) } }
            Spacer(Modifier.width(6.dp))
            SmallAction("Proveedores", MvIcons.Settings) { nav.tab(Route.Settings) }
        }
        Spacer(Modifier.height(10.dp))
        // Modo: pestañas segmentadas y, a la derecha, «Solo con partido ahora».
        Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.background(MvColors.Surface1, RoundedCornerShape(12.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Segment("Proveedores", mem.view == 0, if (entryTarget == null) first else null) { if (mem.view != 0) { mem.view = 0; mem.family = null; mem.series = null; mem.gridIndex = 0 } }
            Segment("Categorías", mem.view == 1, null) { if (mem.view != 1) { mem.view = 1; mem.family = null; mem.series = null; mem.gridIndex = 0 } }
            Segment("Favoritos · ${favorites.size}", mem.view == 2, null) { if (mem.view != 2) { mem.view = 2; mem.gridIndex = 0 } }
        }
            Spacer(Modifier.width(10.dp))
            OnlineFilter("En línea · ${onlineIds.size}", online) { mem.onlineOnly = !mem.onlineOnly; mem.series = null; mem.gridIndex = 0 }
            Spacer(Modifier.weight(1f))
            val nLive = base.count { it.id in liveIds }
            if (nLive > 0 || mem.onlyLive) LiveFilter("Solo con partido ahora · $nLive", onlyLive) { mem.onlyLive = !mem.onlyLive; mem.gridIndex = 0 }
        }
        Spacer(Modifier.height(10.dp))
        // Filtros: proveedores (o categorías) y, dentro de un proveedor, sus grupos.
        when (mem.view) {
            0 -> {
                ChipRow(listOf<Triple<String?, String, Int>>(Triple(null, "Todos", if (online) onlineIds.size else library.channels.size)) +
                    providers.map { p -> Triple(p.key, p.name, if (online) p.channels.count { it.id in onlineIds } else p.channels.size) }
                        .filter { !online || it.third > 0 || it.first == mem.provider }, mem.provider) { k ->
                    if (mem.provider != k) { mem.provider = k; mem.family = null; mem.series = null; mem.gridIndex = 0 }
                }
                if (multiSeries.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    ChipRow(
                        listOf<Triple<String?, String, Int>>(Triple(null, "Todos los grupos", base.size)) +
                            multiSeries.map { Triple(it.key, it.name, it.channels.size) } +
                            (if (singles.isNotEmpty()) listOf(Triple(ChannelsMemory.SINGLES, "Otros canales", singles.size)) else emptyList()),
                        series, small = true
                    ) { k -> if (mem.series != k) { mem.series = k; mem.gridIndex = 0 } }
                }
            }
            1 -> ChipRow(listOf<Triple<String?, String, Int>>(Triple(null, "Todas", base.size)) + families.map { Triple(it.first.id, it.first.label, it.second) },
                family?.id) { id -> val f = families.firstOrNull { it.first.id == id }?.first; if (mem.family != f) { mem.family = f; mem.gridIndex = 0 } }
            else -> Unit
        }
        Spacer(Modifier.height(10.dp))
        if (shownCount == 0) {
            MvText(
                if (mem.view == 2) (if (form.touch) "Aún no tienes favoritos. Mantén pulsado un canal para añadirlo." else "Aún no tienes favoritos. Mantén pulsado OK sobre un canal para añadirlo.")
                else if (online) "Ningún canal en línea aquí todavía. Un canal sale en línea cuando una de sus fuentes ha dado imagen en las últimas 12 horas: al verlo o con Ajustes › Verificador."
                else "No hay canales aquí.",
                MvType.Body.copy(color = MvColors.Text2), modifier = Modifier.padding(top = 12.dp)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(Lib.TileMin), state = grid, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp, start = 3.dp, end = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            entries.forEachIndexed { i, e ->
                when (e) {
                    is Entry.Header -> item(key = e.key, span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.padding(top = if (i == 0) 0.dp else 10.dp), verticalAlignment = Alignment.Bottom) {
                            MvText(e.title, MvType.Heading, maxLines = 1)
                            Spacer(Modifier.width(10.dp))
                            MvText("${e.count} ${if (e.count == 1) "canal" else "canales"}", MvType.Small.copy(color = MvColors.Text3), modifier = Modifier.padding(bottom = 2.dp))
                        }
                    }
                    is Entry.Item -> item(key = e.key) {
                        val ch = e.channel
                        ChannelCard(
                            ch, graph, favorite = ch.id in favorites, onAir = onAir[ch.id],
                            focusRequester = if (ch.id == entryTarget) restore else null,
                            onClick = {
                                mem.lastChannel = ch.id
                                mem.gridIndex = i
                                nav.go(Route.Player(ch.id))
                            },
                            onLongClick = {
                                val was = ch.id in favorites
                                graph.mylist.toggleFavorite(ch.id, ch.name)
                                toaster.show(if (was) "Quitado de Mi lista" else "Añadido a Mi lista")
                            }
                        )
                    }
                }
            }
        }
    }
    if (entryTarget == null) InitialFocus(first)
}

/** Botón pequeño de la cabecera (Buscar, Enlaces, Proveedores). */
@Composable
private fun SmallAction(text: String, icon: Int, onClick: () -> Unit) {
    TvClickable(
        onClick = onClick, shape = RoundedCornerShape(9.dp), focusedScale = 1f,
        color = MvColors.Surface1, focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
        modifier = Modifier.height(32.dp)
    ) { _ ->
        Row(Modifier.fillMaxHeight().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            MvIcon(icon, size = 14.dp, tint = MvColors.Text2)
            Spacer(Modifier.width(6.dp))
            MvText(text, MvType.SmallStrong, maxLines = 1)
        }
    }
}

/** 3k · Filtro «En línea» (pastilla con punto verde). */
@Composable
private fun OnlineFilter(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    TvClickable(
        onClick = onClick, shape = shape, focusedScale = 1.04f,
        color = if (on) Color(0x339FD6C8) else Color.Transparent, focusedColor = Color(0x4D9FD6C8), ringColor = MvColors.Accent2,
        modifier = Modifier.height(34.dp).border(1.dp, Color(0x809FD6C8), shape)
    ) { _ ->
        Row(Modifier.fillMaxHeight().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(MvColors.Ok, CircleShape))
            Spacer(Modifier.width(8.dp))
            MvText(text, MvType.SmallStrong, color = MvColors.Ok, maxLines = 1)
            if (on) { Spacer(Modifier.width(8.dp)); MvIcon(MvIcons.Check, size = 14.dp, tint = MvColors.Ok) }
        }
    }
}

/** 3i · Filtro «Solo con partido ahora» (pastilla con punto rojo). */
@Composable
private fun LiveFilter(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    TvClickable(
        onClick = onClick, shape = shape, focusedScale = 1.04f,
        color = if (on) Color(0x33E58C83) else Color.Transparent, focusedColor = Color(0x4DE58C83), ringColor = MvColors.Accent2,
        modifier = Modifier.height(34.dp).border(1.dp, Color(0x80E58C83), shape)
    ) { _ ->
        Row(Modifier.fillMaxHeight().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(MvColors.Live, CircleShape))
            Spacer(Modifier.width(8.dp))
            MvText(text, MvType.SmallStrong, color = Color(0xFFF2B1AA), maxLines = 1)
            if (on) { Spacer(Modifier.width(8.dp)); MvIcon(MvIcons.Check, size = 14.dp, tint = Color(0xFFF2B1AA)) }
        }
    }
}

/** Pestaña del selector de modo. */
@Composable
private fun Segment(text: String, selected: Boolean, fr: FocusRequester?, onClick: () -> Unit) {
    TvClickable(
        onClick = onClick, focusRequester = fr, shape = RoundedCornerShape(9.dp), focusedScale = 1f,
        color = if (selected) MvColors.Surface3 else Color.Transparent, focusedColor = MvColors.Surface4, ringColor = MvColors.Accent2,
        modifier = Modifier.height(34.dp)
    ) { _ ->
        Box(Modifier.fillMaxHeight().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            MvText(text, MvType.SmallStrong, color = if (selected) MvColors.Accent else MvColors.Text2, maxLines = 1)
        }
    }
}

/**
 * Fila de fichas de filtro (proveedores, grupos o categorías). OK o un toque la elige; pasar por encima no cambia
 * nada. Elegida = champán; enfocada = anillo.
 */
@Composable
private fun ChipRow(items: List<Triple<String?, String, Int>>, selected: String?, small: Boolean = false, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 3.dp, vertical = 3.dp)) {
        items(items, key = { it.first ?: "todos" }) { (k, label, n) ->
            val on = k == selected
            TvClickable(
                onClick = { onSelect(k) }, shape = RoundedCornerShape(20.dp), focusedScale = 1f,
                color = if (on) MvColors.Accent else if (small) Color.Transparent else MvColors.Surface1,
                focusedColor = if (on) MvColors.Accent2 else MvColors.Surface3, ringColor = MvColors.Accent2,
                modifier = Modifier.height(if (small) 30.dp else 36.dp)
                    .then(if (small && !on) Modifier.border(1.dp, MvColors.Stroke, RoundedCornerShape(20.dp)) else Modifier)
            ) { _ ->
                Row(Modifier.fillMaxHeight().padding(horizontal = if (small) 12.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    MvText(label, if (small) MvType.SmallStrong else MvType.BodyStrong, color = if (on) MvColors.Bg else MvColors.Text, maxLines = 1)
                    Spacer(Modifier.width(7.dp))
                    MvText("$n", MvType.Caption.copy(color = if (on) MvColors.Bg.copy(alpha = 0.7f) else MvColors.Text3))
                }
            }
        }
    }
}

/**
 * Tarjeta de canal (3f): el logo a todo el recuadro (si el logo trae fondo, el recuadro es ese fondo: un solo borde),
 * y debajo el nombre completo y su estado con un punto de color y texto (el color nunca va solo).
 */
@Composable
fun ChannelCard(
    ch: TvChannel,
    graph: AppGraph,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onAir: OnChannels.NowNext? = null
) {
    val (status, color) = channelStatus(ch, graph.sources)
    val live = onAir?.now
    val n = ch.sources.size
    val prewarm = com.mivisor.tv.ui.LocalPrewarm.current
    val preview = graph.preview
    val pv by preview.state.collectAsState()
    val showing = pv.channelId == ch.id
    Column(Modifier.fillMaxWidth().onFocusChanged { if (!it.hasFocus) preview.onBlur(ch.id) }) {
        TvClickable(
            onClick = { preview.release(); onClick() }, onLongClick = onLongClick, focusRequester = focusRequester,
            onFocus = { prewarm(ch); preview.onFocus(ch) },
            shape = RoundedCornerShape(12.dp), focusedScale = 1.03f,
            color = MvColors.Surface1, focusedColor = MvColors.Surface1, ringColor = MvColors.Accent2,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        ) { _ ->
            com.mivisor.tv.ui.LogoImage(ch.logo, 120.dp, Modifier.fillMaxSize(), RoundedCornerShape(12.dp), plain = MvColors.Surface2, padding = 0.18f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MvIcon(MvIcons.Signal, size = 26.dp, tint = MvColors.Text3)
                    Spacer(Modifier.height(6.dp))
                    MvText(ch.name, MvType.SmallStrong.copy(color = MvColors.Text2), maxLines = 2, modifier = Modifier.padding(horizontal = 10.dp))
                }
            }
            // 3k · Vista previa en vídeo (sin sonido) dentro de la tarjeta: aparece cuando llega la primera imagen.
            if (showing) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx -> android.view.TextureView(ctx).also { preview.attach(it) } },
                    onRelease = { preview.detach(it) },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).graphicsLayer { alpha = if (pv.ready) 1f else 0f }
                )
                if (pv.ready) Row(
                    Modifier.align(Alignment.TopStart).padding(7.dp).background(Color(0xB3080F19), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MvIcon(MvIcons.Volume, size = 11.dp, tint = MvColors.Text2)
                    Spacer(Modifier.width(4.dp))
                    MvText("Sin sonido", MvType.Caption.copy(color = MvColors.Text2))
                }
            }
            if (favorite) Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(Color(0x99000000), CircleShape),
                contentAlignment = Alignment.Center
            ) { MvIcon(MvIcons.Star, size = 13.dp, tint = MvColors.Accent) }
            if (live != null) {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(start = 7.dp, bottom = 9.dp).background(Color(0xD1080F19), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).background(MvColors.Live, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    MvText("AHORA", MvType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color(0xFFF2B1AA))
                }
                val p = live.progress(System.currentTimeMillis())
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x1FFFFFFF))) {
                    Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(MvColors.Live))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        MvText(ch.name, MvType.SmallStrong, maxLines = 1)
        val next = onAir?.next
        if (live != null) MvText(live.title, MvType.Caption.copy(color = MvColors.Text), maxLines = 1)
        else if (next != null) MvText("A las ${com.mivisor.tv.ui.hhmm(next.startMillis)} · ${next.title}", MvType.Caption.copy(color = MvColors.Text3), maxLines = 1)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Spacer(Modifier.width(5.dp))
            MvText(
                status.substringBefore(" · ") + " · $n ${if (n == 1) "fuente" else "fuentes"}",
                MvType.Caption.copy(color = MvColors.Text3), maxLines = 1
            )
        }
    }
}

/** Logo del canal en su recuadro pequeño (reproductor): sin doble borde si el logo trae su propio fondo. */
@Composable
fun LogoBox(ch: TvChannel, size: androidx.compose.ui.unit.Dp = Lib.Logo) {
    com.mivisor.tv.ui.LogoImage(
        ch.logo, size * 2, Modifier.size(width = size * 1.6f, height = size), RoundedCornerShape(9.dp),
        plain = Color(0xFF0E1A27), frame = MvColors.Stroke, padding = 0.1f
    ) { MvIcon(MvIcons.Signal, size = size * 0.45f, tint = MvColors.Text2) }
}

/** Logo de picons_dobleM (se dibuja sin placa clara). */
fun isPicon(url: String?): Boolean = url != null && url.contains("/picons_dobleM/")
