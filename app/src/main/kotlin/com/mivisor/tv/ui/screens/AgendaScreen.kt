package com.mivisor.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.model.Agenda
import com.mivisor.tv.model.AgendaBoard
import com.mivisor.tv.model.AgendaEvent
import com.mivisor.tv.model.Competition
import com.mivisor.tv.model.CompetitionNames
import com.mivisor.tv.model.CompetitionSummaries
import com.mivisor.tv.model.CompetitionSummary
import com.mivisor.tv.model.CompetitionTemplate
import com.mivisor.tv.model.DayClock
import com.mivisor.tv.model.EventStatus
import com.mivisor.tv.model.sourceNoticeText
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.Nav
import com.mivisor.tv.ui.RemoteImage
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.Toaster
import com.mivisor.tv.ui.ago
import com.mivisor.tv.ui.design.EmptyState
import com.mivisor.tv.ui.design.LiveDot
import com.mivisor.tv.ui.design.LogoPlate
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcon
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvPalette
import com.mivisor.tv.ui.design.MvTag
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.SectionHeader
import com.mivisor.tv.ui.design.Spinner
import com.mivisor.tv.ui.design.TvClickable
import com.mivisor.tv.ui.hhmm

private val SPORT_NAMES = mapOf(
    "futbol" to "Fútbol", "tenis" to "Tenis", "formula1" to "Fórmula 1", "motogp" to "MotoGP",
    "ufc" to "UFC", "baloncesto" to "Baloncesto", "motor" to "Motor"
)

fun sportName(id: String) = SPORT_NAMES[id] ?: id.replaceFirstChar { it.uppercase() }

/** Logo de la competición: el primero que traiga alguno de sus eventos. */
fun competitionLogo(c: Competition, events: List<AgendaEvent>): String? =
    events.firstOrNull { Competition.of(it) == c && it.competitionLogo != null }?.competitionLogo

/**
 * 3i · Agenda para el mando. Arriba, el partido ENFOCADO en grande (estado, equipos, canal recomendado y cuánto suele
 * tardar en arrancar), sobre los colores de los equipos. Debajo, días y deportes en pastillas; luego las competiciones
 * visibles (las ocultas en Ajustes no salen; las seguidas con ★) y una fila por competición con sus partidos por hora,
 * los que están en directo primero. OK: ficha del partido (dónde verlo). Mantener OK: verlo ya.
 */
