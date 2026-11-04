package com.mivisor.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import com.mivisor.tv.ui.dayLabel
import com.mivisor.tv.model.Competition
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.mivisor.tv.ui.design.MvIcon
import com.mivisor.tv.ui.design.MvNotice
import com.mivisor.tv.ui.ago
import androidx.compose.runtime.LaunchedEffect
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

/**
 * Inicio (diseño 06, 3f). En este orden:
 *  1. Portada grande: el evento destacado (en directo o el próximo con canal) o, si no hay, el último canal visto.
 *  2. «En emisión y a continuación»: directos y lo que empieza en las próximas 3 h.
 *  3. «Continuar viendo» y «Próximas citas» lado a lado (en pantallas estrechas, una debajo de otra).
 *  4. Favoritos (hasta cuatro y «Ver todos»).
 *  5. «Competiciones seguidas» (o sugeridas si aún no sigues ninguna).
 *  6. «Agenda próxima»: las siguientes citas en orden de hora.
 */
@Composable
fun HomeScreen(graph: AppGraph, nav: Nav, toaster: Toaster, backdrop: com.mivisor.tv.ui.Backdrop) {
    val lib by graph.providers.state.collectAsState()
    val agenda by graph.agenda.state.collectAsState()
    val mine by graph.mylist.state.collectAsState()
    val reminders by graph.reminders.list.collectAsState()
    val first = remember { FocusRequester() }
    val form = com.mivisor.tv.ui.design.LocalUiForm.current

    if (lib.loadedOnce && lib.providers.isEmpty()) {
        LaunchedEffect(Unit) { backdrop.colors = null }
        EmptyState(
            "Añade tu primer proveedor",
            "MiVisor TV junta los canales de tus proveedores y elige sola la fuente que mejor funciona. Necesitas al menos uno para empezar.",
            icon = MvIcons.Tv
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
    val all = agenda.agenda?.events.orEmpty()
    // En emisión y a continuación: directos y próximas 3 h; primero los que tienen canal en tus proveedores.
    val events = remember(agenda.agenda, library) {
        all.filter { val s = it.status(now); s == EventStatus.LIVE || (s == EventStatus.UPCOMING && it.startMillis - now < 3 * 3600_000L) }
            .map { it to channelsFor(it, library) }
            .sortedWith(compareByDescending<Pair<AgendaEvent, List<TvChannel>>> { it.second.isNotEmpty() }
                .thenByDescending { com.mivisor.tv.model.EventPriority.of(it.first) }.thenBy { it.first.startMillis })
            .take(12)
    }
    val hero = events.firstOrNull { it.second.isNotEmpty() }
    val heroKey = hero?.first?.id
    LaunchedEffect(heroKey) {
        backdrop.colors = hero?.let { (ev, _) -> MvPalette.pair(ev.home ?: ev.title).first to MvPalette.pair(ev.away ?: ev.sport).first }
    }
    val reminded = reminders.map { it.eventId }.toSet()
    val shownIds = events.map { it.first.id }.toSet()
    // Próximas citas (24 h): primero las que tienen recordatorio, luego las más importantes. Se pueden recordar aquí.
    val citas = remember(agenda.agenda, library, reminded) {
        val next = all.filter { it.status(now) == EventStatus.UPCOMING && it.startMillis - now < 24 * 3600_000L && it.id !in shownIds }
        (next.filter { it.id in reminded }.sortedBy { it.startMillis } +
            next.filter { it.id !in reminded }.sortedByDescending { com.mivisor.tv.model.EventPriority.of(it) })
            .distinctBy { it.id }.take(4).sortedBy { it.startMillis }
    }
    // Agenda próxima: lo siguiente en orden de hora (48 h), sin repetir lo de arriba.
    val agendaNext = remember(agenda.agenda, citas) {
        val used = shownIds + citas.map { it.id }
        all.filter { it.status(now) == EventStatus.UPCOMING && it.startMillis - now < 48 * 3600_000L && it.id !in used }
            .sortedBy { it.startMillis }.take(8)
    }
    val favorites = mine.favorites.map { it.first }.toSet()
    val history = mine.history.filter { library.channel(it.channelId) != null }.distinctBy { it.channelId }.take(3)
    val favChannels = mine.favorites.mapNotNull { library.channel(it.first) }
    val followed = graph.prefs.followedCompetitions.mapNotNull { Competition.byName(it) }
    val comps = followed.ifEmpty { Competition.PINNED }
    val toggleFav: (TvChannel) -> Unit = { ch ->
        graph.mylist.toggleFavorite(ch.id, ch.name)
        toaster.show(if (ch.id in favorites) "Quitado de Mi lista" else "Añadido a Mi lista")
    }
    val toggleReminder: (AgendaEvent) -> Unit = { ev ->
        toaster.show(if (graph.reminders.toggle(ev)) "Te aviso 5 min antes de que empiece" else "Recordatorio quitado")
    }
    val pad = PaddingValues(horizontal = MvDims.SafeX)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        if (lib.offline) item {
            MvNotice("Sin conexión a Internet.", "Ves la agenda y los canales guardados. Los canales no se pueden abrir hasta que vuelva la red.",
                Modifier.padding(pad).padding(bottom = 12.dp), MvColors.Soon)
        }
        lib.providers.filter { it.error != null }.takeIf { it.isNotEmpty() }?.let { bad ->
            item {
                MvNotice("No se pudo actualizar:", "${bad.joinToString { it.name }}. Se usa la copia guardada.",
                    Modifier.padding(pad).padding(bottom = 12.dp), MvColors.Soon)
            }
        }
        // 1 · Portada.
        item {
            if (hero != null) Hero(hero.first, hero.second, now, first, graph, form,
                onWatch = { nav.go(Route.Player(hero.second.first().id)) },
                onOptions = { nav.go(Route.Event(hero.first.id)) })
            else {
                val last = history.firstOrNull()?.let { h -> library.channel(h.channelId)?.let { it to h } }
                QuietHero(last?.first, last?.second?.playedAt, first, form,
                    onWatch = { last?.first?.let { nav.go(Route.Player(it.id)) } },
                    onChannels = { nav.tab(Route.Channels) }, onAgenda = { nav.tab(Route.Agenda) })
            }
        }
        // 2 · En emisión y a continuación.
        if (events.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
                val live = events.count { it.first.status(now) == EventStatus.LIVE }
                SectionHeader("En emisión y a continuación", Modifier.padding(pad),
                    if (live > 0) "$live en directo · ${events.size - live} en las próximas 3 h" else "en las próximas 3 h")
                LazyRow(contentPadding = pad, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    itemsIndexed(events, key = { _, e -> e.first.id }) { _, (ev, chs) ->
                        EventTile(ev, chs, now, onClick = { nav.go(Route.Event(ev.id)) }, focusRequester = null, reminded = ev.id in reminded)
                    }
                }
            }
        }
        // 3 · Continuar viendo + Próximas citas.
        item {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(pad).padding(bottom = 26.dp)) {
                val continuePanel = @Composable { m: Modifier ->
                    HomePanel("Continuar viendo", "por donde lo dejaste", m) {
                        if (history.isEmpty()) MvText("Los canales que veas aparecerán aquí para volver con un OK.", MvType.Small.copy(color = MvColors.Text3),
                            modifier = Modifier.padding(vertical = 10.dp))
                        history.forEach { h ->
                            val ch = library.channel(h.channelId) ?: return@forEach
                            ContinueRow(ch, "Viste ${ago(h.playedAt)}" + if (h.seconds >= 60) " · ${h.seconds / 60} min" else "",
                                onClick = { nav.go(Route.Player(ch.id)) }, onLong = { toggleFav(ch) })
                        }
                    }
                }
                val citasPanel = @Composable { m: Modifier ->
                    HomePanel("Próximas citas", "con recordatorio, la fuente se prepara 5 min antes", m) {
                        if (citas.isEmpty()) MvText("Nada destacado en las próximas 24 h.", MvType.Small.copy(color = MvColors.Text3),
                            modifier = Modifier.padding(vertical = 10.dp))
                        citas.forEach { ev ->
                            CitaRow(ev, now, channelsFor(ev, library, 1).firstOrNull()?.name, ev.id in reminded,
                                onOpen = { nav.go(Route.Event(ev.id)) }, onRemind = { toggleReminder(ev) })
                        }
                    }
                }
                if (maxWidth >= 640.dp) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    continuePanel(Modifier.weight(1f))
                    citasPanel(Modifier.weight(1.25f))
                } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    continuePanel(Modifier.fillMaxWidth())
                    citasPanel(Modifier.fillMaxWidth())
                }
            }
        }
        // 4 · Favoritos.
        if (favChannels.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
                SectionHeader("Favoritos", Modifier.padding(pad), "${favChannels.size} canales")
                LazyRow(contentPadding = pad, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(favChannels.take(4), key = { "f-" + it.id }) { ch ->
                        TvChannelTile(ch, graph.sources, favorite = true, onClick = { nav.go(Route.Player(ch.id)) }, onLongClick = { toggleFav(ch) })
                    }
                    item(key = "f-all") { SeeAllTile("Ver todos", "Mi lista") { nav.tab(Route.MyList) } }
                }
            }
        }
        // 5 · Competiciones seguidas.
        item {
            Column(Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
                SectionHeader(if (followed.isEmpty()) "Competiciones" else "Competiciones seguidas", Modifier.padding(pad),
                    if (followed.isEmpty()) "sugeridas · ábrelas y pulsa «Seguir competición»" else "${followed.size} seguidas")
                LazyRow(contentPadding = pad, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(comps, key = { "c-" + it.name }) { c ->
                        CompetitionCard(c, all, now, onClick = { nav.go(Route.Competition(c.name)) })
                    }
                }
            }
        }
        // 6 · Agenda próxima.
        if (agendaNext.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(pad).padding(bottom = 18.dp)) {
                SectionHeader("Agenda próxima", Modifier, "en orden de hora")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    agendaNext.forEach { ev ->
                        AgendaLine(ev, now, channelsFor(ev, library, 1).firstOrNull()?.name, ev.id in reminded) { nav.go(Route.Event(ev.id)) }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(pad).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MvButton("Toda la agenda", { nav.tab(Route.Agenda) }, icon = MvIcons.Calendar)
                MvButton("Todos los canales", { nav.tab(Route.Channels) }, icon = MvIcons.Grid)
            }
        }
    }
    InitialFocus(first)
}

