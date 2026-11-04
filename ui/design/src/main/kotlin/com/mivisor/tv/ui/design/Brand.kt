package com.mivisor.tv.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Símbolo «Órbita» del diseño 06 (marca/orbita.svg, lienzo 120 × 120): arco abierto en degradado marfil → verde agua,
 * punto central champán y el trazo de señal. [spinning] lo hace girar (arranque y carga del reproductor).
 */
@Composable
fun OrbitaMark(size: Dp, modifier: Modifier = Modifier, spinning: Boolean = false, mono: Color? = null) {
    val angle = if (spinning && !LocalLite.current) {
        val t = rememberInfiniteTransition(label = "orbita")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "giro").value
    } else 0f
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 120f
        val arcBrush = if (mono != null) Brush.linearGradient(listOf(mono, mono))
        else Brush.linearGradient(listOf(Color(0xFFF4DEBC), Color(0xFF93E6D1)), Offset.Zero, Offset(this.size.width, this.size.height))
        rotate(angle) {
            // circle r=41, stroke 10, dasharray 190/68 → arco de 265,5°, girado −28°.
            drawArc(
                brush = arcBrush, startAngle = -28f, sweepAngle = 265.5f, useCenter = false,
                topLeft = Offset((60 - 41) * k, (60 - 41) * k), size = Size(82 * k, 82 * k),
                style = Stroke(width = 10 * k, cap = StrokeCap.Round)
            )
            drawLine(mono ?: Color(0xFF93E6D1), Offset(74 * k, 24 * k), Offset(88 * k, 15 * k), strokeWidth = 8 * k, cap = StrokeCap.Round)
        }
        drawCircle(mono ?: Color(0xFFEFD0A6), radius = 11 * k, center = Offset(60 * k, 60 * k))
    }
}

/** «MiVisor TV» con «TV» en champán. */
@Composable
fun MvWordmark(fontSize: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    MvText(
        buildAnnotatedString {
            append("MiVisor ")
            withStyle(SpanStyle(color = MvColors.Accent)) { append("TV") }
        },
        MvType.Heading.copy(fontSize = fontSize, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        modifier = modifier
    )
}

/** Símbolo + logotipo en una fila. */
@Composable
fun MvBrand(mark: Dp = 28.dp, fontSize: TextUnit = 22.sp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OrbitaMark(mark)
        androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        MvWordmark(fontSize)
    }
}
