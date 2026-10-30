package com.mivisor.tv.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tono del rótulo del canal (el del prototipo): claro, oscuro, azul o rojo. */
enum class PlateTone { LIGHT, DARK, BLUE, RED }

private fun plateBrush(tone: PlateTone): Brush = when (tone) {
    PlateTone.LIGHT -> Brush.verticalGradient(listOf(Color(0xFFEEF1F5), Color(0xFFCFD6DF)))
    PlateTone.DARK -> Brush.verticalGradient(listOf(Color(0xFF232A36), Color(0xFF151A22)))
    PlateTone.BLUE -> Brush.linearGradient(listOf(Color(0xFF1452C8), Color(0xFF2E88FF)))
    PlateTone.RED -> Brush.linearGradient(listOf(Color(0xFFC8102E), Color(0xFFFF4D5E)))
}

private fun plateInk(tone: PlateTone) = if (tone == PlateTone.LIGHT) Color(0xFF0A0D12) else Color.White

/** Resplandor del fondo de la tarjeta según el tono del rótulo. */
fun plateGlow(tone: PlateTone): Color = when (tone) {
    PlateTone.LIGHT -> Color(0x24FFFFFF)
    PlateTone.DARK -> Color(0x593D8BFF)
    PlateTone.BLUE -> Color(0x592E88FF)
    PlateTone.RED -> Color(0x4DFF4D5E)
}

/** Rótulo del canal: marca arriba y lo que lo distingue debajo («DAZN / LALIGA»). Escala con su ancho. */
@Composable
fun BrandPlate(top: String, bottom: String?, tone: PlateTone, modifier: Modifier = Modifier, scale: Float = 1f) {
    val ink = plateInk(tone)
    Box(
        modifier
            .background(plateBrush(tone), RoundedCornerShape((10 * scale).dp))
            .then(if (tone == PlateTone.DARK) Modifier.border(1.dp, Color(0x14FFFFFF), RoundedCornerShape((10 * scale).dp)) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            MvText(
                top, MvType.Title.copy(fontSize = (if (top.length > 6) 17 else 22).times(scale).sp, lineHeight = (24 * scale).sp,
                    fontWeight = FontWeight.Black, color = ink, textAlign = TextAlign.Center), maxLines = 1
            )
            if (bottom != null) MvText(
                bottom, MvType.Caption.copy(fontSize = (12 * scale).sp, lineHeight = (14 * scale).sp, fontWeight = FontWeight.Bold,
                    color = ink, letterSpacing = (0.5 * scale).sp, textAlign = TextAlign.Center), maxLines = 1
            )
        }
    }
}

/**
 * Tarjeta de canal del prototipo: fondo con resplandor y el rótulo en el centro; debajo, nombre y estado con punto.
 * El foco (anillo blanco y escala) va en la tarjeta.
 */
@Composable
fun ChannelTile(
    name: String,
    plateTop: String,
    plateBottom: String?,
    tone: PlateTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    status: String? = null,
    statusColor: Color = MvColors.Text3,
    favorite: Boolean = false,
    multiaudio: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    /** Logo real (3c) en lugar del rótulo de texto; recibe el tamaño del rótulo. */
    art: (@Composable (Modifier) -> Unit)? = null
) {
    Column(modifier.width(width)) {
        TvClickable(
            onClick = onClick, onLongClick = onLongClick, onFocus = onFocus, focusRequester = focusRequester,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
            shape = RoundedCornerShape(MvDims.Radius),
            color = MvColors.Surface2, focusedColor = MvColors.Surface3
        ) { _ ->
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(plateGlow(tone), Color.Transparent))))
            val plateModifier = Modifier.align(Alignment.Center).fillMaxWidth(0.62f).aspectRatio(2.3f)
            if (art != null) art(plateModifier) else BrandPlate(plateTop, plateBottom, tone, plateModifier, scale = width.value / 200f)
            if (multiaudio) Box(Modifier.align(Alignment.TopStart).padding(8.dp)) { MvTag("Multiaudio", MvColors.Accent2) }
            if (favorite) Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { MvIcon(MvIcons.Star, size = 16.dp, tint = MvColors.Accent2) }
        }
        Spacer(Modifier.height(10.dp))
        MvText(name, MvType.BodyStrong, maxLines = 1)
        if (status != null) StatusLine(status, statusColor)
    }
}

