package com.mivisor.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mivisor.tv.catalog.tv.ChannelPlates
import com.mivisor.tv.catalog.tv.Plate
import com.mivisor.tv.catalog.tv.TvChannel
import com.mivisor.tv.data.SourceBookImpl
import com.mivisor.tv.ui.design.ChannelTile
import com.mivisor.tv.ui.design.PlateTone
import androidx.compose.ui.unit.Dp
import com.mivisor.tv.catalog.tv.TvLibrary
import com.mivisor.tv.model.AgendaEvent
import com.mivisor.tv.model.EventStatus
import com.mivisor.tv.playback.PlayRequest
import com.mivisor.tv.playback.PlaySource
import com.mivisor.tv.reliability.SourceState
import com.mivisor.tv.ui.design.BrandPlate
import com.mivisor.tv.ui.design.LogoPlate
import com.mivisor.tv.ui.RemoteImage
import androidx.compose.foundation.layout.fillMaxSize
import com.mivisor.tv.ui.design.LiveDot
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvTag
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.SectionHeader
import com.mivisor.tv.ui.design.TvClickable
import com.mivisor.tv.ui.hhmm

fun TvChannel.request(): PlayRequest =
    PlayRequest(id, name, sources.map { PlaySource(it.contentId, it.providerId, it.providerName, it.quality, it.qualityLabel) })

fun TvChannel.subtitle(): String {
    val n = sources.size
    val q = when (bestQuality) { 4 -> " · 4K"; 3 -> " · FHD"; 2 -> " · HD"; else -> "" }
    return (if (n == 1) "1 fuente" else "$n fuentes") + q + if (multiaudio) " · Multiaudio" else ""
}

fun stateLabel(s: SourceState): Pair<String, androidx.compose.ui.graphics.Color> = when (s) {
    SourceState.NEW -> "Sin probar" to MvColors.Text3
    SourceState.NORMAL -> "Funciona" to MvColors.Ok
    SourceState.PAUSED -> "En pausa" to MvColors.Soon
    SourceState.PROBATION -> "A prueba" to MvColors.Soon
    SourceState.QUARANTINE -> "En cuarentena" to MvColors.Bad
    SourceState.QUARANTINE_DUE -> "Se revisará" to MvColors.Soon
    SourceState.INCOMPATIBLE -> "No compatible" to MvColors.Bad
}

fun TvChannel.plate(): Plate = ChannelPlates.of(name, brandKey)

fun Plate.Tone.ui(): PlateTone = when (this) {
    Plate.Tone.LIGHT -> PlateTone.LIGHT
    Plate.Tone.DARK -> PlateTone.DARK
    Plate.Tone.BLUE -> PlateTone.BLUE
    Plate.Tone.RED -> PlateTone.RED
}

/** Estado del canal para la tarjeta: lo mejor que se sabe de sus fuentes (sin ninguna comprobación nueva). */
fun channelStatus(ch: TvChannel, book: SourceBookImpl): Pair<String, androidx.compose.ui.graphics.Color> {
    val states = ch.sources.map { book.state(it.contentId) }
    val n = ch.sources.size
    val fuentes = if (n == 1) "1 fuente" else "$n fuentes"
    return when {
        states.any { it == SourceState.NORMAL } -> "Funciona · $fuentes" to MvColors.Ok
        states.all { it == SourceState.NEW } -> "Sin probar · $fuentes" to MvColors.Text3
        states.any { it == SourceState.NEW || it == SourceState.PROBATION || it == SourceState.QUARANTINE_DUE } -> "A prueba · $fuentes" to MvColors.Soon
        states.any { it == SourceState.PAUSED } -> "En pausa · $fuentes" to MvColors.Soon
        else -> "Sin fuentes disponibles" to MvColors.Bad
    }
}

/** Tarjeta de canal con su rótulo, estado y favorito. */
@Composable
fun TvChannelTile(
    ch: TvChannel,
    book: SourceBookImpl,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    width: Dp = 200.dp,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    val plate = ch.plate()
    val (status, color) = channelStatus(ch, book)
    ChannelTile(
        name = ch.name, plateTop = plate.top, plateBottom = plate.bottom, tone = plate.tone.ui(), onClick = onClick,
        width = width, status = status, statusColor = color, favorite = favorite, multiaudio = ch.multiaudio,
        onLongClick = onLongClick, focusRequester = focusRequester,
        art = ch.logo?.let { url -> @Composable { m: Modifier -> ChannelArt(url, plate, m, width) } }
    )
}