/**
 * Portada: evento destacado con escudos (o retratos), competición, título, canal. Un botón principal («Ver ahora»
 * reproduce el canal más fiable) y como mucho uno secundario (diseño 06).
 */
@Composable
private fun Hero(
    ev: AgendaEvent,
    chs: List<TvChannel>,
    now: Long,
    first: FocusRequester,
    graph: AppGraph,
    form: com.mivisor.tv.ui.design.UiForm,
    onWatch: () -> Unit,
    onOptions: () -> Unit
) {
    val home = ev.home ?: ev.title
    val away = ev.away
    val (h1, _) = MvPalette.pair(home)
    val (a1, _) = MvPalette.pair(away ?: ev.sport)
    val live = ev.status(now) == EventStatus.LIVE
    val main = chs.first()
    val start = graph.sources.typicalStart(main.sources.map { it.contentId })
    val phone = form == com.mivisor.tv.ui.design.UiForm.PHONE
    val big = if (phone) 36.sp else 46.sp
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(if (phone) 250.dp else 300.dp)) {
        val showCrests = away != null && maxWidth >= 720.dp
        if (showCrests) Row(
            Modifier.align(Alignment.CenterEnd).padding(end = MvDims.SafeX * 1.5f, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Visual(home, ev.homeVisual, h1, 130.dp, ev.isPersonVsPerson)
            MvText("vs", MvType.Heading.copy(color = MvColors.Text3), modifier = Modifier.padding(horizontal = 26.dp))
            Visual(away!!, ev.awayVisual, a1, 130.dp, ev.isPersonVsPerson)
        }
        Column(Modifier.padding(start = MvDims.SafeX, top = 18.dp, end = MvDims.SafeX).widthIn(max = if (showCrests) 540.dp else 900.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) MvTag("● EN DIRECTO", MvColors.Live, filled = true)
                else MvTag(if (ev.timeTbd) "HORA POR CONFIRMAR" else "HOY · ${hhmm(ev.startMillis)}", MvColors.Soon, filled = true)
                Spacer(Modifier.width(14.dp))
                MvText((ev.competition ?: sportName(ev.sport)).uppercase(), MvType.Eyebrow.copy(color = MvColors.Text2, fontSize = 14.sp), maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            val titleStyle = MvType.Display.copy(fontSize = big, lineHeight = big * 1.1f, fontWeight = FontWeight.Black)
            if (away != null) {
                MvText(home, titleStyle, maxLines = 1)
                MvText("vs $away", titleStyle.copy(color = MvColors.Text2), maxLines = 1)
            } else MvText(ev.title, titleStyle, maxLines = 2)
            Spacer(Modifier.height(10.dp))
            MvText(main.name + " · " + (if (start != null) "lista, arranca en ~$start s" else main.subtitle()) +
                (if (chs.size > 1) " · ${chs.size} canales" else ""),
                MvType.Heading.copy(color = MvColors.Text2, fontWeight = FontWeight.Normal), maxLines = 1)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MvButton("Ver ahora", onWatch, icon = MvIcons.Play, primary = true, focusRequester = first)
                MvButton(if (chs.size > 1) "Otras opciones" else "Dónde verlo", onOptions, icon = MvIcons.Layers)
            }
        }
    }
}

