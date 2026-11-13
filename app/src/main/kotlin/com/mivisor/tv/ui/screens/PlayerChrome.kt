package com.mivisor.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mivisor.tv.catalog.tv.TvChannel
import com.mivisor.tv.model.AgendaEvent
import com.mivisor.tv.playback.PlaybackController
import com.mivisor.tv.ui.design.LocalUiForm
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcon
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.TvClickable
import com.mivisor.tv.ui.design.UiForm
import com.mivisor.tv.ui.design.rememberLoop
import kotlinx.coroutines.flow.StateFlow

/*
 * 4m · Reproductor rediseñado según el prototipo «Reproductor TV · rediseño» que eligió el usuario, con los colores de
 * MiVisor (azul noche + champán en vez de negro + lima):
 *  - Directo: arriba logo, canal, señal de la fuente y hora; abajo competición, título grande, la línea de directo con
 *    marcas (lo visto, lo descargado y el borde rojo del directo), Pausa, «Volver al directo» y una botonera con
 *    iconos (Fuentes, Audio, Subtítulos, Vídeo, Info, Mi lista).
 *  - Paneles: hoja inferior con pestañas y tarjetas grandes en fila (en vez del panel lateral).
 *  - Carga: título enorme a la izquierda, tres barras de fase y la línea técnica abajo.
 *  - Sin imagen / sin fuentes / motor: pantalla completa con el mismo lenguaje, motivo claro y acciones grandes.
 *  - Corte: tarjeta con el retraso acumulado en grande.
 * Medidas: el prototipo es de 1920 px y la tele trabaja a 1024 dp → ×0,53.
 */

/** Pestañas del reproductor (orden de la hoja inferior). */
internal enum class Panel(val label: String) { SOURCES("Fuentes"), AUDIO("Audio"), SUBS("Subtítulos"), VIDEO("Vídeo"), INFO("Info") }

private val Muted = Color(0xFF8D96A0)
private val Soft = Color(0xFFC9CFD6)
private val Glass = Color(0x1AFFFFFF)

/** Tamaños: tele (prototipo ×0,53) o móvil (más pequeños). */
@Composable
private fun phone() = LocalUiForm.current == UiForm.PHONE

