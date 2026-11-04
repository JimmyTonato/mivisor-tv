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
 * Tokens del sistema de diseño 06 («Arena editorial»): grafito, marfil, champán y verde agua.
 * Medidas en dp/sp sobre el lienzo de TV de 960 × 540 dp (1080p a densidad 2,0). El lienzo del diseñador es de
 * 1600 × 900 px: 1 px del diseño = 0,6 dp aquí (con un mínimo de 12 sp para el texto, legible a 3 m).
 */
object MvColors {
    /** background.0 */
    val Bg = Color(0xFF080F19)
    /** navigation.0: rail y barra superior. */
    val Nav = Color(0xFF091421)
    /** surface.1: tarjetas secundarias. */
    val Surface1 = Color(0xFF132232)
    /** surface.2: contenedores con interacción. */
    val Surface2 = Color(0xFF1A2A39)
    /** surface.focus */
    val Surface3 = Color(0xFF223546)
    val Surface4 = Color(0xFF2A4153)
    /** stroke.default */
    val Stroke = Color(0xFF304555)
    val Text = Color(0xFFF7F0E6)
    val Text2 = Color(0xFFB8C5CE)
    val Text3 = Color(0xFF8D9FAA)
    /** action.primary (champán): llamadas a la acción y selección. */
    val Accent = Color(0xFFE9C99E)
    /** action.focus */
    val Accent2 = Color(0xFFFFE1B8)
    /** status.live */
    val Live = Color(0xFFE58C83)
    /** Fondo de la etiqueta EN DIRECTO (rojo apagado del diseño). */
    val LiveBg = Color(0xFF5A2A2A)
    val Soon = Color(0xFFE8B86D)
    /** status.good */
    val Ok = Color(0xFF9FD6C8)
    /** status.error */
    val Bad = Color(0xFFEF9185)
    val Focus = Color(0xFFFFE1B8)
    val Scrim = Color(0xCC080F19)

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
    /** Sin escalados que invadan a los vecinos (diseño 06): apenas un 2 %. */
    const val FocusScale = 1.02f
}

/**
 * 3f · Tipo de pantalla en la que se dibuja la app (la misma APK sirve en tele, tablet y móvil).
 * Las pantallas lo leen para elegir el menú, el número de columnas y si hace falta enseñar botones táctiles.
 */
enum class UiForm { TV, TABLET, PHONE;
    val touch: Boolean get() = this != TV
}

val LocalUiForm = staticCompositionLocalOf { UiForm.TV }

/** Modo ligero (TVs con poca memoria): sin animaciones ni escalados. */
val LocalLite = staticCompositionLocalOf { false }

@Composable
fun MvTheme(lite: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLite provides lite, content = content)
}