/** Portada sin evento destacado: el último canal visto o, si no hay, la portada de la marca. */
@Composable
private fun QuietHero(
    last: TvChannel?,
    playedAt: Long?,
    first: FocusRequester,
    form: com.mivisor.tv.ui.design.UiForm,
    onWatch: () -> Unit,
    onChannels: () -> Unit,
    onAgenda: () -> Unit
) {
    val phone = form == com.mivisor.tv.ui.design.UiForm.PHONE
    Box(
        Modifier.fillMaxWidth().height(if (phone) 220.dp else 260.dp).padding(horizontal = MvDims.SafeX).padding(bottom = 20.dp)
            .background(Brush.horizontalGradient(listOf(MvColors.Surface1, MvColors.Surface2.copy(alpha = 0.6f))), RoundedCornerShape(18.dp))
            .border(1.dp, MvColors.Stroke, RoundedCornerShape(18.dp))
    ) {
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 40.dp)) {
            if (last != null) ChannelLogo(last, Modifier.size(width = 200.dp, height = 112.dp), 200.dp)
            else com.mivisor.tv.ui.design.OrbitaMark(120.dp)
        }
        Column(Modifier.align(Alignment.CenterStart).padding(start = 30.dp, end = 280.dp)) {
            if (last != null) {
                MvText("SEGUIR VIENDO", MvType.Eyebrow.copy(color = MvColors.Accent))
                Spacer(Modifier.height(8.dp))
                MvText(last.name, MvType.Display.copy(fontSize = if (phone) 32.sp else 40.sp, fontWeight = FontWeight.Black), maxLines = 1)
                Spacer(Modifier.height(6.dp))
                MvText("Lo viste ${playedAt?.let { ago(it) } ?: "hace poco"} · ${last.subtitle()}", MvType.Body.copy(color = MvColors.Text2), maxLines = 1)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MvButton("Ver ahora", onWatch, icon = MvIcons.Play, primary = true, focusRequester = first)
                    MvButton("Canales", onChannels, icon = MvIcons.Grid)
                }
            } else {
                MvText("TU DEPORTE. EN UNA SOLA EXPERIENCIA.", MvType.Eyebrow.copy(color = MvColors.Accent))
                Spacer(Modifier.height(8.dp))
                MvText("Ahora mismo no hay nada destacado", MvType.Display.copy(fontSize = if (phone) 28.sp else 34.sp, fontWeight = FontWeight.Black), maxLines = 2)
                Spacer(Modifier.height(6.dp))
                MvText("Mira la agenda para ver lo próximo o entra en tus canales.", MvType.Body.copy(color = MvColors.Text2), maxLines = 2)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MvButton("Ver canales", onChannels, icon = MvIcons.Grid, primary = true, focusRequester = first)
                    MvButton("Agenda", onAgenda, icon = MvIcons.Calendar)
                }
            }
        }
    }
}