/** «Home vs Away» con el «vs» apagado, o el nombre del canal. */
@Composable
private fun BigTitle(event: AgendaEvent?, channel: TvChannel?, size: androidx.compose.ui.unit.TextUnit, twoLines: Boolean) {
    val st = MvType.Display.copy(fontSize = size, lineHeight = size * 1.0f, fontWeight = FontWeight.Black, letterSpacing = (-0.03f * size.value).sp)
    val home = event?.home
    val away = event?.away
    if (home != null && away != null) {
        if (twoLines) {
            MvText(home, st, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                MvText("vs ", st.copy(color = Muted, fontWeight = FontWeight.Medium), maxLines = 1)
                MvText(away, st, maxLines = 1)
            }
        } else Row(verticalAlignment = Alignment.Bottom) {
            MvText(home, st, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            MvText(" vs ", st.copy(color = Muted, fontWeight = FontWeight.Medium), maxLines = 1)
            MvText(away, st, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        }
    } else MvText(event?.title ?: channel?.name ?: "", st, maxLines = if (twoLines) 2 else 1)
}

/** Línea de contexto en champán y mayúsculas (competición, grupo…). */
@Composable
private fun Eyebrow(text: String, color: Color = MvColors.Accent, size: androidx.compose.ui.unit.TextUnit = 13.sp) {
    MvText(text.uppercase(), MvType.Eyebrow.copy(color = color, fontSize = size, letterSpacing = (size.value * 0.1f).sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
}

/** Logo del canal en baldosa + nombre en mayúsculas espaciadas. */
@Composable
private fun ChannelBadge(channel: TvChannel?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (channel != null) {
            com.mivisor.tv.ui.LogoImage(
                channel.logo, 64.dp, Modifier.size(width = 46.dp, height = 32.dp), RoundedCornerShape(9.dp),
                plain = Color(0xE60E1A27), frame = Color(0x1AFFFFFF), padding = 0.1f
            ) { MvText(channel.initials, MvType.SmallStrong, maxLines = 1) }
            Spacer(Modifier.width(10.dp))
            MvText(channel.name.uppercase(), MvType.SmallStrong.copy(color = Soft, fontSize = 12.sp, letterSpacing = 1.7.sp), maxLines = 1)
        }
    }
}

/** Cuatro barras de señal (0–4 encendidas). */
@Composable
private fun SignalBars(on: Int, color: Color = MvColors.Accent, w: Dp = 3.dp, heights: List<Dp> = listOf(4.dp, 7.dp, 10.dp, 13.dp)) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(heights.last())) {
        heights.forEachIndexed { i, h ->
            Box(Modifier.width(w).height(h).background(if (i < on) color else Color(0x38FFFFFF), RoundedCornerShape(1.dp)))
        }
    }
}

/** Barras de señal de una fuente: banda de Gold o lo que la app ha aprendido. */
internal fun barsOf(row: PlaybackController.SourceRow?): Int = when {
    row == null -> 0
    row.band?.lowercase() == "verde" -> 4
    row.band?.lowercase() == "medio" -> 2
    row.band?.lowercase() == "rojo" -> 1
    row.state == com.mivisor.tv.reliability.SourceState.NORMAL -> 4
    row.state == com.mivisor.tv.reliability.SourceState.NEW -> 3
    row.state == com.mivisor.tv.reliability.SourceState.PROBATION || row.state == com.mivisor.tv.reliability.SourceState.QUARANTINE_DUE -> 2
    else -> 1
}

/** Punto que late (directo, cargando, corte). */
@Composable
private fun PulseDot(color: Color, size: Dp = 8.dp) {
    val loop = rememberLoop(1200)
    Box(Modifier.size(size).drawBehind {
        val a = 0.35f + 0.65f * (1f - kotlin.math.abs(loop.value * 2f - 1f))
        drawCircle(color.copy(alpha = a))
    })
}

/** «−1:05». */
private fun fmtDelay(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "−" + (s / 60) + ":" + (s % 60).toString().padStart(2, '0')
}

/* ───────────────────────────── Directo ───────────────────────────── */

/**
 * Controles sobre el vídeo. Con un panel abierto ([dimmed]) solo queda la barra de arriba: la hoja inferior ocupa
 * el sitio de abajo.
 */
@Composable
internal fun PlayerControls(
    channel: TvChannel,
    event: AgendaEvent?,
    context: String,
    sourceTag: String?,
    signal: Int,
    live: StateFlow<PlaybackController.Live>,
    clock: String,
    paused: Boolean,
    favorite: Boolean,
    hint: String?,
    onGoLive: () -> Unit,
    onJump: () -> Unit,
    pauseFocus: FocusRequester,
    openers: Map<Panel, FocusRequester>,
    dimmed: Boolean,
    onPause: () -> Unit,
    onPanel: (Panel) -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onTouch: () -> Unit,
    onClose: (() -> Unit)? = null
) {
    val l by live.collectAsState()
    val delay = l.delayMs ?: 0
    val behind = l.behind || paused
    val ph = phone()
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onHide() }) }) {
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(118.dp).background(Brush.verticalGradient(listOf(Color(0xCC060B12), Color.Transparent))))
        if (!dimmed) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(if (ph) 260.dp else 340.dp)
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.85f to Color(0xF7060B12))))

        // Arriba: (volver) · logo y canal · señal y fuente · hora.
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = MvDims.SafeX).padding(top = 30.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onClose != null) {
                TvClickable(
                    onClick = onClose, shape = CircleShape, focusedScale = 1f, color = Color(0x1FFFFFFF),
                    focusedColor = MvColors.Surface3, ringColor = MvColors.Accent2,
                    modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center
                ) { _ -> MvIcon(MvIcons.Back, size = 18.dp, tint = MvColors.Text) }
                Spacer(Modifier.width(10.dp))
            }
            ChannelBadge(channel)
            Spacer(Modifier.weight(1f))
            if (sourceTag != null) {
                SignalBars(signal)
                Spacer(Modifier.width(8.dp))
                MvText(sourceTag.uppercase(), MvType.SmallStrong.copy(color = Soft, fontSize = 12.sp, letterSpacing = 1.sp), maxLines = 1)
                Spacer(Modifier.width(18.dp))
            }
            MvText(clock, MvType.Title.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold))
        }

        if (!dimmed) Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = MvDims.SafeX).padding(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(if (ph) 12.dp else 18.dp)
        ) {
            // Competición y título grande.
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Eyebrow(context)
                BigTitle(event, channel, if (ph) 30.sp else 48.sp, twoLines = false)
            }
            // Línea del directo.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.width(if (ph) 92.dp else 122.dp)) {
                    if (!behind) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(MvColors.Live, CircleShape))
                        Spacer(Modifier.width(7.dp))
                        MvText("EN DIRECTO", MvType.BodyStrong.copy(fontSize = if (ph) 12.sp else 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp), maxLines = 1)
                    } else {
                        MvText(fmtDelay(delay), MvType.Display.copy(fontSize = if (ph) 22.sp else 30.sp, lineHeight = if (ph) 24.sp else 32.sp, fontWeight = FontWeight.Black, color = MvColors.Accent), maxLines = 1)
                        MvText(if (paused) "En pausa" else "del directo", MvType.Small.copy(color = Muted), maxLines = 1)
                    }
                }
                LiveTicks(delay, l.aheadMs, Modifier.weight(1f).height(30.dp))
                MvText("DIRECTO", MvType.SmallStrong.copy(color = Color(0xFFFF7A72), fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp), maxLines = 1)
            }
            // Pausa · volver al directo · estado · botonera.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvClickable(
                    onClick = onPause, focusRequester = pauseFocus, onFocus = onTouch, shape = RoundedCornerShape(15.dp), focusedScale = 1.06f,
                    color = MvColors.Text, focusedColor = MvColors.Accent, ringColor = Color.Transparent,
                    contentAlignment = Alignment.Center, modifier = Modifier.size(if (ph) 42.dp else 49.dp)
                ) { _ ->
                    if (paused) MvIcon(MvIcons.Play, size = 22.dp, tint = MvColors.Bg)
                    else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(width = 4.dp, height = 16.dp).background(MvColors.Bg, RoundedCornerShape(1.dp)))
                        Box(Modifier.size(width = 4.dp, height = 16.dp).background(MvColors.Bg, RoundedCornerShape(1.dp)))
                    }
                }
                Spacer(Modifier.width(11.dp))
                if (behind) {
                    ChromeButton("Volver al directo", MvIcons.Live, height = if (ph) 34.dp else 38.dp, onFocus = onTouch, onClick = onGoLive)
                    Spacer(Modifier.width(10.dp))
                }
                if (l.canJump15) {
                    ChromeButton("Adelantar 15 s", MvIcons.ChevronRight, height = if (ph) 34.dp else 38.dp, onFocus = onTouch, onClick = onJump)
                    Spacer(Modifier.width(10.dp))
                }
                MvText(
                    when {
                        paused -> "El directo sigue avanzando"
                        l.behind && l.state == com.mivisor.tv.engine.CatchUpController.State.CATCHING_UP -> "Acercándote al directo poco a poco…"
                        l.behind && l.aheadMs >= 15_000 -> "Lo que falta hasta el directo ya está descargado"
                        l.behind -> "Vas por detrás por un corte: se irá acercando sola"
                        else -> hint ?: ""
                    },
                    MvType.Small.copy(color = Muted), maxLines = 1, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Row(
                    Modifier.background(Glass, RoundedCornerShape(18.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    BarChip("Fuentes", MvIcons.Layers, openers.getValue(Panel.SOURCES), onTouch) { onPanel(Panel.SOURCES) }
                    BarChip("Audio", MvIcons.Volume, openers.getValue(Panel.AUDIO), onTouch) { onPanel(Panel.AUDIO) }
                    BarChip("Subtítulos", MvIcons.Subtitles, openers.getValue(Panel.SUBS), onTouch) { onPanel(Panel.SUBS) }
                    BarChip("Vídeo", MvIcons.Tv, openers.getValue(Panel.VIDEO), onTouch) { onPanel(Panel.VIDEO) }
                    BarChip("Info", MvIcons.Info, openers.getValue(Panel.INFO), onTouch) { onPanel(Panel.INFO) }
                    BarChip(if (favorite) "En Mi lista" else "Mi lista", MvIcons.Star, null, onTouch, highlighted = favorite, onClick = onFavorite)
                }
            }
        }
    }
}

