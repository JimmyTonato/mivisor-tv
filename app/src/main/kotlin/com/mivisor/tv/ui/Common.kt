package com.mivisor.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mivisor.tv.data.AndroidLan
import com.mivisor.tv.remote.MobileInputManager
import com.mivisor.tv.remote.QrMatrix
import com.mivisor.tv.remote.RemoteState
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvPanel
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pide el foco para [requester] al entrar en la pantalla (si el elemento ya está compuesto). */
@Composable
fun InitialFocus(requester: FocusRequester, key: Any? = Unit) {
    LaunchedEffect(key) {
        kotlinx.coroutines.delay(60)
        runCatching { requester.requestFocus() }
    }
}

/**
 * Campo de texto para TV: con el mando, ↑/↓ salen del campo (no se queda atrapado) y OK abre el teclado.
 */
@Composable
fun MvTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
    onDone: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val ime = LocalSoftwareKeyboardController.current
    Column(modifier) {
        MvText(label, MvType.SmallStrong, color = MvColors.Text2)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth()
                .background(MvColors.Surface2, RoundedCornerShape(10.dp))
                .border(if (focused) 3.dp else 1.dp, if (focused) MvColors.Focus else MvColors.Surface4, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) MvText(placeholder, MvType.Body, color = MvColors.Text3, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MvType.Body,
                cursorBrush = SolidColor(MvColors.Accent2),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { ime?.hide(); onDone() }),
                modifier = Modifier.fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        val n = e.nativeKeyEvent
                        if (n.action != AndroidKeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                        when (n.keyCode) {
                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { focus.moveFocus(FocusDirection.Down); true }
                            AndroidKeyEvent.KEYCODE_DPAD_UP -> { focus.moveFocus(FocusDirection.Up); true }
                            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> { ime?.show(); true }
                            else -> false
                        }
                    }
            )
        }
    }
}

/** Código QR dibujado con módulos (sin imágenes). */
@Composable
fun QrView(matrix: QrMatrix, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color.White, RoundedCornerShape(8.dp)).padding(10.dp)) {
        val cell = size.minDimension / matrix.size
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/**
 * «Escribir con el móvil»: QR con una dirección de la red de casa; el texto llega al campo sin guardarse.
 * El servidor solo existe mientras se ve este panel.
 */
@Composable
fun PhoneInputPanel(onText: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { MobileInputManager(scope, { AndroidLan.pick(context) }) }
    val state by manager.state.collectAsState()
    val close = remember { FocusRequester() }
    DisposableEffect(Unit) {
        manager.startInput()
        onDispose { manager.cancel() }
    }
    LaunchedEffect(state) {
        val s = state
        if (s is RemoteState.Received) {
            onText(s.text)
            manager.consume()
            onClose()
        }
    }
    InitialFocus(close)
    MvPanel(Modifier.width(560.dp)) {
        MvText("Escribir con el móvil", MvType.Heading)
        Spacer(Modifier.height(8.dp))
        when (val s = state) {
            is RemoteState.Waiting -> {
                MvText("Escanea el código con un móvil conectado a la misma Wi-Fi y escribe allí.", MvType.Small)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrView(s.qr, Modifier.size(180.dp))
                    Spacer(Modifier.width(20.dp))
                    Column {
                        MvText("O abre en el navegador:", MvType.Caption)
                        MvText(s.url, MvType.BodyStrong)
                        Spacer(Modifier.height(8.dp))
                        MvText("Caduca a las ${hhmm(s.expiresAt)}", MvType.Caption)
                    }
                }
            }
            is RemoteState.Failed -> MvText(
                if (s.reason == RemoteState.Reason.NO_LAN) "La tele no está en una red de casa (Wi-Fi o cable): no se puede usar el móvil."
                else "No se pudo abrir el servidor local para el móvil.", MvType.Small
            )
            RemoteState.Expired -> MvText("El código ha caducado. Ciérralo y vuelve a abrirlo.", MvType.Small)
            else -> MvText("Preparando…", MvType.Small)
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MvButton("Cerrar", onClose, icon = MvIcons.Close, focusRequester = close)
        }
    }
}

private val ES = Locale("es", "ES")

fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", ES).format(Date(t))
fun dayLabel(t: Long): String = SimpleDateFormat("EEE d MMM", ES).format(Date(t))

/** «hace 5 min», «hace 2 h», «ayer»… */
fun ago(t: Long, now: Long = System.currentTimeMillis()): String {
    if (t <= 0) return "nunca"
    val m = (now - t) / 60_000
    return when {
        m < 1 -> "ahora mismo"
        m < 60 -> "hace $m min"
        m < 24 * 60 -> "hace ${m / 60} h"
        m < 48 * 60 -> "ayer"
        else -> "hace ${m / (24 * 60)} días"
    }
}

/**
 * Tarjeta «Escribe desde el móvil» del diseño (asistente y Añadir proveedor): QR siempre visible a la derecha.
 * Cada texto recibido se entrega a [onText] y se abre otra sesión para poder enviar más.
 */
@Composable
fun PhoneQrCard(title: String, body: String, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { MobileInputManager(scope, { AndroidLan.pick(context) }) }
    val state by manager.state.collectAsState()
    DisposableEffect(Unit) {
        manager.startInput()
        onDispose { manager.cancel() }
    }
    LaunchedEffect(state) {
        val s = state
        if (s is RemoteState.Received) {
            onText(s.text)
            manager.consume()
            kotlinx.coroutines.delay(800)
            manager.startInput()
        }
    }
    MvPanel(modifier, androidx.compose.foundation.layout.PaddingValues(24.dp)) {
        MvText(title, MvType.Heading, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(6.dp))
        MvText(body, MvType.Small, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            is RemoteState.Waiting -> {
                QrView(s.qr, Modifier.size(170.dp).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.mivisor.tv.ui.design.MvBadge("Solo red de casa", MvColors.Ok)
                    com.mivisor.tv.ui.design.MvBadge("Caduca a las ${hhmm(s.expiresAt)}", MvColors.Text2)
                }
            }
            is RemoteState.Failed -> MvText(
                if (s.reason == RemoteState.Reason.NO_LAN) "La tele no está en una red de casa (Wi-Fi o cable): no se puede usar el móvil."
                else "No se pudo abrir el servidor local para el móvil.", MvType.Small
            )
            RemoteState.Expired -> MvText("El código ha caducado. Sal y vuelve a entrar para generar otro.", MvType.Small)
            else -> MvText("Preparando…", MvType.Small)
        }
    }
}