/** Panel de Inicio (Continuar viendo, Próximas citas): título, detalle y filas. */
@Composable
private fun HomePanel(title: String, detail: String, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier.background(MvColors.Surface1, RoundedCornerShape(16.dp)).border(1.dp, MvColors.Stroke, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            MvText(title, MvType.Heading, maxLines = 1)
            Spacer(Modifier.width(10.dp))
            MvText(detail, MvType.Caption.copy(color = MvColors.Text3), maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
        }
        Spacer(Modifier.height(2.dp))
        content()
    }
}

/** Fila de «Continuar viendo»: logo, canal y cuándo lo viste. OK lo reproduce; mantener OK: Mi lista. */
@Composable
private fun ContinueRow(ch: TvChannel, detail: String, onClick: () -> Unit, onLong: () -> Unit) {
    TvClickable(
        onClick = onClick, onLongClick = onLong, shape = RoundedCornerShape(10.dp), focusedScale = 1f,
        color = Color.Transparent, focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(ch, Modifier.size(width = 64.dp, height = 36.dp), 64.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                MvText(ch.name, MvType.BodyStrong, maxLines = 1)
                MvText(detail, MvType.Caption.copy(color = MvColors.Text3), maxLines = 1)
            }
            MvIcon(MvIcons.Play, size = 16.dp, tint = MvColors.Text2)
        }
    }
}