/**
 * Línea del directo con marcas (prototipo): lo ya visto en champán, lo descargado por delante en champán tenue, el
 * punto actual blanco y alto, y la última marca roja (el directo). Escala: 3 min o el retraso, si es mayor.
 */
@Composable
private fun LiveTicks(delayMs: Long, aheadMs: Long, modifier: Modifier) {
    val accent = MvColors.Accent
    val red = MvColors.Live
    Box(modifier.drawBehind {
        val n = 72
        val span = maxOf(180_000L, delayMs + 15_000L).toFloat()
        val pct = 1f - (delayMs / span).coerceIn(0f, 1f)
        val cur = (pct * (n - 1)).toInt()
        val ahead = ((aheadMs / span) * (n - 1)).toInt()
        val step = size.width / (n - 1)
        for (i in 0 until n) {
            val isCur = i == cur
            val h = if (isCur) size.height else if (i % 8 == 0) size.height * 0.54f else size.height * 0.32f
            val w = if (isCur) 3.dp.toPx() else 2.dp.toPx()
            val c = when {
                isCur -> Color.White
                i < cur -> accent
                i == n - 1 -> red
                i <= cur + ahead -> accent.copy(alpha = 0.4f)
                else -> Color(0x4DFFFFFF)
            }
            val x = (i * step - w / 2).coerceIn(0f, size.width - w)
            drawRoundRect(c, Offset(x, (size.height - h) / 2f), Size(w, h), CornerRadius(w / 2, w / 2))
        }
    })
}

