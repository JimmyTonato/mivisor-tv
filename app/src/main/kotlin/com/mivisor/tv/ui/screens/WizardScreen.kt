package com.mivisor.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.mivisor.tv.ui.InitialFocus
import com.mivisor.tv.ui.design.MvButton
import com.mivisor.tv.ui.design.MvColors
import com.mivisor.tv.ui.design.MvDims
import com.mivisor.tv.ui.design.MvIcons
import com.mivisor.tv.ui.design.MvText
import com.mivisor.tv.ui.design.MvType

/** Primera vez: qué es MiVisor TV y añadir el primer proveedor. */
@Composable
fun WizardScreen(onStart: () -> Unit, onSkip: () -> Unit) {
    val start = remember { FocusRequester() }
    Column(
        Modifier.fillMaxSize().padding(horizontal = MvDims.SafeX * 2, vertical = MvDims.SafeY * 2),
        verticalArrangement = Arrangement.Center
    ) {
        MvText("BIENVENIDO", MvType.Eyebrow)
        Spacer(Modifier.height(10.dp))
        MvText("MiVisor TV", MvType.Display)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.widthIn(max = 640.dp)) {
            MvText("Junta los canales de tus proveedores, te enseña la agenda deportiva y elige sola la fuente que mejor funciona en esta tele.", MvType.Body.copy(color = MvColors.Text2))
            Spacer(Modifier.height(12.dp))
            MvText("Todo se queda en este aparato: los códigos de tus proveedores se guardan cifrados y nunca se muestran.", MvType.Small)
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MvButton("Añadir mi primer proveedor", onStart, icon = MvIcons.Add, primary = true, focusRequester = start)
            MvButton("Más tarde", onSkip)
        }
    }
    InitialFocus(start)
}
