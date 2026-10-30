package com.mivisor.tv.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tokens del diseño premium de la fase 2 (los mismos que el prototipo navegable).
 * Medidas en dp/sp sobre el lienzo de TV de 960 × 540 dp (1080p a densidad 2,0): un texto de 12 sp mide 24 px en 1080p.
 */
object MvColors {
    val Bg = Color(0xFF07090D)
    val Surface1 = Color(0xFF0F131A)
    val Surface2 = Color(0xFF151A23)
    val Surface3 = Color(0xFF1C222D)
    val Surface4 = Color(0xFF262D3A)
    val Text = Color(0xFFF3F6FA)
    val Text2 = Color(0xFFB3BDCA)
    val Text3 = Color(0xFF8A95A5)
    val Accent = Color(0xFF3D8BFF)
    val Accent2 = Color(0xFF7CC4FF)
    val Live = Color(0xFFFF3D4F)
    val Soon = Color(0xFFFFB547)
    val Ok = Color(0xFF3DDC84)
    val Bad = Color(0xFFFF6B6B)
    val Focus = Color(0xFFFFFFFF)
    val Scrim = Color(0xCC07090D)

    /** Tintes de las placas de texto de los canales (sin logos en 3a). */
    val Plates = listOf(
        Color(0xFF1E3A5F), Color(0xFF3B2A5C), Color(0xFF14463F), Color(0xFF5A2B2B),
        Color(0xFF4A3B17), Color(0xFF233B55), Color(0xFF3D2240), Color(0xFF1F4730)
    )
    fun plate(key: String): Color = Plates[(key.hashCode() and 0x7fffffff) % Plates.size]
}

object MvFonts {
    val Inter = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold)
    )
}

/** Escala tipográfica: nada por debajo de 12 sp (legible a 3 m en 720p, 1080p y 4K). */
object MvType {
    private fun s(size: Int, weight: FontWeight, line: Int, color: Color = MvColors.Text) =
        TextStyle(fontFamily = MvFonts.Inter, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, color = color)

    val Display = s(44, FontWeight.Bold, 50)
    val Title = s(28, FontWeight.Bold, 34)
    val Heading = s(20, FontWeight.SemiBold, 26)
    val Body = s(16, FontWeight.Normal, 22)
    val BodyStrong = s(16, FontWeight.SemiBold, 22)
    val Small = s(14, FontWeight.Normal, 19, MvColors.Text2)
    val SmallStrong = s(14, FontWeight.SemiBold, 19)
    val Caption = s(12, FontWeight.Medium, 16, MvColors.Text3)
    val Eyebrow = s(12, FontWeight.Bold, 16, MvColors.Accent2).copy(letterSpacing = 1.2.sp)
}

object MvDims {
    /** Zona segura de TV (5 %). */
    val SafeX = 48.dp
    val SafeY = 27.dp
    val Gap = 16.dp
    val Radius = 12.dp
    val RadiusLarge = 16.dp
    val FocusRing = 3.dp
    const val FocusScale = 1.06f
}

/** Modo ligero (TVs con poca memoria): sin animaciones ni escalados. */
val LocalLite = staticCompositionLocalOf { false }

@Composable
fun MvTheme(lite: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLite provides lite, content = content)
}