/** Botón de la botonera: icono encima y texto; con foco, relleno champán. */
@Composable
private fun BarChip(text: String, icon: Int, focusRequester: FocusRequester?, onTouch: () -> Unit, highlighted: Boolean = false, onClick: () -> Unit) {
    val ph = phone()
    TvClickable(
        onClick = onClick, focusRequester = focusRequester, onFocus = onTouch, shape = RoundedCornerShape(14.dp), focusedScale = 1f,
        color = Color.Transparent, focusedColor = MvColors.Accent, ringColor = Color.Transparent,
        contentAlignment = Alignment.Center, modifier = Modifier.size(width = if (ph) 54.dp else 68.dp, height = if (ph) 46.dp else 53.dp)
    ) { focused ->
        val fg = if (focused) MvColors.Bg else if (highlighted) MvColors.Accent else MvColors.Text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MvIcon(icon, size = if (ph) 16.dp else 18.dp, tint = fg)
            Spacer(Modifier.height(4.dp))
            MvText(text, MvType.Caption.copy(color = fg, fontSize = if (ph) 9.sp else 11.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}

/**
 * Botón grande del reproductor: [primary] = marfil con texto oscuro; si no, cristal con borde fino. Con foco, champán.
 */
@Composable
internal fun ChromeButton(
    text: String,
    icon: Int? = null,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    onFocus: (() -> Unit)? = null,
    height: Dp = 38.dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    TvClickable(
        onClick = onClick, focusRequester = focusRequester, enabled = enabled, onFocus = onFocus, shape = shape, focusedScale = 1.05f,
        color = if (primary) MvColors.Text else Color(0x24FFFFFF), focusedColor = MvColors.Accent, ringColor = Color.Transparent,
        modifier = Modifier.height(height).then(if (!primary) Modifier.border(1.dp, Color(0x33FFFFFF), shape) else Modifier)
    ) { focused ->
        Row(Modifier.fillMaxHeight().padding(horizontal = 19.dp), verticalAlignment = Alignment.CenterVertically) {
            val fg = if (primary || focused) MvColors.Bg else MvColors.Text
            if (icon != null) {
                MvIcon(icon, size = 15.dp, tint = fg)
                Spacer(Modifier.width(8.dp))
            }
            MvText(text, MvType.BodyStrong.copy(color = fg, fontSize = 14.sp), maxLines = 1)
        }
    }
}

/* ───────────────────────────── Hoja de paneles ───────────────────────────── */

/** Una tarjeta de la hoja: dato grande arriba, título, detalle y una etiqueta (EN USO, Estable…). */
internal class PanelCard(
    val big: String,
    val title: String,
    val sub: String,
    val badge: String? = null,
    val badgeColor: Color = Soft,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val bars: Int? = null,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit = {}
)

/** Hoja inferior con pestañas y tarjetas grandes en fila (prototipo). */
@Composable
internal fun PanelSheet(
    current: Panel,
    hint: String,
    cards: List<PanelCard>,
    note: String?,
    firstFocus: FocusRequester,
    onTab: (Panel) -> Unit,
    onClose: () -> Unit
) {
    val ph = phone()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.22f to Color(0xF2060B12), 1f to MvColors.Bg))
                .padding(start = MvDims.SafeX, end = MvDims.SafeX, top = 60.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Panel.values().forEach { p ->
                    val sel = p == current
                    TvClickable(
                        onClick = { onTab(p) }, shape = RoundedCornerShape(17.dp), focusedScale = 1f,
                        color = if (sel) MvColors.Text else Color.Transparent, focusedColor = if (sel) Color.White else Color(0x29FFFFFF),
                        ringColor = MvColors.Accent2, focusRequester = if (sel && cards.isEmpty()) firstFocus else null,
                        modifier = Modifier.height(34.dp)
                    ) { _ ->
                        Box(Modifier.fillMaxHeight().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            MvText(p.label, MvType.BodyStrong.copy(color = if (sel) MvColors.Bg else Soft, fontSize = 14.sp), maxLines = 1)
                        }
                    }
                }
                MvText(hint, MvType.Small.copy(color = Muted, textAlign = TextAlign.End), maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                TvClickable(
                    onClick = onClose, shape = CircleShape, focusedScale = 1f, color = Color(0x1FFFFFFF),
                    focusedColor = MvColors.Accent, ringColor = Color.Transparent,
                    modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center
                ) { f -> MvIcon(MvIcons.Close, size = 15.dp, tint = if (f) MvColors.Bg else MvColors.Text) }
            }
            if (cards.isEmpty()) MvText(note ?: "Nada que elegir aquí.", MvType.Body.copy(color = Muted))
            else {
                com.mivisor.tv.ui.design.DefaultScroll {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        itemsIndexed(cards) { i, c -> SheetCard(c, if (i == 0) firstFocus else null, if (ph) 150.dp else 196.dp) }
                    }
                }
                if (note != null) MvText(note, MvType.Caption.copy(color = Muted), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SheetCard(c: PanelCard, fr: FocusRequester?, width: Dp) {
    val shape = RoundedCornerShape(15.dp)
    TvClickable(
        onClick = c.onClick, onLongClick = c.onLongClick, enabled = c.enabled, focusRequester = fr, shape = shape, focusedScale = 1.03f,
        color = if (c.selected) Color(0x24FFFFFF) else Color(0x12FFFFFF), focusedColor = Color(0x38FFFFFF), ringColor = MvColors.Accent2,
        modifier = Modifier.width(width).height(144.dp)
            .then(if (c.selected) Modifier.border(2.dp, MvColors.Accent, shape) else Modifier)
            .then(if (c.enabled) Modifier else Modifier.alpha(0.45f))
    ) { _ ->
        Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Top) {
                MvText(c.big, MvType.Display.copy(fontSize = 30.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp),
                    maxLines = 1, modifier = Modifier.weight(1f))
                c.bars?.let { SignalBars(it, w = 4.dp, heights = listOf(5.dp, 8.dp, 11.dp, 15.dp)) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                MvText(c.title, MvType.BodyStrong.copy(fontSize = 14.sp), maxLines = 1)
                if (c.sub.isNotEmpty()) MvText(c.sub, MvType.Small.copy(color = Muted), maxLines = 2)
                val badge = if (c.selected) "EN USO" else c.badge
                if (!badge.isNullOrEmpty()) MvText(
                    badge.uppercase(),
                    MvType.Caption.copy(color = if (c.selected) MvColors.Accent else c.badgeColor, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, fontSize = 10.sp),
                    maxLines = 1
                )
            }
        }
    }
}

/* ───────────────────────────── Carga ───────────────────────────── */

/** Fondo de las pantallas de carga y aviso: noche con un toque de color y las líneas de un campo, muy tenues. */
@Composable
private fun StageBackground(tint: Color, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(tint, Color(0xFF0B1622), Color(0xFF060B12)), center = Offset(1500f, 420f), radius = 1300f))
            .drawBehind {
                val w = size.width
                val h = size.height
                val s = Stroke(width = 2.5.dp.toPx())
                val c = Color.White.copy(alpha = 0.06f)
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.65f, h * 0.19f); lineTo(w * 0.52f, h * 0.67f); lineTo(w * 0.99f, h * 0.67f); lineTo(w * 0.99f, h * 0.19f); close()
                }
                drawPath(p, c, style = s)
                drawLine(c, Offset(w * 0.755f, h * 0.19f), Offset(w * 0.755f, h * 0.67f), s.width)
            }
    ) { content() }
}