/** Logo real sobre el fondo claro; si no carga, el rótulo de texto de siempre. */
@Composable
fun ChannelArt(url: String?, plate: Plate, modifier: Modifier, sizeHint: Dp = 200.dp) {
    if (url == null) {
        BrandPlate(plate.top, plate.bottom, plate.tone.ui(), modifier)
        return
    }
    LogoPlate(modifier) {
        RemoteImage(url, sizeHint, Modifier.fillMaxSize()) {
            BrandPlate(plate.top, plate.bottom, plate.tone.ui(), Modifier.fillMaxSize())
        }
    }
}

@Composable
fun ChannelLogo(ch: TvChannel, modifier: Modifier, sizeHint: Dp = 120.dp) {
    val plate = ch.plate()
    if (ch.logo == null) BrandPlate(plate.top, plate.bottom, plate.tone.ui(), modifier, scale = sizeHint.value / 200f)
    else LogoPlate(modifier) { RemoteImage(ch.logo, sizeHint, Modifier.fillMaxSize()) { BrandPlate(plate.top, plate.bottom, plate.tone.ui(), Modifier.fillMaxSize(), scale = sizeHint.value / 200f) } }
}

/** Canales de tus proveedores para un evento de la agenda (por los nombres de canal del evento). */
fun channelsFor(event: AgendaEvent, library: TvLibrary, limit: Int = 6): List<TvChannel> =
    event.channels.flatMap { library.forAgenda(it.name, 3) }.distinctBy { it.id }.take(limit)

/** Fila horizontal de canales. */
@Composable
fun ChannelRow(
    title: String,
    channels: List<TvChannel>,
    book: SourceBookImpl,
    favorites: Set<String>,
    onOpen: (TvChannel) -> Unit,
    onLong: ((TvChannel) -> Unit)? = null,
    detail: String? = null
) {
    if (channels.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        SectionHeader(title, Modifier.padding(start = MvDims.SafeX), detail)
        LazyRow(
            contentPadding = PaddingValues(horizontal = MvDims.SafeX),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(channels, key = { it.id }) { ch ->
                TvChannelTile(ch, book, favorite = ch.id in favorites, onClick = { onOpen(ch) }, onLongClick = onLong?.let { f -> { f(ch) } })
            }
        }
    }
}

/** Tarjeta de evento de la agenda. */
@Composable
fun EventCard(event: AgendaEvent, now: Long, available: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TvClickable(onClick = onClick, modifier = modifier.width(320.dp), color = MvColors.Surface1, focusedColor = MvColors.Surface3) { _ ->
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (event.status(now)) {
                    EventStatus.LIVE -> { LiveDot(); Spacer(Modifier.width(8.dp)); MvText("EN DIRECTO", MvType.Caption, color = MvColors.Live) }
                    EventStatus.UPCOMING -> MvText(if (event.timeTbd) "Hora por confirmar" else hhmm(event.startMillis), MvType.Caption, color = MvColors.Soon)
                    EventStatus.FINISHED -> MvText("Terminado", MvType.Caption)
                }
                Spacer(Modifier.weight(1f))
                MvText(event.competition ?: event.sport, MvType.Caption, maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            MvText(event.title, MvType.BodyStrong, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            MvText(
                if (available > 0) "$available canal(es) en tus proveedores" else event.channels.joinToString(" · ") { it.name }.ifEmpty { "Sin canales anunciados" },
                MvType.Caption, color = if (available > 0) MvColors.Accent2 else MvColors.Text3, maxLines = 1
            )
        }
    }
}

@Composable
fun Banner(text: String, color: androidx.compose.ui.graphics.Color = MvColors.Soon) {
    Row(
        Modifier.padding(horizontal = MvDims.SafeX).padding(bottom = 16.dp).fillMaxWidth()
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MvTag("AVISO", color)
        Spacer(Modifier.width(12.dp))
        MvText(text, MvType.Small, color = MvColors.Text)
    }
}
