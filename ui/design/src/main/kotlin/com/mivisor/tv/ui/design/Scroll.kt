package com.mivisor.tv.ui.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.relocation.bringIntoViewResponder

/**
 * 3h · Desplazamiento «con pivote», como Google TV: al bajar con el mando, la fila enfocada se queda SIEMPRE a la
 * misma altura (≈28 % desde arriba) y es la lista la que se mueve, siempre la misma distancia.
 * Antes, cada movimiento desplazaba «lo justo» (a veces nada, a veces media pantalla): eso eran los saltos raros
 * al ir rápido. Solo para listas VERTICALES: las filas horizontales de dentro siguen con el comportamiento normal
 * ([DefaultScroll]).
 */
@OptIn(ExperimentalFoundationApi::class)
private object PivotBringIntoView : BringIntoViewSpec {
    private const val PIVOT = 0.28f

    /** 4n · Desplazamiento corto y rápido (antes, el muelle por defecto: lento y se encolaba al mantener abajo). */
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> = FAST_SCROLL

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // Lo enfocado ocupa casi toda la pantalla: se alinea arriba con un pequeño margen.
        if (size >= containerSize * 0.7f) return offset - containerSize * 0.04f
        // 4n · Zona cómoda: si ya se ve entero y no está pegado a los bordes, NO se mueve nada. Así la primera pantalla
        // (portada, ficha de un partido) no se desplaza al recibir el foco, y bajar entre filas visibles no cuesta un
        // desplazamiento animado por cada pulsación.
        if (offset >= containerSize * 0.12f && offset + size <= containerSize * 0.86f) return 0f
        return offset - containerSize * PIVOT
    }
}

/** 4n · Animación de desplazamiento de la tele: 160 ms, sin rebote. */
private val FAST_SCROLL: androidx.compose.animation.core.AnimationSpec<Float> =
    androidx.compose.animation.core.tween(durationMillis = 160, easing = androidx.compose.animation.core.LinearOutSlowInEasing)

/**
 * 4n · El comportamiento normal de Compose (lo justo para que se vea) pero con la animación rápida. Se pone en la raíz
 * de la app ([TvScrollRoot]): Ajustes, filas horizontales, paneles…
 */
@OptIn(ExperimentalFoundationApi::class)
private object FastBringIntoView : BringIntoViewSpec {
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> = FAST_SCROLL

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailing = offset + size
        val leading = offset
        return when {
            leading >= 0 && trailing <= containerSize -> 0f
            leading < 0 && trailing > containerSize -> 0f
            kotlin.math.abs(leading) < kotlin.math.abs(trailing - containerSize) -> leading
            else -> trailing - containerSize
        }
    }
}

/**
 * 4o · Tope en la raíz: lo que ninguna lista ha podido mostrar NO sube a Android. Si subía, Android desplazaba la
 * ventana entera hacia arriba para enseñarlo, y la barra de arriba (Inicio, Canales…) se salía de la pantalla (pasaba
 * en la ficha de un partido en la tele).
 */
@OptIn(ExperimentalFoundationApi::class)
private object NoWindowPan : androidx.compose.foundation.relocation.BringIntoViewResponder {
    override fun calculateRectForParent(localRect: androidx.compose.ui.geometry.Rect): androidx.compose.ui.geometry.Rect =
        androidx.compose.ui.geometry.Rect.Zero

    override suspend fun bringChildIntoView(localRect: () -> androidx.compose.ui.geometry.Rect?) = Unit
}

/** 4n · En la tele, toda la app usa el desplazamiento rápido (en táctil no cambia nada). 4o · Y nunca mueve la ventana. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvScrollRoot(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().bringIntoViewResponder(NoWindowPan)
    ) {
        if (LocalUiForm.current.touch) content()
        else CompositionLocalProvider(LocalBringIntoViewSpec provides FastBringIntoView) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private val LocalOuterBringIntoView = staticCompositionLocalOf<BringIntoViewSpec?> { null }

/** Envuelve una lista VERTICAL de la tele para que se desplace con pivote (en táctil no cambia nada). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotScroll(content: @Composable () -> Unit) {
    if (LocalUiForm.current.touch) {
        content()
        return
    }
    val default = LocalBringIntoViewSpec.current
    CompositionLocalProvider(LocalBringIntoViewSpec provides PivotBringIntoView, LocalOuterBringIntoView provides default) { content() }
}

/** Dentro de [PivotScroll]: una fila horizontal vuelve al desplazamiento normal. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DefaultScroll(content: @Composable () -> Unit) {
    val outer = LocalOuterBringIntoView.current
    if (outer == null) content() else CompositionLocalProvider(LocalBringIntoViewSpec provides outer) { content() }
}

/**
 * 3h.1 · Fracción 0..1 que avanza en bucle cada [periodMs], movida por el RELOJ DE PANTALLA (withFrameNanos), no por
 * el sistema de animaciones. Muchas teles traen las animaciones del sistema desactivadas (escala 0) y entonces las
 * animaciones de Compose se quedan quietas: los indicadores de carga parecían congelados. Leerla solo en la fase de
 * dibujo (graphicsLayer / drawBehind) para no recomponer la pantalla en cada fotograma.
 */
@Composable
fun rememberLoop(periodMs: Int): androidx.compose.runtime.State<Float> {
    val v = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(periodMs) {
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            androidx.compose.runtime.withFrameNanos { t -> v.floatValue = (((t - start) / 1_000_000L) % periodMs) / periodMs.toFloat() }
        }
    }
    return v
}
