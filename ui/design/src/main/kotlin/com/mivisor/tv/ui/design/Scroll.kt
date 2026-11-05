package com.mivisor.tv.ui.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

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

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // Lo enfocado ocupa casi toda la pantalla: se alinea arriba con un pequeño margen.
        if (size >= containerSize * 0.7f) return offset - containerSize * 0.04f
        return offset - containerSize * PIVOT
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