@Composable
fun AgendaScreen(graph: AppGraph, nav: Nav, toaster: Toaster) {
    val state by graph.agenda.state.collectAsState()
    val lib by graph.providers.state.collectAsState()
    val reminders by graph.reminders.list.collectAsState()
    var sport by remember { mutableStateOf<String?>(null) }
    var day by remember { mutableIntStateOf(0) }
    var comp by remember { mutableStateOf<String?>(null) }
    // El enfocado se lee SOLO en la cabecera: moverse entre tarjetas no recompone la pantalla entera.
    val focusedEvent = remember { mutableStateOf<AgendaEvent?>(null) }
    val first = remember { FocusRequester() }
    val agenda: Agenda? = state.agenda

    if (agenda == null) {
        if (state.loading) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Spinner(); Spacer(Modifier.height(16.dp)); MvText("Descargando la agenda…")
            }
        } else {
            EmptyState("Agenda no disponible", state.error ?: "No se pudo descargar la agenda deportiva y no hay copia guardada.", icon = MvIcons.Calendar) {
                MvButton("Reintentar", { graph.agenda.load(force = true) }, icon = MvIcons.Refresh, primary = true, focusRequester = first)
            }
            InitialFocus(first)
        }
        return
    }

    val now = System.currentTimeMillis()
    val clock = remember { DayClock() }
    val followed = remember(agenda) { graph.prefs.followedCompetitions.toSet() }
    val board = remember(agenda, sport, day, followed) {
        AgendaBoard.build(agenda.events, now, clock, day, sport, followed, agenda.categories, otherName = { sportName(it) })
    }
    val shown = remember(board, comp) { board.groups.filter { comp == null || it.id == comp }.ifEmpty { board.groups } }
    // Canales de cada partido ordenados por fiabilidad (una vez por agenda/biblioteca, no en cada tarjeta).
    val channels = remember(board, lib.library) {
        board.events.associate { ev -> ev.id to channelsFor(ev, lib.library, 4).sortedByDescending { c -> graph.sources.bestScore(c.sources.map { it.contentId }) } }
    }
    val reminded = remember(reminders) { reminders.map { it.eventId }.toSet() }
    val prewarm = com.mivisor.tv.ui.LocalPrewarm.current
    val updated = (if (state.loading) "Actualizando…" else "Actualizada ${ago(state.fetchedAt)}") + if (state.offline) " · sin conexión" else ""

    Column(Modifier.fillMaxSize()) {
        AgendaDetail(focusedEvent, board.events.firstOrNull(), channels, graph, now, updated)
        // Días · deportes (con su número) · actualizar.
        com.mivisor.tv.ui.design.DefaultScroll {
            LazyRow(contentPadding = PaddingValues(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                items((0 until 7).toList(), key = { "d$it" }) { d ->
                    val millis = clock.startOfDay(now, d) + 12 * 3600_000L
                    val label = when (d) {
                        0 -> "Hoy"
                        1 -> "Mañana"
                        else -> clock.format("EEE d", millis).replace(".", "").replaceFirstChar { it.uppercase() }
                    }
                    Pill(label, d == day, PillStyle.DAY, dim = board.perDay[d] == 0, fr = if (d == 0 && board.groups.isEmpty()) first else null) {
                        day = d; comp = null
                    }
                }
                item(key = "sep") { Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(Color(0x24FFFFFF))) }
                item(key = "all") { Pill("Todo", sport == null, PillStyle.SPORT) { sport = null; comp = null } }
                items(board.sports, key = { "s" + it.first }) { (id, n) ->
                    Pill("${sportName(id)} · $n", sport == id, PillStyle.SPORT) { sport = id; comp = null }
                }
                item(key = "refresh") {
                    Pill("Actualizar", false, PillStyle.SPORT) { graph.agenda.load(force = true); toaster.show("Actualizando la agenda…") }
                }
            }
        }
        // Competiciones visibles (sin rótulo: se entiende solo). Las seguidas con ★.
        if (board.groups.size > 1) com.mivisor.tv.ui.design.DefaultScroll {
            LazyRow(Modifier.padding(top = 10.dp), contentPadding = PaddingValues(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "all") { Pill("Todas", comp == null, PillStyle.COMPETITION) { comp = null } }
                items(board.groups, key = { it.id }) { g ->
                    Pill((if (g.followed) "★ " else "") + "${g.name} · ${g.events.size}", comp == g.id, PillStyle.COMPETITION) {
                        comp = if (comp == g.id) null else g.id
                    }
                }
            }
        }
        agenda.notices(sport).forEach { (sp, status) ->
            Box(Modifier.padding(top = 10.dp)) { Banner(sourceNoticeText(sportName(sp).lowercase(), status)) }
        }
        if (board.groups.isEmpty()) {
            MvText(if (day == 0) "No hay eventos en directo ni próximos hoy." else "No hay eventos este día.", MvType.Body, color = MvColors.Text2,
                modifier = Modifier.padding(horizontal = MvDims.SafeX, vertical = 24.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { com.mivisor.tv.ui.design.PivotScroll {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                shown.forEachIndexed { gi, g ->
                    item(key = g.id) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = MvDims.SafeX).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (g.followed) { MvIcon(MvIcons.Star, size = 16.dp, tint = MvColors.Accent); Spacer(Modifier.width(8.dp)) }
                                MvText(g.name, MvType.Heading, maxLines = 1)
                                Spacer(Modifier.width(12.dp))
                                MvText(listOfNotNull(
                                    if (g.live > 0) "${g.live} en directo" else null,
                                    if (g.later > 0) "${g.later} " + (if (g.live > 0) "más " else "") + (if (day == 0) "hoy" else if (day == 1) "mañana" else "este día") else null
                                ).joinToString(" · "), MvType.Small, color = MvColors.Text3, maxLines = 1)
                            }
                            com.mivisor.tv.ui.design.DefaultScroll {
                                LazyRow(contentPadding = PaddingValues(horizontal = MvDims.SafeX), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(g.events, key = { it.id }) { ev ->
                                        val chs = channels[ev.id].orEmpty()
                                        AgendaCard(ev, now, chs.firstOrNull()?.name, ev.id in reminded,
                                            fr = if (gi == 0 && ev === g.events.first()) first else null,
                                            onFocus = { focusedEvent.value = ev; prewarm(chs.firstOrNull()) },
                                            onClick = { nav.go(Route.Event(ev.id)) },
                                            onLongClick = { chs.firstOrNull()?.let { nav.go(Route.Player(it.id)) } ?: nav.go(Route.Event(ev.id)) })
                                    }
                                    if (!g.id.startsWith(CompetitionNames.PREFIX + "deporte:")) item(key = "more") {
                                        MoreTile { nav.go(Route.Competition(g.id)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } }
    }
    InitialFocus(first)
}

private enum class PillStyle { DAY, SPORT, COMPETITION }

/** Pastilla de filtro: días (champán al elegir), deportes (marfil) y competiciones (con borde). */
@Composable
private fun Pill(text: String, selected: Boolean, style: PillStyle, dim: Boolean = false, fr: FocusRequester? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val outlined = style == PillStyle.COMPETITION
    val bg = when {
        !selected -> if (outlined) Color.Transparent else Color(0x0FFFFFFF)
        style == PillStyle.DAY -> MvColors.Accent
        style == PillStyle.SPORT -> MvColors.Text
        else -> Color(0x1FE9C99E)
    }
    TvClickable(
        onClick = onClick, focusRequester = fr, shape = shape, focusedScale = 1.05f, color = bg,
        focusedColor = if (selected && !outlined) bg else MvColors.Surface3, ringColor = MvColors.Accent2,
        modifier = if (outlined) Modifier.border(1.dp, if (selected) MvColors.Accent else Color(0x24FFFFFF), shape) else Modifier
    ) { focused ->
        val fg = when {
            selected && !outlined -> MvColors.Bg
            selected -> MvColors.Accent
            focused -> MvColors.Text
            dim -> MvColors.Text3
            outlined -> MvColors.Text
            else -> MvColors.Text2
        }
        MvText(text, MvType.SmallStrong, color = fg, maxLines = 1,
            modifier = Modifier.padding(horizontal = if (outlined) 14.dp else 16.dp, vertical = if (outlined) 6.dp else 7.dp))
    }
}

private fun whenText(ev: AgendaEvent, now: Long, clock: DayClock): String = when {
    ev.status(now) == EventStatus.LIVE -> "EN DIRECTO"
    ev.timeTbd -> "HORA POR CONFIRMAR"
    else -> when (clock.dayIndex(now, ev.startMillis)) {
        -1, 0 -> "HOY " + hhmm(ev.startMillis)
        1 -> "MAÑANA " + hhmm(ev.startMillis)
        else -> clock.format("EEE d", ev.startMillis).replace(".", "").uppercase() + " " + hhmm(ev.startMillis)
    }
}

/** Cabecera: el partido enfocado en grande, sobre los colores de los dos equipos. */
@Composable
private fun AgendaDetail(
    focused: androidx.compose.runtime.State<AgendaEvent?>,
    fallback: AgendaEvent?,
    channels: Map<String, List<com.mivisor.tv.catalog.tv.TvChannel>>,
    graph: AppGraph,
    now: Long,
    updated: String
) {
    val ev = focused.value?.takeIf { f -> channels.containsKey(f.id) } ?: fallback
    val clock = remember { DayClock() }
    val (h1, _) = MvPalette.pair(ev?.home ?: ev?.title ?: "")
    val (a1, _) = MvPalette.pair(ev?.away ?: ev?.sport ?: "")
    Box(Modifier.fillMaxWidth().height(138.dp)
        .background(Brush.horizontalGradient(listOf(h1.copy(alpha = 0.30f), Color.Transparent, a1.copy(alpha = 0.24f))))
        .background(Brush.verticalGradient(listOf(Color.Transparent, MvColors.Bg)))) {
        Column(Modifier.fillMaxSize().padding(horizontal = MvDims.SafeX, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ev != null) {
                    val live = ev.status(now) == EventStatus.LIVE
                    Row(Modifier.background(if (live) Color(0x2EE58C83) else Color(0x24E8B86D), RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (live) { Box(Modifier.size(6.dp).background(MvColors.Live, androidx.compose.foundation.shape.CircleShape)); Spacer(Modifier.width(6.dp)) }
                        MvText(whenText(ev, now, clock), MvType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.6.sp),
                            color = if (live) Color(0xFFF2B1AA) else MvColors.Soon)
                    }
                    Spacer(Modifier.width(12.dp))
                    MvText((ev.competition ?: sportName(ev.sport)).uppercase(), MvType.Caption.copy(letterSpacing = 0.4.sp), color = MvColors.Text2, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                MvText(updated, MvType.Caption, color = MvColors.Text3, maxLines = 1)
            }
            if (ev == null) MvText("Agenda", MvType.Title)
            else EventLines(ev, channels[ev.id].orEmpty(), graph)
        }
    }
}

@Composable
private fun EventLines(ev: AgendaEvent, mine: List<com.mivisor.tv.catalog.tv.TvChannel>, graph: AppGraph) {
            MvText(if (ev.isMatch) "${ev.home} – ${ev.away}" else ev.title, MvType.Title, maxLines = 1)
            val best = mine.firstOrNull()
            val line = if (best != null) {
                val start = graph.sources.typicalStart(best.sources.map { it.contentId })
                listOfNotNull(best.name, if (start != null) "recomendado, arranca en ~$start s" else "recomendado",
                    if (mine.size > 1) "${mine.size} canales en tus proveedores" else null).joinToString(" · ")
            } else ev.channels.joinToString(" · ") { it.name }.let { if (it.isEmpty()) "Sin canal anunciado: la ficha lo busca" else "No está en tus proveedores · $it" }
            MvText(line, MvType.Body, color = if (best != null) MvColors.Text2 else MvColors.Soon, maxLines = 1)
            MvText("OK: dónde verlo · Mantén OK: verlo ya", MvType.Caption, color = MvColors.Text3)
}

/** Tarjeta de partido de una fila de competición. */
@Composable
private fun AgendaCard(
    ev: AgendaEvent, now: Long, channel: String?, reminded: Boolean, fr: FocusRequester?,
    onFocus: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit
) {
    val live = ev.status(now) == EventStatus.LIVE
    TvClickable(
        onClick = onClick, onLongClick = onLongClick, onFocus = onFocus, focusRequester = fr,
        modifier = Modifier.width(214.dp).height(92.dp), shape = RoundedCornerShape(MvDims.RadiusLarge), focusedScale = 1.04f,
        color = MvColors.Surface1, focusedColor = MvColors.Surface2, ringColor = MvColors.Accent2
    ) { _ ->
        Column(Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    Box(Modifier.size(6.dp).background(MvColors.Live, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(6.dp))
                    MvText("EN DIRECTO", MvType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color(0xFFF2B1AA))
                } else MvText(if (ev.timeTbd) "Por confirmar" else hhmm(ev.startMillis), MvType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = MvColors.Soon)
                Spacer(Modifier.weight(1f))
                if (reminded) MvIcon(MvIcons.Clock, size = 14.dp, tint = MvColors.Accent2)
            }
            MvText(if (ev.isMatch) "${ev.home} – ${ev.away}" else ev.title, MvType.BodyStrong, maxLines = 1)
            MvText(
                channel ?: if (ev.channels.isEmpty()) "Sin canal anunciado" else "No está en tus proveedores",
                MvType.Caption, color = if (channel != null) MvColors.Ok else if (ev.channels.isEmpty()) MvColors.Text3 else MvColors.Soon, maxLines = 1
            )
        }
    }
}

/** Al final de cada fila: la competición entera (todos sus días). */
@Composable
private fun MoreTile(onClick: () -> Unit) {
    TvClickable(
        onClick = onClick, modifier = Modifier.width(120.dp).height(92.dp), shape = RoundedCornerShape(MvDims.RadiusLarge), focusedScale = 1.04f,
        color = Color(0x0AFFFFFF), focusedColor = MvColors.Surface2, ringColor = MvColors.Accent2, contentAlignment = Alignment.Center
    ) { _ ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MvIcon(MvIcons.ChevronRight, size = 20.dp, tint = MvColors.Text2)
            Spacer(Modifier.height(4.dp))
            MvText("Toda la\ncompetición", MvType.Caption, color = MvColors.Text2, maxLines = 2)
        }
    }
}

private fun dayTitle(clock: DayClock, now: Long, millis: Long): String = when (clock.dayIndex(now, millis)) {
    -1, 0 -> "Hoy"
    1 -> "Mañana"
    else -> clock.format("EEEE d 'de' MMMM", millis).replaceFirstChar { it.uppercase() }
}

/**
 * Fila de evento del diseño: a la izquierda DIRECTO (pastilla roja) u hora, luego los dos equipos con su escudo y, a la
 * derecha, la competición y el canal. Si no hay rival, el título. Con el foco se vuelve blanca.
 */
@Composable
fun AgendaEventRow(ev: AgendaEvent, now: Long, bestChannel: String?, reminded: Boolean, onClick: () -> Unit, onFocus: (() -> Unit)? = null) {
    val home = ev.home ?: ev.title
    val (h1, _) = MvPalette.pair(home)
    val (a1, _) = MvPalette.pair(ev.away ?: ev.sport)
    val live = ev.status(now) == EventStatus.LIVE
    TvClickable(
        onClick = onClick, onFocus = onFocus, modifier = Modifier.fillMaxWidth().height(54.dp), focusedScale = 1.02f,
        color = MvColors.Surface1, focusedColor = MvColors.Text, ringColor = MvColors.Accent2
    ) { focused ->
        val fg = if (focused) MvColors.Bg else MvColors.Text
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(72.dp)) {
                if (live) Row(
                    Modifier.background(MvColors.LiveBg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(5.dp).background(MvColors.Live, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(4.dp))
                    MvText("DIRECTO", MvType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = MvColors.Text)
                }
                else MvText(if (ev.timeTbd) "Por conf." else hhmm(ev.startMillis), MvType.Heading, color = fg)
            }
            if (ev.away != null) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Visual(home, ev.homeVisual, h1, 32.dp, ev.isPersonVsPerson)
                    Spacer(Modifier.width(10.dp))
                    MvText(home, MvType.Heading, color = fg, maxLines = 1)
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Visual(ev.away ?: "", ev.awayVisual, a1, 32.dp, ev.isPersonVsPerson)
                    Spacer(Modifier.width(10.dp))
                    MvText(ev.away ?: "", MvType.Heading, color = fg, maxLines = 1)
                }
            } else {
                MvText(ev.title, MvType.Heading, color = fg, maxLines = 1, modifier = Modifier.weight(2f))
            }
            if (reminded) {
                MvIcon(MvIcons.Clock, size = 20.dp, tint = if (focused) MvColors.Bg else MvColors.Accent2)
                Spacer(Modifier.width(12.dp))
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 180.dp)) {
                MvText(ev.competition ?: sportName(ev.sport), MvType.Small, color = if (focused) MvColors.Bg else MvColors.Text2, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (bestChannel != null) MvColors.Ok else MvColors.Text3, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(8.dp))
                    MvText(bestChannel ?: ev.channels.firstOrNull()?.name ?: "Dónde verlo", MvType.Small, color = fg, maxLines = 1)
                }
            }
        }
    }
}