/** Fila de «Próximas citas»: hora, evento y canal; a la derecha, el recordatorio (se pone y se quita aquí). */
@Composable
private fun CitaRow(ev: AgendaEvent, now: Long, channel: String?, reminded: Boolean, onOpen: () -> Unit, onRemind: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TvClickable(
            onClick = onOpen, shape = RoundedCornerShape(10.dp), focusedScale = 1f,
            color = Color.Transparent, focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
            modifier = Modifier.weight(1f).height(52.dp)
        ) { _ ->
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(58.dp)) {
                    MvText(if (ev.timeTbd) "--:--" else hhmm(ev.startMillis), MvType.BodyStrong.copy(color = MvColors.Soon))
                    MvText(dayWord(ev.startMillis, now), MvType.Caption.copy(color = MvColors.Text3), maxLines = 1)
                }
                Column(Modifier.weight(1f)) {
                    MvText(ev.title, MvType.BodyStrong, maxLines = 1)
                    MvText(listOfNotNull(ev.competition ?: sportName(ev.sport), channel ?: "no está en tus proveedores").joinToString(" · "),
                        MvType.Caption.copy(color = MvColors.Text3), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        TvClickable(
            onClick = onRemind, shape = RoundedCornerShape(10.dp), focusedScale = 1f,
            color = if (reminded) MvColors.Soon.copy(alpha = 0.16f) else MvColors.Surface2, focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
            modifier = Modifier.height(40.dp)
        ) { _ ->
            Row(Modifier.fillMaxHeight().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                MvIcon(MvIcons.Clock, size = 15.dp, tint = if (reminded) MvColors.Soon else MvColors.Text2)
                Spacer(Modifier.width(6.dp))
                MvText(if (reminded) "Avisaré" else "Recordar", MvType.SmallStrong, color = if (reminded) MvColors.Soon else MvColors.Text, maxLines = 1)
            }
        }
    }
}

/** «Hoy», «Mañana» o el día de la semana. */
private fun dayWord(t: Long, now: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = now
    val today = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    cal.timeInMillis = t
    val that = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    cal.timeInMillis = now
    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
    val tomorrow = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    return when (that) {
        today -> "Hoy"
        tomorrow -> "Mañana"
        else -> dayLabel(t)
    }
}

/** Tarjeta «Ver todos» al final de una fila. */
@Composable
private fun SeeAllTile(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.width(150.dp)) {
        TvClickable(
            onClick = onClick, shape = RoundedCornerShape(MvDims.Radius), color = MvColors.Surface1, focusedColor = MvColors.Surface3,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f * 150f / 200f).border(1.dp, MvColors.Stroke, RoundedCornerShape(MvDims.Radius)),
            contentAlignment = Alignment.Center
        ) { _ ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MvIcon(MvIcons.ChevronRight, size = 26.dp, tint = MvColors.Accent)
                Spacer(Modifier.height(6.dp))
                MvText(title, MvType.BodyStrong)
            }
        }
        Spacer(Modifier.height(10.dp))
        MvText(detail, MvType.Caption.copy(color = MvColors.Text3))
    }
}

