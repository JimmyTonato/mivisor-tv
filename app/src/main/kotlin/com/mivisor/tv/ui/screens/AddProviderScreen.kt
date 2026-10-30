package com.mivisor.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.mivisor.tv.AppGraph
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.MvTextField
import com.mivisor.tv.ui.PhoneInputPanel
import com.mivisor.tv.ui.Route
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvRow
import com.mivisor.tv.ui.design.MvSwitch
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType
import com.mivisor.tv.ui.design.Spinner
import kotlinx.coroutines.launch

/** Añadir proveedor: el código se comprueba antes de guardarlo (cifrado) y no vuelve a mostrarse. */
@Composable
fun AddProviderScreen(graph: AppGraph, route: Route.AddProvider, onDone: () -> Unit) {
    var code by remember { mutableStateOf(route.prefill.orEmpty()) }
    var name by remember { mutableStateOf("") }
    var warp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var phone by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val codeField = remember { FocusRequester() }

    fun submit() {
        if (busy) return
        if (code.isBlank()) { error = "Escribe el código del proveedor"; return }
        busy = true
        error = null
        scope.launch {
            val problem = graph.providers.add(code, name, warp)
            busy = false
            if (problem == null) {
                code = ""
                onDone()
            } else error = problem
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = MvDims.SafeX).widthIn(max = 760.dp)) {
            MvText("Añadir proveedor", MvType.Title)
            Spacer(Modifier.height(6.dp))
            MvText("Escribe el código que te dio tu proveedor. Se comprueba ahora y se guarda cifrado en esta tele; después no se vuelve a mostrar.", MvType.Small)
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                MvTextField(code, { code = it; error = null }, "Código", Modifier.weight(1f), placeholder = "código del proveedor", focusRequester = codeField, onDone = { submit() })
                Spacer(Modifier.width(16.dp))
                MvButton("Escribir con el móvil", { phone = true }, icon = MvIcons.Phone)
            }
            Spacer(Modifier.height(18.dp))
            MvTextField(name, { name = it }, "Nombre (opcional)", placeholder = "p. ej. Casa, Deportes…")
            Spacer(Modifier.height(18.dp))
            MvRow(
                "Este proveedor necesita WARP",
                { warp = !warp },
                subtitle = "Si WARP no está conectada, sus fallos no cuentan contra las fuentes.",
                icon = MvIcons.Shield
            ) { MvSwitch(warp) }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                MvButton(if (busy) "Comprobando…" else "Comprobar y añadir", { submit() }, icon = MvIcons.Add, primary = true, enabled = !busy)
                if (busy) Spinner(28.dp)
            }
            error?.let {
                Spacer(Modifier.height(16.dp))
                MvText(it, MvType.BodyStrong, color = MvColors.Bad)
            }
        }
        if (phone) {
            BackHandler { phone = false }
            Box(Modifier.fillMaxSize().background(MvColors.Scrim), contentAlignment = Alignment.Center) {
                PhoneInputPanel(onText = { code = it.trim(); error = null }, onClose = { phone = false })
            }
        }
    }
    InitialFocus(codeField)
}
