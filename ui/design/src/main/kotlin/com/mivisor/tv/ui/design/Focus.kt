package com.mivisor.tv.ui.design

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/** Estado de la pulsación de OK; fuera del estado de Compose (no provoca recomposición). */
private class PressState {
    var down = false
    var longFired = false
}

/**
 * Pieza base de toda la interfaz: un elemento enfocable con el mando (de la v18, con el foco del diseño nuevo:
 * anillo blanco y escala 1,06).
 *
 *  - OK / Enter: clic al SOLTAR y solo si la pulsación empezó aquí (evita que el «soltar» de una pulsación anterior
 *    active lo que acaba de recibir el foco, p. ej. al abrir un diálogo).
 *  - OK mantenido o MENU/INFO: acción secundaria.
 *  - Táctil o ratón: toque y pulsación larga.
 *  - El escalado va en la capa gráfica: no relanza el layout.
 */
@Composable
fun TvClickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(MvDims.Radius),
    color: Color = MvColors.Surface2,
    focusedColor: Color = MvColors.Surface4,
    focusedScale: Float = MvDims.FocusScale,
    focusRequester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val press = remember { PressState() }
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val currentFocus by rememberUpdatedState(onFocus)
    val lite = LocalLite.current

    val target = if (focused && enabled && !lite) focusedScale else 1f
    val scale = if (lite) rememberUpdatedState(target) else animateFloatAsState(target, tween(durationMillis = 120), label = "focusScale")

    Box(
        modifier = modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                val s = scale.value
                scaleX = s
                scaleY = s
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                val was = focused
                focused = it.isFocused
                if (!it.isFocused) press.down = false
                if (it.isFocused && !was) currentFocus?.invoke()
            }
            .onKeyEvent { event ->
                if (!enabled) return@onKeyEvent false
                val native = event.nativeKeyEvent
                when (native.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER, AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                        when (native.action) {
                            AndroidKeyEvent.ACTION_DOWN -> {
                                if (native.repeatCount == 0) {
                                    press.down = true
                                    press.longFired = false
                                } else if (press.down && !press.longFired && currentLongClick != null &&
                                    (native.isLongPress || native.repeatCount >= LONG_PRESS_REPEATS)
                                ) {
                                    press.longFired = true
                                    currentLongClick?.invoke()
                                }
                                true
                            }
                            AndroidKeyEvent.ACTION_UP -> {
                                val wasDown = press.down
                                press.down = false
                                if (wasDown && !press.longFired) currentClick()
                                true
                            }
                            else -> false
                        }
                    }
                    AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_INFO -> {
                        val long = currentLongClick ?: return@onKeyEvent false
                        if (native.action == AndroidKeyEvent.ACTION_UP) long()
                        true
                    }
                    else -> false
                }
            }
            .focusable(enabled)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onTap = { currentClick() }, onLongPress = { currentLongClick?.invoke() })
            }
            .then(if (enabled) Modifier else Modifier.alpha(0.45f))
            .background(if (focused) focusedColor else color, shape)
            .then(if (focused) Modifier.border(MvDims.FocusRing, MvColors.Focus, shape) else Modifier),
        contentAlignment = contentAlignment
    ) {
        content(focused)
    }
}

/** ~0,5 s a la cadencia de repetición típica de los mandos (≈ 50 ms). */
private const val LONG_PRESS_REPEATS = 10