/** Tarjeta de competición: logo, nombre y lo próximo (o cuántos directos hay). OK abre su página. */
@Composable
private fun CompetitionCard(c: Competition, all: List<AgendaEvent>, now: Long, onClick: () -> Unit) {
    val mine = remember(all, c) { all.filter { Competition.of(it) == c && it.status(now) != EventStatus.FINISHED }.sortedBy { it.startMillis } }
    val live = mine.count { it.status(now) == EventStatus.LIVE }
    val next = mine.firstOrNull { it.status(now) == EventStatus.UPCOMING }
    val logo = remember(all, c) { competitionLogo(c, all) }
    TvClickable(
        onClick = onClick, shape = RoundedCornerShape(14.dp), focusedScale = 1f,
        color = MvColors.Surface1, focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
        modifier = Modifier.width(250.dp).height(84.dp)
    ) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.mivisor.tv.ui.LogoImage(logo, 48.dp, Modifier.size(48.dp), RoundedCornerShape(10.dp), plain = MvColors.Surface2, padding = 0.12f) {
                MvIcon(MvIcons.Trophy, size = 22.dp, tint = MvColors.Accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                MvText(c.label, MvType.BodyStrong, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                when {
                    live > 0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveDot(MvColors.Live, 6.dp)
                        Spacer(Modifier.width(6.dp))
                        MvText(if (live == 1) "1 en directo" else "$live en directo", MvType.Caption.copy(color = MvColors.Live), maxLines = 1)
                    }
                    next != null -> MvText((if (next.timeTbd) dayWord(next.startMillis, now) else dayWord(next.startMillis, now) + " " + hhmm(next.startMillis)) + " · " + next.title,
                        MvType.Caption.copy(color = MvColors.Text2), maxLines = 2)
                    else -> MvText("Sin eventos en la agenda", MvType.Caption.copy(color = MvColors.Text3), maxLines = 1)
                }
            }
        }
    }
}

/** Línea de «Agenda próxima»: hora, competición, evento y canal. OK abre la ficha. */
@Composable
private fun AgendaLine(ev: AgendaEvent, now: Long, channel: String?, reminded: Boolean, onClick: () -> Unit) {
    TvClickable(
        onClick = onClick, shape = RoundedCornerShape(10.dp), focusedScale = 1f,
        color = MvColors.Surface1.copy(alpha = 0.6f), focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
        modifier = Modifier.fillMaxWidth().height(46.dp)
    ) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MvText(if (ev.timeTbd) "--:--" else hhmm(ev.startMillis), MvType.BodyStrong, modifier = Modifier.width(54.dp))
            MvText(dayWord(ev.startMillis, now), MvType.Caption.copy(color = MvColors.Text3), modifier = Modifier.width(70.dp), maxLines = 1)
            MvText((ev.competition ?: sportName(ev.sport)), MvType.Caption.copy(color = MvColors.Accent), maxLines = 1, modifier = Modifier.width(150.dp))
            MvText(ev.title, MvType.BodyStrong, maxLines = 1, modifier = Modifier.weight(1f))
            if (reminded) {
                MvIcon(MvIcons.Clock, size = 14.dp, tint = MvColors.Soon)
                Spacer(Modifier.width(8.dp))
            }
            MvText(channel ?: "—", MvType.Caption.copy(color = if (channel != null) MvColors.Text2 else MvColors.Text3), maxLines = 1,
                modifier = Modifier.widthIn(max = 180.dp))
        }
    }
}

/** Tarjeta de evento de «En directo»: fondo de los dos equipos, escudos, competición y canal. */
@Composable
fun EventTile(ev: AgendaEvent, chs: List<TvChannel>, now: Long, onClick: () -> Unit, focusRequester: FocusRequester?, reminded: Boolean = false) {
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
                Visual(home, ev.homeVisual, h1, 64.dp, ev.isPersonVsPerson)
                MvText("vs", MvType.Small.copy(color = Color(0xCCFFFFFF)), modifier = Modifier.padding(horizontal = 16.dp))
                Visual(away, ev.awayVisual, a1, 64.dp, ev.isPersonVsPerson)
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MvText(ev.competition ?: sportName(ev.sport), MvType.SmallStrong, maxLines = 1, modifier = Modifier.weight(1f))
                if (reminded) {
                    MvIcon(MvIcons.Clock, size = 16.dp, tint = MvColors.Soon)
                    Spacer(Modifier.width(6.dp))
                }
                MvText(if (live) "en directo" else if (ev.timeTbd) "" else hhmm(ev.startMillis), MvType.SmallStrong)
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
