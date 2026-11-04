package com.mivisor.tv.ui.screens

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
import com.mivisor.tv.catalog.tv.TvChannel
import com.mivisor.tv.catalog.tv.TvLibrary
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
    /** Último canal abierto desde aquí: recibe el foco al volver. */
    var lastChannel: String? = null
    var gridIndex = 0
}

/** Medidas del diseño 06 (lienzo 1600 px → 960 dp: × 0,6). */
private object Lib {
    val PadX = 37.dp            // 62 px
    val ProviderCol = 132.dp    // 205 px (+ texto mínimo legible)
    val FamilyCol = 124.dp      // 201 px
    val Row = 36.dp             // 53 px
    val Card = 70.dp            // 110 px
    val Logo = 40.dp            // 65 px
}

/**
 * Canales (diseño 06): Proveedor → familia → canal. Las versiones HD/FHD/4K de un canal ya vienen juntas como fuentes
 * (no hay tarjetas duplicadas). OK sobre un canal lo reproduce al momento con su fuente más fiable; proveedor y
 * familia solo filtran. Mantener OK: Mi lista.
 */
@Composable
fun ChannelsScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val lib by graph.providers.state.collectAsState()
    val mine by graph.mylist.state.collectAsState()
    val library = lib.library
    val first = remember { FocusRequester() }

    if (library.isEmpty && library.links.isEmpty()) {
        EmptyState(
            if (lib.loading) "Leyendo tus proveedores…" else "Aún no hay canales",
            if (lib.providers.isEmpty()) "Añade un proveedor para ver aquí sus canales." else "Tus proveedores no han devuelto canales reproducibles. Prueba a actualizarlos.",
            icon = MvIcons.Tv
        ) {
            if (lib.providers.isEmpty()) MvButton("Añadir proveedor", { nav.go(Route.AddProvider()) }, icon = MvIcons.Add, primary = true, focusRequester = first)
            else MvButton("Actualizar proveedores", { graph.providers.refresh() }, icon = MvIcons.Refresh, primary = true, focusRequester = first)
        }
        InitialFocus(first)
        return
    }

    val mem = ChannelsMemory
    val favorites = mine.favorites.map { it.first }.toSet()
    // Lista a la izquierda (proveedores o categorías) y la familia elegida.
    val providers = library.providers
    val providerSec = providers.firstOrNull { it.key == mem.provider }
    val base: List<TvChannel> = when (mem.view) {
        0 -> providerSec?.channels ?: library.channels
        1 -> library.channels
        else -> mine.favorites.mapNotNull { library.channel(it.first) }
    }
    val families = remember(base) { TvLibrary.familiesOf(base) }
    val family = mem.family?.takeIf { f -> families.any { it.first == f } }
    val shown = remember(base, family, mem.view) {
        val filtered = if (family == null || mem.view == 2) base else base.filter { it.family == family }
        if (mem.view == 2) filtered else TvLibrary.sorted(filtered)
    }

    // Volver del reproductor: foco en el mismo canal.
    val grid = remember { LazyGridState(mem.gridIndex) }
    val restore = remember { FocusRequester() }
    val target = mem.lastChannel?.takeIf { id -> shown.any { it.id == id } }
    // El foco inicial se decide UNA vez al entrar (no se le quita al usuario mientras cambia de filtro).
    val entryTarget = remember { target }
    LaunchedEffect(entryTarget) {
        if (entryTarget != null) {
            val i = shown.indexOfFirst { it.id == entryTarget }
            if (i >= 0) grid.scrollToItem(i)
            delay(60)
            runCatching { restore.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().padding(start = MvDims.SafeX, end = MvDims.SafeX)) {
        MvText("BIBLIOTECA · CANALES", MvType.Eyebrow.copy(color = MvColors.Accent))
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            MvText("Canales", MvType.Title)
            Spacer(Modifier.width(14.dp))
            MvText(
                "${library.channels.size} canales · ${providers.size} proveedores · ${library.providerCount} listas",
                MvType.Small, modifier = Modifier.padding(bottom = 5.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ViewChip("Proveedores", mem.view == 0, if (entryTarget == null) first else null) { mem.view = 0; mem.family = null }
            ViewChip("Categorías", mem.view == 1, null) { mem.view = 1; mem.family = null }
            ViewChip("Favoritos · ${favorites.size}", mem.view == 2, null) { mem.view = 2 }
            ViewChip("Buscar canal", false, null, MvIcons.Search) { nav.tab(Route.Search) }
            if (library.links.isNotEmpty()) ViewChip("Enlaces · ${library.links.size}", false, null, MvIcons.Link) { nav.go(Route.Links) }
            ViewChip("Gestionar proveedores", false, null, MvIcons.Settings) { nav.tab(Route.Settings) }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mem.view == 0) {
                FilterColumn("PROVEEDORES", Lib.ProviderCol) {
                    items(listOf<Pair<String?, Pair<String, Int>>>(null to ("Todos" to library.channels.size)) +
                        providers.map { it.key to (it.name to it.channels.size) }, key = { it.first ?: "todos" }) { (key, label) ->
                        FilterRow(label.first, label.second, selected = mem.provider == key) {
                            if (mem.provider != key) { mem.provider = key; mem.family = null; mem.gridIndex = 0 }
                        }
                    }
                }
            }
            if (mem.view != 2) {
                FilterColumn(if (mem.view == 0) "FAMILIAS" else "CATEGORÍAS", Lib.FamilyCol) {
                    items(listOf<Pair<ChannelFamily?, Int>>(null to base.size) + families, key = { it.first?.id ?: "todas" }) { (f, n) ->
                        FilterRow(f?.label ?: "Todas", n, selected = family == f) { if (mem.family != f) { mem.family = f; mem.gridIndex = 0 } }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    MvText(
                        when (mem.view) {
                            2 -> "Tus favoritos"
                            else -> listOfNotNull(if (mem.view == 0) providerSec?.name ?: "Todos" else null, family?.label).joinToString(" · ").ifEmpty { "Todos los canales" }
                        },
                        MvType.Heading, maxLines = 1, modifier = Modifier.weight(1f)
                    )
                    MvText("${shown.size} ${if (shown.size == 1) "canal" else "canales"}", MvType.Small)
                }
                Spacer(Modifier.height(8.dp))
                if (shown.isEmpty()) {
                    MvText(
                        if (mem.view == 2) "Aún no tienes favoritos. Mantén pulsado OK sobre un canal para añadirlo." else "No hay canales aquí.",
                        MvType.Body.copy(color = MvColors.Text2), modifier = Modifier.padding(top = 12.dp)
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3), state = grid, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp, start = 3.dp, end = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(shown, key = { _, c -> c.id }) { i, ch ->
                        ChannelCard(
                            ch, graph, favorite = ch.id in favorites,
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

@Composable
private fun ViewChip(text: String, selected: Boolean, fr: FocusRequester?, icon: Int? = null, onClick: () -> Unit) {
    TvClickable(
        onClick = onClick, focusRequester = fr, shape = RoundedCornerShape(9.dp), focusedScale = 1f,
        color = if (selected) MvColors.Accent.copy(alpha = 0.16f) else MvColors.Surface1,
        focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
        modifier = Modifier.height(30.dp)
    ) { _ ->
        Row(Modifier.fillMaxHeight().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                MvIcon(icon, size = 14.dp, tint = MvColors.Text2)
                Spacer(Modifier.width(6.dp))
            }
            MvText(text, MvType.SmallStrong, color = if (selected) MvColors.Accent else MvColors.Text, maxLines = 1)
        }
    }
}

/** Columna de filtro (Proveedores / Familias) del diseño: panel con su título y filas de 36 dp. */
@Composable
private fun FilterColumn(title: String, width: androidx.compose.ui.unit.Dp, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(
        Modifier.width(width).fillMaxHeight().padding(bottom = 20.dp)
            .background(MvColors.Surface1, RoundedCornerShape(12.dp))
            .border(1.dp, MvColors.Stroke, RoundedCornerShape(12.dp))
            .padding(6.dp)
    ) {
        MvText(title, MvType.Caption.copy(color = MvColors.Text2, letterSpacing = 1.2.sp), modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(3.dp)) { content() }
    }
}

/**
 * Fila de filtro: OK la elige (nunca reproduce). Pasar por encima no cambia nada, para que moverse con las flechas
 * entre la rejilla y las columnas no cambie el filtro sin querer. Seleccionado (champán) ≠ enfocado (anillo).
 */
@Composable
private fun FilterRow(label: String, count: Int, selected: Boolean, onSelect: () -> Unit) {
    TvClickable(
        onClick = onSelect, shape = RoundedCornerShape(9.dp), focusedScale = 1f,
        color = if (selected) MvColors.Surface3 else Color.Transparent, focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
        modifier = Modifier.fillMaxWidth().height(Lib.Row)
    ) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            MvText(label, MvType.SmallStrong, color = if (selected) MvColors.Accent else MvColors.Text, maxLines = 2, modifier = Modifier.weight(1f))
            MvText("$count", MvType.Caption.copy(color = if (selected) MvColors.Accent else MvColors.Text3))
        }
    }
}

/**
 * Tarjeta compacta de canal (diseño 06): logo en su recuadro de 40 dp (sin deformar), nombre en dos líneas como
 * máximo y su estado con un punto de color y texto (el color nunca va solo).
 */
@Composable
fun ChannelCard(
    ch: TvChannel,
    graph: AppGraph,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    val (status, color) = channelStatus(ch, graph.sources)
    val n = ch.sources.size
    TvClickable(
        onClick = onClick, onLongClick = onLongClick, focusRequester = focusRequester,
        shape = RoundedCornerShape(12.dp), focusedScale = 1f,
        color = MvColors.Surface1, focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
        modifier = Modifier.fillMaxWidth().height(Lib.Card)
    ) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoBox(ch)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                MvText(ch.name, MvType.SmallStrong.copy(lineHeight = 17.sp), maxLines = 2)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(color, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    MvText(
                        status.substringBefore(" · ") + " · $n ${if (n == 1) "fuente" else "fuentes"}",
                        MvType.Caption.copy(color = MvColors.Text2), maxLines = 1, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (favorite) {
                        Spacer(Modifier.width(4.dp))
                        MvIcon(MvIcons.Star, size = 11.dp, tint = MvColors.Accent)
                    }
                }
            }
        }
    }
}

/** Logo del canal en su recuadro fijo: imagen «contain» sobre placa clara; sin logo, el pictograma de señal. */
@Composable
fun LogoBox(ch: TvChannel, size: androidx.compose.ui.unit.Dp = Lib.Logo) {
    Box(
        Modifier.size(size).background(Color(0xFF0E1A27), RoundedCornerShape(9.dp)).border(1.dp, MvColors.Stroke, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        val signal = @Composable { MvIcon(MvIcons.Signal, size = size * 0.45f, tint = MvColors.Text2) }
        if (ch.logo != null) {
            // Placa clara (casi todos los logos están pensados para fondo blanco), imagen sin deformar.
            Box(
                Modifier.size(size * 0.84f).background(Color(0xFFEEF1F5), RoundedCornerShape(7.dp)).padding(3.dp),
                contentAlignment = Alignment.Center
            ) { RemoteImage(ch.logo, size, Modifier.fillMaxSize()) { signal() } }
        } else signal()
    }
}