/**
 * Carga (prototipo «Cargando canal»): logo y canal arriba; a la izquierda EN DIRECTO, el partido en grande y la
 * competición; abajo, qué está pasando, tres barras de fase y la línea técnica.
 */
@Composable
internal fun LoadingStage(
    channel: TvChannel?,
    event: AgendaEvent?,
    /** 0 motor/buscar, 1 emisores, 2 imagen. */
    step: Int,
    sourceIndex: Int,
    status: String,
    hint: String?,
    detail: String?,
    extra: @Composable () -> Unit = {}
) {
    val ph = phone()
    StageBackground(Color(0xFF1C2E42)) {
        Box(Modifier.align(Alignment.TopStart).padding(start = MvDims.SafeX, top = 40.dp)) { ChannelBadge(channel) }
        Column(
            Modifier.align(Alignment.TopStart).padding(start = MvDims.SafeX, end = MvDims.SafeX, top = if (ph) 110.dp else 150.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(if (event != null) MvColors.Live else MvColors.Accent)
                Spacer(Modifier.width(8.dp))
                MvText(
                    if (event != null) "EN DIRECTO" else "CONECTANDO",
                    MvType.SmallStrong.copy(color = if (event != null) Color(0xFFFF7A72) else MvColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                )
            }
            Column(Modifier.widthIn(max = 760.dp)) { BigTitle(event, channel, if (ph) 44.sp else 76.sp, twoLines = true) }
            val ctx = event?.competition?.takeIf { it.isNotBlank() } ?: event?.let { sportName(it.sport) }
                ?: channel?.brandName ?: "Televisión en directo"
            Eyebrow(ctx, size = 14.sp)
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = MvDims.SafeX).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MvText(status, MvType.BodyStrong.copy(fontSize = 15.sp), maxLines = 1)
                if (hint != null) MvText(hint, MvType.Small.copy(color = Muted), maxLines = 2, modifier = Modifier.widthIn(max = 640.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf("Buscando el canal", "Conectando fuente ${sourceIndex.coerceAtLeast(1)}", "Iniciando vídeo").forEachIndexed { i, label ->
                    PhaseBar(label, i < step, i == step, if (ph) 96.dp else 118.dp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { extra() }
                if (detail != null) MvText(detail, MvType.Small.copy(color = Muted), maxLines = 1)
            }
        }
    }
}

@Composable
private fun PhaseBar(label: String, done: Boolean, running: Boolean, width: Dp) {
    val loop = rememberLoop(1400)
    val accent = MvColors.Accent
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().height(4.dp).background(Color(0x2EFFFFFF), RoundedCornerShape(2.dp)).drawBehind {
                when {
                    done -> drawRoundRect(accent, cornerRadius = CornerRadius(2.dp.toPx()))
                    running -> {
                        // Se va llenando y vuelve a empezar: se ve que avanza.
                        val e = androidx.compose.animation.core.FastOutSlowInEasing.transform(loop.value)
                        drawRoundRect(accent.copy(alpha = 0.8f), size = Size(size.width * (0.15f + 0.85f * e), size.height), cornerRadius = CornerRadius(2.dp.toPx()))
                    }
                }
            }
        )
        MvText(
            label, MvType.Small.copy(color = if (done || running) MvColors.Text else Muted, fontWeight = if (running) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 1
        )
    }
}

/* ───────────────────────────── Avisos ───────────────────────────── */

/** Tipo de aviso: color del punto y del fondo. */
internal enum class Alert(val color: Color, val tint: Color) {
    /** No hay imagen o no se puede reproducir. */
    ERROR(Color(0xFFFF7A72), Color(0xFF3A1A22)),
    /** Fuentes descansando, motor, sin red: hay que esperar o elegir. */
    WAIT(Color(0xFFF2B866), Color(0xFF33281A)),
    /** Informativo (canal no disponible…). */
    INFO(Color(0xFFE9C99E), Color(0xFF1C2E42))
}

/**
 * 4m · Pantalla de aviso (sin imagen, sin fuentes, motor, canal no disponible) con el mismo lenguaje que la carga:
 * qué pasa en una línea de color, el titular grande, la explicación y acciones grandes. Atrás siempre sale.
 */
@Composable
internal fun AlertStage(
    channel: TvChannel?,
    kind: Alert,
    eyebrow: String,
    title: String,
    body: String,
    tech: String? = null,
    actions: @Composable () -> Unit
) {
    val ph = phone()
    StageBackground(kind.tint) {
        Box(Modifier.align(Alignment.TopStart).padding(start = MvDims.SafeX, top = 40.dp)) { ChannelBadge(channel) }
        Column(
            Modifier.align(Alignment.CenterStart).padding(horizontal = MvDims.SafeX).padding(top = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(kind.color, CircleShape))
                Spacer(Modifier.width(8.dp))
                MvText(eyebrow.uppercase(), MvType.SmallStrong.copy(color = kind.color, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp), maxLines = 1)
            }
            MvText(title, MvType.Display.copy(fontSize = if (ph) 30.sp else 46.sp, lineHeight = if (ph) 33.sp else 50.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
                maxLines = 2, modifier = Modifier.widthIn(max = 760.dp))
            MvText(body, MvType.Body.copy(color = Soft, fontSize = 16.sp, lineHeight = 23.sp), maxLines = 4, modifier = Modifier.widthIn(max = 620.dp))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
        }
        if (tech != null) MvText(tech, MvType.Small.copy(color = Muted), maxLines = 1,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = MvDims.SafeX, bottom = 28.dp))
    }
}

/**
 * 4m · Corte con la imagen en marcha: tarjeta abajo a la derecha con el retraso acumulado en grande (la imagen sigue
 * donde estaba; no se salta al directo).
 */
@Composable
internal fun StallCard(
    text: String,
    live: StateFlow<PlaybackController.Live>,
    caption: String,
    modifier: Modifier,
    buttons: @Composable () -> Unit
) {
    val l by live.collectAsState()
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.width(330.dp).background(Color(0xE6060B12), shape).border(1.dp, Color(0x1AFFFFFF), shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(Alert.WAIT.color)
            Spacer(Modifier.width(8.dp))
            MvText(text.substringBefore(" · ").uppercase(), MvType.SmallStrong.copy(color = Alert.WAIT.color, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            l.delayMs?.let { d ->
                MvText(fmtDelay(d), MvType.Display.copy(fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, color = MvColors.Accent))
                Spacer(Modifier.width(10.dp))
            }
            MvText("Sigues donde estabas · no te pierdes nada", MvType.Small.copy(color = Soft), maxLines = 2, modifier = Modifier.padding(bottom = 3.dp))
        }
        MvText(caption, MvType.Caption.copy(color = Muted), maxLines = 3)
        buttons()
    }
}
