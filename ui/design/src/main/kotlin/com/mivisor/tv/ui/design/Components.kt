package com.mivisor.tv.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Texto con el estilo del diseño (sin Material). */
@Composable
fun MvText(
    text: String,
    style: TextStyle = MvType.Body,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (color != Color.Unspecified) style.copy(color = color) else style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/** Botón de TV: primario (acento) o secundario (superficie). */
@Composable
fun MvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    TvClickable(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = if (primary) MvColors.Accent else MvColors.Surface3,
        focusedColor = if (primary) MvColors.Accent2 else MvColors.Text,
        focusRequester = focusRequester,
        contentAlignment = Alignment.Center
    ) { focused ->
        val fg = if (focused) MvColors.Bg else MvColors.Text
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                MvIcon(icon, size = 20.dp, tint = fg)
                Spacer(Modifier.width(10.dp))
            }
            MvText(text, MvType.BodyStrong, color = fg, maxLines = 1)
        }
    }
}

/** Fila de lista de Ajustes / fuentes: título, detalle y algo a la derecha. */
@Composable
fun MvRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    trailing: String? = null,
    trailingColor: Color = MvColors.Text2,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    extra: (@Composable () -> Unit)? = null
) {
    TvClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        focusedScale = 1.02f,
        focusRequester = focusRequester
    ) { _ ->
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                MvIcon(icon, size = 22.dp, tint = MvColors.Text2)
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                MvText(title, MvType.BodyStrong, maxLines = 1)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    MvText(subtitle, MvType.Small, maxLines = 2)
                }
            }
            if (extra != null) {
                Spacer(Modifier.width(12.dp))
                extra()
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                MvText(trailing, MvType.SmallStrong, color = trailingColor, maxLines = 1, modifier = Modifier.widthIn(max = 260.dp))
            }
        }
    }
}

/** Interruptor visual (el estado lo lleva la fila que lo contiene). */
@Composable
fun MvSwitch(on: Boolean) {
    Box(
        Modifier.size(width = 44.dp, height = 24.dp)
            .background(if (on) MvColors.Accent else MvColors.Surface4, RoundedCornerShape(12.dp)),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.padding(3.dp).size(18.dp).background(MvColors.Text, CircleShape))
    }
}

/** Etiqueta pequeña: «EN DIRECTO», «HD», «Pausa 10 min»… */
@Composable
fun MvTag(text: String, color: Color = MvColors.Text2, filled: Boolean = false) {
    Box(
        Modifier
            .background(if (filled) color else color.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        MvText(text, MvType.Caption, color = if (filled) MvColors.Bg else color, maxLines = 1)
    }
}

@Composable
fun LiveDot(color: Color = MvColors.Live, size: Dp = 8.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** Encabezado de sección con contador opcional. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, detail: String? = null) {
    Row(modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        MvText(title, MvType.Heading)
        if (detail != null) {
            Spacer(Modifier.width(12.dp))
            MvText(detail, MvType.Small)
        }
    }
}

/** Panel de superficie con margen interior. */
@Composable
fun MvPanel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(24.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.background(MvColors.Surface1, RoundedCornerShape(MvDims.RadiusLarge))
            .border(1.dp, MvColors.Surface3, RoundedCornerShape(MvDims.RadiusLarge))
            .padding(padding),
        content = content
    )
}

/** Placa de canal (sin logos en 3a): iniciales sobre un tinte estable. */
@Composable
fun ChannelPlate(initials: String, key: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Box(modifier.background(MvColors.plate(key), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        MvText(initials, if (large) MvType.Title else MvType.Heading, maxLines = 1)
    }
}

/** Tarjeta de canal para las rejillas y filas. */
@Composable
fun ChannelCard(
    name: String,
    initials: String,
    key: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    live: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    TvClickable(
        onClick = onClick, onLongClick = onLongClick, onFocus = onFocus, focusRequester = focusRequester,
        modifier = modifier.width(200.dp), color = MvColors.Surface1, focusedColor = MvColors.Surface3
    ) { _ ->
        Column(Modifier.padding(10.dp)) {
            Box {
                ChannelPlate(initials, key, Modifier.fillMaxWidth().height(96.dp))
                if (live) Box(Modifier.padding(8.dp)) { MvTag("EN DIRECTO", MvColors.Live, filled = true) }
            }
            Spacer(Modifier.height(10.dp))
            MvText(name, MvType.BodyStrong, maxLines = 1)
            MvText(subtitle ?: " ", MvType.Caption, maxLines = 1)
        }
    }
}

/** Indicador de carga: arco que gira (sin animación en modo ligero). */
@Composable
fun Spinner(size: Dp = 32.dp, color: Color = MvColors.Accent2) {
    val lite = LocalLite.current
    val angle = if (lite) 0f else {
        val t = rememberInfiniteTransition(label = "spin")
        val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "a")
        a
    }
    Canvas(Modifier.size(size)) {
        val w = 3.dp.toPx()
        drawArc(
            color = color, startAngle = angle, sweepAngle = 270f, useCenter = false,
            topLeft = Offset(w / 2, w / 2),
            size = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w),
            style = Stroke(width = w, cap = StrokeCap.Round)
        )
    }
}

/** Estado vacío o de error con acciones. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        MvText(title, MvType.Title)
        Spacer(Modifier.height(12.dp))
        MvText(body, MvType.Body.copy(color = MvColors.Text2), modifier = Modifier.widthIn(max = 560.dp))
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { actions() }
    }
}

/** Diálogo modal a pantalla completa con fondo atenuado. */
@Composable
fun MvDialog(title: String, body: String, actions: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MvColors.Scrim), contentAlignment = Alignment.Center) {
        MvPanel(Modifier.widthIn(max = 620.dp), PaddingValues(32.dp)) {
            MvText(title, MvType.Title)
            Spacer(Modifier.height(12.dp))
            MvText(body, MvType.Body.copy(color = MvColors.Text2))
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { actions() }
        }
    }
}