/** «● Funciona · arranca en ~3 s». */
@Composable
fun StatusLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LiveDot(color, 7.dp)
        Spacer(Modifier.width(6.dp))
        MvText(text, MvType.Caption, maxLines = 1)
    }
}

/** Tarjeta grande de grupo (marca) de Canales. */
@Composable
fun GroupTile(
    name: String,
    plateTop: String,
    plateBottom: String?,
    tone: PlateTone,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    art: (@Composable (Modifier) -> Unit)? = null
) {
    ChannelTile(
        name = name, plateTop = plateTop, plateBottom = plateBottom, tone = tone, onClick = onClick, modifier = modifier,
        width = 300.dp, status = detail, statusColor = MvColors.Ok, focusRequester = focusRequester, art = art
    )
}

/** Fondo claro y redondeado donde se pintan los logos reales (pensados para fondo blanco). */
@Composable
fun LogoPlate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.background(Brush.verticalGradient(listOf(Color(0xFFF2F4F7), Color(0xFFDCE1E8))), RoundedCornerShape(10.dp)).padding(6.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Tarjeta de temática (Fútbol, Generalistas…): bloque de color con título y número de canales. */
@Composable
fun ThemeTile(title: String, detail: String, colors: List<Color>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvClickable(
        onClick = onClick, modifier = modifier.width(300.dp).height(120.dp), shape = RoundedCornerShape(MvDims.Radius),
        color = colors.first(), focusedColor = colors.first()
    ) { _ ->
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)))
        Column(Modifier.padding(20.dp)) {
            MvText(title, MvType.Heading.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Spacer(Modifier.height(4.dp))
            MvText(detail, MvType.Small.copy(color = Color(0xD9FFFFFF)), maxLines = 1)
        }
    }
}

/** Escudo de texto (siglas) para equipos y jugadores. */
@Composable
fun Crest(text: String, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size)
            .background(Brush.radialGradient(listOf(color, color.copy(alpha = 0.55f))), CircleShape)
            .border(2.dp, Color(0x33FFFFFF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        MvText(text, MvType.Title.copy(fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Black, color = Color(0xF2FFFFFF)), maxLines = 1)
    }
}

/** Colores estables por nombre (equipo, temática): pares de un mismo tono. */
object MvPalette {
    private val PAIRS = listOf(
        Color(0xFF17B05F) to Color(0xFF0A6B39), Color(0xFFE8323B) to Color(0xFF9B1018), Color(0xFF2F6FD6) to Color(0xFF163A7A),
        Color(0xFFD2282E) to Color(0xFF1D3B8F), Color(0xFFF39A1E) to Color(0xFF6B3A08), Color(0xFF6CABDD) to Color(0xFF2B6AA0),
        Color(0xFF8E44AD) to Color(0xFF4A1C61), Color(0xFF5B5F6B) to Color(0xFF2B2E35)
    )
    fun pair(key: String): Pair<Color, Color> = PAIRS[(key.hashCode() and 0x7fffffff) % PAIRS.size]

    private val THEMES = mapOf(
        "cat:sports" to (Color(0xFF14663A) to Color(0xFF0B3A22)),
        "cat:movies" to (Color(0xFF7A2E0E) to Color(0xFF3F1606)),
        "cat:kids" to (Color(0xFF1E6B8C) to Color(0xFF0D3446)),
        "cat:documentary" to (Color(0xFF6B5A16) to Color(0xFF332A08)),
        "cat:music" to (Color(0xFF7A1E5A) to Color(0xFF3A0D2B)),
        "cat:news" to (Color(0xFF1B3A6B) to Color(0xFF0D1D36)),
        "cat:general" to (Color(0xFF243B6B) to Color(0xFF111D36)),
        "otros" to (Color(0xFF4B2A8C) to Color(0xFF241345)),
        "links" to (Color(0xFF2B4A55) to Color(0xFF13252B))
    )
    fun theme(key: String): List<Color> = (THEMES[key] ?: pair(key)).let { listOf(it.first, it.second) }
}

@Suppress("unused")
private fun BoxScope.keepScope() = Unit