/** Una competición (3c): cabecera y sus eventos por día; en motor, las sesiones del fin de semana. */
@Composable
fun CompetitionScreen(graph: AppGraph, nav: Nav, route: Route.Competition) {
    val state by graph.agenda.state.collectAsState()
    val lib by graph.providers.state.collectAsState()
    val reminders by graph.reminders.list.collectAsState()
    val first = remember { FocusRequester() }
    // 3g · Fija (LALIGA…) o real de la agenda ("raw:copa libertadores").
    val id = route.name
    val competition = Competition.byName(id)
    val raw = CompetitionNames.isRaw(id)
    val all = state.agenda?.events.orEmpty()
    val seen by graph.agenda.competitions.collectAsState()
    if (competition == null && !raw) {
        EmptyState("Competición no disponible", "No se encuentra esta competición en la agenda.") { MvButton("Volver", { nav.back() }, focusRequester = first) }
        InitialFocus(first)
        return
    }
    val now = System.currentTimeMillis()
    val clock = remember { DayClock() }
    val matching = remember(all, id) { all.filter { CompetitionNames.matches(id, it) } }
    val events = remember(matching) { matching.filter { it.status(now) != EventStatus.FINISHED }.sortedBy { it.startMillis } }
    val kind = competition ?: matching.firstOrNull()?.let { Competition.of(it) } ?: Competition.OTHER
    val label = competition?.label ?: matching.firstNotNullOfOrNull { it.competition?.trim()?.takeIf(String::isNotEmpty) }
        ?: seen.firstOrNull { it.id == id }?.name ?: "Competición"
    val summary = remember(matching) { CompetitionSummaries.summary(kind, matching, now, clock) }
    val logo = if (competition != null) competitionLogo(competition, all) else matching.firstNotNullOfOrNull { it.competitionLogo }
    val reminded = reminders.map { it.eventId }.toSet()
    val byDay = remember(events) { events.groupBy { clock.dayIndex(now, it.startMillis).coerceAtLeast(0) }.toSortedMap() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = MvDims.SafeX, end = MvDims.SafeX, bottom = 48.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logo != null) {
                    LogoPlate(Modifier.size(64.dp)) { RemoteImage(logo, 64.dp, Modifier.fillMaxSize()) {} }
                    Spacer(Modifier.width(16.dp))
                }
                Column {
                    MvText(label, MvType.Title)
                    MvText(
                        listOfNotNull(summary.liveText, summary.text.ifEmpty { null }, "${events.size} eventos").joinToString(" · ") +
                            if (kind.template == CompetitionTemplate.RACING) " · sesiones del fin de semana" else "",
                        MvType.Body.copy(color = MvColors.Text2)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // 3f · Seguir la competición: sale en Inicio › «Competiciones seguidas».
            var followed by remember { mutableStateOf(id in graph.prefs.followedCompetitions) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MvButton("Toda la agenda", { nav.back() }, icon = MvIcons.Calendar, focusRequester = first)
                MvButton(if (followed) "Siguiendo" else "Seguir competición", {
                    val list = graph.prefs.followedCompetitions
                    graph.prefs.followedCompetitions = if (followed) list - id else list + id
                    followed = !followed
                }, icon = if (followed) MvIcons.Check else MvIcons.Star, primary = !followed)
            }
        }
        if (events.isEmpty()) item {
            MvText("Sin eventos próximos en la agenda.", MvType.Body, color = MvColors.Text2, modifier = Modifier.padding(top = 20.dp))
        }
        byDay.forEach { (day, list) ->
            item(key = "day-$day") {
                MvText(dayTitle(clock, now, list.first().startMillis), MvType.Heading, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
            }
            items(list, key = { it.id }) { ev ->
                Box(Modifier.padding(vertical = 5.dp)) {
                    AgendaEventRow(ev, now, channelsFor(ev, lib.library, 1).firstOrNull()?.name, ev.id in reminded, onClick = { nav.go(Route.Event(ev.id)) })
                }
            }
        }
    }
    InitialFocus(first, route.name)
}
